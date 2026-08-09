#!/usr/bin/env python3
"""Audit the parsed ExploreKind objects used by Legado's Explore UI.

Use --mcp-url for device audits.  That mode calls BookSource.exploreKinds() in
the installed app and treats the returned objects and render roles as the source
of truth.  Static/cache parsing remains available only for isolated unit tests
and offline diagnostics.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any, Iterable


URL_TYPE = "url"
CONTROL_TYPES = {"text", "toggle", "select"}
CONTROL_ROLES = {"button", "text_input", "toggle", "select"}
MAX_SPAN = 60
DEFAULT_SPAN = 12
CLICKABLE_ROOT_MIN_CHILDREN = 2
HEADER_GROUP_MIN_NAMED_SECTIONS = 2
UNGROUPED_MIN_ITEMS = 2
FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS = 3
FLATTENED_PARENT_MIN_CHILD_SECTIONS = 2
FALLBACK_LABEL = "分类"
UNGROUPED_LABEL = "未分组"


@dataclass(frozen=True)
class Kind:
    title: str = ""
    url: str = ""
    type: str = URL_TYPE
    action: str = ""
    view_name: str = ""
    grow: float = 0.0
    basis: float = -1.0
    wrap_before: bool = False
    index: int = -1
    runtime_role: str = ""

    @property
    def literal_label(self) -> str:
        value = self.view_name
        if 3 <= len(value) <= 19 and value[0] == value[-1] == "'":
            return value[1:-1]
        return self.title

    @property
    def section_label(self) -> str:
        value = self.view_name
        if len(value) >= 2 and value[0] == value[-1] == "'":
            return value[1:-1].strip()
        return self.title.strip()

    @property
    def display_label(self) -> str:
        return sanitize_label(self.literal_label) or FALLBACK_LABEL

    @property
    def is_header(self) -> bool:
        label = self.view_name if self.view_name.strip() else self.title
        return (
            self.type == URL_TYPE
            and not self.url.strip()
            and not self.action.strip()
            and bool(label.strip())
            and self.basis >= 1.0
        )

    @property
    def is_openable(self) -> bool:
        return self.render_role == "category"

    @property
    def render_role(self) -> str:
        if self.runtime_role:
            return self.runtime_role
        if self.type == URL_TYPE and self.title.startswith("ERROR:"):
            return "error"
        if self.type == URL_TYPE and self.url.strip():
            return "category"
        if self.type == "button" and self.action.strip():
            return "button"
        if self.type == "text":
            return "text_input"
        if self.type == "toggle":
            return "toggle"
        if self.type == "select":
            return "select"
        if self.type in {URL_TYPE, "button"}:
            return "passive"
        return "unsupported"

    @property
    def is_actionable(self) -> bool:
        return self.is_openable or self.is_control

    @property
    def is_control(self) -> bool:
        return self.render_role in CONTROL_ROLES

    @property
    def is_full_width_root(self) -> bool:
        return self.is_openable and self.basis >= 1.0


@dataclass(frozen=True)
class Section:
    header: Kind | None
    items: tuple[Kind, ...]


def sanitize_label(value: str) -> str:
    result: list[str] = []
    for char in value:
        code = ord(char)
        if (
            "A" <= char <= "Z"
            or "a" <= char <= "z"
            or char.isdigit()
            or code == 0x3007
            or 0x3400 <= code <= 0x4DBF
            or 0x4E00 <= code <= 0x9FFF
            or 0xF900 <= code <= 0xFAFF
            or 0x20000 <= code <= 0x2FA1F
            or 0x30000 <= code <= 0x323AF
        ):
            result.append(char)
    return "".join(result)


def _float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _kind_from_json(value: dict[str, Any], index: int) -> Kind:
    style = value.get("style") or {}
    if isinstance(style, str):
        try:
            style = json.loads(style)
        except json.JSONDecodeError:
            style = {}
    if not isinstance(style, dict):
        style = {}
    return Kind(
        title=str(value.get("title") or ""),
        url=str(value.get("url") or ""),
        type=str(value.get("type") or URL_TYPE),
        action=str(value.get("action") or ""),
        view_name=str(value.get("viewName") or ""),
        grow=_float(style.get("layout_flexGrow"), 0.0),
        basis=_float(style.get("layout_flexBasisPercent"), -1.0),
        wrap_before=bool(style.get("layout_wrapBefore", False)),
        index=int(value.get("index", index)),
        runtime_role=str(value.get("render_role") or ""),
    )


def fetch_runtime_kinds(
    mcp_url: str,
    source_url: str,
    timeout_seconds: float,
) -> tuple[list[Kind] | None, str | None]:
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "book_source_explore_kinds_get",
            "arguments": {
                "url": source_url,
                "timeout_seconds": timeout_seconds,
            },
        },
    }
    request = urllib.request.Request(
        mcp_url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds + 5) as response:
            body = json.loads(response.read().decode("utf-8"))
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
        return None, str(error)
    if "error" in body:
        return None, str(body["error"].get("message") or body["error"])
    result = body.get("result") or {}
    structured = result.get("structuredContent") or {}
    if not structured.get("ok"):
        warnings = structured.get("warnings") or []
        normalized = structured.get("normalized_data") or {}
        message = normalized.get("error_message") or "; ".join(map(str, warnings))
        return None, message or "runtime ExploreKind export failed"
    normalized = structured.get("normalized_data") or {}
    values = normalized.get("kinds")
    if not isinstance(values, list):
        return None, "runtime response does not contain a kinds array"
    return [_kind_from_json(value, index) for index, value in enumerate(values)], None


def parse_explore_url(raw: Any) -> tuple[str, list[Kind]]:
    """Parse formats the Android parser can resolve without executing JS."""
    if raw is None:
        return "empty", []
    if isinstance(raw, list):
        return "json", [_kind_from_json(value, index) for index, value in enumerate(raw)]

    text = str(raw)
    stripped = text.strip()
    if not stripped:
        return "empty", []
    if stripped.lower().startswith("@js:") or stripped.lower().startswith("<js>"):
        return "dynamic", []
    if stripped.startswith("["):
        try:
            values = json.loads(stripped)
        except json.JSONDecodeError:
            values = None
        if isinstance(values, list):
            return "json", [_kind_from_json(value, index) for index, value in enumerate(values)]

    parts = re.split(r"(?:&&|\r?\n)+", text)
    kinds: list[Kind] = []
    for index, part in enumerate(parts):
        fields = part.split("::")
        kinds.append(
            Kind(
                title=fields[0] if fields else "",
                url=fields[1] if len(fields) > 1 else "",
                index=index,
            )
        )
    return "legacy", kinds


def visible_kinds(kinds: Iterable[Kind]) -> list[Kind]:
    """Return only elements that participate in the category grammar."""
    return [kind for kind in kinds if kind.is_header or kind.is_openable]


def header_sections(kinds: list[Kind]) -> list[Section]:
    sections: list[Section] = []
    header: Kind | None = None
    items: list[Kind] = []
    for kind in visible_kinds(kinds):
        if kind.is_header:
            if header is not None or items:
                sections.append(Section(header, tuple(items)))
            header = kind
            items = []
        else:
            items.append(kind)
    if header is not None or items:
        sections.append(Section(header, tuple(items)))
    return sections


def clickable_root_sections(kinds: list[Kind]) -> list[Section] | None:
    visible = visible_kinds(kinds)
    root_indices = [index for index, kind in enumerate(visible) if kind.is_full_width_root]
    if len(root_indices) < 2 or root_indices[0] != 0:
        return None

    sections: list[Section] = []
    for position, root_index in enumerate(root_indices):
        next_index = root_indices[position + 1] if position + 1 < len(root_indices) else len(visible)
        root = visible[root_index]
        children = visible[root_index + 1 : next_index]
        all_item = replace(root, view_name="'全部'", grow=1.0, basis=0.2, wrap_before=False)
        sections.append(Section(root, tuple([all_item, *children])))

    labels = [section.header.section_label for section in sections if section.header]
    unambiguous = all(
        len(section.items[1:]) >= CLICKABLE_ROOT_MIN_CHILDREN
        and all(child.is_openable and not child.is_full_width_root for child in section.items[1:])
        for section in sections
    )
    if not unambiguous or len(set(labels)) != len(labels):
        return None
    return sections


def nested_clickable_root_sections(kinds: list[Kind]) -> list[Section] | None:
    root_indices = [index for index, kind in enumerate(kinds) if kind.is_full_width_root]
    if not root_indices or root_indices[0] != 0:
        return None

    sections: list[Section] = []
    for position, root_index in enumerate(root_indices):
        next_index = root_indices[position + 1] if position + 1 < len(root_indices) else len(kinds)
        root = kinds[root_index]
        children = kinds[root_index + 1 : next_index]
        all_item = replace(root, view_name="'全部'", grow=1.0, basis=0.2, wrap_before=False)
        sections.append(Section(root, tuple([all_item, *children])))

    direct_roots_supported = all(
        len(section.items[1:]) >= CLICKABLE_ROOT_MIN_CHILDREN
        and all(child.is_openable and not child.is_full_width_root for child in section.items[1:])
        for section in sections
    )
    if direct_roots_supported:
        return sections

    parent_indices = [
        index for index, section in enumerate(sections) if len(section.items) == 1
    ]
    if not parent_indices:
        return None
    child_counts = []
    for position, parent_index in enumerate(parent_indices):
        next_parent_index = (
            parent_indices[position + 1]
            if position + 1 < len(parent_indices)
            else len(sections)
        )
        child_counts.append(
            sum(1 for section in sections[parent_index + 1 : next_parent_index] if len(section.items) > 1)
        )
    if len(parent_indices) == 1:
        supported_parent_shape = (
            child_counts[0] >= FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS
        )
    else:
        supported_parent_shape = all(
            count >= FLATTENED_PARENT_MIN_CHILD_SECTIONS for count in child_counts
        )
    if not supported_parent_shape:
        return None
    if any(
        len(section.items[1:]) < CLICKABLE_ROOT_MIN_CHILDREN
        or any(not child.is_openable or child.is_full_width_root for child in section.items[1:])
        for index, section in enumerate(sections)
        if index not in parent_indices
    ):
        return None

    parent_root: Kind | None = None
    candidates: list[tuple[Kind, Kind | None, tuple[Kind, ...]]] = []
    for section in sections:
        if section.header is None:
            return None
        if len(section.items) == 1:
            parent_root = section.header
            candidates.append((section.header, None, section.items))
        else:
            candidates.append((section.header, parent_root, section.items))

    nearest_labels = [sanitize_label(candidate[0].section_label) for candidate in candidates]
    if any(not label for label in nearest_labels):
        return None
    nearest_counts = {label: nearest_labels.count(label) for label in set(nearest_labels)}
    result: list[Section] = []
    for index, (nearest_header, parent, items) in enumerate(candidates):
        nearest_label = nearest_labels[index]
        if nearest_counts[nearest_label] == 1:
            final_label = nearest_label
        else:
            if parent is None:
                return None
            parent_label = sanitize_label(parent.section_label)
            if not parent_label:
                return None
            final_label = parent_label + nearest_label
        result.append(Section(replace(nearest_header, view_name=f"'{final_label}'"), items))

    final_labels = [section.header.section_label for section in result if section.header]
    if len(final_labels) != len(result) or len(set(final_labels)) != len(final_labels):
        return None
    return result


def flattened_header_sections(source_sections: list[Section]) -> list[Section] | None:
    if any(section.header is None for section in source_sections):
        return None

    parent_indices = [
        index
        for index, section in enumerate(source_sections)
        if section.header is not None and not section.items
    ]
    if not parent_indices:
        return None

    child_counts = []
    for position, parent_index in enumerate(parent_indices):
        next_parent_index = (
            parent_indices[position + 1]
            if position + 1 < len(parent_indices)
            else len(source_sections)
        )
        child_counts.append(
            sum(
                1
                for section in source_sections[parent_index + 1 : next_parent_index]
                if section.header is not None and section.items
            )
        )
    if len(parent_indices) == 1:
        supported_parent_shape = (
            child_counts[0] >= FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS
        )
    else:
        supported_parent_shape = all(
            count >= FLATTENED_PARENT_MIN_CHILD_SECTIONS for count in child_counts
        )
    if not supported_parent_shape:
        return None

    parent_header: Kind | None = None
    candidates: list[tuple[Kind, Kind | None, tuple[Kind, ...]]] = []
    for section in source_sections:
        if section.header is None:
            return None
        if not section.items:
            parent_header = section.header
            continue
        if any(not item.is_openable for item in section.items):
            return None

        roots = nested_clickable_root_sections(list(section.items))
        if roots is not None:
            for root_section in roots:
                if root_section.header is None:
                    return None
                candidates.append(
                    (
                        root_section.header,
                        parent_header or section.header,
                        root_section.items,
                    )
                )
        else:
            candidates.append((section.header, parent_header, section.items))

    if len(candidates) < HEADER_GROUP_MIN_NAMED_SECTIONS:
        return None

    nearest_labels = [sanitize_label(candidate[0].section_label) for candidate in candidates]
    if any(not label for label in nearest_labels):
        return None
    nearest_counts = {label: nearest_labels.count(label) for label in set(nearest_labels)}

    result: list[Section] = []
    for index, (nearest_header, parent, items) in enumerate(candidates):
        nearest_label = nearest_labels[index]
        if nearest_counts[nearest_label] == 1:
            final_label = nearest_label
        else:
            if parent is None:
                return None
            parent_label = sanitize_label(parent.section_label)
            if not parent_label:
                return None
            final_label = parent_label + nearest_label
        result.append(
            Section(
                replace(nearest_header, view_name=f"'{final_label}'"),
                items,
            )
        )

    final_labels = [section.header.section_label for section in result if section.header]
    if len(final_labels) != len(result) or len(set(final_labels)) != len(final_labels):
        return None
    return result


def build_sections(kinds: list[Kind]) -> tuple[list[Section], bool, str]:
    visible = visible_kinds(kinds)
    if not visible:
        return [], False, "empty"

    def inline_category_sections() -> list[Section]:
        categories = tuple(kind for kind in visible if kind.is_openable)
        return [Section(None, categories)] if categories else []

    # Explicit blank-url headers and clickable full-width roots are separate
    # grammars.  They never compete for the same list.
    if any(kind.is_header for kind in visible):
        sections = header_sections(visible)
        leading = next(
            (
                section
                for section in sections[:1]
                if section.header is None
                and len(section.items) >= UNGROUPED_MIN_ITEMS
                and all(item.is_openable for item in section.items)
            ),
            None,
        )
        if sections[0].header is None and leading is None:
            return inline_category_sections(), False, "inline"
        named = sections[1:] if leading is not None else sections
        labels = [section.header.section_label for section in named if section.header]
        use_top_level = (
            len(named) >= HEADER_GROUP_MIN_NAMED_SECTIONS
            and all(
                section.header is not None
                and bool(section.items)
                and all(item.is_openable for item in section.items)
                for section in named
            )
            and len(labels) == len(named)
            and len(set(labels)) == len(labels)
        )
        if use_top_level:
            return sections, True, "header_groups"
        flattened = flattened_header_sections(sections)
        if flattened is not None:
            return flattened, True, "header_groups"
        return inline_category_sections(), False, "inline"

    roots = clickable_root_sections(visible)
    if roots is not None:
        return roots, True, "clickable_roots"

    return inline_category_sections(), False, "inline"


def legacy_nonexclusive_header_group_candidate(kinds: list[Kind]) -> bool:
    """Report structures accepted by the old permissive header rule.

    This is audit-only and must not participate in production replay.
    """
    sections = header_sections(kinds)
    leading = next(
        (
            section
            for section in sections[:1]
            if section.header is None
            and section.items
            and all(item.is_openable for item in section.items)
        ),
        None,
    )
    named = sections[1:] if leading is not None else sections
    labels = [section.header.section_label for section in named if section.header]
    minimum_named = 1 if leading is not None else 2
    use_top_level = (
        len(named) >= minimum_named
        and all(section.header is not None and any(item.is_openable for item in section.items) for section in named)
        and len(labels) == len(named)
        and len(set(labels)) == len(labels)
    )
    return use_top_level


def item_span(kind: Kind) -> int:
    if kind.is_openable:
        return DEFAULT_SPAN
    if kind.basis >= 1.0:
        return MAX_SPAN
    if kind.basis > 0.0:
        return max(1, min(MAX_SPAN, int(MAX_SPAN * kind.basis + 0.5)))
    return DEFAULT_SPAN


def calculate_rows(kinds: Iterable[Kind]) -> list[list[tuple[Kind, int]]]:
    rows: list[list[tuple[Kind, int]]] = []
    current: list[tuple[Kind, int]] = []
    current_span = 0
    for kind in kinds:
        span = item_span(kind)
        if (not kind.is_openable and kind.wrap_before and current) or current_span + span > MAX_SPAN:
            rows.append(current)
            current = []
            current_span = 0
        current.append((kind, span))
        current_span += span
        if current_span >= MAX_SPAN:
            rows.append(current)
            current = []
            current_span = 0
    if current:
        rows.append(current)
    return rows


def _item_report(kind: Kind, span: int) -> dict[str, Any]:
    label_resolution = "title"
    if kind.view_name:
        label_resolution = (
            "quoted_literal"
            if 3 <= len(kind.view_name) <= 19
            and kind.view_name[0] == kind.view_name[-1] == "'"
            else "runtime_expression"
        )
    return {
        "index": kind.index,
        "raw_label": kind.literal_label,
        "display_label": kind.display_label,
        "view_name": kind.view_name,
        "label_resolution": label_resolution,
        "type": kind.type,
        "render_role": kind.render_role,
        "span": span,
        "openable": kind.is_openable,
        "full_width": span >= MAX_SPAN,
    }


def _panel_report(name: str, sections: Iterable[Section], include_headers: bool) -> dict[str, Any]:
    logical_rows: list[dict[str, Any]] = []
    item_count = 0
    for section in sections:
        if include_headers and section.header is not None:
            logical_rows.append(
                {
                    "kind": "section_header",
                    "raw_label": section.header.section_label,
                    "display_label": sanitize_label(section.header.section_label) or FALLBACK_LABEL,
                    "source_index": section.header.index,
                }
            )
        for row in calculate_rows(section.items):
            logical_rows.append(
                {
                    "kind": "category_row",
                    "items": [_item_report(kind, span) for kind, span in row],
                }
            )
            item_count += len(row)
    return {
        "name": name,
        "item_count": item_count,
        "logical_row_count": len(logical_rows),
        "first_two_logical_rows": logical_rows[:2],
        "rows": logical_rows,
    }


def category_box_report(kinds: list[Kind]) -> dict[str, Any]:
    controls = [kind for kind in kinds if kind.is_control]
    control_rows = [
        {
            "kind": "control_row",
            "items": [_item_report(kind, span) for kind, span in row],
        }
        for row in calculate_rows(controls)
    ]
    control_report = {
        "item_count": len(controls),
        "logical_row_count": len(control_rows),
        "rows": control_rows,
    }
    sections, use_top_level, mode = build_sections(kinds)
    if not sections:
        return {
            "status": "controls_only" if controls else "empty",
            "mode": mode,
            "tabs": ["全部"] if controls else [],
            "panels": [],
            "controls": control_report,
            "initial_selected": None,
        }

    if use_top_level:
        tabs = [
            sanitize_label(section.header.section_label) or FALLBACK_LABEL
            if section.header is not None
            else UNGROUPED_LABEL
            for section in sections
        ]
        panels = [
            _panel_report(tab, [section], include_headers=False)
            for tab, section in zip(tabs, sections)
        ]
    else:
        tabs = ["全部"]
        panels = [_panel_report("全部", sections, include_headers=True)]

    # ExploreShowViewModel selects from the original ExploreKind list.  Do not
    # derive the initial item from the first rendered panel: clickable roots use
    # a copied "全部" item and inline/header layouts may start with header-only
    # sections.
    initial_kind = next((kind for kind in kinds if kind.is_openable), None)
    initial_section_index = next(
        (
            index
            for index, section in enumerate(sections)
            if initial_kind is not None and initial_kind in section.items
        ),
        0,
    )
    initial_panel_index = initial_section_index if use_top_level else 0
    return {
        "status": "replayed",
        "mode": mode,
        "use_top_level_groups": use_top_level,
        "tabs": tabs,
        "initial_tab": tabs[0],
        "initial_selected": initial_kind.display_label if initial_kind else None,
        "initial_panel_index": initial_panel_index,
        "initial_panel": panels[initial_panel_index]["name"] if panels else None,
        "controls": control_report,
        "panels": panels,
    }


def _issue(code: str, **details: Any) -> dict[str, Any]:
    return {"code": code, **details}


def explore_cache_filename(source: dict[str, Any]) -> str:
    source_key = str(source.get("bookSourceUrl") or "") + str(source.get("exploreUrl") or "")
    digest = hashlib.md5(source_key.encode("utf-8")).hexdigest()
    value = 0
    for char in digest:
        value = (31 * value + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return str(value)


def load_adb_explore_cache(package_name: str) -> dict[str, str]:
    adb = shutil.which("adb") or shutil.which("adb.exe")
    if adb is None:
        raise RuntimeError("adb/adb.exe was not found in PATH")
    listing = subprocess.run(
        [adb, "shell", "run-as", package_name, "ls", "cache/explore"],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    values: dict[str, str] = {}
    for name in listing.stdout.splitlines():
        name = name.strip()
        if not re.fullmatch(r"-?\d+", name):
            continue
        content = subprocess.run(
            [adb, "exec-out", "run-as", package_name, "cat", f"cache/explore/{name}"],
            check=True,
            capture_output=True,
        ).stdout.decode("utf-8")
        values[name] = content
    return values


def audit_source(
    source: dict[str, Any],
    runtime_value: str | None = None,
    runtime_kinds: list[Kind] | None = None,
    runtime_error: str | None = None,
) -> dict[str, Any]:
    source_name = str(source.get("bookSourceName") or "")
    source_url = str(source.get("bookSourceUrl") or "")
    original_format, kinds = parse_explore_url(source.get("exploreUrl"))
    source_format = original_format
    runtime_object_used = runtime_kinds is not None
    if runtime_object_used:
        source_format = "runtime_parsed"
        kinds = runtime_kinds
    elif original_format == "dynamic" and runtime_value is not None:
        cached_format, cached_kinds = parse_explore_url(runtime_value)
        if cached_format in {"json", "legacy"}:
            source_format = "dynamic_cached"
            kinds = cached_kinds
    result: dict[str, Any] = {
        "source_name": source_name,
        "source_url": source_url,
        "format": source_format,
        "original_format": original_format,
        "runtime_cache_used": source_format == "dynamic_cached",
        "runtime_object_used": runtime_object_used,
        "kind_count": len(kinds),
        "render_role_counts": {
            role: sum(kind.render_role == role for kind in kinds)
            for role in sorted({kind.render_role for kind in kinds})
        },
        "issues": [],
    }
    if runtime_error is not None:
        result["category_box"] = {
            "status": "runtime_error",
            "reason": runtime_error,
            "mode": "runtime_error",
            "tabs": [],
            "panels": [],
        }
        result["issues"].append(_issue("runtime_export_failed", reason=runtime_error))
        return result
    if source_format == "dynamic":
        result["category_box"] = {
            "status": "runtime_required",
            "reason": "exploreUrl executes JavaScript and cannot be replayed statically",
        }
        result["issues"].append(_issue("dynamic_requires_runtime_capture"))
        return result
    if source_format == "empty":
        result["category_box"] = {"status": "empty", "mode": "empty", "tabs": [], "panels": []}
        return result

    original_text = str(source.get("exploreUrl") or "").strip()
    if original_format == "legacy" and original_text.startswith("["):
        result["issues"].append(
            _issue(
                "json_like_value_fell_back_to_legacy",
                reason="value starts with '[' but is not a valid JSON array",
            )
        )

    result["category_box"] = category_box_report(kinds)
    inert = [kind for kind in kinds if not kind.is_header and not kind.is_actionable]
    if inert:
        result["issues"].append(
            _issue(
                "filtered_inert_items",
                count=len(inert),
                items=[
                    {"index": kind.index, "title": kind.title, "type": kind.type, "basis": kind.basis}
                    for kind in inert[:20]
                ],
            )
        )

    sections, use_top_level, mode = build_sections(kinds)
    if mode == "inline" and legacy_nonexclusive_header_group_candidate(kinds):
        result["issues"].append(
            _issue(
                "legacy_header_group_rejected_by_ng_contract",
                reason=(
                    "the old permissive rule accepted this structure, but the exclusive NG "
                    "grammar requires at least two named groups, ordinary URL-only children, "
                    "and at least two leading ungrouped items"
                ),
            )
        )
    if mode == "inline":
        headers = [section.header for section in sections if section.header is not None]
        if headers:
            result["issues"].append(
                _issue(
                    "section_headers_inside_category_box",
                    count=len(headers),
                    labels=[sanitize_label(header.section_label) or FALLBACK_LABEL for header in headers],
                )
            )
        header_labels = [header.section_label for header in headers]
        duplicate_labels = sorted(
            {
                sanitize_label(label) or FALLBACK_LABEL
                for label in header_labels
                if header_labels.count(label) > 1
            }
        )
        if duplicate_labels:
            result["issues"].append(
                _issue(
                    "duplicate_section_labels_force_inline",
                    labels=duplicate_labels,
                )
            )
    panels = result["category_box"].get("panels", [])
    for panel in panels:
        if panel["logical_row_count"] > 2:
            result["issues"].append(
                _issue(
                    "category_box_scrolls",
                    panel=panel["name"],
                    logical_rows=panel["logical_row_count"],
                )
            )
        if panel["item_count"] == 1:
            result["issues"].append(
                _issue("single_item_category_box", panel=panel["name"])
            )
        leading_headers = 0
        for row in panel["rows"]:
            if row["kind"] != "section_header":
                break
            leading_headers += 1
        if leading_headers >= 2:
            result["issues"].append(
                _issue(
                    "first_viewport_contains_only_headers",
                    panel=panel["name"],
                    count=leading_headers,
                    labels=[
                        row["display_label"]
                        for row in panel["rows"][:leading_headers]
                    ],
                )
            )

        fallback_items = [
            item
            for row in panel["rows"]
            if row["kind"] == "category_row"
            for item in row["items"]
            if item["display_label"] == FALLBACK_LABEL
            and sanitize_label(item["raw_label"]) != FALLBACK_LABEL
        ]
        if fallback_items:
            result["issues"].append(
                _issue(
                    "category_label_sanitized_to_fallback",
                    panel=panel["name"],
                    items=[
                        {
                            "index": item["index"],
                            "raw_label": item["raw_label"],
                        }
                        for item in fallback_items[:20]
                    ],
                )
            )

        non_category_items = [
            item
            for row in panel["rows"]
            if row["kind"] == "category_row"
            for item in row["items"]
            if item["render_role"] != "category"
        ]
        if non_category_items:
            result["issues"].append(
                _issue(
                    "non_category_role_inside_category_box",
                    panel=panel["name"],
                    items=[
                        {
                            "index": item["index"],
                            "label": item["display_label"],
                            "type": item["type"],
                        }
                        for item in non_category_items[:20]
                    ],
                )
            )

        nonstandard_category_spans = [
            item
            for row in panel["rows"]
            if row["kind"] == "category_row"
            for item in row["items"]
            if item["render_role"] == "category" and item["span"] != DEFAULT_SPAN
        ]
        if nonstandard_category_spans:
            result["issues"].append(
                _issue(
                    "nonstandard_category_span_inside_category_box",
                    panel=panel["name"],
                    items=[
                        {
                            "index": item["index"],
                            "label": item["display_label"],
                            "span": item["span"],
                        }
                        for item in nonstandard_category_spans[:20]
                    ],
                )
            )

        runtime_labels = [
            item
            for row in panel["rows"]
            if row["kind"] == "category_row"
            for item in row["items"]
            if item["label_resolution"] == "runtime_expression"
        ]
        if runtime_labels:
            result["issues"].append(
                _issue(
                    "runtime_label_expressions_inside_category_box",
                    panel=panel["name"],
                    count=len(runtime_labels),
                    items=[
                        {
                            "index": item["index"],
                            "title_preview": item["display_label"],
                            "view_name": item["view_name"],
                        }
                        for item in runtime_labels[:20]
                    ],
                )
            )

    suspicious_terms = ("首页", "个人中心", "我的收藏", "观看记录", "作者集", "登录", "导入")
    suspicious = [
        kind
        for kind in visible_kinds(kinds)
        if kind.is_openable and any(term in sanitize_label(kind.literal_label) for term in suspicious_terms)
    ]
    if suspicious:
        result["issues"].append(
            _issue(
                "function_like_openable_categories",
                items=[{"index": kind.index, "label": kind.display_label} for kind in suspicious],
            )
        )
    return result


def audit_collection(
    values: list[dict[str, Any]],
    runtime_cache: dict[str, str] | None = None,
    runtime_kinds_by_url: dict[str, list[Kind]] | None = None,
    runtime_errors_by_url: dict[str, str] | None = None,
) -> dict[str, Any]:
    runtime_cache = runtime_cache or {}
    runtime_kinds_by_url = runtime_kinds_by_url or {}
    runtime_errors_by_url = runtime_errors_by_url or {}
    sources = [
        audit_source(
            value,
            runtime_cache.get(explore_cache_filename(value)),
            runtime_kinds_by_url.get(str(value.get("bookSourceUrl") or "")),
            runtime_errors_by_url.get(str(value.get("bookSourceUrl") or "")),
        )
        for value in values
    ]
    issue_source_counts: dict[str, int] = {}
    issue_occurrence_counts: dict[str, int] = {}
    format_counts: dict[str, int] = {}
    replay_status_counts: dict[str, int] = {}
    mode_counts: dict[str, int] = {}
    render_role_counts: dict[str, int] = {}
    render_role_source_counts: dict[str, int] = {}
    panel_count = 0
    category_item_count = 0
    control_item_count = 0
    section_header_count = 0
    for source in sources:
        format_counts[source["format"]] = format_counts.get(source["format"], 0) + 1
        status = source["category_box"]["status"]
        replay_status_counts[status] = replay_status_counts.get(status, 0) + 1
        for role, count in source["render_role_counts"].items():
            render_role_counts[role] = render_role_counts.get(role, 0) + count
            render_role_source_counts[role] = render_role_source_counts.get(role, 0) + 1
        control_item_count += source["category_box"].get("controls", {}).get("item_count", 0)
        if status == "replayed":
            box = source["category_box"]
            mode = box["mode"]
            mode_counts[mode] = mode_counts.get(mode, 0) + 1
            panels = box["panels"]
            panel_count += len(panels)
            category_item_count += sum(panel["item_count"] for panel in panels)
            section_header_count += sum(
                row["kind"] == "section_header"
                for panel in panels
                for row in panel["rows"]
            )
        source_issue_codes: set[str] = set()
        for issue in source["issues"]:
            code = issue["code"]
            issue_occurrence_counts[code] = issue_occurrence_counts.get(code, 0) + 1
            source_issue_codes.add(code)
        for code in source_issue_codes:
            issue_source_counts[code] = issue_source_counts.get(code, 0) + 1
    return {
        "summary": {
            "source_count": len(sources),
            "format_counts": dict(sorted(format_counts.items())),
            "replay_status_counts": dict(sorted(replay_status_counts.items())),
            "mode_source_counts": dict(sorted(mode_counts.items())),
            "parsed_render_role_counts": dict(sorted(render_role_counts.items())),
            "parsed_render_role_source_counts": dict(sorted(render_role_source_counts.items())),
            "rendered_panel_count": panel_count,
            "rendered_section_header_count": section_header_count,
            "rendered_category_item_count": category_item_count,
            "rendered_function_control_count": control_item_count,
            "issue_source_counts": dict(sorted(issue_source_counts.items())),
            "issue_occurrence_counts": dict(sorted(issue_occurrence_counts.items())),
        },
        "sources": sources,
    }


def inventory_rows(report: dict[str, Any]) -> Iterable[dict[str, Any]]:
    """Flatten every category-box element for spreadsheet/manual review."""
    for source in report["sources"]:
        box = source["category_box"]
        base = {
            "source_name": source["source_name"],
            "source_url": source["source_url"],
            "source_format": source["format"],
            "replay_status": box["status"],
            "mode": box.get("mode", ""),
            "initial_selected": box.get("initial_selected", ""),
        }
        for row_index, row in enumerate(box.get("controls", {}).get("rows", []), start=1):
            for item in row["items"]:
                yield {
                    **base,
                    "panel": "功能",
                    "logical_row": row_index,
                    "element_kind": "function_control",
                    "source_index": item["index"],
                    "raw_label": item["raw_label"],
                    "display_label": item["display_label"],
                    "type": item["type"],
                    "render_role": item["render_role"],
                    "span": item["span"],
                    "openable": item["openable"],
                    "full_width": item["full_width"],
                    "label_resolution": item["label_resolution"],
                }
        if box["status"] != "replayed":
            yield {
                **base,
                "panel": "",
                "logical_row": "",
                "element_kind": box["status"],
                "source_index": "",
                "raw_label": "",
                "display_label": "",
                "type": "",
                "render_role": "",
                "span": "",
                "openable": "",
                "full_width": "",
                "label_resolution": "",
            }
            continue
        for panel in box["panels"]:
            for row_index, row in enumerate(panel["rows"], start=1):
                if row["kind"] == "section_header":
                    yield {
                        **base,
                        "panel": panel["name"],
                        "logical_row": row_index,
                        "element_kind": "section_header",
                        "source_index": row["source_index"],
                        "raw_label": row["raw_label"],
                        "display_label": row["display_label"],
                        "type": URL_TYPE,
                        "render_role": "passive",
                        "span": MAX_SPAN,
                        "openable": False,
                        "full_width": True,
                        "label_resolution": "section_label",
                    }
                    continue
                for item in row["items"]:
                    yield {
                        **base,
                        "panel": panel["name"],
                        "logical_row": row_index,
                        "element_kind": "category_item",
                        "source_index": item["index"],
                        "raw_label": item["raw_label"],
                        "display_label": item["display_label"],
                        "type": item["type"],
                        "render_role": item["render_role"],
                        "span": item["span"],
                        "openable": item["openable"],
                        "full_width": item["full_width"],
                        "label_resolution": item["label_resolution"],
                    }


def write_inventory(report: dict[str, Any], path: Path) -> None:
    rows = list(inventory_rows(report))
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "source_name",
        "source_url",
        "source_format",
        "replay_status",
        "mode",
        "initial_selected",
        "panel",
        "logical_row",
        "element_kind",
        "source_index",
        "raw_label",
        "display_label",
        "type",
        "render_role",
        "span",
        "openable",
        "full_width",
        "label_resolution",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _print_panel(source: dict[str, Any]) -> None:
    box = source["category_box"]
    print(f"\n{source['source_name']} [{source['format']}] status={box['status']}")
    if box["status"] != "replayed":
        print(f"  {box.get('reason', '')}")
        return
    print(f"  mode={box['mode']} tabs={box['tabs']} initial={box['initial_selected']}")
    controls = box.get("controls", {})
    if controls.get("item_count"):
        labels = [
            item["display_label"]
            for row in controls["rows"]
            for item in row["items"]
        ]
        print(f"  controls={labels}")
    for panel in box["panels"]:
        print(f"  panel={panel['name']} rows={panel['logical_row_count']} items={panel['item_count']}")
        for row in panel["rows"]:
            if row["kind"] == "section_header":
                print(f"    HEADER: {row['display_label']}")
            else:
                labels = [item["display_label"] for item in row["items"]]
                print(f"    ROW: {labels}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("collection", type=Path, help="Path to a Legado book-source JSON collection")
    parser.add_argument("--output", type=Path, help="Optional full JSON report path")
    parser.add_argument(
        "--inventory-output",
        type=Path,
        help="Optional UTF-8 CSV containing every element that enters each category box",
    )
    parser.add_argument(
        "--show",
        choices=("summary", "risks", "panels", "all"),
        default="summary",
    )
    parser.add_argument(
        "--source",
        action="append",
        default=[],
        help="Only print sources whose name contains this text; may be repeated",
    )
    parser.add_argument(
        "--adb-package",
        help="Read evaluated dynamic explore results from this debuggable Android package",
    )
    parser.add_argument(
        "--mcp-url",
        help="Read the actual BookSource.exploreKinds() result from the installed app, e.g. http://192.168.11.11:1124/mcp",
    )
    parser.add_argument(
        "--mcp-timeout",
        type=float,
        default=30.0,
        help="Per-source runtime export timeout in seconds",
    )
    args = parser.parse_args()

    if args.adb_package and args.mcp_url:
        parser.error("--adb-package and --mcp-url are mutually exclusive")

    with args.collection.open("r", encoding="utf-8-sig") as handle:
        values = json.load(handle)
    runtime_cache = load_adb_explore_cache(args.adb_package) if args.adb_package else {}
    runtime_kinds_by_url: dict[str, list[Kind]] = {}
    runtime_errors_by_url: dict[str, str] = {}
    if args.mcp_url:
        for position, source in enumerate(values, start=1):
            source_url = str(source.get("bookSourceUrl") or "")
            if not source_url:
                runtime_errors_by_url[source_url] = "source has no bookSourceUrl"
                continue
            kinds, error = fetch_runtime_kinds(args.mcp_url, source_url, args.mcp_timeout)
            if kinds is not None:
                runtime_kinds_by_url[source_url] = kinds
            else:
                runtime_errors_by_url[source_url] = error or "runtime export failed"
            if position % 25 == 0 or position == len(values):
                print(
                    f"runtime parsed {position}/{len(values)}; "
                    f"ok={len(runtime_kinds_by_url)} error={len(runtime_errors_by_url)}",
                    file=sys.stderr,
                )
    report = audit_collection(
        values,
        runtime_cache,
        runtime_kinds_by_url,
        runtime_errors_by_url,
    )
    report["summary"]["adb_cache_file_count"] = len(runtime_cache)
    report["summary"]["dynamic_cache_match_count"] = sum(
        source["runtime_cache_used"] for source in report["sources"]
    )
    report["summary"]["runtime_object_source_count"] = sum(
        source["runtime_object_used"] for source in report["sources"]
    )
    report["summary"]["runtime_export_error_count"] = len(runtime_errors_by_url)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.inventory_output:
        write_inventory(report, args.inventory_output)

    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    selected = report["sources"]
    if args.source:
        selected = [
            source
            for source in selected
            if any(value in source["source_name"] for value in args.source)
        ]
    if args.show in {"panels", "all"}:
        for source in selected:
            _print_panel(source)
    if args.show in {"risks", "all"}:
        for source in selected:
            if source["issues"]:
                print(f"\n{source['source_name']} risks")
                for issue in source["issues"]:
                    print(f"  - {json.dumps(issue, ensure_ascii=False)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
