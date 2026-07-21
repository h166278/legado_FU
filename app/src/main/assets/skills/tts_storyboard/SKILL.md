---
id: tts_storyboard
name: 听书分镜
description: 为多人朗读拆分旁白、对白和心声，并按所选模式补充演绎上下文
version: 1
---

# 听书分镜

这是 Legado NG 的听书分镜 Skill Package。

界面选择分镜模式后，后台只加载对应的模式资源：

- 基础分镜：`modes/basic.md`
- 场景演绎：先用 `modes/performance-scenes.md` 划分自然场景，再用 `modes/performance.md` 完成归因和场景整理

两种模式使用相同的输入与角色归因字段。基础分镜只负责拆分和说话人归因；场景演绎在此基础上，为当前对白或心声补充简短的场景理解与表演方向。
