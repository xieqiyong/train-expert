package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.config.ExpertProperties;
import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertReleaseTaskEntity;
import com.databuff.digitalexpert.dao.entity.ExpertSkillBindingEntity;
import com.databuff.digitalexpert.dao.entity.ExpertTrainingTaskEntity;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.enums.PackageStatus;
import com.databuff.digitalexpert.dao.enums.ReleaseTaskStatus;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertReleaseTaskMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertSkillBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertTrainingTaskMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import com.databuff.digitalexpert.service.proxy.TrainingProxyClient;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.SkillArchiveMetadata;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import com.databuff.digitalexpert.util.TaskIdGenerator;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ExpertTrainingServiceImpl implements ExpertTrainingService {

    private final Set<String> pollingTaskLocks = ConcurrentHashMap.newKeySet();

    @Autowired
    private ExpertTrainingTaskMapper expertTrainingTaskMapper;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private SkillPackageMapper skillPackageMapper;
    @Autowired
    private ExpertReleaseTaskMapper expertReleaseTaskMapper;
    @Autowired
    private ExpertReleaseService expertReleaseService;
    @Autowired
    private ExpertConfigService expertConfigService;
    @Autowired
    private SharedStorageService sharedStorageService;
    @Autowired
    private ZipArchiveService zipArchiveService;
    @Autowired
    private ExpertProperties properties;
    @Autowired
    private TrainingProxyClient trainingProxyClient;
    @Autowired
    @Qualifier("trainingSubmitExecutor")
    private Executor trainingSubmitExecutor;
    @Autowired
    @Qualifier("trainingPollExecutor")
    private Executor trainingPollExecutor;

    @Override
    @Transactional
    public ExpertTrainingTaskResponse submitTrainingTask(Long expertId, CreateExpertTrainingTaskRequest request) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
            throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED, "专家已被禁用: " + expertId);
        }
        if (hasActiveTrainingTask(expertId)) {
            throw BusinessException.conflict(ErrorCode.ACTIVE_TRAINING_TASK_EXISTS,
                    "专家存在进行中的训练任务: " + expertId);
        }

        List<TrainingSourceRequest> normalizedSources = normalizeSources(request.sources());
        String taskId = TaskIdGenerator.nextTrainingTaskId();
        Path outputDirectory = resolveTrainingOutputDirectory(expertId, taskId);

        LocalDateTime now = LocalDateTime.now();
        ExpertTrainingTaskEntity task = new ExpertTrainingTaskEntity();
        task.setTaskId(taskId);
        task.setExpertId(expertId);
        task.setStatus(TrainingTaskStatus.PENDING.name());
        task.setPreviousExpertStatus(expert.getStatus());
        task.setSourceManifestJson(JSON.toJSONString(normalizedSources));
        task.setOutputDir(outputDirectory.toString());
        task.setManifestPath(null);
        task.setPollCount(0);
        task.setArtifactVerified(0);
        task.setRequestedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.insert(task);
        trainingSubmitExecutor.execute(() -> submitTaskToProxy(taskId, request.trainingGoal()));
        return toResponse(task);
    }

    @Override
    public ExpertTrainingTaskResponse getTask(Long expertId, String taskId) {
        ExpertTrainingTaskEntity task = expertTrainingTaskMapper.selectOne(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getTaskId, taskId)
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId));
        if (task == null) {
            throw BusinessException.notFound(ErrorCode.TRAINING_TASK_NOT_FOUND,
                    "训练任务不存在: " + taskId);
        }
        return toResponse(task);
    }

    @Override
    @Scheduled(fixedDelayString = "${digital-expert.training.poll-interval-ms:5000}")
    public void pollTrainingTasks() {
        List<ExpertTrainingTaskEntity> tasks = expertTrainingTaskMapper.selectList(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .in(ExpertTrainingTaskEntity::getStatus,
                        TrainingTaskStatus.pollingStatuses())
                .orderByAsc(ExpertTrainingTaskEntity::getUpdatedAt));
        for (ExpertTrainingTaskEntity task : tasks) {
            if (!pollingTaskLocks.add(task.getTaskId())) {
                continue;
            }
            trainingPollExecutor.execute(() -> {
                try {
                    pollSingleTask(task.getTaskId());
                } catch (Exception ex) {
                    log.error("轮询训练任务失败, taskId={}", task.getTaskId(), ex);
                } finally {
                    pollingTaskLocks.remove(task.getTaskId());
                }
            });
        }
    }

    private void submitTaskToProxy(String taskId, String trainingGoal) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        if (!TrainingTaskStatus.PENDING.name().equals(task.getStatus())) {
            return;
        }
        try {
            markTaskRunning(task);
            markExpertTraining(task.getExpertId(), taskId);

            Path outputDir = normalizeAndCheckOutputPath(task.getOutputDir());
            recreateTrainingOutputDirectory(outputDir);

            List<TrainingSourceRequest> sources = parseSources(task.getSourceManifestJson());
            String skillDirName = resolveSkillDirectoryName(sources, taskId);
            Path skillDirectory = resolveSkillDirectory(outputDir, skillDirName);
            String prompt = buildPrompt(trainingGoal, sources, skillDirName, skillDirectory);
            TrainingProxyClient.ProxySubmitResult submitResult = trainingProxyClient.submitTraining(
                    taskId,
                    prompt,
                    resolveProxyInputPaths(sources),
                    skillDirectory.toString()
            );

            task = requireTask(taskId);
            task.setSessionId(submitResult.sessionId());
            task.setSubmitRequestId(submitResult.requestId());
            task.setUpdatedAt(LocalDateTime.now());
            expertTrainingTaskMapper.updateById(task);

            DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
            expert.setLastTrainingSessionId(submitResult.sessionId());
            expert.setUpdatedAt(LocalDateTime.now());
            digitalExpertMapper.updateById(expert);
        } catch (Exception ex) {
            log.error("训练任务提交到代理服务失败, taskId={}", taskId, ex);
            markTaskFailed(taskId, ex.getMessage());
        }
    }

    private void pollSingleTask(String taskId) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        if (TrainingTaskStatus.RUNNING.name().equals(task.getStatus())) {
            handleRunningTask(task);
            return;
        }
        if (TrainingTaskStatus.VERIFYING_ARTIFACTS.name().equals(task.getStatus())) {
            handleVerifyingArtifacts(task);
            return;
        }
        if (TrainingTaskStatus.RELEASING.name().equals(task.getStatus())) {
            handleReleasingTask(task);
        }
    }

    private void handleRunningTask(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        long elapsedMs = calculateElapsedMs(resolveRunningStageStartTime(task));
        long timeoutMs = properties.getTraining().getSessionTimeoutMs();
        if (elapsedMs >= timeoutMs) {
            markTaskTimeout(task.getTaskId(), "训练会话轮询超时");
            return;
        }
        if (!StringUtils.hasText(task.getSessionId())) {
            if (shouldLogProgress(pollCount, 12)) {
                log.info("训练任务尚未拿到会话ID，继续等待, taskId={}, elapsedMs={}, timeoutMs={}",
                        task.getTaskId(), elapsedMs, timeoutMs);
            }
            return;
        }
        if (!trainingProxyClient.isSessionFinished(task.getSessionId())) {
            if (shouldLogProgress(pollCount, 12)) {
                log.info("训练会话尚未结束，继续等待, taskId={}, sessionId={}, elapsedMs={}, timeoutMs={}",
                        task.getTaskId(), task.getSessionId(), elapsedMs, timeoutMs);
            }
            return;
        }

        task = requireTask(task.getTaskId());
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.VERIFYING_ARTIFACTS.name());
        task.setPollCount(0);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        log.info("训练会话已结束，进入产物缓冲等待, taskId={}, sessionId={}, gracePeriodMs={}",
                task.getTaskId(), task.getSessionId(), properties.getTraining().getArtifactGracePeriodMs());
    }

    private void handleVerifyingArtifacts(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        long elapsedMs = calculateElapsedMs(task.getUpdatedAt());
        long gracePeriodMs = properties.getTraining().getArtifactGracePeriodMs();
        if (elapsedMs < gracePeriodMs) {
            if (shouldLogProgress(pollCount, 6)) {
                long remainingMs = Math.max(gracePeriodMs - elapsedMs, 0L);
                log.info("训练会话已结束，等待产物落盘, taskId={}, elapsedMs={}, remainingMs={}",
                        task.getTaskId(), elapsedMs, remainingMs);
            }
            return;
        }

        try {
            log.info("产物缓冲结束，开始校验训练产物, taskId={}, waitMs={}", task.getTaskId(), elapsedMs);
            List<Path> skillDirectories = collectSkillDirectories(task);
            task = requireTask(task.getTaskId());
            task.setStatus(TrainingTaskStatus.IMPORTING_SKILLS.name());
            task.setUpdatedAt(LocalDateTime.now());
            expertTrainingTaskMapper.updateById(task);

            List<Long> skillIds = importSkillPackages(skillDirectories);
            replaceExpertSkills(task.getExpertId(), skillIds);

            task = requireTask(task.getTaskId());
            task.setStatus(TrainingTaskStatus.RELEASING.name());
            task.setArtifactVerified(1);
            task.setPollCount(0);
            task.setUpdatedAt(LocalDateTime.now());

            ExpertReleaseTaskResponse releaseTask = expertReleaseService.submitReleaseTask(task.getExpertId());
            task.setReleaseTaskId(releaseTask.taskId());
            expertTrainingTaskMapper.updateById(task);
            log.info("训练产物已入库，开始发布专家, taskId={}, releaseTaskId={}", task.getTaskId(), releaseTask.taskId());
        } catch (BusinessException ex) {
            if (isArtifactsNotReady(ex)) {
                markTaskFailed(task.getTaskId(), "训练产物在缓冲期结束后仍未就绪: " + ex.getMessage());
            } else {
                log.error("训练产物校验或入库失败, taskId={}", task.getTaskId(), ex);
                markTaskFailed(task.getTaskId(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("训练产物校验或入库失败, taskId={}", task.getTaskId(), ex);
            markTaskFailed(task.getTaskId(), ex.getMessage());
        }
    }

    private void handleReleasingTask(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        long elapsedMs = calculateElapsedMs(task.getUpdatedAt());
        long timeoutMs = properties.getTraining().getReleaseTimeoutMs();
        if (elapsedMs >= timeoutMs) {
            markTaskTimeout(task.getTaskId(), "发布任务轮询超时");
            return;
        }
        if (!StringUtils.hasText(task.getReleaseTaskId())) {
            markTaskFailed(task.getTaskId(), "发布任务ID为空");
            return;
        }

        ExpertReleaseTaskEntity releaseTask = expertReleaseTaskMapper.selectOne(new LambdaQueryWrapper<ExpertReleaseTaskEntity>()
                .eq(ExpertReleaseTaskEntity::getTaskId, task.getReleaseTaskId())
                .eq(ExpertReleaseTaskEntity::getExpertId, task.getExpertId()));
        if (releaseTask == null) {
            markTaskFailed(task.getTaskId(), "发布任务不存在: " + task.getReleaseTaskId());
            return;
        }

        if (ReleaseTaskStatus.SUCCEEDED.name().equals(releaseTask.getStatus())) {
            log.info("专家发布完成，训练任务成功结束, taskId={}, releaseTaskId={}", task.getTaskId(), task.getReleaseTaskId());
            markTaskSucceeded(task.getTaskId());
            return;
        }
        if (ReleaseTaskStatus.FAILED.name().equals(releaseTask.getStatus())) {
            markTaskFailed(task.getTaskId(), releaseTask.getFailureReason());
            return;
        }
        if (shouldLogProgress(pollCount, 12)) {
            log.info("专家发布尚未完成，继续等待, taskId={}, releaseTaskId={}, elapsedMs={}, timeoutMs={}",
                    task.getTaskId(), task.getReleaseTaskId(), elapsedMs, timeoutMs);
        }
    }

    private List<Long> importSkillPackages(List<Path> skillDirectories) {
        List<Long> skillIds = new ArrayList<>();
        for (Path skillDirectory : skillDirectories) {
            byte[] zipBytes = zipArchiveService.zipDirectory(skillDirectory);
            String packageName = resolvePackageName(skillDirectory);
            SkillArchiveMetadata metadata = zipArchiveService.inspectSkillArchive(zipBytes, packageName);

            LocalDateTime now = LocalDateTime.now();
            SkillPackageEntity entity = new SkillPackageEntity();
            entity.setName(metadata.name());
            entity.setDescription(metadata.description());
            entity.setPackageName(packageName);
            entity.setChecksum(zipArchiveService.sha256Hex(zipBytes));
            entity.setStatus(PackageStatus.ACTIVE.name());
            entity.setPackagePath("");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            skillPackageMapper.insert(entity);

            Path directory = sharedStorageService.resolveSkillDirectory(entity.getId());
            sharedStorageService.recreateDirectory(directory);
            Path packagePath = directory.resolve(packageName);
            sharedStorageService.writeBytes(packagePath, zipBytes);
            entity.setPackagePath(sharedStorageService.toStoragePath(packagePath));
            entity.setUpdatedAt(LocalDateTime.now());
            skillPackageMapper.updateById(entity);
            skillIds.add(entity.getId());
        }
        if (skillIds.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "未找到有效的技能产物");
        }
        return skillIds;
    }

    @Transactional
    protected void replaceExpertSkills(Long expertId, List<Long> skillIds) {
        expertSkillBindingMapper.delete(new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                .eq(ExpertSkillBindingEntity::getExpertId, expertId));
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < skillIds.size(); i++) {
            ExpertSkillBindingEntity binding = new ExpertSkillBindingEntity();
            binding.setExpertId(expertId);
            binding.setSkillId(skillIds.get(i));
            binding.setSortNo(i + 1);
            binding.setCreatedAt(now);
            expertSkillBindingMapper.insert(binding);
        }
    }

    private List<Path> collectSkillDirectories(ExpertTrainingTaskEntity task) {
        Path outputDir = normalizeAndCheckOutputPath(task.getOutputDir());
        if (!Files.exists(outputDir) || !Files.isDirectory(outputDir)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "训练输出目录不存在: " + outputDir);
        }

        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        if (StringUtils.hasText(task.getManifestPath())) {
            directories.addAll(loadSkillDirectoriesFromManifestJson(task.getManifestPath(), outputDir));
        }

        if (directories.isEmpty()) {
            Path skillDirectory = resolveExpectedSkillDirectory(task, outputDir);
            validateSkillDirectory(skillDirectory);
            directories.add(skillDirectory);
            persistSkillManifest(task, List.copyOf(directories));
        }

        if (directories.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "未在输出目录下找到技能目录: " + outputDir);
        }

        for (Path directory : directories) {
            if (!Files.isRegularFile(directory.resolve("SKILL.md"))) {
                throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                        "生成目录缺少 SKILL.md: " + directory);
            }
        }
        return List.copyOf(directories);
    }

    private List<Path> loadSkillDirectoriesFromManifestJson(String manifestJson, Path outputDir) {
        String text = manifestJson == null ? "" : manifestJson.trim();
        if (!text.startsWith("[")) {
            return List.of();
        }
        JSONArray skills;
        try {
            skills = JSON.parseArray(text);
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "训练产物清单JSON格式非法");
        }
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<Path> results = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            JSONObject item = skills.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String rawPath = item.getString("path");
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }
            Path path = Path.of(rawPath);
            if (!path.isAbsolute()) {
                path = outputDir.resolve(rawPath);
            }
            Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(outputDir)) {
                throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                        "技能路径超出输出目录: " + normalized);
            }
            results.add(normalized);
        }
        return results;
    }

    private String buildPrompt(String trainingGoal,
                               List<TrainingSourceRequest> sources,
                               String skillDirName,
                               Path skillDirectory) {
        StringBuilder builder = new StringBuilder();
        Path specSkillPath = resolveConfiguredSpecSkillPath();
        AppInfoSource appInfoSource = findAppInfoSource(sources);
        builder.append("请生成 1 个数字专家 skill。\n");
        if (StringUtils.hasText(trainingGoal)) {
            builder.append("目标: ").append(trainingGoal.trim()).append("\n");
        }
        if (appInfoSource != null) {
            builder.append("输入路径: ").append(appInfoSource.jarsDirectory()).append("\n");
            appendPromptLine(builder, "应用名", appInfoSource.appName());
        } else {
            builder.append("输入:\n");
            for (int i = 0; i < sources.size(); i++) {
                TrainingSourceRequest source = sources.get(i);
                builder.append(i + 1)
                        .append(". [")
                        .append(source.sourceType())
                        .append("] ")
                        .append(source.sourceValue())
                        .append("\n");
            }
        }

        builder.append("输出路径: ").append(skillDirectory).append("\n");
        if (specSkillPath != null) {
            builder.append("规范路径: ").append(specSkillPath.resolve("SKILL.md")).append("\n");
        } else {
            builder.append("规范路径: 未配置\n");
        }
        builder.append("要求:\n")
                .append("1. 使用 skill-creator，按规范生成 1 个 skill。\n")
                .append("2. 目录名必须是 ").append(skillDirName).append("，且只能写入输出路径。\n")
                .append("3. 输出目录必须包含 SKILL.md，不要生成其他 skill。\n");
        return builder.toString();
    }

    private Path resolveConfiguredSpecSkillPath() {
        String configuredPath = properties.getTraining().getSpecSkillPath();
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }
        Path path = Path.of(configuredPath).normalize();
        if (Files.isRegularFile(path.resolve("SKILL.md"))) {
            return path;
        }
        log.warn("配置的规范 skill 路径不可用，未找到 SKILL.md, path={}", path);
        return path;
    }

    private Path resolveExpectedSkillDirectory(ExpertTrainingTaskEntity task, Path outputDir) {
        List<TrainingSourceRequest> sources = parseSources(task.getSourceManifestJson());
        String skillDirName = resolveSkillDirectoryName(sources, task.getTaskId());
        return resolveSkillDirectory(outputDir, skillDirName);
    }

    private Path resolveSkillDirectory(Path outputDir, String skillDirName) {
        Path skillDirectory = outputDir.resolve(skillDirName).normalize();
        if (!skillDirectory.startsWith(outputDir)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能目录超出输出目录: " + skillDirectory);
        }
        return skillDirectory;
    }

    private void validateSkillDirectory(Path skillDirectory) {
        if (!Files.exists(skillDirectory) || !Files.isDirectory(skillDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能目录不存在: " + skillDirectory);
        }
        Path skillMdPath = skillDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMdPath)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能目录缺少 SKILL.md: " + skillDirectory);
        }
    }

    private void persistSkillManifest(ExpertTrainingTaskEntity task, List<Path> skillDirectories) {
        task.setManifestPath(buildSkillManifestJson(skillDirectories));
        task.setUpdatedAt(LocalDateTime.now());
        expertTrainingTaskMapper.updateById(task);
    }

    private String buildSkillManifestJson(List<Path> skillDirectories) {
        JSONArray skills = new JSONArray();
        for (Path skillDirectory : skillDirectories) {
            JSONObject item = new JSONObject();
            item.put("name", skillDirectory.getFileName().toString());
            item.put("path", skillDirectory.toString());
            skills.add(item);
        }
        return JSON.toJSONString(skills);
    }

    private boolean isArtifactsNotReady(BusinessException ex) {
        if (ex.getErrorCode() != ErrorCode.TRAINING_OUTPUT_INVALID) {
            return false;
        }
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return message.contains("训练输出目录不存在")
                || message.contains("技能目录不存在")
                || message.contains("缺少 SKILL.md")
                || message.contains("未在输出目录下找到技能目录");
    }

    private String resolveSkillDirectoryName(List<TrainingSourceRequest> sources, String taskId) {
        for (TrainingSourceRequest source : sources) {
            AppInfoSource appInfoSource = resolveAppInfoSource(source);
            if (appInfoSource != null && StringUtils.hasText(appInfoSource.appName())) {
                String normalized = normalizeSkillDirectoryName(appInfoSource.appName());
                if (StringUtils.hasText(normalized)) {
                    return normalized;
                }
            }
            String fileName = extractSourceFileName(source);
            if (!StringUtils.hasText(fileName)) {
                continue;
            }
            String normalized = normalizeSkillDirectoryName(removeExtension(fileName));
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "generated_skill_" + taskId.substring(0, Math.min(taskId.length(), 8));
    }

    private String extractSourceFileName(TrainingSourceRequest source) {
        if (source == null || !StringUtils.hasText(source.sourceType()) || !StringUtils.hasText(source.sourceValue())) {
            return null;
        }
        if (TrainingSourceType.GIT_URL.name().equals(source.sourceType())) {
            return null;
        }
        AppInfoSource appInfoSource = resolveAppInfoSource(source);
        if (appInfoSource != null && StringUtils.hasText(appInfoSource.appName())) {
            return appInfoSource.appName();
        }
        if (TrainingSourceType.DOC_URL.name().equals(source.sourceType())
                || TrainingSourceType.JAR_URL.name().equals(source.sourceType())) {
            return extractFileNameFromUrl(source.sourceValue());
        }
        return extractLastPathSegment(source.sourceValue());
    }

    private void appendPromptLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value).append("\n");
        }
    }

    private List<String> resolveProxyInputPaths(List<TrainingSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (TrainingSourceRequest source : sources) {
            AppInfoSource appInfoSource = resolveAppInfoSource(source);
            if (appInfoSource != null) {
                result.add(appInfoSource.jarsDirectory().toString());
                continue;
            }
            result.add(source.sourceValue());
        }
        return result;
    }

    private AppInfoSource findAppInfoSource(List<TrainingSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        for (TrainingSourceRequest source : sources) {
            AppInfoSource appInfoSource = resolveAppInfoSource(source);
            if (appInfoSource != null) {
                return appInfoSource;
            }
        }
        return null;
    }

    private AppInfoSource resolveAppInfoSource(TrainingSourceRequest source) {
        if (source == null
                || !TrainingSourceType.LOCAL_PATH.name().equals(source.sourceType())
                || !StringUtils.hasText(source.sourceValue())) {
            return null;
        }
        try {
            Path appInfoDirectory = Path.of(source.sourceValue()).normalize();
            String expectedDirName = properties.getTraining().getKafka().getAppInfoDirName();
            if (StringUtils.hasText(expectedDirName)
                    && appInfoDirectory.getFileName() != null
                    && !expectedDirName.equals(appInfoDirectory.getFileName().toString())
                    && !Files.exists(appInfoDirectory.resolve("app.json"))
                    && !Files.exists(appInfoDirectory.resolve("jars"))) {
                return null;
            }

            Path appJsonPath = appInfoDirectory.resolve("app.json");
            Path jarsDirectory = appInfoDirectory.resolve("jars");
            if (!Files.isRegularFile(appJsonPath) || !Files.isDirectory(jarsDirectory) || !containsJarFile(jarsDirectory)) {
                return null;
            }
            JSONObject appInfoJson = readJsonObject(appJsonPath);
            String appName = resolveAppName(appInfoDirectory, appInfoJson);
            return new AppInfoSource(appInfoDirectory, jarsDirectory, appName);
        } catch (Exception ex) {
            log.warn("Resolve app_info training source failed, sourceValue={}", source.sourceValue(), ex);
            return null;
        }
    }

    private boolean containsJarFile(Path jarsDirectory) {
        try (var stream = Files.list(jarsDirectory)) {
            return stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName() != null
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"));
        } catch (IOException ex) {
            log.warn("Check jars directory failed, path={}", jarsDirectory, ex);
            return false;
        }
    }

    private JSONObject readJsonObject(Path path) {
        try {
            return JSON.parseObject(Files.readString(path), JSONObject.class);
        } catch (Exception ex) {
            log.warn("Read JSON file failed, path={}", path, ex);
            return null;
        }
    }

    private String resolveAppName(Path appInfoDirectory, JSONObject appInfoJson) {
        Path timestampDirectory = appInfoDirectory.getParent();
        Path appNameDirectory = timestampDirectory == null ? null : timestampDirectory.getParent();
        if (appNameDirectory != null && appNameDirectory.getFileName() != null) {
            String appName = appNameDirectory.getFileName().toString();
            if (StringUtils.hasText(appName)) {
                return appName;
            }
        }
        if (appInfoJson != null) {
            String serviceName = appInfoJson.getString("serviceName");
            if (StringUtils.hasText(serviceName)) {
                int separatorIndex = serviceName.lastIndexOf("::");
                if (separatorIndex >= 0 && separatorIndex < serviceName.length() - 2) {
                    return serviceName.substring(separatorIndex + 2);
                }
                return serviceName;
            }
        }
        return extractLastPathSegment(appInfoDirectory.toString());
    }

    private String extractFileNameFromUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (StringUtils.hasText(uri.getPath())) {
                return extractLastPathSegment(uri.getPath());
            }
        } catch (Exception ignored) {
            // 回退到普通字符串截取。
        }
        String sanitized = value;
        int queryIndex = sanitized.indexOf('?');
        if (queryIndex >= 0) {
            sanitized = sanitized.substring(0, queryIndex);
        }
        int fragmentIndex = sanitized.indexOf('#');
        if (fragmentIndex >= 0) {
            sanitized = sanitized.substring(0, fragmentIndex);
        }
        return extractLastPathSegment(sanitized);
    }

    private String extractLastPathSegment(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private String removeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return fileName;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String normalizeSkillDirectoryName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            return null;
        }
        String normalized = rawName.trim()
                .replaceAll("[^\\p{L}\\p{N}._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        return normalized;
    }

    private void markTaskRunning(ExpertTrainingTaskEntity task) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.RUNNING.name());
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
    }

    private void markExpertTraining(Long expertId, String taskId) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(expertId);
        if (ExpertStatus.DISABLED.name().equals(expert.getStatus())) {
            throw BusinessException.conflict(ErrorCode.EXPERT_DISABLED,
                    "训练过程中专家被禁用");
        }
        if (!ExpertStatus.STARTED.name().equals(expert.getStatus())) {
            expert.setStatus(ExpertStatus.TRAINING.name());
        }
        expert.setLastTrainingTaskId(taskId);
        expert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(expert);
    }

    private void markTaskSucceeded(String taskId) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.SUCCEEDED.name());
        task.setArtifactVerified(1);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);

        DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
        expert.setLastTrainingTaskId(taskId);
        expert.setLastTrainingSessionId(task.getSessionId());
        expert.setTrainingVersion((expert.getTrainingVersion() == null ? 0 : expert.getTrainingVersion()) + 1);
        expert.setUpdatedAt(now);
        digitalExpertMapper.updateById(expert);
        log.info("训练任务完成, taskId={}, expertId={}, sessionId={}",
                task.getTaskId(), task.getExpertId(), task.getSessionId());
    }

    private void markTaskFailed(String taskId, String reason) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.FAILED.name());
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        restoreExpertStatus(task);
        log.warn("训练任务失败, taskId={}, expertId={}, reason={}",
                task.getTaskId(), task.getExpertId(), truncateReason(reason));
    }

    private void markTaskTimeout(String taskId, String reason) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.TIMEOUT.name());
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        restoreExpertStatus(task);
        log.warn("训练任务超时, taskId={}, expertId={}, reason={}",
                task.getTaskId(), task.getExpertId(), truncateReason(reason));
    }

    private boolean hasActiveTrainingTask(Long expertId) {
        Long count = expertTrainingTaskMapper.selectCount(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId)
                .in(ExpertTrainingTaskEntity::getStatus, TrainingTaskStatus.activeTaskStatuses()));
        return count != null && count > 0;
    }

    private void restoreExpertStatus(ExpertTrainingTaskEntity task) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
        String previousStatus = task.getPreviousExpertStatus();
        if (StringUtils.hasText(previousStatus)) {
            expert.setStatus(previousStatus);
        } else if (ExpertStatus.TRAINING.name().equals(expert.getStatus())) {
            expert.setStatus(ExpertStatus.DRAFT.name());
        }
        expert.setLastTrainingTaskId(task.getTaskId());
        if (StringUtils.hasText(task.getSessionId())) {
            expert.setLastTrainingSessionId(task.getSessionId());
        }
        expert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(expert);
    }

    private int increasePollCount(ExpertTrainingTaskEntity task) {
        int pollCount = (task.getPollCount() == null ? 0 : task.getPollCount()) + 1;
        task.setPollCount(pollCount);
        expertTrainingTaskMapper.updateById(task);
        return pollCount;
    }

    private LocalDateTime resolveRunningStageStartTime(ExpertTrainingTaskEntity task) {
        if (task.getStartedAt() != null) {
            return task.getStartedAt();
        }
        if (task.getUpdatedAt() != null) {
            return task.getUpdatedAt();
        }
        return task.getCreatedAt();
    }

    private long calculateElapsedMs(LocalDateTime stageStartTime) {
        if (stageStartTime == null) {
            return 0L;
        }
        return Math.max(Duration.between(stageStartTime, LocalDateTime.now()).toMillis(), 0L);
    }

    private boolean shouldLogProgress(int pollCount, int interval) {
        return interval > 0 && pollCount % interval == 0;
    }

    private Path normalizeAndCheckOutputPath(String outputPath) {
        if (!StringUtils.hasText(outputPath)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "输出目录为空");
        }
        return Path.of(outputPath).normalize();
    }

    private Path resolveTrainingOutputDirectory(Long expertId, String taskId) {
        return getTrainingOutputRoot()
                .resolve(String.valueOf(expertId))
                .resolve(taskId)
                .resolve("generated-skills")
                .normalize();
    }

    private Path getTrainingOutputRoot() {
        String configuredRoot = properties.getTraining().getOutputRoot();
        if (!StringUtils.hasText(configuredRoot)) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "未配置训练输出根目录");
        }
        return Path.of(configuredRoot).normalize();
    }

    private void recreateTrainingOutputDirectory(Path directory) {
        deleteTrainingOutputDirectory(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "创建训练输出目录失败: " + directory);
        }
    }

    private void deleteTrainingOutputDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ex) {
                    throw new IllegalStateException("删除训练输出目录失败: " + current, ex);
                }
            });
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "清理训练输出目录失败: " + path);
        }
    }

    private List<TrainingSourceRequest> normalizeSources(List<TrainingSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "训练输入源不能为空");
        }
        List<TrainingSourceRequest> result = new ArrayList<>();
        for (TrainingSourceRequest source : sources) {
            if (source == null) {
                continue;
            }
            String type = source.sourceType() == null ? "" : source.sourceType().trim().toUpperCase(Locale.ROOT);
            String value = source.sourceValue() == null ? "" : source.sourceValue().trim();
            if (!StringUtils.hasText(type) || !StringUtils.hasText(value)) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                        "sourceType 和 sourceValue 不能为空");
            }
            try {
                TrainingSourceType.valueOf(type);
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                        "不支持的 sourceType: " + type);
            }
            result.add(new TrainingSourceRequest(type, value));
        }
        if (result.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "训练输入源不能为空");
        }
        return result;
    }

    private List<TrainingSourceRequest> parseSources(String sourceManifestJson) {
        if (!StringUtils.hasText(sourceManifestJson)) {
            return List.of();
        }
        JSONArray array = JSON.parseArray(sourceManifestJson);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<TrainingSourceRequest> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.getString("sourceType");
            String value = item.getString("sourceValue");
            if (StringUtils.hasText(type) && StringUtils.hasText(value)) {
                result.add(new TrainingSourceRequest(type, value));
            }
        }
        return result;
    }

    private String resolvePackageName(Path skillDirectory) {
        String base = skillDirectory.getFileName() == null ? "generated-skill" : skillDirectory.getFileName().toString();
        String normalized = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(normalized)) {
            normalized = "generated-skill";
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            normalized = normalized + ".zip";
        }
        return normalized;
    }

    private ExpertTrainingTaskEntity requireTask(String taskId) {
        ExpertTrainingTaskEntity task = expertTrainingTaskMapper.selectOne(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getTaskId, taskId));
        if (task == null) {
            throw BusinessException.notFound(ErrorCode.TRAINING_TASK_NOT_FOUND,
                    "训练任务不存在: " + taskId);
        }
        return task;
    }

    private String truncateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "未知失败";
        }
        return reason.length() > 1800 ? reason.substring(0, 1800) : reason;
    }

    private ExpertTrainingTaskResponse toResponse(ExpertTrainingTaskEntity entity) {
        return new ExpertTrainingTaskResponse(
                entity.getTaskId(),
                entity.getExpertId(),
                entity.getStatus(),
                entity.getSessionId(),
                entity.getOutputDir(),
                entity.getReleaseTaskId(),
                entity.getFailureReason(),
                entity.getRequestedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private record AppInfoSource(
            Path appInfoDirectory,
            Path jarsDirectory,
            String appName
    ) {
    }
}
