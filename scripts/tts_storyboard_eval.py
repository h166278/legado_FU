#!/usr/bin/env python3
"""Evaluate AI role attribution for novel TTS candidate units.

Usage:
  set SILICONFLOW_API_KEY=...
  python scripts/tts_storyboard_eval.py --book "C:\\path\\novel.txt" --chapters 1-3

The model must only return attribution for local candidate unit IDs. Original
text is reconstructed locally for review reports and must not appear in model
responses.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SKILL_ROOT = ROOT / "app/src/main/assets/skills/tts_storyboard"
PROTOCOL_SKILL_FILE = SKILL_ROOT / "modules/protocol.md"
BASE_ROUTING_SKILL_FILE = SKILL_ROOT / "modules/base-routing.md"
SCENE_CONTEXT_SKILL_FILE = SKILL_ROOT / "modules/scene-context.md"
PERFORMANCE_INSTRUCTION_SKILL_FILE = SKILL_ROOT / "modules/performance-instruction.md"
SCENE_SKILL_FILE = SKILL_ROOT / "modes/performance-scenes.md"
CAP_SCENE_CONTEXT = "scene_context"
CAP_PERFORMANCE_INSTRUCTION = "performance_instruction"
STORYBOARD_CAPABILITIES = {
    CAP_SCENE_CONTEXT,
    CAP_PERFORMANCE_INSTRUCTION,
}
API_URL = "https://api.siliconflow.cn/v1/chat/completions"
DEFAULT_MODEL = "Qwen/Qwen3-8B"
DEFAULT_OUT_DIR = Path("build/tts_storyboard_eval")
CHAPTER_RE = re.compile(
    r"(?m)^(第[0-9零一二三四五六七八九十百千万两]+[章节回卷部集].*)$"
)
QUOTE_PAIRS = {
    "“": "”",
    "‘": "’",
    "「": "」",
    "『": "』",
    '"': '"',
}
QUOTE_CLOSE_CANDIDATES = {
    "“": ("”", "“"),
    "‘": ("’", "‘"),
    "「": ("」",),
    "『": ("』",),
    '"': ('"', "”"),
}
SENTENCE_PUNCTUATION = set("。！？!?；;")
TEXT_LEAK_KEYS = {
    "text",
    "input",
    "content",
    "sourceText",
    "source_text",
    "output",
    "ranges",
    "start",
    "end",
}
UNIT_KEYS = {
    "unitId",
    "roleType",
    "characterName",
    "characterId",
    "speakerGender",
    "status",
    "confidence",
    "evidence",
    "performanceContext",
}
OPTIONAL_UNIT_KEYS = {"performanceInstruction"}
ROOT_KEYS = {"units", "newCharacters"}
ROLE_TYPES = {"narrator", "character", "thought", "other"}
STATUSES = {"assigned", "unknown"}
SPEAKER_GENDERS = {"male", "female", "unknown"}
DIALOGUE_CUES = (
    "说",
    "说道",
    "问",
    "问道",
    "道",
    "喊",
    "喊道",
    "叫",
    "叫道",
    "开口",
    "吐槽",
    "坦言",
    "回答",
    "答道",
    "回道",
    "回复",
    "嘀咕",
    "喃喃",
    "念道",
    "朗声",
    "低声",
    "提醒",
)
COLON_DIALOGUE_CUES = (
    "说",
    "说道",
    "问",
    "问道",
    "喊",
    "喊道",
    "叫",
    "叫道",
    "道",
    "开口",
    "吐槽",
    "坦言",
    "回答",
    "答道",
    "回道",
    "回复",
    "说了句",
    "喊上一句",
    "补了一句",
)
THOUGHT_CUES = ("心想", "心道", "暗道", "想道", "心里想", "心中想", "心里暗道", "心中暗道")


@dataclass
class Chapter:
    index: int
    title: str
    content: str


@dataclass(frozen=True)
class TextRange:
    paragraph_index: int
    start: int
    end: int


@dataclass
class CandidateUnit:
    unit_id: str
    kind: str
    role_hint: str
    ranges: list[TextRange]
    text_preview: str
    cue_before: str = ""
    cue_after: str = ""

    def to_payload(self) -> dict[str, Any]:
        return {
            "unitId": self.unit_id,
            "kind": self.kind,
            "roleHint": self.role_hint,
            "ranges": [
                {
                    "paragraphIndex": item.paragraph_index,
                    "start": item.start,
                    "end": item.end,
                }
                for item in self.ranges
            ],
            "textPreview": self.text_preview,
            "cueBefore": self.cue_before,
            "cueAfter": self.cue_after,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", help="TXT novel path")
    parser.add_argument(
        "--mcp-snapshot",
        help="MCP read_aloud_storyboard_debug_get JSON snapshot with include_payload=true",
    )
    parser.add_argument("--chapters", default="1", help="Chapter range, e.g. 1 or 1-3")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--api-url", default=API_URL)
    parser.add_argument("--api-key-env", default="SILICONFLOW_API_KEY")
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR))
    parser.add_argument("--max-chars", type=int, default=7000)
    parser.add_argument(
        "--mode",
        choices=("basic", "performance"),
        default="basic",
        help="Storyboard output mode. Performance mode requires per-unit scene context.",
    )
    parser.add_argument(
        "--scene-pass",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Run the engine-independent natural-scene boundary and title pass first.",
    )
    parser.add_argument(
        "--scene-only",
        action="store_true",
        help="Only generate and validate natural scenes; skip unit attribution.",
    )
    parser.add_argument(
        "--engine-capability",
        action="append",
        choices=tuple(sorted(STORYBOARD_CAPABILITIES)),
        default=[],
        help=(
            "Capability declared by the target TTS engine. Repeat for multiple values. "
            "performance_instruction automatically includes scene_context."
        ),
    )
    parser.add_argument("--max-tokens", type=int)
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--top-p", type=float)
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--sleep", type=float, default=0.5)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--retry-sleep", type=float, default=8.0)
    parser.add_argument(
        "--character",
        action="append",
        default=[],
        help="Known character as id:name[:alias1,alias2][:role]. Can be repeated.",
    )
    parser.add_argument(
        "--json-mode",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Send OpenAI-compatible response_format json_object.",
    )
    parser.add_argument(
        "--enable-thinking",
        choices=("true", "false", "omit"),
        default="omit",
        help="Optional Qwen3-style enable_thinking parameter.",
    )
    parser.add_argument(
        "--thinking-param",
        choices=("omit", "enable_thinking", "thinking"),
        default="omit",
        help="Optional provider thinking switch field. Use 'thinking' to match App DeepSeek-compatible requests.",
    )
    parser.add_argument(
        "--thinking-state",
        choices=("enabled", "disabled"),
        default="disabled",
    )
    return parser.parse_args()


def resolve_storyboard_capabilities(
    mode: str,
    capabilities: list[str] | tuple[str, ...] | set[str] | None = None,
) -> list[str]:
    if mode not in ("basic", "performance"):
        raise ValueError(f"unsupported storyboard mode: {mode}")
    if mode == "basic":
        return []
    requested = set(capabilities or (CAP_SCENE_CONTEXT,))
    unknown = requested - STORYBOARD_CAPABILITIES
    if unknown:
        raise ValueError(f"unsupported storyboard capabilities: {','.join(sorted(unknown))}")
    if CAP_PERFORMANCE_INSTRUCTION in requested:
        requested.add(CAP_SCENE_CONTEXT)
    return [
        capability
        for capability in (CAP_SCENE_CONTEXT, CAP_PERFORMANCE_INSTRUCTION)
        if capability in requested
    ]


def build_system_prompt(
    mode: str,
    capabilities: list[str] | tuple[str, ...] | set[str] | None = None,
) -> str:
    resolved = resolve_storyboard_capabilities(mode, capabilities)
    module_files = [PROTOCOL_SKILL_FILE, BASE_ROUTING_SKILL_FILE]
    if CAP_SCENE_CONTEXT in resolved:
        module_files.append(SCENE_CONTEXT_SKILL_FILE)
    if CAP_PERFORMANCE_INSTRUCTION in resolved:
        module_files.append(PERFORMANCE_INSTRUCTION_SKILL_FILE)
    return "\n\n---\n\n".join(
        extract_skill_body(path.read_text(encoding="utf-8")) for path in module_files
    )


def extract_skill_body(content: str) -> str:
    if not content.startswith("---"):
        return content.strip()
    end = re.search(r"\r?\n---(?:\r?\n|$)", content[3:])
    if not end:
        return content.strip()
    return content[3 + end.end() :].strip()


def read_text(path: Path) -> str:
    data = path.read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise UnicodeDecodeError("unknown", data, 0, min(len(data), 32), "unsupported encoding")


def load_repo_env() -> None:
    env_path = ROOT / ".env"
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key and key not in os.environ:
            os.environ[key] = value.strip()


def split_chapters(text: str) -> list[Chapter]:
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    matches = list(CHAPTER_RE.finditer(text))
    if not matches:
        return [Chapter(1, "全文", text.strip())]
    chapters: list[Chapter] = []
    for pos, match in enumerate(matches):
        start = match.end()
        end = matches[pos + 1].start() if pos + 1 < len(matches) else len(text)
        title = match.group(1).strip()
        content = text[start:end].strip()
        chapters.append(Chapter(pos + 1, title, content))
    return chapters


def parse_range(value: str, max_value: int) -> list[int]:
    if "-" in value:
        start_text, end_text = value.split("-", 1)
        start = int(start_text)
        end = int(end_text)
        return [i for i in range(start, min(end, max_value) + 1)]
    index = int(value)
    if index < 1 or index > max_value:
        raise ValueError(f"chapter index out of range: {index}")
    return [index]


def request_storyboard(
    api_url: str,
    api_key: str,
    model: str,
    payload: dict[str, Any],
    max_tokens: int | None,
    temperature: float,
    top_p: float | None,
    timeout: int,
    json_mode: bool,
    enable_thinking: str,
    thinking_param: str,
    thinking_state: str,
    retries: int,
    retry_sleep: float,
    system_prompt: str | None = None,
) -> tuple[dict[str, Any], str]:
    request_payload: dict[str, Any] = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt or build_system_prompt("basic")},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ],
        "temperature": temperature,
        "stream": False,
    }
    if top_p is not None:
        request_payload["top_p"] = top_p
    if max_tokens is not None:
        request_payload["max_tokens"] = max_tokens
    if json_mode:
        request_payload["response_format"] = {"type": "json_object"}
    if enable_thinking != "omit":
        request_payload["enable_thinking"] = enable_thinking == "true"
    if thinking_param == "enable_thinking":
        request_payload["enable_thinking"] = thinking_state == "enabled"
    elif thinking_param == "thinking":
        request_payload["thinking"] = {"type": thinking_state}
    body = json.dumps(request_payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        api_url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    last_error: Exception | None = None
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = json.loads(response.read().decode("utf-8"))
                content_text = raw["choices"][0]["message"]["content"]
                return raw, content_text
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            last_error = RuntimeError(f"HTTP {error.code}: {detail}")
            if error.code not in (429, 500, 502, 503, 504) or attempt >= retries:
                raise last_error from error
            time.sleep(retry_sleep)
    raise RuntimeError("request failed") from last_error


def build_scene_payload(payload: dict[str, Any]) -> dict[str, Any]:
    paragraphs = payload.get("contextParagraphs") or []
    indexes = [
        item.get("paragraphIndex")
        for item in paragraphs
        if isinstance(item, dict) and isinstance(item.get("paragraphIndex"), int)
    ]
    return {
        "chapter": payload.get("chapter") or {},
        "paragraphCount": len(indexes),
        "firstParagraphIndex": indexes[0] if indexes else None,
        "lastParagraphIndex": indexes[-1] if indexes else None,
        "contextParagraphs": paragraphs,
    }


def validate_scene_result(payload: dict[str, Any], result: dict[str, Any]) -> list[dict[str, Any]]:
    if set(result) != {"scenes"} or not isinstance(result.get("scenes"), list):
        raise ValueError("scene result must contain only a scenes array")
    paragraph_indexes = [
        item.get("paragraphIndex")
        for item in payload.get("contextParagraphs") or []
        if isinstance(item, dict) and isinstance(item.get("paragraphIndex"), int)
    ]
    if not paragraph_indexes:
        return []
    normalized: list[dict[str, Any]] = []
    scene_ids: set[str] = set()
    for index, item in enumerate(result["scenes"], 1):
        if not isinstance(item, dict) or set(item) != {
            "sceneId",
            "title",
            "startParagraphIndex",
            "endParagraphIndex",
        }:
            raise ValueError(f"scene {index} has invalid fields")
        scene_id = str(item.get("sceneId") or "").strip()
        title = " ".join(str(item.get("title") or "").split())
        start = item.get("startParagraphIndex")
        end = item.get("endParagraphIndex")
        if not scene_id or scene_id in scene_ids:
            raise ValueError(f"scene {index} has invalid sceneId")
        if not 2 <= len(title) <= 30:
            raise ValueError(f"scene {scene_id} has invalid title")
        if not isinstance(start, int) or not isinstance(end, int) or start > end:
            raise ValueError(f"scene {scene_id} has invalid range")
        scene_ids.add(scene_id)
        normalized.append(
            {
                "sceneId": scene_id,
                "title": title,
                "startParagraphIndex": start,
                "endParagraphIndex": end,
            }
        )
    normalized.sort(key=lambda item: item["startParagraphIndex"])
    coverage = {
        paragraph_index: [
            scene
            for scene in normalized
            if scene["startParagraphIndex"] <= paragraph_index <= scene["endParagraphIndex"]
        ]
        for paragraph_index in paragraph_indexes
    }
    invalid = [index for index, scenes in coverage.items() if len(scenes) != 1]
    if invalid:
        raise ValueError(f"scene ranges do not cover paragraphs exactly once: {invalid[:8]}")
    if normalized[0]["startParagraphIndex"] != paragraph_indexes[0]:
        raise ValueError("scene ranges do not start at the first paragraph")
    if normalized[-1]["endParagraphIndex"] != paragraph_indexes[-1]:
        raise ValueError("scene ranges do not end at the last paragraph")
    return normalized


def attach_scene_ranges(payload: dict[str, Any], scenes: list[dict[str, Any]]) -> None:
    payload["scenes"] = scenes
    for unit in payload.get("units") or []:
        ranges = unit.get("ranges") or []
        paragraph_index = ranges[0].get("paragraphIndex") if ranges else None
        scene = next(
            (
                item
                for item in scenes
                if isinstance(paragraph_index, int)
                and item["startParagraphIndex"] <= paragraph_index <= item["endParagraphIndex"]
            ),
            None,
        )
        if scene is None:
            raise ValueError(f"unit {unit.get('unitId')} does not belong to a scene")
        unit["sceneId"] = scene["sceneId"]


def extract_json(text: str) -> str:
    clean = text.strip()
    if clean.startswith("```"):
        clean = re.sub(r"^```(?:json)?\s*", "", clean)
        clean = re.sub(r"\s*```$", "", clean)
    first = clean.find("{")
    last = clean.rfind("}")
    if first >= 0 and last >= first:
        return clean[first : last + 1]
    return clean


def build_storyboard_payload(
    chapter: Chapter,
    max_chars: int,
    known_characters: list[dict[str, Any]] | None = None,
    mode: str = "basic",
    capabilities: list[str] | tuple[str, ...] | set[str] | None = None,
) -> dict[str, Any]:
    paragraphs = build_context_paragraphs(chapter, max_chars)
    units = build_candidate_units(paragraphs)
    resolved_capabilities = resolve_storyboard_capabilities(mode, capabilities)
    return {
        "book": {"name": "", "author": ""},
        "chapter": {"index": chapter.index, "title": chapter.title},
        "mode": mode,
        "storyboardCapabilities": resolved_capabilities,
        "allowNewCharacters": False,
        "knownCharacters": known_characters or [],
        "contextParagraphs": [
            {"paragraphIndex": item["paragraphIndex"], "text": item["text"]}
            for item in paragraphs
        ],
        "units": [unit.to_payload() for unit in units],
        "targetUnitIds": [unit.unit_id for unit in units],
    }


def payload_from_mcp_snapshot(path: Path) -> tuple[Chapter, dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8-sig"))
    data = root
    if isinstance(root, dict) and isinstance(root.get("result"), dict):
        data = root["result"]
    if isinstance(data, dict) and isinstance(data.get("structuredContent"), dict):
        data = data["structuredContent"]
    if isinstance(data, dict) and isinstance(data.get("structured_content"), dict):
        data = data["structured_content"]
    if isinstance(data, dict) and isinstance(data.get("normalized_data"), dict):
        data = data["normalized_data"]
    if isinstance(data, dict) and isinstance(data.get("normalizedData"), dict):
        data = data["normalizedData"]
    snapshot = data.get("storyboard_snapshot") if isinstance(data, dict) else None
    if not isinstance(snapshot, dict):
        snapshot = data
    payload = snapshot.get("payload") if isinstance(snapshot, dict) else None
    if not isinstance(payload, dict):
        raise ValueError(
            "MCP snapshot does not contain storyboard_snapshot.payload; "
            "call read_aloud_storyboard_debug_get with include_payload=true"
        )
    chapter_data = payload.get("chapter") or {}
    context = payload.get("contextParagraphs") or []
    content = "\n".join(str(item.get("text") or "") for item in context if isinstance(item, dict))
    chapter = Chapter(
        index=int(chapter_data.get("index") or 0),
        title=str(chapter_data.get("title") or path.stem),
        content=content,
    )
    return chapter, payload


def parse_known_characters(values: list[str]) -> list[dict[str, Any]]:
    characters: list[dict[str, Any]] = []
    for raw in values:
        parts = raw.split(":", 4)
        if len(parts) < 2:
            raise ValueError(f"bad --character value: {raw}")
        character_id = int(parts[0])
        name = parts[1].strip()
        aliases = [
            alias.strip()
            for alias in (parts[2].split(",") if len(parts) >= 3 and parts[2].strip() else [])
            if alias.strip()
        ]
        gender = "unknown"
        role = ""
        if len(parts) >= 4:
            if parts[3].strip() in SPEAKER_GENDERS:
                gender = parts[3].strip()
                role = parts[4].strip() if len(parts) >= 5 else ""
            else:
                role = parts[3].strip()
        characters.append(
            {
                "characterId": character_id,
                "name": name,
                "aliases": aliases,
                "gender": gender,
                "role": role,
            }
        )
    return characters


def build_context_paragraphs(chapter: Chapter, max_chars: int) -> list[dict[str, Any]]:
    raw_parts = [
        normalize_selected_text(part)
        for part in chapter.content.splitlines()
        if normalize_selected_text(part)
    ]
    if not raw_parts:
        raw_parts = [normalize_selected_text(chapter.content)]
    paragraphs: list[dict[str, Any]] = []
    current_length = 0
    for paragraph_index, part in enumerate(raw_parts):
        if paragraphs and current_length + len(part) > max_chars:
            break
        if not paragraphs and len(part) > max_chars:
            part = part[:max_chars]
        paragraphs.append({"paragraphIndex": paragraph_index, "text": part})
        current_length += len(part)
    return paragraphs


def normalize_selected_text(text: str) -> str:
    return "\n".join(line.strip() for line in text.splitlines()).strip()


def build_candidate_units(paragraphs: list[dict[str, Any]]) -> list[CandidateUnit]:
    units: list[CandidateUnit] = []
    texts = {item["paragraphIndex"]: str(item["text"]) for item in paragraphs}
    for item in paragraphs:
        paragraph_index = int(item["paragraphIndex"])
        text = str(item["text"])
        quote_spans = find_quote_spans(text)
        for span_start, span_end, kind in quote_spans:
            range_item = TextRange(paragraph_index, span_start, span_end)
            preview = text[span_start:span_end]
            role_hint = quote_role_hint(text, span_start, span_end)
            units.append(
                make_unit(
                    kind="quote_reference" if role_hint == "narrator" else kind,
                    role_hint=role_hint,
                    ranges=[range_item],
                    text_preview=preview,
                    cue_before=context_before(texts, paragraph_index, span_start),
                    cue_after=context_after(texts, paragraph_index, span_end),
                )
            )
        for span_start, span_end, kind, role_hint in find_colon_units(text, quote_spans):
            range_item = TextRange(paragraph_index, span_start, span_end)
            preview = text[span_start:span_end]
            units.append(
                make_unit(
                    kind=kind,
                    role_hint=role_hint,
                    ranges=[range_item],
                    text_preview=preview,
                    cue_before=context_before(texts, paragraph_index, span_start),
                    cue_after=context_after(texts, paragraph_index, span_end),
                )
            )
    return sorted(units, key=lambda unit: (unit.ranges[0].paragraph_index, unit.ranges[0].start, unit.ranges[0].end))


def find_quote_spans(text: str) -> list[tuple[int, int, str]]:
    spans: list[tuple[int, int, str]] = []
    index = 0
    while index < len(text):
        open_quote = text[index]
        if open_quote not in QUOTE_PAIRS:
            index += 1
            continue
        close_index = find_next_quote_close(text, index + 1, open_quote)
        if close_index < 0:
            spans.append((index, len(text), "quote_unclosed"))
            break
        spans.append((index, close_index + 1, "quote"))
        index = close_index + 1
    return spans


def find_next_quote_close(text: str, start: int, open_quote: str) -> int:
    candidates = QUOTE_CLOSE_CANDIDATES.get(open_quote, (QUOTE_PAIRS[open_quote],))
    indexes = [text.find(candidate, start) for candidate in candidates]
    indexes = [index for index in indexes if index >= 0]
    return min(indexes) if indexes else -1


def find_colon_units(
    text: str,
    quote_spans: list[tuple[int, int, str]],
) -> list[tuple[int, int, str, str]]:
    results: list[tuple[int, int, str, str]] = []
    quote_mask = make_quote_mask(len(text), quote_spans)
    index = 0
    while index < len(text):
        if quote_mask[index] or text[index] not in "：:":
            index += 1
            continue
        if is_ratio_or_time_colon(text, index):
            index += 1
            continue
        prefix_start = previous_boundary(text, index)
        prefix = text[prefix_start:index].strip()
        role_hint = colon_role_hint(prefix)
        speech_start = index + 1
        while speech_start < len(text) and text[speech_start].isspace():
            speech_start += 1
        if role_hint is None or speech_start >= len(text):
            index += 1
            continue
        if text[speech_start] in QUOTE_PAIRS:
            index += 1
            continue
        speech_end = len(text) if role_hint == "thought" else next_sentence_end(text, speech_start)
        if speech_end <= speech_start:
            index += 1
            continue
        kind = "thought_colon" if role_hint == "thought" else "dialogue_colon"
        results.append((speech_start, speech_end, kind, role_hint))
        index = speech_end
    return results


def make_quote_mask(length: int, quote_spans: list[tuple[int, int, str]]) -> list[bool]:
    mask = [False] * length
    for start, end, _ in quote_spans:
        for index in range(max(start, 0), min(end, length)):
            mask[index] = True
    return mask


def is_ratio_or_time_colon(text: str, index: int) -> bool:
    before = text[index - 1] if index > 0 else ""
    after = text[index + 1] if index + 1 < len(text) else ""
    return before.isdigit() and after.isdigit()


def previous_boundary(text: str, index: int) -> int:
    start = 0
    for char in "。！？!?；;\n":
        start = max(start, text.rfind(char, 0, index) + 1)
    return start


def next_sentence_end(text: str, index: int) -> int:
    cursor = index
    while cursor < len(text):
        if text[cursor] in SENTENCE_PUNCTUATION:
            end = cursor + 1
            while end < len(text) and text[end] in "。！？!?…":
                end += 1
            return end
        cursor += 1
    return len(text)


def colon_role_hint(prefix: str) -> str | None:
    value = prefix.strip().strip("“”‘’\"'，,。:：")
    if not value or len(value) > 40:
        return None
    if any(cue in value[-16:] for cue in THOUGHT_CUES):
        return "thought"
    if ("心里" in value[-16:] or "心中" in value[-16:]) and value.endswith("想"):
        return "thought"
    if any(cue in value[-16:] for cue in COLON_DIALOGUE_CUES):
        return "character"
    return None


def looks_like_thought(text: str, start: int, end: int) -> bool:
    before = text[max(0, start - 40) : start]
    after = text[end : min(len(text), end + 40)]
    return any(cue in before or cue in after for cue in THOUGHT_CUES)


NARRATED_QUOTE_STRONG_CUES = (
    "那句", "这句", "那句话", "这句话", "原话", "所谓", "口头禅",
    "字眼", "词语", "称呼", "标题", "名字", "写着", "显示",
)
NARRATED_QUOTE_SHORT_CUES = (
    "一句", "一声", "一串", "几个字", "两个字", "三个字", "四个字", "五个字",
)


def quote_role_hint(text: str, start: int, end: int) -> str:
    if looks_like_thought(text, start, end):
        return "thought"
    if looks_like_narrated_quote(text, start, end):
        return "narrator"
    return "character"


def looks_like_narrated_quote(text: str, start: int, end: int) -> bool:
    if start < 0 or end < start or end > len(text):
        return False
    prefix = text[previous_boundary(text, start) : start].strip().rstrip("，,、")
    if not prefix or prefix.endswith(("：", ":")):
        return False
    nearby_prefix = prefix[-28:]
    if any(cue in nearby_prefix for cue in NARRATED_QUOTE_STRONG_CUES):
        return True
    quoted_length = max(0, end - start - 2)
    return quoted_length <= 16 and any(
        cue in nearby_prefix for cue in NARRATED_QUOTE_SHORT_CUES
    )


def context_before(
    paragraphs: dict[int, str],
    paragraph_index: int,
    start: int,
    limit: int = 120,
) -> str:
    current = paragraphs.get(paragraph_index, "")[:start]
    previous = paragraphs.get(paragraph_index - 1, "")
    return (previous[-40:] + "\n" + current).strip()[-limit:]


def context_after(
    paragraphs: dict[int, str],
    paragraph_index: int,
    end: int,
    limit: int = 120,
) -> str:
    current = paragraphs.get(paragraph_index, "")[end:]
    next_text = paragraphs.get(paragraph_index + 1, "")
    return (current + "\n" + next_text[:40]).strip()[:limit]


def make_unit(
    kind: str,
    role_hint: str,
    ranges: list[TextRange],
    text_preview: str,
    cue_before: str,
    cue_after: str,
) -> CandidateUnit:
    first = ranges[0]
    last = ranges[-1]
    digest = hashlib.sha1(text_preview.encode("utf-8")).hexdigest()[:8]
    unit_id = f"u_{first.paragraph_index}_{first.start}_{last.paragraph_index}_{last.end}_{kind}_{digest}"
    return CandidateUnit(
        unit_id=unit_id,
        kind=kind,
        role_hint=role_hint,
        ranges=ranges,
        text_preview=text_preview,
        cue_before=cue_before,
        cue_after=cue_after,
    )


def validate_storyboard_result(
    payload: dict[str, Any],
    result: dict[str, Any],
) -> dict[str, Any]:
    target_ids = list(payload.get("targetUnitIds") or [])
    target_set = set(target_ids)
    known_ids = {unit["unitId"]: unit for unit in payload.get("units") or []}
    capabilities = payload_storyboard_capabilities(payload)
    invalid_schema: list[str] = []
    text_leaks: list[str] = []
    root_extra_keys = set(result) - ROOT_KEYS
    if root_extra_keys:
        invalid_schema.append(f"root:extra_keys={','.join(sorted(root_extra_keys))}")
    if not isinstance(result.get("units"), list):
        invalid_schema.append("root:units_not_array")
        model_units: list[Any] = []
    else:
        model_units = result["units"]
    if "newCharacters" in result and not isinstance(result["newCharacters"], list):
        invalid_schema.append("root:newCharacters_not_array")
    if payload.get("allowNewCharacters") is False and result.get("newCharacters") not in ([], None):
        invalid_schema.append("root:newCharacters_not_empty")
    seen: list[str] = []
    valid_units: list[dict[str, Any]] = []
    counts = {"narrator": 0, "character": 0, "thought": 0, "other": 0, "invalid": 0}
    for index, item in enumerate(model_units):
        if not isinstance(item, dict):
            invalid_schema.append(f"units[{index}]:not_object")
            counts["invalid"] += 1
            continue
        leak_paths = find_text_leaks(item, f"units[{index}]")
        text_leaks.extend(leak_paths)
        extra_keys = set(item) - UNIT_KEYS - OPTIONAL_UNIT_KEYS
        if extra_keys:
            invalid_schema.append(f"units[{index}]:extra_keys={','.join(sorted(extra_keys))}")
        missing_keys = UNIT_KEYS - set(item)
        if missing_keys:
            invalid_schema.append(f"units[{index}]:missing_keys={','.join(sorted(missing_keys))}")
        unit_id = str(item.get("unitId") or "")
        seen.append(unit_id)
        if unit_id not in target_set:
            invalid_schema.append(f"units[{index}]:unknown_unit_id={unit_id}")
        role_type = item.get("roleType")
        if role_type not in ROLE_TYPES:
            invalid_schema.append(f"units[{index}]:bad_roleType={role_type}")
            counts["invalid"] += 1
        else:
            counts[role_type] += 1
        status = item.get("status")
        if status not in STATUSES:
            invalid_schema.append(f"units[{index}]:bad_status={status}")
        speaker_gender = item.get("speakerGender")
        if speaker_gender not in SPEAKER_GENDERS:
            invalid_schema.append(f"units[{index}]:bad_speakerGender={speaker_gender}")
        confidence = item.get("confidence")
        if not isinstance(confidence, (int, float)) or not 0 <= float(confidence) <= 1:
            invalid_schema.append(f"units[{index}]:bad_confidence={confidence}")
        character_name = str(item.get("characterName") or "")
        character_id = item.get("characterId")
        performance_context = item.get("performanceContext")
        if not isinstance(character_id, int):
            invalid_schema.append(f"units[{index}]:bad_characterId={character_id}")
        validate_performance_context(
            payload=payload,
            unit=item,
            unit_index=index,
            capabilities=capabilities,
            context=performance_context,
            invalid_schema=invalid_schema,
        )
        validate_performance_instruction(
            unit=item,
            unit_index=index,
            capabilities=capabilities,
            instruction=item.get("performanceInstruction"),
            invalid_schema=invalid_schema,
        )
        if role_type in ("narrator", "other"):
            if character_name or character_id not in (0, None):
                invalid_schema.append(f"units[{index}]:character_must_be_blank")
        if role_type in ("narrator", "other") and speaker_gender != "unknown":
            invalid_schema.append(f"units[{index}]:narrator_gender_must_be_unknown")
        if role_type in ("character", "thought") and status == "assigned":
            if not character_name and character_id in (0, None) and speaker_gender not in ("male", "female"):
                invalid_schema.append(f"units[{index}]:assigned_character_missing")
        if role_type in ("character", "thought") and status == "unknown":
            if character_id not in (0, None):
                invalid_schema.append(f"units[{index}]:unknown_character_id_must_be_zero")
            if speaker_gender == "unknown":
                invalid_schema.append(f"units[{index}]:unknown_dialogue_gender_missing")
        if unit_id in known_ids and not leak_paths:
            valid_units.append(item)
    duplicate_ids = sorted({unit_id for unit_id in seen if unit_id and seen.count(unit_id) > 1})
    missing_ids = [unit_id for unit_id in target_ids if unit_id not in seen]
    unknown_ids = [unit_id for unit_id in seen if unit_id and unit_id not in target_set]
    cacheable = not invalid_schema and not text_leaks and not missing_ids and not duplicate_ids and not unknown_ids
    performance_units = [
        item
        for item in model_units
        if isinstance(item, dict) and item.get("roleType") in ("character", "thought")
    ]
    performance_context_count = sum(
        1
        for item in performance_units
        if bool(item.get("performanceContext"))
    )
    performance_instruction_count = sum(
        1
        for item in performance_units
        if bool(str(item.get("performanceInstruction") or "").strip())
    )
    return {
        "counts": counts,
        "target_count": len(target_ids),
        "returned_count": len(model_units),
        "missing_target_count": len(missing_ids),
        "missing_target_samples": missing_ids[:10],
        "duplicate_unit_count": len(duplicate_ids),
        "duplicate_unit_samples": duplicate_ids[:10],
        "unknown_unit_count": len(unknown_ids),
        "unknown_unit_samples": unknown_ids[:10],
        "text_leak_count": len(text_leaks),
        "text_leak_samples": text_leaks[:10],
        "invalid_schema_count": len(invalid_schema),
        "invalid_schema_samples": invalid_schema[:20],
        "performance_target_count": len(performance_units),
        "performance_context_count": performance_context_count,
        "performance_context_coverage": (
            round(performance_context_count / len(performance_units), 4)
            if performance_units
            else 0.0
        ),
        "performance_instruction_count": performance_instruction_count,
        "performance_instruction_coverage": (
            round(performance_instruction_count / len(performance_units), 4)
            if performance_units
            else 0.0
        ),
        "cacheable": cacheable,
        "accepted_units": valid_units if cacheable else [],
    }


def validate_performance_context(
    payload: dict[str, Any],
    unit: dict[str, Any],
    unit_index: int,
    capabilities: list[str],
    context: Any,
    invalid_schema: list[str],
) -> None:
    prefix = f"units[{unit_index}]:performanceContext"
    if not isinstance(context, list):
        invalid_schema.append(f"{prefix}_not_array")
        return
    if any(not isinstance(value, str) or not value.strip() for value in context):
        invalid_schema.append(f"{prefix}_has_blank_or_non_string")
        return
    role_type = unit.get("roleType")
    requires_context = (
        CAP_SCENE_CONTEXT in capabilities and role_type in ("character", "thought")
    )
    if not requires_context:
        if context:
            invalid_schema.append(f"{prefix}_must_be_empty")
        return
    if not context:
        invalid_schema.append(f"{prefix}_required")
        return
    if not 1 <= len(context) <= 3:
        invalid_schema.append(f"{prefix}_count={len(context)}")
    if any(len(value.strip()) > 80 for value in context):
        invalid_schema.append(f"{prefix}_item_too_long")
    if sum(len(value.strip()) for value in context) > 220:
        invalid_schema.append(f"{prefix}_too_long")
    source_unit = next(
        (item for item in payload.get("units") or [] if item.get("unitId") == unit.get("unitId")),
        None,
    )
    cue_after = normalize_comparison_text(str((source_unit or {}).get("cueAfter") or ""))
    if len(cue_after) >= 8 and any(cue_after in normalize_comparison_text(value) for value in context):
        invalid_schema.append(f"{prefix}_copies_cue_after")
    target_text = normalize_comparison_text(str((source_unit or {}).get("textPreview") or ""))
    if len(target_text) >= 12 and any(target_text in normalize_comparison_text(value) for value in context):
        invalid_schema.append(f"{prefix}_repeats_target")


def payload_storyboard_capabilities(payload: dict[str, Any]) -> list[str]:
    raw = payload.get("storyboardCapabilities")
    if raw is None:
        return resolve_storyboard_capabilities(str(payload.get("mode") or "basic"))
    if not isinstance(raw, list):
        return []
    return resolve_storyboard_capabilities(
        str(payload.get("mode") or "basic"),
        [str(value) for value in raw],
    )


def validate_performance_instruction(
    unit: dict[str, Any],
    unit_index: int,
    capabilities: list[str],
    instruction: Any,
    invalid_schema: list[str],
) -> None:
    prefix = f"units[{unit_index}]:performanceInstruction"
    actor_enabled = CAP_PERFORMANCE_INSTRUCTION in capabilities
    if actor_enabled:
        if not isinstance(instruction, str):
            invalid_schema.append(f"{prefix}_not_string")
            return
    elif instruction is None:
        return
    elif not isinstance(instruction, str):
        invalid_schema.append(f"{prefix}_not_string")
        return

    value = str(instruction or "").strip()
    role_type = unit.get("roleType")
    if not actor_enabled or role_type in ("narrator", "other"):
        if value:
            invalid_schema.append(f"{prefix}_must_be_empty")
        return
    if not value:
        invalid_schema.append(f"{prefix}_required")
        return
    if not 4 <= len(value) <= 40:
        invalid_schema.append(f"{prefix}_length={len(value)}")


def normalize_comparison_text(value: str) -> str:
    return re.sub(r"[\s“”‘’\"'，。！？!?；;：:、…—-]+", "", value)


def build_source_window(payload: dict[str, Any], source_unit: dict[str, Any] | None) -> str:
    if not source_unit:
        return ""
    paragraphs = payload.get("contextParagraphs") or []
    paragraph_indexes = [
        item.get("paragraphIndex")
        for item in source_unit.get("ranges") or []
        if isinstance(item, dict) and isinstance(item.get("paragraphIndex"), int)
    ]
    if not paragraph_indexes:
        return "\n".join(
            str(source_unit.get(key) or "") for key in ("cueBefore", "textPreview", "cueAfter")
        )
    start = min(paragraph_indexes) - 2
    end = max(paragraph_indexes) + 2
    nearby = [
        str(item.get("text") or "")
        for item in paragraphs
        if isinstance(item, dict)
        and isinstance(item.get("paragraphIndex"), int)
        and start <= item["paragraphIndex"] <= end
    ]
    return "\n".join(nearby)


def find_text_leaks(value: Any, path: str) -> list[str]:
    leaks: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in TEXT_LEAK_KEYS:
                leaks.append(child_path)
            leaks.extend(find_text_leaks(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            leaks.extend(find_text_leaks(child, f"{path}[{index}]"))
    return leaks


def reconstruct_unit_text(payload: dict[str, Any], unit_id: str) -> str:
    paragraphs = {
        item["paragraphIndex"]: str(item["text"])
        for item in payload.get("contextParagraphs") or []
    }
    for unit in payload.get("units") or []:
        if unit.get("unitId") != unit_id:
            continue
        parts = []
        for range_item in unit.get("ranges") or []:
            paragraph = paragraphs.get(range_item.get("paragraphIndex"), "")
            start = int(range_item.get("start") or 0)
            end = int(range_item.get("end") or 0)
            parts.append(paragraph[start:end])
        return "\n".join(parts)
    return ""


def target_restatement_terms(text: str) -> set[str]:
    terms: set[str] = set()
    for chunk in re.findall(r"[\u4e00-\u9fff]+", text):
        if len(chunk) >= 2:
            for size in range(2, min(6, len(chunk)) + 1):
                terms.update(chunk[index : index + size] for index in range(len(chunk) - size + 1))
        if len(chunk) >= 2 and re.fullmatch(r"[零一二三四五六七八九十百千万两]+", chunk):
            terms.add(chunk)
    return terms


def write_report(
    out_dir: Path,
    chapter: Chapter,
    payload: dict[str, Any],
    result: dict[str, Any],
    validation: dict[str, Any],
) -> None:
    result_by_id = {
        item.get("unitId"): item
        for item in result.get("units") or []
        if isinstance(item, dict)
    }
    lines = [
        f"# {chapter.index}. {chapter.title}",
        "",
        "## Audit",
        "",
        f"cacheable: {validation['cacheable']}",
        f"target_count: {validation['target_count']}",
        f"returned_count: {validation['returned_count']}",
        f"missing_target_count: {validation['missing_target_count']}",
        f"unknown_unit_count: {validation['unknown_unit_count']}",
        f"duplicate_unit_count: {validation['duplicate_unit_count']}",
        f"text_leak_count: {validation['text_leak_count']}",
        f"invalid_schema_count: {validation['invalid_schema_count']}",
        f"performance_target_count: {validation['performance_target_count']}",
        f"performance_context_count: {validation['performance_context_count']}",
        f"performance_context_coverage: {validation['performance_context_coverage']}",
        f"performance_instruction_count: {validation['performance_instruction_count']}",
        f"performance_instruction_coverage: {validation['performance_instruction_coverage']}",
        "",
        "## Counts",
        "",
        json.dumps(validation["counts"], ensure_ascii=False, indent=2),
        "",
    ]
    if validation["invalid_schema_samples"]:
        lines.append("## Invalid Schema")
        lines.append("")
        for sample in validation["invalid_schema_samples"]:
            lines.append(f"- {sample}")
        lines.append("")
    if validation["text_leak_samples"]:
        lines.append("## Text Leak")
        lines.append("")
        for sample in validation["text_leak_samples"]:
            lines.append(f"- {sample}")
        lines.append("")
    lines.extend(["## Units", ""])
    for index, unit in enumerate(payload.get("units") or [], 1):
        unit_id = unit["unitId"]
        resolution = result_by_id.get(unit_id, {})
        local_text = reconstruct_unit_text(payload, unit_id).replace("\n", " ")
        lines.append(f"{index}. `{unit_id}` / `{unit.get('kind')}` / hint=`{unit.get('roleHint')}`")
        lines.append(f"   local: {local_text}")
        if resolution:
            lines.append(
                "   result: "
                f"{resolution.get('roleType')} / {resolution.get('status')} / "
                f"{resolution.get('characterName') or '-'} / "
                f"{resolution.get('speakerGender') or '-'} / "
                f"{resolution.get('confidence')} / {resolution.get('evidence') or '-'}"
            )
            lines.append(
                "   performance: "
                + (" | ".join(str(value) for value in resolution.get("performanceContext") or []) or "-")
            )
            lines.append(
                "   instruction: "
                + (str(resolution.get("performanceInstruction") or "-").strip() or "-")
            )
        else:
            lines.append("   result: MISSING")
        lines.append("")
    (out_dir / f"chapter_{chapter.index:03d}.md").write_text(
        "\n".join(lines),
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    capabilities = resolve_storyboard_capabilities(args.mode, args.engine_capability or None)
    load_repo_env()
    api_key = os.getenv(args.api_key_env)
    if not api_key:
        print(f"missing env: {args.api_key_env}", file=sys.stderr)
        return 2
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    cases: list[tuple[Chapter, dict[str, Any]]] = []
    if args.mcp_snapshot:
        cases.append(payload_from_mcp_snapshot(Path(args.mcp_snapshot)))
    else:
        if not args.book:
            print("missing --book or --mcp-snapshot", file=sys.stderr)
            return 2
        book_path = Path(args.book)
        chapters = split_chapters(read_text(book_path))
        indexes = parse_range(args.chapters, len(chapters))
        known_characters = parse_known_characters(args.character)
        for index in indexes:
            chapter = chapters[index - 1]
            payload = build_storyboard_payload(
                chapter,
                args.max_chars,
                known_characters,
                args.mode,
                capabilities,
            )
            cases.append((chapter, payload))
    summary: list[dict[str, Any]] = []
    for chapter, payload in cases:
        if (
            (args.scene_pass or args.scene_only)
            and payload.get("contextParagraphs")
        ):
            print(f"request scene boundaries chapter {chapter.index}: {chapter.title}", flush=True)
            scene_payload = build_scene_payload(payload)
            try:
                scene_raw, scene_content_text = request_storyboard(
                    api_url=args.api_url,
                    api_key=api_key,
                    model=args.model,
                    payload=scene_payload,
                    max_tokens=args.max_tokens,
                    temperature=args.temperature,
                    top_p=args.top_p,
                    timeout=args.timeout,
                    json_mode=args.json_mode,
                    enable_thinking=args.enable_thinking,
                    thinking_param=args.thinking_param,
                    thinking_state=args.thinking_state,
                    retries=args.retries,
                    retry_sleep=args.retry_sleep,
                    system_prompt=SCENE_SKILL_FILE.read_text(encoding="utf-8").strip(),
                )
                (out_dir / f"chapter_{chapter.index:03d}.scene.raw.json").write_text(
                    json.dumps(scene_raw, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                (out_dir / f"chapter_{chapter.index:03d}.scene.content.txt").write_text(
                    scene_content_text,
                    encoding="utf-8",
                )
                scene_result = json.loads(extract_json(scene_content_text))
                (out_dir / f"chapter_{chapter.index:03d}.scene.json").write_text(
                    json.dumps(scene_result, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                scenes = validate_scene_result(payload, scene_result)
                attach_scene_ranges(payload, scenes)
            except Exception as error:
                error_payload = {
                    "chapter": chapter.index,
                    "title": chapter.title,
                    "error": f"scene pass failed: {error}",
                }
                (out_dir / f"chapter_{chapter.index:03d}.scene.error.json").write_text(
                    json.dumps(error_payload, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                summary.append(error_payload)
                print(json.dumps(error_payload, ensure_ascii=False), flush=True)
                continue
            if args.scene_only:
                summary.append(
                    {
                        "chapter": chapter.index,
                        "title": chapter.title,
                        "scene_count": len(scenes),
                        "scenes": scenes,
                    }
                )
                time.sleep(args.sleep)
                continue
        print(f"request chapter {chapter.index}: {chapter.title}", flush=True)
        (out_dir / f"chapter_{chapter.index:03d}.payload.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        if not payload["targetUnitIds"]:
            empty_validation = validate_storyboard_result(payload, {"units": [], "newCharacters": []})
            summary.append({"chapter": chapter.index, "title": chapter.title, **empty_validation})
            write_report(out_dir, chapter, payload, {"units": [], "newCharacters": []}, empty_validation)
            continue
        try:
            raw, content_text = request_storyboard(
                api_url=args.api_url,
                api_key=api_key,
                model=args.model,
                payload=payload,
                max_tokens=args.max_tokens,
                temperature=args.temperature,
                top_p=args.top_p,
                timeout=args.timeout,
                json_mode=args.json_mode,
                enable_thinking=args.enable_thinking,
                thinking_param=args.thinking_param,
                thinking_state=args.thinking_state,
                retries=args.retries,
                retry_sleep=args.retry_sleep,
                system_prompt=build_system_prompt(
                    str(payload.get("mode") or args.mode),
                    payload_storyboard_capabilities(payload),
                ),
            )
        except Exception as error:
            error_payload = {
                "chapter": chapter.index,
                "title": chapter.title,
                "error": str(error),
                "payload_file": f"chapter_{chapter.index:03d}.payload.json",
            }
            (out_dir / f"chapter_{chapter.index:03d}.error.json").write_text(
                json.dumps(error_payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            summary.append(error_payload)
            print(json.dumps(error_payload, ensure_ascii=False), flush=True)
            time.sleep(args.sleep)
            continue
        (out_dir / f"chapter_{chapter.index:03d}.raw.json").write_text(
            json.dumps(raw, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        (out_dir / f"chapter_{chapter.index:03d}.content.txt").write_text(
            content_text,
            encoding="utf-8",
        )
        try:
            result = json.loads(extract_json(content_text))
        except json.JSONDecodeError as error:
            error_payload = {
                "chapter": chapter.index,
                "title": chapter.title,
                "error": str(error),
                "content_file": f"chapter_{chapter.index:03d}.content.txt",
                "payload_file": f"chapter_{chapter.index:03d}.payload.json",
            }
            (out_dir / f"chapter_{chapter.index:03d}.error.json").write_text(
                json.dumps(error_payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            summary.append(error_payload)
            print(json.dumps(error_payload, ensure_ascii=False), flush=True)
            time.sleep(args.sleep)
            continue
        validation = validate_storyboard_result(payload, result)
        (out_dir / f"chapter_{chapter.index:03d}.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        (out_dir / f"chapter_{chapter.index:03d}.audit.json").write_text(
            json.dumps(validation, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        write_report(out_dir, chapter, payload, result, validation)
        summary.append(
            {
                "chapter": chapter.index,
                "title": chapter.title,
                **{key: value for key, value in validation.items() if key != "accepted_units"},
            }
        )
        time.sleep(args.sleep)
    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
