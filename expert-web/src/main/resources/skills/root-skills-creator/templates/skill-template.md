---
name: {skill-name}
description: 提供 {项目名} 多版本认知查询与故障排查能力。Use when answering architecture questions, exploring version-specific behavior, or troubleshooting this project with versioned docs and static source snapshots.
---

# {项目名} Skill

## 版本选择

- 优先识别用户指定的版本号、tag、镜像版本或快照名称
- 如果用户未明确指定版本，则使用最新版本
- 所有后续认知查询、排障分析和源码探索，都必须在选定版本目录下进行

## 认知查询

1. 选择版本号；如果未指定则使用最新版本
2. 在对应版本目录中查找相关认知文档
3. 优先阅读 `整体架构.md`、相关环节目录下的 `整体逻辑.md` 和逻辑细节文档
4. 理解文档后，继续读取 `static_package/` 中的源码进行进一步分析和探索
5. 给出合理、完整、基于版本上下文的认知回答

## 故障排查

1. 选择版本号；如果未指定则使用最新版本
2. 读取 `故障排查思路.md`，严格按其中流程执行
3. 在对应版本目录中加载相关认知文档进行排查
4. 如果需要进一步分析代码，则读取 `static_package/` 中的源码继续分析
5. 给出最终排查结论和建议

## 版本入口

- `故障排查思路.md`
- `{最新版本目录}/整体架构.md`
- `{最新版本目录}/static_package/`
- `{其他版本目录}/...`

## 使用约束

- 根 `SKILL.md` 只负责版本选择、认知查询入口、排障入口和版本入口导航
- 不要把详细排障 SOP 直接写在根 `SKILL.md` 中
- 若存在多个版本，必须体现“不同版本相互独立”的原则
- 文档内部禁止出现本地绝对路径和网络 URL
