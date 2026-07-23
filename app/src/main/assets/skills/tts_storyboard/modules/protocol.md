# 公共协议

输入是一个 JSON 对象：

- `knownCharacters`：本书已有角色卡，包含 `characterId/name/aliases/gender/role`。
- `storyboardCapabilities`：当前引擎实际启用的分镜能力，按顺序包含 `scene_context` 和可选的 `performance_instruction`。
- `contextParagraphs`：本章连续自然段，用于理解人物、事件和对话关系。
- `scenes`：场景演绎时提供的连续场景边界。
- `units`：客户端切出的候选片段，包含 `unitId/sceneId/kind/roleHint/ranges/textPreview/cueBefore/cueAfter`。
- `targetUnitIds`：本次必须处理的候选片段 ID。
- `allowNewCharacters`：是否允许创建角色；当前通常为 `false`。

客户端按原文 range 保留并合成正文。输出只负责归因与可选演绎信息，不返回正文或 range。

只返回一个 JSON 对象：

```json
{
  "units": [
    {
      "unitId": "输入中的目标 ID",
      "roleType": "character",
      "characterName": "角色名或空字符串",
      "characterId": 0,
      "speakerGender": "female",
      "status": "unknown",
      "confidence": 0.88,
      "evidence": "前文动作: 角色名",
      "performanceContext": []
    }
  ],
  "newCharacters": []
}
```

字段约定：

- `roleType`：`narrator`、`character`、`thought`、`other`。
- `speakerGender`：`male`、`female`、`unknown`。
- `status`：`assigned`、`unknown`。
- `confidence`：0 到 1。
- `evidence`：一句极短的归因线索，不复制长段正文。
- `performanceContext`：每项始终包含。导演层未加载时为空数组；加载后 `character/thought` 为 1～3 条有效场景短句。
- 演员层会扩展每项的输出结构；未加载演员层时不要自行增加其字段。
- `narrator/other`：姓名为空、ID 为 0、性别为 `unknown`，演绎字段为空。
- `character/thought + unknown`：ID 为 0，性别为 `male` 或 `female`。
- `newCharacters`：`allowNewCharacters=false` 时返回空数组。

输出中的 `units` 与 `targetUnitIds` 一一对应，每个目标只出现一次。根对象只使用 `units` 和 `newCharacters`。
