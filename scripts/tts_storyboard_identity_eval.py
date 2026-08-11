#!/usr/bin/env python3
"""Run a sequential, cross-chapter TTS speaker identity evaluation.

The probe starts with an empty cast ledger, sends the ledger produced by each
chapter back as ``knownCastRoles`` for the next chapter, and applies the same
high-level admission/correction rules as the Android coordinator. It is meant
to catch identity-pool regressions before wiping device data.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

import tts_storyboard_eval as base


API_URL = "https://token.sensenova.cn/v1/chat/completions"
MODEL = "deepseek-v4-flash"
IDENTITY_TYPES = {
    "none",
    "formal_character",
    "cast_role",
    "stable_candidate",
    "pending",
    "guest",
}
NAME_TYPES = {"proper_name", "alias", "unique_title", "generic_label", "unknown"}
EVIDENCE = {"explicit", "contextual", "inferred", "unknown"}
UNIT_KEYS = {
    "unitId",
    "roleType",
    "characterName",
    "characterId",
    "castRoleId",
    "speakerGender",
    "identityType",
    "nameType",
    "identityEvidence",
    "genderEvidence",
    "mergeCastRoleIds",
    "status",
    "confidence",
    "evidence",
    "performanceContext",
}
GENERIC_EXPECTATIONS = ("大汉", "镇魔司下属", "老捕头")
FEMALE_EVIDENCE_MARKERS = ("女", "她", "妹妹", "姐姐", "姑娘", "小姐", "娘", "母", "妻", "公主")
MALE_EVIDENCE_MARKERS = ("男", "他", "哥哥", "弟弟", "公子", "少爷", "父", "爹", "丈夫")
FEMALE_ADDRESSES = ("小妹妹", "妹妹", "小姑娘", "姑娘", "小姐", "女士", "女侠", "夫人", "娘子")
MALE_ADDRESSES = ("小弟弟", "弟弟", "小公子", "公子", "少爷", "先生", "小哥", "大哥", "大叔", "老爷")


@dataclass
class CastRole:
    castRoleId: int
    name: str
    aliases: list[str] = field(default_factory=list)
    gender: str = "unknown"
    identityState: str = "pending"
    nameType: str = "unknown"
    identityEvidence: str = "unknown"
    genderEvidence: str = "unknown"
    firstChapterIndex: int = 0
    lastChapterIndex: int = 0
    occurrenceCount: int = 0
    representativeTexts: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    chapterOccurrences: dict[str, int] = field(default_factory=dict)

    def to_model_payload(self) -> dict[str, Any]:
        chapter_range = str(self.firstChapterIndex + 1)
        if self.firstChapterIndex != self.lastChapterIndex:
            chapter_range = f"{self.firstChapterIndex + 1}-{self.lastChapterIndex + 1}"
        return {
            "castRoleId": self.castRoleId,
            "name": self.name,
            "aliases": self.aliases,
            "gender": self.gender,
            "identityState": self.identityState,
            "nameType": self.nameType,
            "identityEvidence": self.identityEvidence,
            "genderEvidence": self.genderEvidence,
            "chapterRange": chapter_range,
            "occurrenceCount": self.occurrenceCount,
            "representativeTexts": self.representativeTexts[:3],
            "evidence": self.evidence[:4],
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", required=True)
    parser.add_argument("--chapters", default="1-6")
    parser.add_argument("--book-title", default="山河稷")
    parser.add_argument("--author", default="姬叉")
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--api-url", default=API_URL)
    parser.add_argument("--api-key-env", default="SENSENOVA_API_KEY")
    parser.add_argument("--max-chars", type=int, default=7000)
    parser.add_argument("--max-tokens", type=int, default=16384)
    parser.add_argument(
        "--initial-summary",
        help="Seed the cross-chapter ledger from a previous summary.json before running selected chapters.",
    )
    parser.add_argument(
        "--resume",
        action="store_true",
        help="Reuse protocol-valid chapter JSON files already present in --out.",
    )
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--sleep", type=float, default=0.5)
    parser.add_argument("--out", default="build/tts_storyboard_identity_eval/shanheji_ch1_6")
    return parser.parse_args()


def normalized_name(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip(" \t\r\n“”‘’「」『』:：，,。.!！?？")).lower()


def evidence_rank(value: str) -> int:
    return {"unknown": 0, "inferred": 1, "contextual": 2, "explicit": 3}.get(value, 0)


def name_type_rank(value: str) -> int:
    return {"unknown": 0, "generic_label": 1, "unique_title": 2, "alias": 3, "proper_name": 4}.get(value, 0)


def has_supported_gender(item: dict[str, Any]) -> bool:
    gender = str(item.get("speakerGender") or "unknown")
    evidence_level = str(item.get("genderEvidence") or "unknown")
    if gender not in {"male", "female"}:
        return False
    if evidence_level != "explicit":
        return True
    evidence = str(item.get("evidence") or "")
    markers = FEMALE_EVIDENCE_MARKERS if gender == "female" else MALE_EVIDENCE_MARKERS
    return any(marker in evidence for marker in markers)


def find_role_by_name(ledger: dict[int, CastRole], name: str) -> CastRole | None:
    key = normalized_name(name)
    if not key:
        return None
    for role in ledger.values():
        if key in {normalized_name(role.name), *(normalized_name(item) for item in role.aliases)}:
            return role
    return None


def validate_result(payload: dict[str, Any], result: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if set(result) != {"units", "newCharacters"}:
        errors.append(f"root keys must be units/newCharacters: {sorted(result)}")
    if set(result) - {"units", "newCharacters"}:
        errors.append(f"root extra keys: {sorted(set(result) - {'units', 'newCharacters'})}")
    if result.get("newCharacters") not in (None, []):
        errors.append("newCharacters must be empty")
    units = result.get("units")
    if not isinstance(units, list):
        return errors + ["units is not an array"]
    target_ids = list(payload.get("targetUnitIds") or [])
    returned_ids = [str(item.get("unitId") or "") for item in units if isinstance(item, dict)]
    if set(returned_ids) != set(target_ids) or len(returned_ids) != len(target_ids):
        errors.append("unit coverage mismatch")
    for index, item in enumerate(units):
        if not isinstance(item, dict):
            errors.append(f"units[{index}] is not an object")
            continue
        extra = set(item) - UNIT_KEYS
        missing = UNIT_KEYS - set(item)
        if extra:
            errors.append(f"units[{index}] extra keys: {sorted(extra)}")
        if missing:
            errors.append(f"units[{index}] missing keys: {sorted(missing)}")
        if item.get("identityType") not in IDENTITY_TYPES:
            errors.append(f"units[{index}] invalid identityType={item.get('identityType')}")
        if item.get("nameType") not in NAME_TYPES:
            errors.append(f"units[{index}] invalid nameType={item.get('nameType')}")
        if item.get("identityEvidence") not in EVIDENCE:
            errors.append(f"units[{index}] invalid identityEvidence={item.get('identityEvidence')}")
        if item.get("genderEvidence") not in EVIDENCE:
            errors.append(f"units[{index}] invalid genderEvidence={item.get('genderEvidence')}")
        if item.get("speakerGender") not in {"male", "female", "unknown"}:
            errors.append(f"units[{index}] invalid speakerGender={item.get('speakerGender')}")
        if not isinstance(item.get("castRoleId"), int):
            errors.append(f"units[{index}] castRoleId is not an integer")
        if not isinstance(item.get("mergeCastRoleIds"), list):
            errors.append(f"units[{index}] mergeCastRoleIds is not an array")
        if item.get("roleType") in {"narrator", "other"}:
            if item.get("identityType") != "none" or item.get("castRoleId") not in (0, None):
                errors.append(f"units[{index}] narration carries identity")
    return errors


def subset_payload(payload: dict[str, Any], target_ids: list[str]) -> dict[str, Any]:
    selected = set(target_ids)
    return {
        **payload,
        "units": [unit for unit in payload.get("units") or [] if unit.get("unitId") in selected],
        "targetUnitIds": target_ids,
    }


def apply_adjacent_gender_evidence(payload: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    units = {str(item.get("unitId") or ""): item for item in payload.get("units") or []}
    assignments = {
        str(item.get("unitId") or ""): item
        for item in result.get("units") or []
        if isinstance(item, dict)
    }
    ordered_ids = [str(value) for value in payload.get("targetUnitIds") or []]
    for previous_id, current_id in zip(ordered_ids, ordered_ids[1:]):
        previous = assignments.get(previous_id)
        current = assignments.get(current_id)
        previous_unit = units.get(previous_id)
        current_unit = units.get(current_id)
        if not all((previous, current, previous_unit, current_unit)):
            continue
        if previous.get("roleType") not in {"character", "thought"} or current.get("roleType") not in {
            "character",
            "thought",
        }:
            continue
        if str(current.get("speakerGender") or "unknown") != "unknown":
            continue
        previous_identity = int(previous.get("characterId") or previous.get("castRoleId") or 0)
        current_identity = int(current.get("characterId") or current.get("castRoleId") or 0)
        if previous_identity > 0 and previous_identity == current_identity:
            continue
        if previous_identity <= 0 and normalized_name(str(previous.get("characterName") or "")) == normalized_name(
            str(current.get("characterName") or "")
        ):
            continue
        previous_ranges = previous_unit.get("ranges") or []
        current_ranges = current_unit.get("ranges") or []
        if not previous_ranges or not current_ranges:
            continue
        paragraph_gap = int(current_ranges[0].get("paragraphIndex") or 0) - int(
            previous_ranges[0].get("paragraphIndex") or 0
        )
        if paragraph_gap not in {0, 1}:
            continue
        text = str(previous_unit.get("textPreview") or "").lstrip(" \t\r\n“”‘’\"'")
        address = next((value for value in FEMALE_ADDRESSES if text.startswith(value)), None)
        gender = "female"
        if address is None:
            address = next((value for value in MALE_ADDRESSES if text.startswith(value)), None)
            gender = "male"
        if address is None:
            continue
        current["speakerGender"] = gender
        current["genderEvidence"] = "explicit"
        current["evidence"] = (str(current.get("evidence") or "").rstrip("；; ") + f"；紧邻称呼“{address}”").lstrip("；")
    return result


def normalize_identity_consistency(payload: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    known_by_name: dict[str, int] = {}
    for role in payload.get("knownCastRoles") or []:
        role_id = int(role.get("castRoleId") or 0)
        for name in [role.get("name"), *(role.get("aliases") or [])]:
            key = normalized_name(str(name or ""))
            if role_id > 0 and key:
                known_by_name[key] = role_id
    stable_name_types = {"proper_name", "alias", "unique_title"}
    for item in result.get("units") or []:
        if not isinstance(item, dict):
            continue
        if item.get("identityType") != "guest" or item.get("nameType") not in stable_name_types:
            continue
        known_id = known_by_name.get(normalized_name(str(item.get("characterName") or "")), 0)
        item["identityType"] = "cast_role" if known_id > 0 else "stable_candidate"
        item["castRoleId"] = known_id
        item["status"] = "assigned" if known_id > 0 else "unknown"
    return result


def request_validated(
    args: argparse.Namespace,
    payload: dict[str, Any],
    system_prompt: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    def request_once(target_payload: dict[str, Any], max_tokens: int) -> tuple[dict[str, Any], dict[str, Any]]:
        raw, content = base.request_storyboard(
            api_url=args.api_url,
            api_key=os.environ[args.api_key_env],
            model=args.model,
            payload=target_payload,
            max_tokens=max_tokens,
            temperature=0.0,
            top_p=None,
            timeout=args.timeout,
            json_mode=True,
            enable_thinking="omit",
            thinking_param="thinking",
            thinking_state="disabled",
            retries=2,
            retry_sleep=8.0,
            system_prompt=system_prompt,
        )
        result = json.loads(base.extract_json(content))
        errors = validate_result(target_payload, result)
        if errors:
            raise ValueError("; ".join(errors))
        return raw, result

    target_ids = list(payload.get("targetUnitIds") or [])
    full_max_tokens = args.max_tokens if len(target_ids) > 40 else min(args.max_tokens, 8192)
    try:
        return request_once(payload, full_max_tokens)
    except Exception as first_error:
        if len(target_ids) < 2:
            raise
        print(f"  full request invalid, split retry: {first_error}", flush=True)
        midpoint = (len(target_ids) + 1) // 2
        split_raw: list[dict[str, Any]] = []
        combined_units: list[dict[str, Any]] = []
        for chunk_ids in (target_ids[:midpoint], target_ids[midpoint:]):
            chunk_payload = subset_payload(payload, chunk_ids)
            raw, result = request_once(chunk_payload, min(args.max_tokens, 8192))
            split_raw.append(raw)
            combined_units.extend(result["units"])
        combined = {"units": combined_units, "newCharacters": []}
        errors = validate_result(payload, combined)
        if errors:
            raise ValueError("split result invalid: " + "; ".join(errors))
        return {"splitRetry": split_raw, "initialError": str(first_error)}, combined


def merge_roles(ledger: dict[int, CastRole], target: CastRole, source_id: int) -> None:
    source = ledger.get(source_id)
    if source is None or source.castRoleId == target.castRoleId:
        return
    target.aliases = distinct(target.aliases + [source.name] + source.aliases, target.name)
    target.representativeTexts = distinct(target.representativeTexts + source.representativeTexts)[:4]
    target.evidence = distinct(target.evidence + source.evidence)[:6]
    for chapter, count in source.chapterOccurrences.items():
        target.chapterOccurrences[chapter] = target.chapterOccurrences.get(chapter, 0) + count
    if evidence_rank(source.genderEvidence) > evidence_rank(target.genderEvidence):
        target.gender = source.gender
        target.genderEvidence = source.genderEvidence
    del ledger[source_id]


def distinct(values: list[str], excluded_name: str = "") -> list[str]:
    output: list[str] = []
    seen: set[str] = set()
    excluded = normalized_name(excluded_name)
    for raw in values:
        value = str(raw or "").strip()
        key = normalized_name(value)
        if not key or key == excluded or key in seen:
            continue
        seen.add(key)
        output.append(value)
    return output


def apply_chapter(
    ledger: dict[int, CastRole],
    next_id: int,
    chapter_index: int,
    payload: dict[str, Any],
    result: dict[str, Any],
) -> tuple[int, list[dict[str, Any]]]:
    source_units = {item["unitId"]: item for item in payload.get("units") or []}
    rows: list[dict[str, Any]] = []
    occurrences: dict[int, int] = {}
    for item in result.get("units") or []:
        unit_id = str(item.get("unitId") or "")
        identity_type = str(item.get("identityType") or "none")
        name = str(item.get("characterName") or "").strip()
        row = {
            "unitId": unit_id,
            "text": str(source_units.get(unit_id, {}).get("textPreview") or ""),
            "name": name,
            "identityType": identity_type,
            "nameType": item.get("nameType"),
            "castRoleId": item.get("castRoleId"),
            "gender": item.get("speakerGender"),
            "evidence": item.get("evidence"),
        }
        rows.append(row)
        if item.get("roleType") not in {"character", "thought"}:
            continue
        if identity_type in {"none", "formal_character", "guest"} or not name:
            continue
        requested_id = int(item.get("castRoleId") or 0)
        role = ledger.get(requested_id) if requested_id > 0 else None
        if role is None:
            role = find_role_by_name(ledger, name)
        if role is None:
            role = CastRole(castRoleId=next_id, name=name, firstChapterIndex=chapter_index, lastChapterIndex=chapter_index)
            ledger[next_id] = role
            next_id += 1
        if (
            item.get("nameType") == "proper_name"
            and item.get("identityEvidence") == "explicit"
            and normalized_name(role.name) != normalized_name(name)
        ):
            role.aliases = distinct(role.aliases + [role.name], name)
            role.name = name
        elif normalized_name(name) != normalized_name(role.name):
            role.aliases = distinct(role.aliases + [name], role.name)
        merge_ids = [int(value) for value in item.get("mergeCastRoleIds") or [] if isinstance(value, int)]
        if item.get("identityEvidence") == "explicit" and float(item.get("confidence") or 0) >= 0.85:
            for source_id in merge_ids:
                merge_roles(ledger, role, source_id)
        if has_supported_gender(item) and evidence_rank(
            str(item.get("genderEvidence") or "unknown")
        ) > evidence_rank(role.genderEvidence):
            role.gender = str(item.get("speakerGender") or "unknown")
            role.genderEvidence = str(item.get("genderEvidence") or "unknown")
        if evidence_rank(str(item.get("identityEvidence") or "unknown")) > evidence_rank(role.identityEvidence):
            role.identityEvidence = str(item.get("identityEvidence") or "unknown")
        if name_type_rank(str(item.get("nameType") or "unknown")) > name_type_rank(role.nameType):
            role.nameType = str(item.get("nameType") or "unknown")
        if identity_type == "stable_candidate" or (
            item.get("nameType") == "proper_name" and item.get("identityEvidence") == "explicit"
        ):
            role.identityState = "stable"
        elif identity_type == "pending" and role.identityState != "stable":
            role.identityState = "pending"
        role.firstChapterIndex = min(role.firstChapterIndex, chapter_index)
        role.lastChapterIndex = max(role.lastChapterIndex, chapter_index)
        role.representativeTexts = distinct(role.representativeTexts + [row["text"]])[:4]
        role.evidence = distinct(role.evidence + [str(item.get("evidence") or "")])[:6]
        occurrences[role.castRoleId] = occurrences.get(role.castRoleId, 0) + 1
        row["resolvedCastRoleId"] = role.castRoleId
    for role_id, count in occurrences.items():
        role = ledger[role_id]
        role.chapterOccurrences[str(chapter_index)] = count
        role.occurrenceCount = sum(role.chapterOccurrences.values())
    return next_id, rows


def role_contains(role: CastRole, name: str) -> bool:
    key = normalized_name(name)
    return key in {normalized_name(role.name), *(normalized_name(item) for item in role.aliases)}


def evaluate_expectations(
    ledger: dict[int, CastRole],
    chapter_rows: list[dict[str, Any]],
    protocol_errors: list[str],
) -> dict[str, Any]:
    visible = [role for role in ledger.values() if role.identityState == "stable"]
    pending = [role for role in ledger.values() if role.identityState == "pending"]
    guest_names = [
        row["name"]
        for chapter in chapter_rows
        for row in chapter["rows"]
        if row["identityType"] == "guest" and row["name"]
    ]
    a_nuo_roles = [role for role in ledger.values() if role_contains(role, "阿糯")]
    novice_roles = [role for role in ledger.values() if role_contains(role, "小道童")]
    same_identity = bool(a_nuo_roles and novice_roles and a_nuo_roles[0].castRoleId == novice_roles[0].castRoleId)
    corrected_identity = bool(
        same_identity
        and a_nuo_roles[0].name == "阿糯"
        and "小道童" in a_nuo_roles[0].aliases
        and a_nuo_roles[0].identityState == "stable"
        and a_nuo_roles[0].nameType == "proper_name"
        and a_nuo_roles[0].identityEvidence == "explicit"
        and a_nuo_roles[0].gender == "female"
        and a_nuo_roles[0].genderEvidence == "explicit"
    )
    generic_visible = [
        expected
        for expected in GENERIC_EXPECTATIONS
        if any(role_contains(role, expected) for role in visible)
    ]
    mentioned_names = {
        normalized_name(row["name"])
        for chapter in chapter_rows
        for row in chapter["rows"]
        if row["name"]
    }
    generic_observed = [name for name in GENERIC_EXPECTATIONS if normalized_name(name) in mentioned_names]
    inconsistent_guests = sorted(
        {
            row["name"]
            for chapter in chapter_rows
            for row in chapter["rows"]
            if row["identityType"] == "guest" and row["nameType"] in {"proper_name", "alias", "unique_title"}
        }
    )
    checks = {
        "protocolValid": not protocol_errors,
        "aNuoAndNoviceMerged": same_identity,
        "aNuoIdentityCorrected": corrected_identity,
        "genericLabelsNotVisible": not generic_visible,
        "properNamesNotGuest": not inconsistent_guests,
    }
    return {
        "ok": all(checks.values()),
        "checks": checks,
        "protocolErrors": protocol_errors,
        "genericObserved": generic_observed,
        "genericGuestNames": sorted(set(guest_names)),
        "genericVisibleViolations": generic_visible,
        "inconsistentGuestNames": inconsistent_guests,
        "visibleRoles": [asdict(role) for role in visible],
        "pendingRoles": [asdict(role) for role in pending],
    }


def write_report(out_dir: Path, chapters: list[dict[str, Any]], summary: dict[str, Any]) -> None:
    lines = [
        "# 山河稷前六章分镜身份回传验证",
        "",
        f"- overall: {'PASS' if summary['ok'] else 'FAIL'}",
        f"- protocol: {'PASS' if summary['checks']['protocolValid'] else 'FAIL'}",
        f"- 小道童/阿糯同一身份: {'PASS' if summary['checks']['aNuoAndNoviceMerged'] else 'FAIL'}",
        f"- 待确认身份纠正为阿糯: {'PASS' if summary['checks']['aNuoIdentityCorrected'] else 'FAIL'}",
        f"- 泛称不进入可见临时池: {'PASS' if summary['checks']['genericLabelsNotVisible'] else 'FAIL'}",
        f"- 正式姓名不标路人: {'PASS' if summary['checks']['properNamesNotGuest'] else 'FAIL'}",
        f"- 观察到的目标泛称: {', '.join(summary['genericObserved']) or '-'}",
        f"- guest 称呼: {', '.join(summary['genericGuestNames']) or '-'}",
        "",
        "## 最终可见临时角色",
        "",
    ]
    for role in summary["visibleRoles"]:
        aliases = "、".join(role["aliases"]) or "-"
        lines.append(
            f"- #{role['castRoleId']} {role['name']} / aliases={aliases} / "
            f"{role['gender']} / count={role['occurrenceCount']}"
        )
    lines.extend(["", "## 后台待确认身份", ""])
    for role in summary["pendingRoles"]:
        lines.append(f"- #{role['castRoleId']} {role['name']} / {role['gender']} / count={role['occurrenceCount']}")
    lines.extend(["", "## 分章结果", ""])
    for chapter in chapters:
        lines.append(f"### {chapter['index']}. {chapter['title']}")
        lines.append("")
        for row in chapter["rows"]:
            if row["identityType"] == "none":
                continue
            text = row["text"].replace("\n", " ")[:100]
            lines.append(
                f"- {row['name'] or '-'} / {row['identityType']} / {row['nameType']} / "
                f"modelId={row.get('castRoleId')} / ledgerId={row.get('resolvedCastRoleId', '-')} / {text}"
            )
        lines.append("")
    (out_dir / "report.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    base.load_repo_env()
    api_key = os.getenv(args.api_key_env)
    if not api_key:
        print(f"missing env: {args.api_key_env}", file=sys.stderr)
        return 2
    book_path = Path(args.book)
    chapters = base.split_chapters(base.read_text(book_path))
    chapter_indexes = base.parse_range(args.chapters, len(chapters))
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    ledger: dict[int, CastRole] = {}
    if args.initial_summary:
        initial = json.loads(Path(args.initial_summary).read_text(encoding="utf-8"))
        for item in [*(initial.get("visibleRoles") or []), *(initial.get("pendingRoles") or [])]:
            role = CastRole(**item)
            ledger[role.castRoleId] = role
    next_id = max(ledger, default=0) + 1
    protocol_errors: list[str] = []
    chapter_rows: list[dict[str, Any]] = []
    system_prompt = base.build_system_prompt("basic", [])
    for chapter_number in chapter_indexes:
        chapter = chapters[chapter_number - 1]
        payload = base.build_storyboard_payload(chapter, args.max_chars, [], "basic", [])
        payload["book"] = {"name": args.book_title, "author": args.author}
        payload["knownCastRoles"] = [
            role.to_model_payload()
            for role in sorted(ledger.values(), key=lambda item: item.castRoleId)
            if role.identityState == "stable" or chapter.index - role.lastChapterIndex <= 12
        ]
        print(
            f"chapter {chapter.index}: {chapter.title} / units={len(payload['targetUnitIds'])} / "
            f"knownCastRoles={len(payload['knownCastRoles'])}",
            flush=True,
        )
        (out_dir / f"chapter_{chapter.index:03d}.payload.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        result_path = out_dir / f"chapter_{chapter.index:03d}.json"
        result = None
        if args.resume and result_path.exists():
            candidate = json.loads(result_path.read_text(encoding="utf-8"))
            if not validate_result(payload, candidate):
                result = apply_adjacent_gender_evidence(
                    payload,
                    normalize_identity_consistency(payload, candidate),
                )
                print("  reused existing valid result", flush=True)
        if result is None:
            raw, result = request_validated(args, payload, system_prompt)
            result = apply_adjacent_gender_evidence(
                payload,
                normalize_identity_consistency(payload, result),
            )
            (out_dir / f"chapter_{chapter.index:03d}.raw.json").write_text(
                json.dumps(raw, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        (out_dir / f"chapter_{chapter.index:03d}.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        errors = validate_result(payload, result)
        protocol_errors.extend(f"chapter {chapter.index}: {error}" for error in errors)
        next_id, rows = apply_chapter(ledger, next_id, chapter.index, payload, result)
        chapter_rows.append({"index": chapter.index, "title": chapter.title, "rows": rows})
        print(f"  ledger={len(ledger)} / schemaErrors={len(errors)}", flush=True)
        time.sleep(args.sleep)
    summary = evaluate_expectations(ledger, chapter_rows, protocol_errors)
    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    write_report(out_dir, chapter_rows, summary)
    print(json.dumps({"ok": summary["ok"], "checks": summary["checks"]}, ensure_ascii=False))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
