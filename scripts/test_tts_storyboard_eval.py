#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


SCRIPT = Path(__file__).with_name("tts_storyboard_eval.py")
SPEC = importlib.util.spec_from_file_location("tts_storyboard_eval", SCRIPT)
assert SPEC and SPEC.loader
storyboard = importlib.util.module_from_spec(SPEC)
sys.modules["tts_storyboard_eval"] = storyboard
SPEC.loader.exec_module(storyboard)


def unit_text(payload: dict, unit_id: str) -> str:
    return storyboard.reconstruct_unit_text(payload, unit_id)


class TtsStoryboardEvalTest(unittest.TestCase):

    def test_prompt_modules_follow_engine_capabilities(self) -> None:
        basic = storyboard.build_system_prompt("basic")
        scene = storyboard.build_system_prompt(
            "performance",
            [storyboard.CAP_SCENE_CONTEXT],
        )
        actor = storyboard.build_system_prompt(
            "performance",
            [storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )

        self.assertIn("# 公共协议", basic)
        self.assertIn("# 基础归因", basic)
        self.assertNotIn("# 导演层", basic)
        self.assertNotIn("# 演员层", basic)
        self.assertIn("# 导演层", scene)
        self.assertNotIn("# 演员层", scene)
        self.assertIn("# 导演层", actor)
        self.assertIn("# 演员层", actor)
        self.assertNotIn("你不是", basic)
        self.assertNotIn("你不是", scene)
        self.assertNotIn("你不是", actor)

    def test_base_prompt_merges_explicit_online_alias_with_known_identity(self) -> None:
        prompt = storyboard.build_system_prompt("basic")

        self.assertIn("网名、QQ 昵称、账号名、群名片", prompt)
        self.assertIn("青青子衿是谁？来源是群添加。哦，是沈言卿", prompt)
        self.assertIn("不得为 X 返回新的 `stable_candidate`", prompt)
        self.assertIn("禁止反向把规范人物并入别名记录", prompt)

    def test_online_alias_mapping_and_message_are_in_same_storyboard_context(self) -> None:
        chapter = storyboard.Chapter(
            9,
            "购彩",
            "\n".join(
                [
                    "QQ上有一个添加信息，打开一看，青青子衿是谁？",
                    "来源是群添加。",
                    "到同学群看了下，哦，是沈言卿。",
                    "陈升以为是赵文博，坐到书桌前却发现是【青青子衿】。",
                    "“你在干嘛？”",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        context = "\n".join(item["text"] for item in payload["contextParagraphs"])
        message = next(unit for unit in payload["units"] if unit["textPreview"] == "“你在干嘛？”")

        self.assertIn("青青子衿是谁", context)
        self.assertIn("哦，是沈言卿", context)
        self.assertIn("发现是【青青子衿】", message["cueBefore"])

    def test_actor_capability_implicitly_loads_scene_context(self) -> None:
        resolved = storyboard.resolve_storyboard_capabilities(
            "performance",
            [storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )

        self.assertEqual(
            resolved,
            [storyboard.CAP_SCENE_CONTEXT, storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )

    def test_performance_scene_pass_loads_shared_skill_resource(self) -> None:
        prompt = storyboard.SCENE_SKILL_FILE.read_text(encoding="utf-8").strip()

        self.assertIn("场景边界与场景标题分析器", prompt)
        self.assertIn("scene_1", prompt)
        self.assertIn('"title"', prompt)
        self.assertIn("陈升与沈言卿在车棚", prompt)
        self.assertNotIn("你不是", prompt)

    def test_unknown_mode_has_no_prompt_fallback(self) -> None:
        with self.assertRaises(ValueError):
            storyboard.build_system_prompt("unknown")

    def test_quote_units_do_not_include_surrounding_narration(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "网吧",
            "\n".join(
                [
                    "一小时后，网吧里。",
                    "“升子，你怎么变这么菜？”",
                    "在陈升被爆头很多次后，赵文博终于吐槽。",
                    "“脑子里全是分数。”陈升耸耸肩，装作一脸无奈。",
                    "绝不可能承认自己32岁的灵魂已经是个手残。",
                    "提到分数，赵文博也放下手中的鼠标，“升子，你给自己估分多少？”",
                    "“卧槽！升子，你是吃了仙丹还是怎么了？”赵文博目瞪口呆，“你学神附体了？”",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        texts = [unit_text(payload, unit_id) for unit_id in payload["targetUnitIds"]]

        self.assertIn("“升子，你怎么变这么菜？”", texts)
        self.assertIn("“脑子里全是分数。”", texts)
        self.assertIn("“升子，你给自己估分多少？”", texts)
        self.assertIn("“卧槽！升子，你是吃了仙丹还是怎么了？”", texts)
        self.assertIn("“你学神附体了？”", texts)
        self.assertNotIn("在陈升被爆头很多次后，赵文博终于吐槽。", texts)
        self.assertFalse(any("赵文博目瞪口呆" in text for text in texts))
        self.assertFalse(any("绝不可能承认" in text for text in texts))

    def test_narrated_quote_references_are_hinted_as_narrator(self) -> None:
        chapter = storyboard.Chapter(
            95,
            "我拄拐，你拿碗",
            "\n".join(
                [
                    "那句“靠你了”总往她心窝子钻，钻得她暖暖热热。",
                    "“很想很想很想很想……”",
                    "电话里，沈言卿娇憨甜脆地说了好长一串“很想”。",
                    "陈升问：“有多想？”",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        hints = {
            unit_text(payload, unit["unitId"]): (unit["kind"], unit["roleHint"])
            for unit in payload["units"]
        }

        self.assertEqual(("quote_reference", "narrator"), hints["“靠你了”"])
        self.assertEqual(("quote_reference", "narrator"), hints["“很想”"])
        self.assertEqual(("quote", "character"), hints["“很想很想很想很想……”"])
        self.assertEqual(("quote", "character"), hints["“有多想？”"])

    def test_colon_units_are_conservative(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "冒号",
            "\n".join(
                [
                    "前面黑板上写着大大的一行字：",
                    "“2010年5月27日，离高考还有10天。”",
                    "她脸上微红，矢口否认道：",
                    "“哪有，我是想考试的事走神了。”",
                    "他在女孩微信中的备注是：优质舔狗三型-升。",
                    "她心里慌乱地想：肯定是他！怎么办？",
                    "有些同学那渴望的眼神，就差喊上一句：快读啊，我们等着呢！",
                    "德国4:1英格兰！",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        texts = [unit_text(payload, unit_id) for unit_id in payload["targetUnitIds"]]

        self.assertIn("“2010年5月27日，离高考还有10天。”", texts)
        self.assertIn("“哪有，我是想考试的事走神了。”", texts)
        self.assertIn("肯定是他！怎么办？", texts)
        self.assertIn("快读啊，我们等着呢！", texts)
        self.assertFalse(any("前面黑板" in text for text in texts))
        self.assertFalse(any("备注是" in text or "优质舔狗" in text for text in texts))
        self.assertFalse(any("4:1" in text for text in texts))

    def test_validation_rejects_missing_unknown_extra_and_text_leak(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“谁啊？”里面传出妈妈陈小杏的声音。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈小杏",
                    "characterId": 1,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "后文声音: 陈小杏",
                    "performanceContext": [],
                    "text": "“谁啊？”",
                },
                {
                    "unitId": "unknown",
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 2,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.8,
                    "evidence": "",
                    "performanceContext": [],
                },
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertEqual(audit["text_leak_count"], 1)
        self.assertEqual(audit["unknown_unit_count"], 1)
        self.assertGreater(audit["invalid_schema_count"], 0)

    def test_validation_accepts_complete_compact_result(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“谁啊？”里面传出妈妈陈小杏的声音。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈小杏",
                    "characterId": 1,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "后文声音: 陈小杏",
                    "performanceContext": [],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["missing_target_count"], 0)
        self.assertEqual(audit["text_leak_count"], 0)
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_unknown_dialogue_with_gender(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“你是谁？”屋里传出一道年轻男声。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "unknown",
                    "confidence": 0.72,
                    "evidence": "后文声音: 年轻男声",
                    "performanceContext": [],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_unknown_dialogue_with_display_name(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "柳烟儿冷哼一声：“这是我们请来的丹师。”")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "柳烟儿",
                    "characterId": 0,
                    "speakerGender": "female",
                    "status": "unknown",
                    "confidence": 0.95,
                    "evidence": "前文动作: 柳烟儿",
                    "performanceContext": [],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_assigned_missing_character_with_gender_fallback(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "身后下属厉声道：“你敢！”")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "前文声音: 身后下属",
                    "performanceContext": [],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_accepts_target_specific_context(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "手机",
            "陈升拿出手机问她的号码。\n“QQ1314……，我……我没有手机。”安秋月窘迫地回答。",
        )
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 7, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 7,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文主语: 安秋月",
                    "performanceContext": [
                        "当前说话人是安秋月，她独自在陌生城市丢了仅有的生活费，身无分文。",
                        "陈升拿出手机，向她询问QQ号和手机号。",
                        "她仍在擦哭红的眼睛，回答时断断续续。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_requires_context_for_neutral_dialogue(self) -> None:
        chapter = storyboard.Chapter(1, "金额", "“丢了多少？”陈升问。")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 1, "name": "陈升", "aliases": [], "gender": "male", "role": "男主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 1,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文: 陈升问",
                    "performanceContext": ["对方刚说丢了生活费；陈升想确认金额后决定如何帮助。"],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_accepts_single_grounded_context(self) -> None:
        chapter = storyboard.Chapter(1, "哽咽", "安秋月还在哽咽。\n“听……听到。”")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 7, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 7,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "前文: 安秋月哽咽",
                    "performanceContext": ["当前说话人是安秋月，她还在哽咽。"],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_accepts_director_interpretation(self) -> None:
        chapter = storyboard.Chapter(1, "拒绝", "陈升瞪了她一眼。\n“不……不是……”安秋月擦了擦眼泪。")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 7, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 7,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文动作: 安秋月擦泪",
                    "performanceContext": ["当前说话人是安秋月，她担心陈升不让自己工作。"],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_rejects_repeated_target_text(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "手机",
            "陈升拿出手机问她的号码。\n“QQ1314……，我……我没有手机。”安秋月窘迫地回答。",
        )
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000, mode="performance")
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 0,
                    "speakerGender": "female",
                    "status": "unknown",
                    "confidence": 0.9,
                    "evidence": "后文主语: 安秋月",
                    "performanceContext": [
                        "当前说话人是陈升，他拿出手机询问联系方式。",
                        "QQ1314……，我……我没有手机。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(any("repeats_target" in value for value in audit["invalid_schema_samples"]))

    def test_performance_mode_accepts_scene_emotion_summary(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "丢钱",
            "陈升催她快点说，但没有太大声。\n“我……生活费丢了……”安秋月浑身一颤，嘴巴一瘪，哭了起来。",
        )
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 7, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 7,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文动作: 安秋月",
                    "performanceContext": [
                        "当前说话人是安秋月，她终于鼓起勇气说出原因。",
                        "她刚被陈升严厉催促，情绪崩溃。",
                        "她说完后继续哭泣。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_accepts_scene_based_motive(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "递酥饺",
            "沈言卿脸红着偷看陈升，又捏起一个酥饺递过去。\n“你吃。”",
        )
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 2, "name": "沈言卿", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "沈言卿",
                    "characterId": 2,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "前文动作: 沈言卿",
                    "performanceContext": [
                        "当前说话人是沈言卿，她感到害羞。",
                        "她忍不住想继续互动。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_accepts_natural_scene_summary(self) -> None:
        chapter = storyboard.Chapter(1, "确认", "“是我，陈升。”安秋月认出了他。")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 1, "name": "陈升", "aliases": [], "gender": "male", "role": "男主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 1,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文: 安秋月认出陈升",
                    "performanceContext": [
                        "当前说话人是陈升，他在路灯下认出独自哭泣的安秋月。",
                        "安秋月抬头询问来人身份，但背着路灯看不清他的脸。",
                        "他说完后安秋月认出了他。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_performance_mode_rejects_copied_cue_after(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "确认",
            "陈升走到她面前。\n“是我，陈升。”安秋月慌忙擦了擦眼泪，适应光线后认出了他。",
        )
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 1, "name": "陈升", "aliases": [], "gender": "male", "role": "男主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 1,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.96,
                    "evidence": "后文: 安秋月认出陈升",
                    "performanceContext": [
                        "当前说话人是陈升，他走到独自哭泣的安秋月面前。",
                        "安秋月慌忙擦了擦眼泪，适应光线后认出了他。",
                    ],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(any("copies_cue_after" in value for value in audit["invalid_schema_samples"]))

    def test_scene_ranges_cover_paragraphs_and_attach_to_units(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "场景",
            "“第一句。”甲说。\n两人继续交谈。\n夜里他去了操场。\n“第二句。”乙说。",
        )
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000, mode="performance")
        scenes = storyboard.validate_scene_result(
            payload,
            {
                "scenes": [
                    {
                        "sceneId": "scene_1",
                        "title": "甲乙交谈",
                        "startParagraphIndex": 0,
                        "endParagraphIndex": 1,
                    },
                    {
                        "sceneId": "scene_2",
                        "title": "夜访操场",
                        "startParagraphIndex": 2,
                        "endParagraphIndex": 3,
                    },
                ]
            },
        )

        storyboard.attach_scene_ranges(payload, scenes)

        self.assertEqual(payload["scenes"], scenes)
        self.assertEqual([unit["sceneId"] for unit in payload["units"]], ["scene_1", "scene_2"])

    def test_scene_ranges_reject_gap(self) -> None:
        chapter = storyboard.Chapter(1, "场景", "第一段。\n第二段。\n第三段。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000, mode="performance")

        with self.assertRaisesRegex(ValueError, "cover paragraphs exactly once"):
            storyboard.validate_scene_result(
                payload,
                {
                    "scenes": [
                        {
                            "sceneId": "scene_1",
                            "title": "第一段",
                            "startParagraphIndex": 0,
                            "endParagraphIndex": 0,
                        },
                        {
                            "sceneId": "scene_2",
                            "title": "第三段",
                            "startParagraphIndex": 2,
                            "endParagraphIndex": 2,
                        },
                    ]
                },
            )

    def test_performance_mode_accepts_condensed_scene_context(self) -> None:
        chapter = storyboard.Chapter(1, "困境", "安秋月浑身一颤。\n“我……生活费丢了……呜……”")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 3, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 3,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.98,
                    "evidence": "前文动作: 安秋月颤抖",
                    "performanceContext": ["当前说话人是安秋月，她的生活费丢了。"],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_actor_capability_accepts_short_performance_instruction(self) -> None:
        chapter = storyboard.Chapter(1, "困境", "安秋月浑身一颤。\n“我……生活费丢了……”")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            known_characters=[
                {"characterId": 3, "name": "安秋月", "aliases": [], "gender": "female", "role": "女主"}
            ],
            mode="performance",
            capabilities=[storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 3,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.98,
                    "evidence": "前文动作: 安秋月颤抖",
                    "performanceContext": ["安秋月丢失生活费后，无助地向陈升说明情况。"],
                    "performanceInstruction": "开口迟疑，后半句逐渐变轻",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["performance_target_count"], 1)
        self.assertEqual(audit["performance_instruction_count"], 1)
        self.assertEqual(audit["performance_instruction_coverage"], 1.0)

    def test_actor_capability_rejects_blank_performance_instruction(self) -> None:
        chapter = storyboard.Chapter(1, "问话", "“联系方式呢？”陈升自然地问。")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            mode="performance",
            capabilities=[storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "unknown",
                    "confidence": 0.98,
                    "evidence": "后文主语: 陈升",
                    "performanceContext": ["对方仍有些局促；陈升想确认信息后继续提供帮助。"],
                    "performanceInstruction": "",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(
            any("performanceInstruction_required" in value for value in audit["invalid_schema_samples"])
        )

    def test_scene_capability_rejects_blank_performance_context(self) -> None:
        chapter = storyboard.Chapter(1, "问话", "“联系方式呢？”陈升自然地问。")
        payload = storyboard.build_storyboard_payload(
            chapter,
            max_chars=1000,
            mode="performance",
            capabilities=[storyboard.CAP_SCENE_CONTEXT],
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "unknown",
                    "confidence": 0.98,
                    "evidence": "后文主语: 陈升",
                    "performanceContext": [],
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(
            any("performanceContext_required" in value for value in audit["invalid_schema_samples"])
        )

    def test_scene_only_capability_rejects_actor_instruction(self) -> None:
        chapter = storyboard.Chapter(1, "困境", "“我……生活费丢了……”安秋月哭着说。")
        payload = storyboard.build_storyboard_payload(chapter, 1000, mode="performance")
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "安秋月",
                    "characterId": 0,
                    "speakerGender": "female",
                    "status": "unknown",
                    "confidence": 0.92,
                    "evidence": "后文主语: 安秋月",
                    "performanceContext": ["安秋月丢失生活费后正在哭泣。"],
                    "performanceInstruction": "含着眼泪，声音逐渐变轻",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(
            any("performanceInstruction_must_be_empty" in value for value in audit["invalid_schema_samples"])
        )

    def test_actor_capability_rejects_instruction_on_narration(self) -> None:
        chapter = storyboard.Chapter(1, "通知", "黑板上写着：“明天放假。”")
        payload = storyboard.build_storyboard_payload(
            chapter,
            1000,
            mode="performance",
            capabilities=[storyboard.CAP_PERFORMANCE_INSTRUCTION],
        )
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "narrator",
                    "characterName": "",
                    "characterId": 0,
                    "speakerGender": "unknown",
                    "status": "unknown",
                    "confidence": 0.99,
                    "evidence": "叙述动作",
                    "performanceContext": [],
                    "performanceInstruction": "声音低沉，放慢讲述",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertTrue(
            any("performanceInstruction_must_be_empty" in value for value in audit["invalid_schema_samples"])
        )


if __name__ == "__main__":
    unittest.main()
