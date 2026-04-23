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
                .append("7. 本次训练内容只能写入版本目录，并在其中生成 static_package 目录。\n")
                .append("7a. 【P0】从 Git 获取的**全部**源码与仓库内容（如 src、deps、tests、utils、README、版本子目录 7.x/ 等）**必须**只落在「静态资源目录」下，即与上文「静态资源目录」一致（通常形如 …/{版本名}/static_package/ 之内）；可在此目录下直接放仓库根内容，或再套一层子目录。"
                        + " **禁止**在「技能根目录」下（与 SKILL.md 同级）clone、checkout 或平铺 `src/`、`7.2.7/` 等；技能根下除 SKILL.md 与**本次训练的版本子目录**外，不得出现仓库树或 .git 目录；01～05 与 Markdown 等训练产出仍只写在**版本子目录**的规范路径中。\n")
                .append("8. Git 仓库首次拉取后，后续优先复用本地仓库，先执行 git fetch --all --prune --tags，再切换到指定版本或分支，不要每次重新全量 clone。\n")
                .append("9. 切换分支前必须确保工作区干净；若存在未提交修改、脏文件或未跟踪文件，先清理再切换，避免分支冲突影响训练结果。\n")
                .append("9a. 【P0】若训练输入中已指定 Git 分支/标签/版本名：在 git fetch 后必须能成功 checkout 到该引用；若经 git show-ref、git switch/git checkout 等确认该引用在远端/本地均不存在，必须立即结束本次训练：明确说明失败原因与可获取的引用列表，"
                        + "禁止改用 unstable、main、其它分支或“最新稳定 tag”等继续拉代码并生成 skill，禁止在目标版本不存在时仍创建 static_package 与版本目录产物。\n")
                .append("10. 优先阅读 README、构建脚本、配置文件、核心模块源码和业务文档，提炼业务能力、关键流程、边界条件与可复用操作。\n")
                .append("11. 整仓**仅**应置于「版本目录/static_package/」中（见 7a），**不要**在技能根下再放一份、也不要在 01-04 等同级再复制整仓；static_package 内保留训练阅读所需的源码与只读材料即可，可按需裁掉无关体积（如仅 tests 的附加策略由任务显式要求时再执行），未说明则默认保留以支撑认知生成。\n")
                .append("12. 如果找不到分支或者代码，直接退出，禁止推演查找或自行判断。\n");
        return builder.toString();
    }

    private boolean isGitSource(TrainingSourceRequest source) {
        return source != null
                && TrainingSourceType.GIT_URL.name().equals(source.sourceType())
                && StringUtils.hasText(source.sourceValue());
    }
}
