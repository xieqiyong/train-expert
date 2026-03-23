# 新skills规范
1. 你不能使用sessionId作为目录标识了，也就是输出路径
2. 你每次训练可能需要改写SKILL.md文件
3. 通用的/var/dacheng/agent/skills/root-skills-creator 这个skills 可能需要也改写

## 目录结构

skill-name/
├── SKILL.md              # 必须，核心文件，需要在每次迭代的时候追加，注意改写此文件
└── v1                    # 当app.json里面没有serviceVersion字段的时候，就用v1/v2/v3 第一次训练
    ├── 可选资源/           # 其他目录或引用资源
    ├── static_package/   # 静态源代码，你需要解压所有的jar，然后删除掉
└── v2                    # 当app.json里面没有serviceVersion字段的时候，第二次训练
    ├── 可选资源/           # 其他目录或引用资源
    ├── static_package/    # 静态源代码，你需要解压所有的jar，然后删除掉
└── v3                    # 当app.json里面没有serviceVersion字段的时候，第二次训练
    ├── 可选资源/           # 其他目录或引用资源
    ├── static_package/   # 静态源代码，你需要解压所有的jar，然后删除掉