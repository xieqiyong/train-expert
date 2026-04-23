package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import java.util.List;
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
        List<TrainingSourceRequest> sources = context.trainingSources();
        for (int i = 0; i < sources.size(); i++) {
            TrainingSourceRequest source = sources.get(i);
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
        context.appendAttachmentResources(builder);

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
                .append("7. 【P0】Git 与 `static_package`：① 所有 `git clone/fetch/pull/checkout/switch` **仅**能在**技能产出树之外**的临时目录（如 `mktemp -d`）或**非产出路径**的缓存库中执行，**禁止**在技能根、版本目录、`static_package` 下执行上述命令或留下 `.git`。"
                        + " ② 在**临时区**取码、`fetch` 并**切到输入指定引用**（存在性与失败处理见下条）后，将工作区**同步**到上文「静态资源目录」，建议**不**拷入 `.git`；仓库源码**只**应出现在此目录（可再套子目录），技能根下除 `SKILL.md` 与本次版本子目录外**不得**平铺 `src/` 等，**不要**在 01～04 旁再塞第二份整仓。③ 除根 `SKILL.md` 外，认知类 Markdown 仅写在版本目录约定路径。④ 未要求瘦身时默认可保留 tests 以支撑分析。\n")
                .append("8. 在临时 Git 工作区内，切换/拉取**前**须**工作区干净**（无脏文件、未提交变更），必要时先清理。\n")
                .append("9. 【P0】若输入指定了分支/标签/版本名：临时区在 `git fetch` 后必须能 `checkout/switch` 到该引用。若经 `git show-ref` 等确认不存在，**立即结束**并说明原因与可用引用；**禁止** fallback 到 main、unstable、其他分支或“最新 tag”，**禁止**再同步 `static_package` 或继续生成完整认知。找不到对应分支/代码时直接退出，**禁止**自行推演或编造。\n")
                .append("10. 优先阅读 README、构建脚本、配置、核心模块与业务文档，再按 root-skills-creator 落档。\n");
        return builder.toString();
    }

    private boolean isGitSource(TrainingSourceRequest source) {
        return source != null
                && TrainingSourceType.GIT_URL.name().equals(source.sourceType())
                && StringUtils.hasText(source.sourceValue());
    }
}
