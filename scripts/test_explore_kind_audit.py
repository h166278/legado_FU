import unittest

from explore_kind_audit import (
    Kind,
    audit_collection,
    audit_source,
    build_sections,
    calculate_rows,
    category_box_report,
    parse_explore_url,
    sanitize_label,
    inventory_rows,
)


class ExploreKindAuditTest(unittest.TestCase):

    def test_render_roles_match_production_list_interaction_semantics(self):
        values = [
            {"title": "分类", "url": "/books", "render_role": "category"},
            {"title": "按钮", "type": "button", "action": "run()", "render_role": "button"},
            {"title": "输入", "type": "text", "render_role": "text_input"},
            {"title": "切换", "type": "toggle", "render_role": "toggle"},
            {"title": "选择", "type": "select", "render_role": "select"},
            {"title": "说明", "render_role": "passive"},
        ]
        _, kinds = parse_explore_url(values)

        self.assertEqual(
            ["category", "button", "text_input", "toggle", "select", "passive"],
            [kind.render_role for kind in kinds],
        )
        self.assertEqual(
            [True, True, True, True, True, False],
            [kind.is_actionable for kind in kinds],
        )

    def test_runtime_parsed_objects_replace_raw_source_interpretation(self):
        result = audit_source(
            {
                "bookSourceName": "运行时",
                "bookSourceUrl": "https://example.test",
                "exploreUrl": "错误原始值::/wrong",
            },
            runtime_kinds=[
                Kind(title="真实分类", url="/actual", runtime_role="category", index=0),
                Kind(title="功能按钮", type="button", action="run()", runtime_role="button", index=1),
            ],
        )

        self.assertEqual("runtime_parsed", result["format"])
        self.assertTrue(result["runtime_object_used"])
        items = result["category_box"]["panels"][0]["rows"][0]["items"]
        controls = result["category_box"]["controls"]["rows"][0]["items"]
        self.assertEqual(["真实分类"], [item["display_label"] for item in items])
        self.assertEqual(["category"], [item["render_role"] for item in items])
        self.assertEqual(["功能按钮"], [item["display_label"] for item in controls])
        self.assertEqual(["button"], [item["render_role"] for item in controls])

    def test_sanitizer_matches_detail_page_character_policy(self):
        self.assertEqual("男生频道R18G2026", sanitize_label(" ༺ 男 生频道 R18G-2026 ༻ "))

    def test_legacy_trailing_line_is_filtered_from_category_box(self):
        result = audit_source(
            {
                "bookSourceName": "漫画",
                "exploreUrl": "全部::/all\n奇幻::/fantasy\n",
            }
        )

        issue = next(issue for issue in result["issues"] if issue["code"] == "filtered_inert_items")
        self.assertEqual(1, issue["count"])
        rows = result["category_box"]["panels"][0]["rows"]
        self.assertEqual(["全部", "奇幻"], [item["display_label"] for item in rows[0]["items"]])

    def test_actionless_items_are_filtered_but_full_width_empty_url_is_header(self):
        _, kinds = parse_explore_url(
            [
                {"title": "全部", "style": {"layout_flexBasisPercent": 0.2}},
                {"title": "说明", "type": "button"},
                {"title": "男生", "style": {"layout_flexBasisPercent": 1}},
                {"title": "历史", "url": "/history"},
            ]
        )

        self.assertFalse(kinds[0].is_actionable)
        self.assertFalse(kinds[1].is_actionable)
        self.assertTrue(kinds[2].is_header)

    def test_url_categories_are_always_five_columns_in_detail_rows(self):
        _, kinds = parse_explore_url(
            [
                {"title": str(index), "url": f"/{index}", "style": {"layout_flexBasisPercent": 0.4}}
                for index in range(6)
            ]
        )

        rows = calculate_rows(kinds)

        self.assertEqual([5, 1], [len(row) for row in rows])
        self.assertEqual([12] * 6, [span for row in rows for _, span in row])

    def test_clickable_roots_replay_as_tabs_with_all_entry(self):
        values = []
        for root in ("男生", "女生", "二次元"):
            values.append({"title": root, "url": f"/{root}", "style": {"layout_flexBasisPercent": 1}})
            values.extend(
                [
                    {"title": f"{root}分类1", "url": f"/{root}/1"},
                    {"title": f"{root}分类2", "url": f"/{root}/2"},
                ]
            )
        _, kinds = parse_explore_url(values)

        sections, use_top, mode = build_sections(kinds)
        box = category_box_report(kinds)

        self.assertTrue(use_top)
        self.assertEqual("clickable_roots", mode)
        self.assertEqual(["男生", "女生", "二次元"], box["tabs"])
        self.assertEqual(
            ["全部", "男生分类1", "男生分类2"],
            [item["display_label"] for item in box["panels"][0]["rows"][0]["items"]],
        )
        self.assertEqual(3, len(sections))

    def test_baling_source_replays_three_tabs_and_keeps_ranking_items_ungrouped(self):
        values = [
            {"title": "全本小说", "url": "/full"},
            {"title": "日点击榜", "url": "/day"},
            {"title": "男生分类推荐", "url": "", "style": {"layout_flexBasisPercent": 1}},
            {"title": "修真", "url": "/male/1"},
            {"title": "魔法", "url": "/male/2"},
            {"title": "女生分类推荐", "url": "", "style": {"layout_flexBasisPercent": 1}},
            {"title": "女强", "url": "/female/1"},
            {"title": "婚姻", "url": "/female/2"},
        ]
        _, kinds = parse_explore_url(values)

        box = category_box_report(kinds)

        self.assertEqual(["未分组", "男生分类推荐", "女生分类推荐"], box["tabs"])
        self.assertEqual(
            ["全本小说", "日点击榜"],
            [item["display_label"] for item in box["panels"][0]["rows"][0]["items"]],
        )

    def test_single_leading_category_and_one_header_are_rejected_by_ng_contract(self):
        source = {
            "bookSourceName": "幻梦轻说",
            "exploreUrl": [
                {"title": "🍑 幻梦轻小说首页 🍑", "url": "/home", "style": {"layout_flexBasisPercent": 1}},
                {"title": "🍓 全部 🍓", "url": "", "style": {"layout_flexBasisPercent": 0.29}},
                {"title": "🍅 ↓ 以下为题材分类 ↓ 🍅", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "最新", "url": "/latest"},
                {"title": "校园", "url": "/school"},
            ],
        }

        result = audit_source(source)

        self.assertEqual(["全部"], result["category_box"]["tabs"])
        self.assertEqual("inline", result["category_box"]["mode"])
        self.assertEqual("幻梦轻小说首页", result["category_box"]["panels"][0]["rows"][0]["items"][0]["display_label"])
        codes = {issue["code"] for issue in result["issues"]}
        self.assertIn("legacy_header_group_rejected_by_ng_contract", codes)

    def test_explicit_headers_and_clickable_roots_never_compete(self):
        _, kinds = parse_explore_url(
            [
                {"title": "首页", "url": "/home", "style": {"layout_flexBasisPercent": 1}},
                {"title": "男生", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "玄幻", "url": "/male/1"},
                {"title": "武侠", "url": "/male/2"},
                {"title": "女生", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "言情", "url": "/female/1"},
                {"title": "女强", "url": "/female/2"},
            ]
        )

        sections, use_top, mode = build_sections(kinds)

        self.assertFalse(use_top)
        self.assertEqual("inline", mode)
        self.assertEqual([None], [section.header.title if section.header else None for section in sections])
        self.assertEqual(
            ["首页", "玄幻", "武侠", "言情", "女强"],
            [item.title for item in sections[0].items],
        )

    def test_controls_inside_named_section_do_not_change_category_groups(self):
        _, kinds = parse_explore_url(
            [
                {"title": "男生", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "玄幻", "url": "/male"},
                {"title": "排序", "type": "select"},
                {"title": "女生", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "言情", "url": "/female"},
            ]
        )

        sections, use_top, mode = build_sections(kinds)

        self.assertTrue(use_top)
        self.assertEqual("header_groups", mode)
        self.assertEqual([["玄幻"], ["言情"]], [[item.title for item in section.items] for section in sections])
        box = category_box_report(kinds)
        self.assertEqual(["排序"], [item["display_label"] for item in box["controls"]["rows"][0]["items"]])

    def test_inline_fallback_removes_passive_headers_from_category_box(self):
        source = {
            "bookSourceName": "猫眼看书",
            "exploreUrl": [
                {"title": "男频榜单", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "必读榜", "url": "/must"},
                {"title": "潜力榜", "url": "/potential"},
                {"title": "男频全部", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "玄幻", "url": "/fantasy"},
                {"title": "男频榜单", "url": "", "style": {"layout_flexBasisPercent": 1}},
                {"title": "女生必读", "url": "/female/must"},
            ],
        }

        result = audit_source(source)
        box = result["category_box"]

        self.assertEqual("inline", box["mode"])
        self.assertEqual(["全部"], box["tabs"])
        self.assertTrue(all(row["kind"] == "category_row" for row in box["panels"][0]["rows"]))
        self.assertEqual(
            ["必读榜", "潜力榜", "玄幻", "女生必读"],
            [
                item["display_label"]
                for row in box["panels"][0]["rows"]
                for item in row["items"]
            ],
        )
        self.assertFalse(any(issue["code"] == "section_headers_inside_category_box" for issue in result["issues"]))

    def test_initial_selection_matches_view_model_original_kind_order(self):
        source = {
            "bookSourceName": "内联标题",
            "exploreUrl": [
                {"title": "说明一", "style": {"layout_flexBasisPercent": 1}},
                {"title": "说明二", "style": {"layout_flexBasisPercent": 1}},
                {"title": "真正分类", "url": "/real"},
            ],
        }

        result = audit_source(source)

        self.assertEqual("真正分类", result["category_box"]["initial_selected"])
        self.assertEqual("全部", result["category_box"]["initial_panel"])

    def test_repeated_headers_fall_back_to_categories_only(self):
        source = {
            "bookSourceName": "嵌套分组",
            "exploreUrl": [
                {"title": "男生分类", "style": {"layout_flexBasisPercent": 1}},
                {"title": "主题", "style": {"layout_flexBasisPercent": 1}},
                {"title": "玄幻", "url": "/male/fantasy"},
                {"title": "女生分类", "style": {"layout_flexBasisPercent": 1}},
                {"title": "主题", "style": {"layout_flexBasisPercent": 1}},
                {"title": "言情", "url": "/female/romance"},
            ],
        }

        result = audit_source(source)
        issues = {issue["code"]: issue for issue in result["issues"]}

        self.assertNotIn("first_viewport_contains_only_headers", issues)
        self.assertNotIn("section_headers_inside_category_box", issues)
        self.assertEqual(
            ["玄幻", "言情"],
            [
                item["display_label"]
                for row in result["category_box"]["panels"][0]["rows"]
                for item in row["items"]
            ],
        )

    def test_nested_parent_headers_flatten_and_prefix_duplicate_children(self):
        values = [
            {"title": "男生频道", "style": {"layout_flexBasisPercent": 1}},
            {"title": "榜单", "style": {"layout_flexBasisPercent": 1}},
            {"title": "男生必读", "url": "/male/must"},
            {"title": "男生潜力", "url": "/male/potential"},
            {"title": "分类", "style": {"layout_flexBasisPercent": 1}},
            {"title": "男生玄幻", "url": "/male/fantasy"},
            {"title": "男生武侠", "url": "/male/wuxia"},
            {"title": "女生频道", "style": {"layout_flexBasisPercent": 1}},
            {"title": "榜单", "style": {"layout_flexBasisPercent": 1}},
            {"title": "女生必读", "url": "/female/must"},
            {"title": "女生潜力", "url": "/female/potential"},
            {"title": "分类", "style": {"layout_flexBasisPercent": 1}},
            {"title": "女生言情", "url": "/female/romance"},
            {"title": "女生古言", "url": "/female/ancient"},
        ]
        _, kinds = parse_explore_url(values)

        sections, use_top, mode = build_sections(kinds)

        self.assertTrue(use_top)
        self.assertEqual("header_groups", mode)
        self.assertEqual(
            ["男生频道榜单", "男生频道分类", "女生频道榜单", "女生频道分类"],
            [section.header.section_label for section in sections],
        )

    def test_nested_clickable_roots_flatten_to_nearest_groups(self):
        values = [
            {"title": "男生频道", "style": {"layout_flexBasisPercent": 1}},
            {"title": "榜单", "style": {"layout_flexBasisPercent": 1}},
            {"title": "必读榜", "url": "/rank/must"},
            {"title": "潜力榜", "url": "/rank/potential"},
            {"title": "分类", "style": {"layout_flexBasisPercent": 1}},
            {"title": "玄幻", "url": "/fantasy", "style": {"layout_flexBasisPercent": 1}},
            {"title": "东方玄幻", "url": "/fantasy/east"},
            {"title": "异世大陆", "url": "/fantasy/world"},
            {"title": "武侠", "url": "/wuxia", "style": {"layout_flexBasisPercent": 1}},
            {"title": "传统武侠", "url": "/wuxia/traditional"},
            {"title": "现代武侠", "url": "/wuxia/modern"},
            {"title": "女生频道", "style": {"layout_flexBasisPercent": 1}},
            {"title": "榜单", "style": {"layout_flexBasisPercent": 1}},
            {"title": "女生必读", "url": "/female/must"},
            {"title": "女生潜力", "url": "/female/potential"},
            {"title": "分类", "style": {"layout_flexBasisPercent": 1}},
            {"title": "现代言情", "url": "/romance", "style": {"layout_flexBasisPercent": 1}},
            {"title": "豪门总裁", "url": "/romance/president"},
            {"title": "都市情缘", "url": "/romance/city"},
        ]
        _, kinds = parse_explore_url(values)

        sections, use_top, mode = build_sections(kinds)

        self.assertTrue(use_top)
        self.assertEqual("header_groups", mode)
        self.assertEqual(
            ["男生频道榜单", "玄幻", "武侠", "女生频道榜单", "现代言情"],
            [section.header.section_label for section in sections],
        )
        self.assertEqual(
            ["全部", "东方玄幻", "异世大陆"],
            [item.section_label for item in sections[1].items],
        )

    def test_clickable_parent_roots_flatten_without_losing_categories(self):
        values = [
            {"title": "排行榜单", "style": {"layout_flexBasisPercent": 1}},
            {"title": "人气榜", "style": {"layout_flexBasisPercent": 1}},
            {"title": "男频人气", "url": "/rank/male"},
            {"title": "女频人气", "url": "/rank/female"},
            {"title": "新书榜", "style": {"layout_flexBasisPercent": 1}},
            {"title": "男频新书", "url": "/new/male"},
            {"title": "女频新书", "url": "/new/female"},
            {"title": "书籍分类", "style": {"layout_flexBasisPercent": 1}},
            {"title": "男频", "url": "/male", "style": {"layout_flexBasisPercent": 1}},
            {"title": "都市", "url": "/city", "style": {"layout_flexBasisPercent": 1}},
            {"title": "都市连载", "url": "/city/ongoing"},
            {"title": "都市完结", "url": "/city/complete"},
            {"title": "玄幻", "url": "/fantasy", "style": {"layout_flexBasisPercent": 1}},
            {"title": "玄幻连载", "url": "/fantasy/ongoing"},
            {"title": "玄幻完结", "url": "/fantasy/complete"},
            {"title": "女频", "url": "/female", "style": {"layout_flexBasisPercent": 1}},
            {"title": "现代言情", "url": "/romance", "style": {"layout_flexBasisPercent": 1}},
            {"title": "言情连载", "url": "/romance/ongoing"},
            {"title": "言情完结", "url": "/romance/complete"},
            {"title": "古代言情", "url": "/ancient", "style": {"layout_flexBasisPercent": 1}},
            {"title": "古言连载", "url": "/ancient/ongoing"},
            {"title": "古言完结", "url": "/ancient/complete"},
        ]
        _, kinds = parse_explore_url(values)

        sections, use_top, mode = build_sections(kinds)

        self.assertTrue(use_top)
        self.assertEqual("header_groups", mode)
        self.assertEqual(
            ["人气榜", "新书榜", "男频", "都市", "玄幻", "女频", "现代言情", "古代言情"],
            [section.header.section_label for section in sections],
        )
        self.assertEqual(18, sum(len(section.items) for section in sections))
        self.assertEqual("/male", sections[2].items[0].url)
        self.assertEqual("全部", sections[2].items[0].section_label)

    def test_single_parent_with_two_children_stays_inline(self):
        values = [
            {"title": "频道", "style": {"layout_flexBasisPercent": 1}},
            {"title": "热门排行", "style": {"layout_flexBasisPercent": 1}},
            {"title": "日榜", "url": "/day"},
            {"title": "最新排行", "style": {"layout_flexBasisPercent": 1}},
            {"title": "今日", "url": "/today"},
        ]
        _, kinds = parse_explore_url(values)

        sections, use_top, mode = build_sections(kinds)

        self.assertFalse(use_top)
        self.assertEqual("inline", mode)
        self.assertEqual(["日榜", "今日"], [item.title for item in sections[0].items])

    def test_full_width_openable_left_inline_is_normalized_to_category_grid(self):
        source = {
            "bookSourceName": "全本",
            "exploreUrl": [
                {"title": "男生频道", "url": "/male", "style": {"layout_flexBasisPercent": 1}},
                {"title": "都市娱乐", "url": "/male/city", "style": {"layout_flexBasisPercent": 1}},
                {"title": "日榜", "url": "/male/city/day"},
            ],
        }

        result = audit_source(source)
        items = [
            item
            for row in result["category_box"]["panels"][0]["rows"]
            for item in row["items"]
        ]
        self.assertEqual([12, 12, 12], [item["span"] for item in items])
        self.assertNotIn(
            "nonstandard_category_span_inside_category_box",
            {issue["code"] for issue in result["issues"]},
        )

    def test_url_categories_ignore_wrap_before_and_full_width_format(self):
        _, kinds = parse_explore_url(
            [
                {
                    "title": str(index),
                    "url": f"/{index}",
                    "style": {
                        "layout_flexBasisPercent": 1,
                        "layout_wrapBefore": True,
                    },
                }
                for index in range(6)
            ]
        )

        rows = calculate_rows(kinds)

        self.assertEqual([5, 1], [len(row) for row in rows])
        self.assertEqual([12] * 6, [span for row in rows for _, span in row])

    def test_functional_controls_keep_legacy_span_and_wrap(self):
        _, kinds = parse_explore_url(
            [
                {
                    "title": "按钮一",
                    "type": "button",
                    "action": "run()",
                    "style": {"layout_flexBasisPercent": 0.5},
                },
                {
                    "title": "按钮二",
                    "type": "button",
                    "action": "run()",
                    "style": {
                        "layout_flexBasisPercent": 0.5,
                        "layout_wrapBefore": True,
                    },
                },
            ]
        )

        rows = calculate_rows(kinds)

        self.assertEqual([[30], [30]], [[span for _, span in row] for row in rows])

    def test_label_loss_is_reported_and_controls_are_separated(self):
        source = {
            "bookSourceName": "控件",
            "exploreUrl": [
                {"title": "ＭＦ", "url": "/mf"},
                {"title": "排序", "type": "select"},
            ],
        }

        result = audit_source(source)
        codes = {issue["code"] for issue in result["issues"]}

        self.assertIn("category_label_sanitized_to_fallback", codes)
        self.assertNotIn("non_category_role_inside_category_box", codes)
        self.assertEqual(
            ["select"],
            [
                item["render_role"]
                for row in result["category_box"]["controls"]["rows"]
                for item in row["items"]
            ],
        )

    def test_runtime_view_name_is_not_reported_as_an_exact_rendered_label(self):
        source = {
            "bookSourceName": "动态标题",
            "exploreUrl": [
                {"title": "标题预览", "url": "/books", "viewName": "java.getName()"},
            ],
        }

        result = audit_source(source)
        item = result["category_box"]["panels"][0]["rows"][0]["items"][0]
        codes = {issue["code"] for issue in result["issues"]}

        self.assertEqual("runtime_expression", item["label_resolution"])
        self.assertIn("runtime_label_expressions_inside_category_box", codes)

    def test_invalid_json_like_value_is_reported_after_production_fallback(self):
        result = audit_source(
            {
                "bookSourceName": "单引号数组",
                "exploreUrl": "[{'title':'全部','url':'/all'}]",
            }
        )

        self.assertEqual("empty", result["category_box"]["status"])
        self.assertIn(
            "json_like_value_fell_back_to_legacy",
            {issue["code"] for issue in result["issues"]},
        )

    def test_dynamic_source_is_not_falsely_claimed_as_replayed(self):
        result = audit_source({"bookSourceName": "动态", "exploreUrl": "@js:return []"})

        self.assertEqual("runtime_required", result["category_box"]["status"])
        self.assertEqual(["dynamic_requires_runtime_capture"], [issue["code"] for issue in result["issues"]])

    def test_dynamic_runtime_cache_is_replayed_through_the_same_category_box_logic(self):
        result = audit_source(
            {"bookSourceName": "动态", "exploreUrl": "@js:return []"},
            runtime_value='[{"title":"男生","url":"/male"}]',
        )

        self.assertEqual("dynamic_cached", result["format"])
        self.assertTrue(result["runtime_cache_used"])
        self.assertEqual("replayed", result["category_box"]["status"])
        self.assertEqual("男生", result["category_box"]["initial_selected"])

    def test_collection_keeps_every_source_in_report(self):
        report = audit_collection(
            [
                {"bookSourceName": "动态", "exploreUrl": "@js:return []"},
                {"bookSourceName": "静态", "exploreUrl": "全部::/all\n"},
            ]
        )

        self.assertEqual(2, len(report["sources"]))
        self.assertEqual({"dynamic": 1, "legacy": 1}, report["summary"]["format_counts"])

    def test_collection_counts_roles_before_grouping(self):
        report = audit_collection(
            [
                {
                    "bookSourceName": "角色",
                    "exploreUrl": [
                        {"title": "分类", "url": "/books"},
                        {"title": "功能", "type": "button", "action": "run()"},
                        {"title": "说明"},
                    ],
                }
            ]
        )

        self.assertEqual(
            {"button": 1, "category": 1, "passive": 1},
            report["summary"]["parsed_render_role_counts"],
        )
        self.assertEqual(1, report["summary"]["rendered_category_item_count"])
        self.assertEqual(1, report["summary"]["rendered_function_control_count"])

    def test_collection_separates_issue_sources_from_panel_occurrences(self):
        report = audit_collection(
            [
                {
                    "bookSourceName": "多分组",
                    "exploreUrl": [
                        {"title": "分组一", "style": {"layout_flexBasisPercent": 1}},
                        *({"title": str(index), "url": f"/one/{index}"} for index in range(11)),
                        {"title": "分组二", "style": {"layout_flexBasisPercent": 1}},
                        *({"title": str(index), "url": f"/two/{index}"} for index in range(11)),
                    ],
                }
            ]
        )

        summary = report["summary"]
        self.assertEqual(1, summary["issue_source_counts"]["category_box_scrolls"])
        self.assertEqual(2, summary["issue_occurrence_counts"]["category_box_scrolls"])
        self.assertEqual(2, summary["rendered_panel_count"])
        self.assertEqual(22, summary["rendered_category_item_count"])

    def test_inventory_contains_only_items_that_enter_category_box(self):
        report = audit_collection(
            [
                {
                    "bookSourceName": "分类",
                    "exploreUrl": [
                        {"title": "分组", "style": {"layout_flexBasisPercent": 1}},
                        {"title": "项目", "url": "/item"},
                    ],
                }
            ]
        )

        rows = list(inventory_rows(report))

        self.assertEqual(["category_item"], [row["element_kind"] for row in rows])
        self.assertEqual(["项目"], [row["display_label"] for row in rows])


if __name__ == "__main__":
    unittest.main()
