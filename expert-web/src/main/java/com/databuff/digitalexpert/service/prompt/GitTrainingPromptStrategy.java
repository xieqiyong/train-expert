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
                builder.append("Git 工作目录（同仓库 URL 持久复用; mkdir -p 根目录后使用）: ")
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
                .append("\"`（固定为 `/app/upload/projects`，勿用 `mktemp` 做仓库根）。② 上文明示的**每个**「Git 工作目录」= `GIT_WORKDIR`：若该目录**已存在**且为合法 git 工作区（含 `.git`），则 `cd \"$GIT_WORKDIR\"`，`git fetch --all --prune`（视需要），再按输入的引用执行 `git checkout` 或 `git switch`；若目录**尚不存在**或不是克隆结果，则 `git clone <对应 GIT URL> \"$GIT_WORKDIR\"`，**然后**再切换到指定引用。**禁止**在训练结束 `rm -rf` 已给出的 `GIT_WORKDIR`（持久缓存、供重复训练复用）。③ 在**任何**`GIT_WORKDIR` 中，切换/拉取**前**须**工作区干净**（无脏文件、未提交变更），必要时先 `git status` 并清理。④ 将生产源码**同步**到「静态资源目录」；`static_package` **不得**含 `.git`；**禁止**在技能根、版本目录、`static_package` 内执行 `git` 或留下 `.git`。\n")
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
        return Paths.get(GIT_PROJECTS_ROOT).resolve(safeRepoDirName(gitUrl)).normalize();
    }

    /**
     * 由仓库地址生成稳定、可作为目录名的片段（同 URL 得到同一路径）。
     */
    static String safeRepoDirName(String gitUrl) {
        if (!StringUtils.hasText(gitUrl)) {
            return "unknown";
        }
        String t = gitUrl.trim();
        try {
            if (t.startsWith("git@")) {
                int c = t.indexOf(':');
                if (c > 4) {
                    String host = t.substring(4, c);
                    String pathPart = t.substring(c + 1).replaceAll("(?i)\\.git$", "");
                    return sanitizeFileName(host + "_" + pathPart.replace('/', '_').replace(':', '_'));
                }
            }
            URI uri = URI.create(t);
            String host = uri.getHost() != null ? uri.getHost() : "host";
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (path.toLowerCase().endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            return sanitizeFileName(host + path.replace('/', '_').replace(':', '_'));
        } catch (Exception e) {
            return "repo_" + Integer.toHexString(t.hashCode());
        }
    }

    private static String sanitizeFileName(String s) {
        String x = s.replaceAll("[^a-zA-Z0-9._-]+", "_");
        x = x.replaceAll("^_+", "");
        if (!StringUtils.hasText(x) || x.length() > 200) {
            return "repo_" + Integer.toHexString(s.hashCode());
        }
        return x;
    }
}
