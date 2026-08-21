#!/usr/bin/env python3
"""Generate the built-in Mossland TTS script from provider or cloned voices."""

from __future__ import annotations

import argparse
import csv
import json
import re
import uuid
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


REQUIRED_COLUMNS = {
    "音色ID",
    "音色名称",
    "场景",
    "风格",
    "语言代码",
    "口音代码",
    "性别",
    "年龄段",
    "使用次数",
    "收藏数",
    "来源类型",
}

GENDER_MAP = {"男": "male", "女": "female", "中性": "neutral"}
AGE_STAGE_MAP = {
    "幼年": "child",
    "少年": "teen",
    "青年": "young_adult",
    "中年": "mature",
    "老年": "senior",
}

API_AGE_STAGE_MAP = {
    "child": ("幼年", "child"),
    "juvenile": ("少年", "teen"),
    "adult": ("青年", "young_adult"),
    "middle": ("中年", "mature"),
    "elder": ("老年", "senior"),
}

AUDIOBOOK_IDENTITY_DENYLIST = re.compile(r"曾仕强", re.IGNORECASE)


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = REQUIRED_COLUMNS.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"{path}: missing columns: {', '.join(sorted(missing))}")
        rows = [{key: (value or "").strip() for key, value in row.items()} for row in reader]
    for row in rows:
        uuid.UUID(row["音色ID"])
    return rows


def popularity(row: dict[str, str]) -> tuple[int, int, str]:
    usage = int(row["使用次数"] or 0)
    favorites = int(row["收藏数"] or 0)
    return usage + favorites * 3, usage, row["音色ID"]


