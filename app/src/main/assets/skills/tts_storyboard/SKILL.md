---
id: tts_storyboard
name: 听书分镜
description: 为多人朗读拆分旁白、对白和心声，并按 TTS 引擎能力补充场景与表演信息
version: 2
---

# 听书分镜

这是 Legado NG 的听书分镜 Skill Package。

APP 根据当前多人 TTS 引擎声明的能力自动逐层加载：

1. `modules/base-routing.md`：基础归因，始终加载。
2. `modules/scene-context.md`：引擎声明 `scene_context` 时加载。
3. `modules/performance-instruction.md`：引擎声明 `performance_instruction` 时加载，并同时加载场景上下文层。

公共输入与输出边界由 `modules/protocol.md` 定义。自然场景边界仍由 `modes/performance-scenes.md` 独立生成。

能力采用向下兼容：演员层可以同时获得场景和表演指令；只支持导演层的引擎只获得场景；两者都不支持时仍可使用基础归因。用户不选择内部能力等级，TTS 脚本负责把通用信息转换成自己的接口参数。

APP 按全局多人 TTS 引擎的能力声明动态装配上述模块。切换引擎或能力集合后使用新的分镜缓存，避免沿用旧演绎结果。
