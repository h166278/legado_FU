# 阅融 Legafuse

> 集成各版本优点的开源阅读器 — 基于 [legado_NG](https://github.com/joestar817/legado_NG) 演进。

## 特性

- **阿拉伯章节序号自动转中文章节号**：目录、阅读页、书架统一显示 `第一章`、`第九百三十八章`，网络书源与本地 TXT 均生效（`1.` → `第一章`，`10.` → `第十章`，`938.` → `第九百三十八章`；带「第/卷」或非数字的标题不受影响）
- 继承 legado_NG 全部能力：TXT 目录规则支持 `replacement` JavaScript、WebDAV 同步、规则引擎、自定义书源等
- 独立包名 `io.legado.legafuse`，可与官方阅读、阅读NG 共存安装

## 与上游的区别

| | 官方 Legado | legado_NG | 阅融 |
|---|---|---|---|
| 包名 | io.legado.app | io.legado.app.ng | io.legado.legafuse |
| 应用名 | 阅读 | 阅读NG | 阅融 |
| 中文章节号 | ✗ | 仅 TXT 目录规则 | 全局自动 |

## 构建

```bash
./gradlew assembleAppRelease
```

GitHub Actions 已配置（`test.yml` / `release.yml`），推送即构建。

签名：使用仓库 Secrets `RELEASE_KEY_STORE`（base64 的 jks）、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`RELEASE_STORE_PASSWORD`；未配置时构建为未签名 release。

## 致谢

- [gedoor/legado](https://github.com/gedoor/legado) — 开源阅读
- [joestar817/legado_NG](https://github.com/joestar817/legado_NG) — 本仓库基础

## License

[GPL-3.0](LICENSE)
