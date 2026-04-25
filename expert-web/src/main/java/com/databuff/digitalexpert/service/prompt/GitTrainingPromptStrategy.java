package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitTrainingPromptStrategy implements TrainingPromptStrategy {

    /** Git 训练时仓库存放根目录（固定路径，与配置无关）。 */
    private static final String GIT_PROJECTS_ROOT = "/app/upload/projects";

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
        String gitRoot = GIT_PROJECTS_ROOT;
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
        for (TrainingSourceRequest source : sources) {
            if (isGitSource(source)) {
                builder.append("Git 工作目录（先 `cd ")
                        .append(GIT_PROJECTS_ROOT)
                        .append("` 再 `git clone <URL>`，不指定第三参数时 Git 默认创建的目录）: ")
                        .append(resolveGitWorkDir(source.sourceValue()))
                        .append("\n");
            }
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
                .append("7. 【P0】Git 与 `static_package`：① 先 `mkdir -p \"")
                .append(gitRoot)
                .append("\"`（固定 `/app/upload/projects`，勿用 `mktemp`）。② **首次/未克隆**时**必须**使用两步：`cd \"")
                .append(gitRoot)
                .append("\"` 再 `git clone <对应 GIT URL>`（**不要**为 clone 再写第三参数，目录名与上文「Git 工作目录」一致，由 URL 最后一段/仓库名决定，与 Git 默认行为一致）。③ 若**该「Git 工作目录」**已存在且为合法工作区（含 `.git`）：`cd \"$GIT_WORKDIR\"`，`git fetch --all --prune`（视需要），再 `git checkout`/`git switch` 到指定引用。④ **禁止**在训练结束 `rm -rf` `$GIT_WORKDIR`（持久复用）。⑤ 在 `$GIT_WORKDIR` 中切换/拉取**前**工作区须**干净**，必要时 `git status` 并清理。⑥ 将生产源码**同步**到「静态资源目录」；`static_package` **不得**含 `.git`；**禁止**在技能根、版本目录、`static_package` 内执行 `git` 或留 `.git`。\n")
                .append("8. 【P0】若输入指定了分支/标签/版本名：在 `GIT_WORKDIR` 中 `git fetch` 后必须能 `checkout/switch` 到该引用。若经 `git show-ref` 等确认不存在，**立即结束**并说明原因与可用引用；**禁止** fallback 到 main、unstable、其他分支或“最新 tag”，**禁止**再同步 `static_package` 或继续生成完整认知。找不到对应分支/代码时直接退出，**禁止**自行推演或编造。\n")
                .append("9. 优先阅读 README、构建脚本、配置、核心模块与业务文档，再按 root-skills-creator 落档。\n");
        return builder.toString();
    }

    private boolean isGitSource(TrainingSourceRequest source) {
        return source != null
                && TrainingSourceType.GIT_URL.name().equals(source.sourceType())
                && StringUtils.hasText(source.sourceValue());
    }

    private Path resolveGitWorkDir(String gitUrl) {
        return Paths.get(GIT_PROJECTS_ROOT).resolve(gitDefaultCloneDirName(gitUrl)).normalize();
    }

    /**
     * 与在 {@code /app/upload/projects} 下执行 {@code git clone <url>}（不指定目标目录名）时 Git 使用的目录名一致
     * （一般为 URL 路径最后一段、去掉 .git）。
     */
    static String gitDefaultCloneDirName(String gitUrl) {
        if (!StringUtils.hasText(gitUrl)) {
            return "unknown";
        }
        String t = gitUrl.trim();
        String pathPart;
        try {
            if (t.startsWith("git@")) {
                int c = t.indexOf(':');
                if (c < 0) {
                    return "unknown";
                }
                pathPart = t.substring(c + 1);
            } else {
                URI uri = URI.create(t);
                pathPart = uri.getPath() != null ? uri.getPath() : "";
            }
        } catch (Exception e) {
            return "repo_" + Integer.toHexString(t.hashCode());
        }
        while (pathPart.endsWith("/")) {
            pathPart = pathPart.substring(0, pathPart.length() - 1);
        }
        if (pathPart.length() > 4 && pathPart.toLowerCase().endsWith(".git")) {
            pathPart = pathPart.substring(0, pathPart.length() - 4);
        }
        int slash = pathPart.lastIndexOf('/');
        String base = slash >= 0 ? pathPart.substring(slash + 1) : pathPart;
        if (!StringUtils.hasText(base)) {
            return "repo_" + Integer.toHexString(t.hashCode());
        }
        return base;
    }
}
