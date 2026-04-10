package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppInfoTrainingPromptStrategy implements TrainingPromptStrategy {

    @Override
    public boolean supports(TrainingPromptContext context) {
        return context != null && context.hasAppInfoSource();
    }

    @Override
    public String buildPrompt(TrainingPromptContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("请生成 1 个数字专家 skill。\n");
        if (StringUtils.hasText(context.trainingGoal())) {
            builder.append("目标: ").append(context.trainingGoal().trim()).append("\n");
        }
        if (context.hasAppInfoSource()) {
            builder.append("输入路径: ").append(context.appInfoInputPath()).append("\n");
        } else if (context.sources() != null && !context.sources().isEmpty()) {
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
                .append("4. 训练结束前须用终端命令自证根 SKILL.md 存在（例如 test -f \"<技能根>/SKILL.md\" 并展示 head 前几行），不得仅凭文字声称已生成。\n")
                .append("5. 根目录名必须是 ").append(context.skillDirName()).append("；除版本目录内增量外，每次训练都要改写或追加上述根 SKILL.md。\n")
                .append("6. 本次训练内容只能写入版本目录，并在其中生成 static_package 目录。\n")
                .append("7. 静态代码须按顺序处理：① 先使用 CFR 反编译，固定使用工具路径 /opt/cfr/cfr.jar（例如 java -jar /opt/cfr/cfr.jar ...）对输入的 jar 反编译，得到 Java 源码（.java）；")
                .append("② 若反编译产物为压缩包，再解压并释放到上述“静态资源目录”（static_package）下对应路径；")
                .append("③ 最终 static_package 目录树中应以 .java 源文件为主，便于阅读与检索；")
                .append("不要在版本目录或 static_package 中保留未处理的 jar。\n");
        return builder.toString();
    }
}
