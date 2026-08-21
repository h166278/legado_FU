# 情绪语义模块

当前引擎支持 `emotion`。为 `character/thought` 输出通用 `emotion` 与 `expressiveConfidence`；只有能力列表同时包含 `emotion_intensity` 时才输出 0 到 1 的 `emotionIntensity`。

- 情绪使用简短通用语义，如 `neutral/happy/sad/angry/fear/surprise/disgust`。
- 不确定时使用 `neutral` 或返回 `null`，不得为了填字段猜测。
- `expressiveConfidence` 反映当前文本对情绪判断的支持程度，不代表角色身份置信度。
