package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitTrainingPromptStrategy implements TrainingPromptStrategy {

    @Override
    public boolean supports(TrainingPromptContext context) {
        if (context == null || context.sources() == null || context.sources().isEmpty()) {
            return false;
        }
        return context.sources().stream().anyMatch(this::isGitSource);
    }

    @Override
    public String buildPrompt(TrainingPromptContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("请生成 1 个数字专家 skill。\n");
        if (StringUtils.hasText(context.trainingGoal())) {
            builder.append("目标: ").append(context.trainingGoal().trim()).append("\n");
        }
        builder.append("输入:\n");
        for (int i = 0; i < context.sources().size(); i++) {
            TrainingSourceRequest source = context.sources().get(i);
            builder.append(i + 1)
                    .append(". [")
                    .append(source.sourceType())
                    .append("] ")
                    .append(source.sourceValue());
            if (StringUtils.hasText(source.sourceVersion())) {
                builder.append(" (版本/分支: ").append(source.sourceVersion().trim()).append(")");
            }
            builder.append("\n");
        }

        builder.append("技能根目录: ").append(context.skillRootDirectory()).append("\n");
        builder.append("技能文件: ").append(context.skillRootDirectory().resolve("SKILL.md")).append("\n");
        builder.append("版本目录: ").append(context.versionDirectory()).append("\n");
        builder.append("静态资源目录: ").append(context.staticPackageDirectory()).append("\n");
        if (context.specSkillPath() != null) {
            builder.append("规范路径: ").append(context.specSkillPath()).append("\n");
        }
        builder.append("要求:\n")
                .append("1. 使用 root-skills-creator 技能，按规范生成 1 个 skill。\n")
                .append("2. 【P0】必须在技能根目录（见上文「技能根目录」）创建或更新物理文件 SKILL.md，路径恰好为 技能根目录/SKILL.md，与版本子目录同级；禁止只写版本目录而不写根 SKILL.md。\n")
                .append("3. 【P0】SKILL.md 顶部 YAML 必须同时包含非空的 name 与 description 字段（与平台 zip 技能包校验一致，缺一则训练产物视为失败）。\n")
                .append("4. 【P0】SKILL.md 里的name必须是 ").append(context.skillDirName()).append("，禁止自定义名称。\n")
                .append("5. 训练结束前须用终端命令自证根 SKILL.md 存在（例如 test -f \"<技能根>/SKILL.md\" 并展示 head 前几行），不得仅凭文字声称已生成。\n")
                .append("6. 根目录名必须是 ").append(context.skillDirName()).append("；除版本目录内增量外，每次训练都要改写或追加上述根 SKILL.md。\n")
                .append("7. 本次训练内容只能写入版本目录，并在其中生成 static_package 目录。\n")
                .append("8. Git 仓库首次拉取后，后续优先复用本地仓库，先执行 git fetch --all --prune --tags，再切换到指定版本或分支，不要每次重新全量 clone。\n")
                .append("9. 切换分支前必须确保工作区干净；若存在未提交修改、脏文件或未跟踪文件，先清理再切换，避免分支冲突影响训练结果。\n")
                .append("10. 优先阅读 README、构建脚本、配置文件、核心模块源码和业务文档，提炼业务能力、关键流程、边界条件与可复用操作。\n")
                .append("11. 不要把整个仓库复制到版本目录或 static_package，只保留训练产物和必要的可读静态材料。\n");
        return builder.toString();
    }

    private boolean isGitSource(TrainingSourceRequest source) {
        return source != null
                && TrainingSourceType.GIT_URL.name().equals(source.sourceType())
                && StringUtils.hasText(source.sourceValue());
    }
}
