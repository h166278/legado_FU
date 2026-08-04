# [English](English.md) [中文](README.md)

<div align="center">
<img width="125" height="125" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="legado"/>
<br>
阅读NG — Next Generation Legado
<br>
基于阅读Sigma继续演进的独立阅读器分支。
</div>

## 项目说明

阅读NG 只提供阅读器、规则引擎和相关管理工具，不提供任何内容。用户需要自行导入书源、订阅源或本地文件，并自行确认数据来源的合法性。

## 与其他版本共存

阅读NG 使用独立包名前缀 `io.legado.app.ng`，可与阅读原版、阅读Sigma同时安装，数据相互独立。

- 对外分发版：`io.legado.app.ng.release`
- 调试版：`io.legado.app.ng.debug`

## 交流反馈

- [legado_NG·交流反馈](https://t.me/+lYttMZGrQ1RkOTE1)

## 功能特性

|  | 功能 | 说明 |
| :---: | --- | --- |
| 📖 | 在线与本地阅读 | 支持自定义书源、多源搜索、发现、目录与正文规则，也可扫描或导入本地 TXT、EPUB 文件 |
| 🗂️ | 书架与书籍详情 | 支持列表、网格、分组、排序、阅读记录、单本书快捷操作及关联作品展示 |
| 🎨 | **NG 主题体系** | 内置多套主题与背景，支持透明栏、阅读排版和独立的阅读日间／夜间配色 |
| 🪟 | **沉浸阅读界面** | 提供悬浮顶栏、底部抽屉、纵向快捷工具栏、亮度调节、自动翻页与多种翻页动画 |
| 🔎 | 搜索与换源 | 支持并发搜索、进度反馈、来源聚合、全书换源和搜索书源筛选 |
| 🧩 | 书源管理 | 支持导入、编辑、登录、启停、批量选择、分组视图和按地址／类型自动分组 |
| 🔁 | 替换净化 | 支持替换规则分组与作用域、正文净化、重复标题处理及已生效规则查看 |
| ✨ | **AI 净化与扫书** | 支持段落／章节净化、生成替换规则、书籍分析和角色卡整理 |
| 💬 | **AI 阅读助手** | 支持多提供商、流式对话、书籍上下文、会话记忆、Skills 与工具调用 |
| 🎧 | **AI 听书与多人朗读** | 支持系统及在线朗读引擎、默认发音人、多角色分镜、音色路由、预缓存和进度跳转 |
| 🔌 | MCP 服务 | 内置 MCP 服务，提供书架、书籍、章节、缓存及 AI 上下文等能力 |
| 🛠️ | 调试与日志 | 支持书源步骤调试、代码高亮、调试日志、网络日志、凭据脱敏与日志导出 |
| ☁️ | 备份与导入 | 支持 WebDAV 备份恢复，以及书源、订阅源、主题、阅读排版和朗读引擎配置导入 |

## 致谢

- [gedoor/legado](https://github.com/gedoor/legado) - Legado 原项目。
- [Luoyacheng/legado](https://github.com/Luoyacheng/legado) - 阅读Sigma，Reading NG 的直接基础。
- [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) - 主题体系设计参考。
- [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) - AI Provider 管理与模型配置设计参考。
- 感谢本项目使用的所有开源依赖。
