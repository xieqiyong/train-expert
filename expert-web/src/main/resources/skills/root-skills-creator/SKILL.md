---
name: root-skills-creator
description: 基于项目快照、认知文档和源码包生成多版本 skill。Use when creating or updating a versioned skill package with architecture docs, troubleshooting guidance, and static source snapshots.
---

# Skill 包生成器

## 提供的信息

使用本技能时，只需要用户提供以下信息：

1. **项目名称**：要分析的项目名称
2. **项目代码路径**：要分析的项目源代码目录
3. **指标文件路径**：包含监控指标的Markdown文件（格式：## 指标名称\n描述）
4. **输出目录路径**：生成skill包的目标目录

## 生成目标

基于源码、指标说明和版本快照，生成一个多版本 skill。输出目录结构必须如下：

```text
<Skill 根目录>/
├── SKILL.md
├── 故障排查思路.md
├── <版本目录 A>/
│   ├── 整体架构.md
│   ├── 环节1/
│   │   ├── 整体逻辑.md
│   │   ├── 逻辑1.md
│   │   └── 逻辑2.md
│   ├── 环节2/
│   │   └── ...
│   └── static_package/
└── <版本目录 B>/
    └── ...
```

## 生成要求

### 1. 版本目录

- 每个版本目录代表一个独立快照，目录名可以是 tag、镜像版本、发布日期或其他可稳定标识版本的名称
- 如果能识别多个版本，则分别生成多个版本目录
- 如果没有明确版本信息，则至少生成一个默认版本目录，并在根 `SKILL.md` 中将其视为最新版本
- 不同版本目录下的 `整体架构.md`、环节目录和 `static_package/` 相互独立，禁止混写

### 2. `故障排查思路.md`

- 该文件存放原 `databuff-skill-creator` 中 `templates/skill-md-template.md` 的内容
- 生成时保留模板原意，可根据当前项目名称做最小替换，但不要改写成版本选择说明
- 该文件专门用于排障流程，不承担认知问答入口职责

### 3. 版本认知文档

- 每个版本目录都需要生成 `整体架构.md`
- 每个核心环节都需要有独立目录和 `整体逻辑.md`
- 每个环节下继续拆分关键逻辑说明文档，命名可以是子环节名、逻辑名或流程名
- 文档风格延续 `databuff-skill-creator`：聚焦架构、运行逻辑、指标、配置参数、故障模式，避免源码实现细节

### 4. `static_package/`

- 每个版本目录下都必须保留 `static_package/`
- 该目录用于存放对应版本的源码快照、反编译结果、静态资源或原始采集材料
- 认知问答和排障在文档不足时，都可以继续读取 `static_package/` 进行进一步分析

## 根 `SKILL.md` 生成规则

根 `SKILL.md` 必须包含 YAML frontmatter，并明确说明以下行为：

1. 先选择版本号；如果用户没有明确指定，则使用最新版本
2. 如果用户要查找认知：
   - 先选择版本号；如果未指定则使用最新版本
   - 在对应版本中寻找相关文档内容
   - 理解文档后，继续读取该版本 `static_package/` 中的源码进行进一步分析和探索
   - 最终给出一个合理的认知回答
3. 如果用户要排障：
   - 按照 `故障排查思路.md` 的流程进行排查，并访问对应版本的详细认知
   - 如果需要进一步分析代码，则读取该版本 `static_package/` 中的源码继续分析

## 模板文件

生成文件时，不要把模板直接写死在根 `SKILL.md` 中，必须使用 `templates/` 目录下的独立模板文件：

- `templates/skill-template.md`：用于生成根 `SKILL.md`
- `templates/troubleshooting-template.md`：用于生成 `故障排查思路.md`
- `templates/architecture-template.md`：用于生成各版本目录下的 `整体架构.md`
- `templates/component-logic-template.md`：用于生成各环节目录下的 `整体逻辑.md`
- `templates/component-detail-template.md`：用于生成各环节下的逻辑细节文档

生成时应优先基于模板填充内容，而不是在根 `SKILL.md` 中重新拼接一套临时结构说明。

## 生成约束

- 根 `SKILL.md` 只负责版本选择、认知查询入口、排障入口和版本入口导航
- 排障 SOP 不要直接写进根 `SKILL.md`，而是写入 `故障排查思路.md`
- 根 `SKILL.md` 必须明确“认知”和“排障”两条路径都可以继续读取对应版本的 `static_package/`
- 若存在多个版本，必须体现“不同版本相互独立”的原则
- 文档内部禁止出现本地绝对路径和网络 URL
- 文档内部避免具体函数名、代码行号、底层实现细节；但可以保留对诊断有价值的指标名、配置参数名和高层流程名