def deduplicate_by_name(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    selected: dict[str, dict[str, str]] = {}
    for row in rows:
        key = re.sub(r"\s+", "", row["音色名称"]).casefold()
        previous = selected.get(key)
        if previous is None or popularity(row) > popularity(previous):
            selected[key] = row
    return sorted(selected.values(), key=lambda row: (-popularity(row)[0], row["音色名称"]))


def split_tags(value: str) -> list[str]:
    return [part.strip() for part in re.split(r"[·,/，、|]+", value) if part.strip()]


def to_voice(row: dict[str, str], category: str) -> dict[str, object]:
    voice_id = row["音色ID"]
    tags = list(
        dict.fromkeys(
            [category, row["年龄段"], *split_tags(row["场景"]), *split_tags(row["风格"])]
        )
    )
    return {
        "id": voice_id,
        "name": row["音色名称"],
        "language": row["语言代码"],
        "gender": GENDER_MAP.get(row["性别"], ""),
        "style": row["风格"],
        "tags": tags,
        "extra": {
            "provider": "mossland",
            "provider_speaker": voice_id,
            "age_stage": AGE_STAGE_MAP.get(row["年龄段"], ""),
            "accent": row["口音代码"],
            "group": row["场景"],
            "catalog_category": category,
            "source_type": row["来源类型"],
            "profile_source": "provider_catalog",
        },
    }


def read_vv_catalog(path: Path) -> list[dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    voices = data.get("voices")
    if not isinstance(voices, list):
        raise ValueError(f"{path}: voices must be an array")
    unique: list[dict[str, object]] = []
    seen: set[str] = set()
    for voice in voices:
        name = str(voice.get("name") or "").strip()
        if name and name not in seen:
            seen.add(name)
            unique.append(voice)
    return unique


def fetch_public_cloned_voices(
    api_url: str,
    creator_user_id: str,
    expected_names: list[str],
) -> list[dict[str, object]]:
    expected = set(expected_names)
    selected: dict[str, dict[str, object]] = {}
    offset = 0
    limit = 100
    total: int | None = None
    while total is None or offset < total:
        query = urlencode(
            {
                "offset": offset,
                "limit": limit,
                "order_by": "created_at",
                "order_desc": "true",
                "show_recent_used": "false",
            }
        )
        request = Request(
            f"{api_url.rstrip('/')}?{query}",
            headers={"Accept": "application/json", "User-Agent": "Legado-NG-catalog/1"},
        )
        with urlopen(request, timeout=30) as response:
            payload = json.load(response)
        if payload.get("code") != 0:
            raise RuntimeError(f"Mossland voice list failed: {payload}")
        data = payload.get("data") or {}
        page = data.get("voices") or []
        total = int(data.get("total") or 0)
        for voice in page:
            name = str(voice.get("voice_name") or "").strip()
            if (
                str(voice.get("creator_user_id") or "") == creator_user_id
                and name in expected
            ):
                selected.setdefault(name, voice)
        if len(selected) == len(expected):
            break
        if not page:
            break
        offset += limit
    missing = [name for name in expected_names if name not in selected]
    if missing:
        raise RuntimeError(
            f"Mossland cloned catalog missing {len(missing)} voices: "
            + "、".join(missing[:20])
        )
    return [selected[name] for name in expected_names]


def to_cloned_voice(
    row: dict[str, object],
    vv_voice: dict[str, object],
) -> dict[str, object]:
    voice_id = str(row.get("voice_id") or "")
    uuid.UUID(voice_id)
    extra = vv_voice.get("extra") or {}
    age_label, age_stage = API_AGE_STAGE_MAP.get(
        str(row.get("age_range") or ""),
        ("", ""),
    )
    style_tags = [str(tag).strip() for tag in row.get("tags") or [] if str(tag).strip()]
    group = str(extra.get("group") or "").strip()
    vv_tags = [
        str(tag).strip()
        for tag in vv_voice.get("tags") or []
        if str(tag).strip() and str(tag).strip() != "VV"
    ]
    tags = list(dict.fromkeys(["有声书", age_label, group, *vv_tags, *style_tags]))
    return {
        "id": voice_id,
        "name": str(row.get("voice_name") or ""),
        "language": str(row.get("language") or "zh-CN"),
        "gender": str(row.get("gender") or vv_voice.get("gender") or ""),
        "style": "·".join(style_tags) or group,
        "tags": [tag for tag in tags if tag],
        "extra": {
            "provider": "mossland",
            "provider_speaker": voice_id,
            "age_stage": age_stage,
            "age_min": extra.get("age_min"),
            "age_max": extra.get("age_max"),
            "accent": str(row.get("accent") or ""),
            "group": group,
            "catalog_category": "有声书",
            "source_type": str(row.get("source_type") or "user_clone"),
            "profile_source": "vv_clone_catalog",
            "description": str(row.get("description") or ""),
            "vv_style": str(vv_voice.get("style") or ""),
            "vv_tags": vv_tags,
            "persona": str(extra.get("persona") or ""),
        },
    }


def render_script(voices: list[dict[str, object]]) -> str:
    catalog = json.dumps(voices, ensure_ascii=False, indent=4)
    return f'''// @name Mossland
// @schema 1
// @version 1.3.0
// @uuid mossland_moss_tts
// @author Legado NG
// @url https://api.mosi.cn/v1/audio/speech
// @enabled false
// @cookieJar false
// @audioType audio/mpeg
// @defaultSpeed 50
// @defaultVolume 50
// @defaultPitch 50
// @maxConcurrency 2
// @sampleText 前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。
// @capabilities casting_metadata
// @description Mossland 单人语音合成。内置 VV 全量复刻音色及画像，不在运行时请求发音人目录。

var MOSS_DEFAULT_BASE_URL = "https://api.mosi.cn";
var MOSS_VOICES = {catalog};

function trimText(value) {{
    return String(value || "").replace(/^\\s+|\\s+$/g, "");
}}

function baseUrl(options) {{
    return trimText(options && options.baseUrl || MOSS_DEFAULT_BASE_URL).replace(/\\/+$/, "");
}}

function outputFormat(options) {{
    return trimText(options && options.outputFormat).toLowerCase() === "wav" ? "wav" : "mp3";
}}

function options() {{
    return [
        {{ key: "apiKey", label: "Mossland API Key", type: "password", defaultValue: "" }},
        {{ key: "baseUrl", label: "服务地址", type: "text", defaultValue: MOSS_DEFAULT_BASE_URL }},
        {{ key: "version", label: "模型版本（可选）", type: "text", defaultValue: "" }},
        {{
            key: "outputFormat",
            label: "合成格式",
            type: "select",
            defaultValue: "mp3",
            values: [
                {{ label: "MP3", value: "mp3" }},
                {{ label: "WAV", value: "wav" }}
            ]
        }},
        {{ key: "timeout", label: "超时（秒）", type: "number", defaultValue: "120" }}
    ];
}}

function voices(options, ctx) {{
    return MOSS_VOICES;
}}

function synthesize(text, voice, params, options, ctx) {{
    var apiKey = trimText(options && options.apiKey);
    if (!apiKey) throw "请先填写 Mossland API Key";
    var voiceId = trimText(voice && voice.id);
    if (!voiceId) throw "请先选择 Mossland 发音人";
    var format = outputFormat(options);
    var payload = {{
        model: "moss-tts",
        input: String(text || ""),
        voice_id: voiceId,
        response_format: format,
        delivery_method: "audio"
    }};
    var version = trimText(options && options.version);
    if (version) payload.version = version;
    return {{
        url: baseUrl(options) + "/v1/audio/speech",
        method: "POST",
        headers: {{
            Authorization: "Bearer " + apiKey,
            "Content-Type": "application/json"
        }},
        requestContentType: "application/json",
        body: JSON.stringify(payload),
        audioContentType: format === "wav" ? "audio/wav" : "audio/mpeg",
        timeout: Number(options && options.timeout || 120),
        retry: 1
    }};
}}
'''


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audiobook-csv", type=Path)
    parser.add_argument("--vv-catalog-json", type=Path)
    parser.add_argument("--creator-user-id")
    parser.add_argument(
        "--voices-api-url",
        default="https://mossland.mosi.cn/api/v1/voices",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.vv_catalog_json:
        if not args.creator_user_id:
            parser.error("--vv-catalog-json requires --creator-user-id")
        vv_voices = read_vv_catalog(args.vv_catalog_json)
        names = [str(voice.get("name") or "") for voice in vv_voices]
        cloned_rows = fetch_public_cloned_voices(
            args.voices_api_url,
            args.creator_user_id,
            names,
        )
        voices = [
            to_cloned_voice(row, vv_voice)
            for row, vv_voice in zip(cloned_rows, vv_voices, strict=True)
        ]
    elif args.audiobook_csv:
        audiobook_rows = [
            row
            for row in read_rows(args.audiobook_csv)
            if not AUDIOBOOK_IDENTITY_DENYLIST.search(row["音色名称"])
        ]
        audiobook_rows = deduplicate_by_name(audiobook_rows)
        voices = [to_voice(row, "有声书") for row in audiobook_rows]
    else:
        parser.error("provide --vv-catalog-json or --audiobook-csv")
    voice_ids = [str(voice["id"]) for voice in voices]
    voice_names = [str(voice["name"]) for voice in voices]
    if len(set(voice_ids)) != len(voice_ids):
        raise RuntimeError("Mossland catalog contains duplicate voice IDs")
    if len(set(voice_names)) != len(voice_names):
        raise RuntimeError("Mossland catalog contains duplicate voice names")
    args.output.write_text(render_script(voices), encoding="utf-8", newline="\n")
    print(f"generated {len(voices)} Mossland voices")


if __name__ == "__main__":
    main()
