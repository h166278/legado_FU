# 公共协议

输入是一个 JSON 对象：

- `knownCharacters`：本书已有正式角色卡，包含 `characterId/name/aliases/gender/role`。
- `knownCastRoles`：本书已有临时角色和近期待确认说话人，包含稳定 `castRoleId`、名称、别称、性别、身份状态、出现范围、代表台词和历史身份依据。它们是跨章节身份记忆，不是正式角色卡。
- `storyboardCapabilities`：当前引擎实际启用的分镜能力，可包含 `scene_context/performance_instruction/style_tags/emotion/emotion_intensity`。
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
      "castRoleId": 0,
      "speakerGender": "female",
      "identityType": "guest",
      "nameType": "generic_label",
      "identityEvidence": "contextual",
      "genderEvidence": "explicit",
      "mergeCastRoleIds": [],
      "status": "unknown",
      "confidence": 0.88,
      "evidence": "前文动作: 角色名",
      "performanceContext": [],
      "performanceInstruction": "",
      "styleConcepts": [],
      "emotion": null,
      "emotionIntensity": null,
      "expressiveConfidence": null
    }
  ],
  "newCharacters": []
}
```

字段约定：

- `roleType`：`narrator`、`character`、`thought`、`other`。
- `speakerGender`：`male`、`female`、`unknown`。
- `identityType`：
  - `formal_character`：命中 `knownCharacters`，返回其 `characterId`，`castRoleId=0`。
  - `cast_role`：命中 `knownCastRoles`，返回其 `castRoleId`，`characterId=0`。
  - `stable_candidate`：正文已给出稳定姓名、外号或唯一称谓，值得建立书籍级临时角色。
  - `pending`：看起来会持续指向同一人，但当前只有“小道童”等描述性称呼，需要隐藏保留以等待后文纠正；若是在纠正已有但证据不足的 `knownCastRoles`，保留它的 `castRoleId`。
  - `guest`：`大汉`、`镇魔司下属`、`老捕头`等场景路人或职业／群体泛称，不进入临时角色池。
  - `none`：旁白或其它非人物声音。
- `nameType`：`proper_name`、`alias`、`unique_title`、`generic_label`、`unknown`。是否稳定不能只按有没有姓名判断；唯一且持续指向同一人的称谓可以是稳定角色，一场戏里的职业称呼仍是路人。`alias` 表示网名、昵称、账号名、代号、乳名或外号等同一人物标签；一旦正文把别名映射到已有身份，就必须复用已有 ID，不能因为显示名称不同而另建角色。只有无法映射到任何已有身份时，稳定别名才可暂作为 `stable_candidate`。
- `identityType/nameType` 必须一致：`guest` 只能搭配 `generic_label/unknown`；`proper_name/alias/unique_title` 应使用 `formal_character/cast_role/stable_candidate`，不得一边确认正式姓名一边标为路人。
- `identityEvidence` 与 `genderEvidence`：`explicit`、`contextual`、`inferred`、`unknown`。自我介绍、旁白明确命名、明确代词或“小妹妹”等称谓属于 `explicit`；不得用高置信度代替证据等级。
- `mergeCastRoleIds`：仅在正文给出自我介绍、旁白明确同一人、明确说明网名／昵称归属等直接证据时填写需要并入当前规范身份的旧临时／待确认身份；否则为空。不得仅凭名字相似、性别或语气合并。合并时返回规范人物的名称和 ID，被误建的别名记录 ID 只放入此数组，禁止反向把规范人物并入别名记录。
- `status`：`assigned`、`unknown`。
- `confidence`：0 到 1。
- `evidence`：一句极短的归因线索，不复制长段正文；若 `genderEvidence=explicit`，同时引用支持该性别的原文词语，例如 `称呼“小妹妹”`，不得用姓名、职业或“道童”等中性称呼冒充性别证据。
- `performanceContext`：每项始终包含。导演层未加载时为空数组；加载后 `character/thought` 为 1～3 条有效场景短句。
- `performanceInstruction`：每项始终包含。演员层未加载时为空字符串；加载后 `character/thought` 必须返回有效的供应商无关演员指导。
- `styleConcepts`：始终存在。未加载风格模块时为空；加载后最多 4 个供应商无关短标签，不得返回具体 style ID。
- `emotion/emotionIntensity/expressiveConfidence`：未加载情绪模块时为 `null`。情绪使用通用短语义；强度与置信度范围均为 0 到 1。未声明 `emotion_intensity` 时强度必须为 `null`。
- `narrator/other`：姓名为空、两个 ID 均为 0、性别为 `unknown`，身份字段使用 `none/unknown/unknown/unknown`，合并列表和演绎字段为空。
- `character/thought + unknown`：两个 ID 均为 0，并按语义返回 `stable_candidate/pending/guest`；正文无法可靠确认性别时返回 `unknown`，不得为了选音猜测性别。
- `newCharacters`：`allowNewCharacters=false` 时返回空数组。

输出中的 `units` 与 `targetUnitIds` 一一对应，每个目标只出现一次。根对象只使用 `units` 和 `newCharacters`。
