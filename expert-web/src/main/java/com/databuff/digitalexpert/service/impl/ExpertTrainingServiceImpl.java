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
import com.databuff.digitalexpert.dao.entity.ExpertStaticPackageBindingEntity;
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
import com.databuff.digitalexpert.dao.mapper.ExpertStaticPackageBindingMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertTrainingTaskMapper;
import com.databuff.digitalexpert.dao.mapper.SkillPackageMapper;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import com.databuff.digitalexpert.dao.bo.TrainingContext;
import com.databuff.digitalexpert.service.TrainingDispatcher;
import com.databuff.digitalexpert.service.prompt.TrainingPromptContext;
import com.databuff.digitalexpert.service.prompt.TrainingPromptStrategy;
import com.databuff.digitalexpert.service.proxy.TrainingProxyClient;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import com.databuff.digitalexpert.service.storage.SkillArchiveMetadata;
import com.databuff.digitalexpert.service.storage.ZipArchiveService;
import com.databuff.digitalexpert.util.AgentSessionId;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final Pattern AUTO_VERSION_PATTERN = Pattern.compile("^v(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final Set<String> pollingTaskLocks = ConcurrentHashMap.newKeySet();
    private final Set<String> abortingTaskIds = ConcurrentHashMap.newKeySet();

    @Autowired
    private ExpertTrainingTaskMapper expertTrainingTaskMapper;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertSkillBindingMapper expertSkillBindingMapper;
    @Autowired
    private ExpertStaticPackageBindingMapper expertStaticPackageBindingMapper;
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
    private TrainingDispatcher trainingDispatcher;
    @Autowired
    private TrainingProxyClient trainingProxyClient;
    @Autowired
    @Qualifier("trainingSubmitExecutor")
    private Executor trainingSubmitExecutor;
    @Autowired
    @Qualifier("trainingPollExecutor")
    private Executor trainingPollExecutor;

    @Autowired
    private TrainingPromptStrategy appInfoTrainingPromptStrategy;
    @Autowired
    private TrainingPromptStrategy gitTrainingPromptStrategy;

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
        String taskId = AgentSessionId.generate();
        String skillDirName = resolveSkillDirectoryName(normalizedSources, taskId, expert.getName());
        AppInfoSource appInfoSource = findAppInfoSource(normalizedSources);
        Path skillRootDirectory = resolveSkillRootDirectory(expertId, skillDirName);
        Path outputDirectory = resolveTrainingOutputDirectory(skillRootDirectory, appInfoSource, normalizedSources);

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
    public List<ExpertTrainingTaskResponse> listTasks(Long expertId) {
        LambdaQueryWrapper<ExpertTrainingTaskEntity> queryWrapper = new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .orderByDesc(ExpertTrainingTaskEntity::getId);
        if (expertId != null) {
            queryWrapper.eq(ExpertTrainingTaskEntity::getExpertId, expertId);
        }
        return expertTrainingTaskMapper.selectList(queryWrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public boolean abortTask(Long expertId, String taskId) {
        ExpertTrainingTaskEntity task = expertTrainingTaskMapper.selectOne(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getTaskId, taskId)
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId));
        if (task == null) {
            throw BusinessException.notFound(ErrorCode.TRAINING_TASK_NOT_FOUND,
                    "训练任务不存在: " + taskId);
        }
        if (!supportsAbort(task.getStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_REQUEST,
                    "当前训练任务状态不支持中止: " + task.getStatus());
        }
        abortingTaskIds.add(taskId);
        try {
            if (StringUtils.hasText(task.getSessionId())) {
                trainingProxyClient.abortConversation(task.getSessionId());
            }
            cleanupTaskOutputSafely(task);
            restoreExpertStatusAfterAbort(task);
            pollingTaskLocks.remove(taskId);
            expertTrainingTaskMapper.deleteById(task.getId());
            log.info("训练任务已中止并清理记录, taskId={}, expertId={}, sessionId={}",
                    task.getTaskId(), task.getExpertId(), task.getSessionId());
            return true;
        } finally {
            abortingTaskIds.remove(taskId);
        }
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
        if (abortingTaskIds.contains(taskId)) {
            return;
        }
        ExpertTrainingTaskEntity task = findTask(taskId);
        if (task == null) {
            return;
        }
        if (!TrainingTaskStatus.PENDING.name().equals(task.getStatus())) {
            return;
        }
        try {
            if (abortingTaskIds.contains(taskId)) {
                return;
            }
            markTaskRunning(task);
            markExpertTraining(task.getExpertId(), taskId);

            Path outputDir = normalizeAndCheckOutputPath(task.getOutputDir());
            Path skillRootDirectory = resolveSkillRootDirectory(outputDir);
            ensureSkillRootDirectory(skillRootDirectory);
            recreateTrainingVersionDirectory(outputDir);

            List<TrainingSourceRequest> sources = parseSources(task.getSourceManifestJson());
            DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
            String skillDirName = resolveSkillDirectoryName(sources, taskId, expert.getName());
            Path versionDirectory = outputDir;
            String prompt = buildPrompt(trainingGoal, sources, skillDirName, skillRootDirectory, versionDirectory);
            if (abortingTaskIds.contains(taskId)) {
                return;
            }
            TrainingProxyClient.ProxySubmitResult submitResult = trainingProxyClient.submitTraining(
                    taskId,
                    prompt,
                    resolveProxyInputPaths(sources),
                    versionDirectory.toString()
            );

            task = findTask(taskId);
            if (task == null || abortingTaskIds.contains(taskId)) {
                return;
            }
            task.setSessionId(taskId);
            task.setSubmitRequestId(submitResult.requestId());
            task.setUpdatedAt(LocalDateTime.now());
            expertTrainingTaskMapper.updateById(task);
            expert.setLastTrainingSessionId(taskId);
            expert.setUpdatedAt(LocalDateTime.now());
            digitalExpertMapper.updateById(expert);
        } catch (Exception ex) {
            log.error("提交训练任务到代理失败, taskId={}", taskId, ex);
            if (findTask(taskId) != null && !abortingTaskIds.contains(taskId)) {
                markTaskFailed(taskId, ex.getMessage());
            }
        }
    }

    private void pollSingleTask(String taskId) {
        if (abortingTaskIds.contains(taskId)) {
            return;
        }
        ExpertTrainingTaskEntity task = findTask(taskId);
        if (task == null) {
            return;
        }
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
        long timeoutMs = properties.getTraining().getSessionTimeout().toMillis();
        if (elapsedMs >= timeoutMs) {
            markTaskTimeout(task.getTaskId(), "训练会话轮询超时");
            return;
        }
        if (!StringUtils.hasText(task.getSessionId())) {
            if (shouldLogProgress(pollCount, 12)) {
                log.info("训练任务等待会话ID中, taskId={}, elapsedMs={}, timeoutMs={}",
                        task.getTaskId(), elapsedMs, timeoutMs);
            }
            return;
        }
        if (!trainingProxyClient.isSessionFinished(task.getSessionId())) {
            if (shouldLogProgress(pollCount, 12)) {
                log.info("训练会话未结束, taskId={}, sessionId={}, elapsedMs={}, timeoutMs={}",
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
        log.info("训练会话已结束，进入产物缓冲期, taskId={}, sessionId={}, gracePeriodMs={}",
                task.getTaskId(), task.getSessionId(), properties.getTraining().getArtifactGracePeriod().toMillis());
    }

    private void handleVerifyingArtifacts(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        long elapsedMs = calculateElapsedMs(task.getUpdatedAt());
        long gracePeriodMs = properties.getTraining().getArtifactGracePeriod().toMillis();
        boolean skillReadySignalDetected = hasSkillReadySignal(task);
        if (!skillReadySignalDetected && elapsedMs >= gracePeriodMs) {
            markTaskFailed(task.getTaskId(), "训练产物在最大等待时间内未生成 SKILL.md");
            return;
        }
        if (!skillReadySignalDetected) {
            if (shouldLogProgress(pollCount, 6)) {
                long remainingMs = Math.max(gracePeriodMs - elapsedMs, 0L);
                log.info("训练产物缓冲中, taskId={}, elapsedMs={}, remainingMs={}",
                        task.getTaskId(), elapsedMs, remainingMs);
            }
            return;
        }

        try {
            log.info("开始校验训练产物, taskId={}, waitMs={}", task.getTaskId(), elapsedMs);
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
            log.info("训练产物导入完成，已触发发布任务, taskId={}, releaseTaskId={}", task.getTaskId(), releaseTask.taskId());
        } catch (BusinessException ex) {
            if (isArtifactsNotReady(ex)) {
                if (elapsedMs < gracePeriodMs) {
                    if (shouldLogProgress(pollCount, 6)) {
                        long remainingMs = Math.max(gracePeriodMs - elapsedMs, 0L);
                        log.info("训练产物尚未完全就绪，继续等待, taskId={}, elapsedMs={}, remainingMs={}, reason={}",
                                task.getTaskId(), elapsedMs, remainingMs, ex.getMessage());
                    }
                    return;
                }
                markTaskFailed(task.getTaskId(), "训练产物未就绪或校验失败: " + ex.getMessage());
            } else {
                log.error("处理训练产物失败, taskId={}", task.getTaskId(), ex);
                markTaskFailed(task.getTaskId(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("处理训练产物失败, taskId={}", task.getTaskId(), ex);
            markTaskFailed(task.getTaskId(), ex.getMessage());
        }
    }

    private void handleReleasingTask(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        long elapsedMs = calculateElapsedMs(task.getUpdatedAt());
        long timeoutMs = properties.getTraining().getReleaseTimeout().toMillis();
        if (elapsedMs >= timeoutMs) {
            markTaskTimeout(task.getTaskId(), "发布任务等待超时");
            return;
        }
        if (!StringUtils.hasText(task.getReleaseTaskId())) {
            markTaskFailed(task.getTaskId(), "发布任务缺少任务ID");
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
            log.info("训练任务发布成功, taskId={}, releaseTaskId={}", task.getTaskId(), task.getReleaseTaskId());
            markTaskSucceeded(task.getTaskId());
            return;
        }
        if (ReleaseTaskStatus.FAILED.name().equals(releaseTask.getStatus())) {
            markTaskFailed(task.getTaskId(), releaseTask.getFailureReason());
            return;
        }
        if (shouldLogProgress(pollCount, 12)) {
            log.info("发布任务未完成, taskId={}, releaseTaskId={}, elapsedMs={}, timeoutMs={}",
                    task.getTaskId(), task.getReleaseTaskId(), elapsedMs, timeoutMs);
        }
    }

    private List<Long> importSkillPackages(List<Path> skillDirectories) {
        List<Long> skillIds = new ArrayList<>();
        for (Path skillDirectory : skillDirectories) {
            String packageName = resolvePackageName(skillDirectory);
            Path tempDirectory = createSkillPackageTempDirectory();
            try {
                Path tempArchivePath = tempDirectory.resolve(packageName);
                zipArchiveService.zipDirectory(skillDirectory, tempArchivePath);
                SkillArchiveMetadata metadata = zipArchiveService.inspectSkillArchive(tempArchivePath, packageName);
                String checksum = zipArchiveService.sha256Hex(tempArchivePath);

                LocalDateTime now = LocalDateTime.now();
                SkillPackageEntity entity = new SkillPackageEntity();
                entity.setName(metadata.name());
                entity.setDescription(metadata.description());
                entity.setPackageName(packageName);
                entity.setChecksum(checksum);
                entity.setStatus(PackageStatus.ACTIVE.name());
                entity.setPackagePath("");
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                skillPackageMapper.insert(entity);

                Path directory = sharedStorageService.resolveSkillDirectory(entity.getId());
                sharedStorageService.recreateDirectory(directory);
                Path packagePath = directory.resolve(packageName);
                sharedStorageService.moveFile(tempArchivePath, packagePath);
                entity.setPackagePath(sharedStorageService.toStoragePath(packagePath));
                entity.setUpdatedAt(LocalDateTime.now());
                skillPackageMapper.updateById(entity);
                skillIds.add(entity.getId());
            } finally {
                sharedStorageService.deleteRecursively(tempDirectory);
            }
        }
        if (skillIds.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "未找到可导入的技能包");
        }
        return skillIds;
    }

    private Path createSkillPackageTempDirectory() {
        Path tempDirectory = sharedStorageService.resolveTemporaryDirectory("skill-import")
                .resolve(AgentSessionId.generate())
                .normalize();
        sharedStorageService.createDirectories(tempDirectory);
        return tempDirectory;
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
        Path versionDirectory = normalizeAndCheckOutputPath(task.getOutputDir());
        Path skillRootDirectory = resolveSkillRootDirectory(versionDirectory);
        if (!Files.exists(skillRootDirectory) || !Files.isDirectory(skillRootDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能根目录不存在: " + skillRootDirectory);
        }

        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        if (StringUtils.hasText(task.getManifestPath())) {
            directories.addAll(loadSkillDirectoriesFromManifestJson(task.getManifestPath(), skillRootDirectory));
        }

        if (directories.isEmpty()) {
            Path skillDirectory = resolveExpectedSkillDirectory(task, versionDirectory);
            validateSkillDirectory(skillDirectory, versionDirectory);
            directories.add(skillDirectory);
            persistSkillManifest(task, List.copyOf(directories));
        }

        if (directories.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能根目录下未找到可用技能目录: " + skillRootDirectory);
        }

        for (Path directory : directories) {
            validateSkillDirectory(directory, versionDirectory);
        }
        return List.copyOf(directories);
    }

    private boolean hasSkillReadySignal(ExpertTrainingTaskEntity task) {
        try {
            Path versionDirectory = normalizeAndCheckOutputPath(task.getOutputDir());
            Path skillRootDirectory = resolveSkillRootDirectory(versionDirectory);
            return Files.isRegularFile(skillRootDirectory.resolve("SKILL.md"));
        } catch (Exception ex) {
            return false;
        }
    }

    private List<Path> loadSkillDirectoriesFromManifestJson(String manifestJson, Path skillRootDirectory) {
        String text = manifestJson == null ? "" : manifestJson.trim();
        if (!text.startsWith("[")) {
            return List.of();
        }
        JSONArray skills;
        try {
            skills = JSON.parseArray(text);
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "训练产物清单JSON格式不正确");
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
                path = skillRootDirectory.resolveSibling(rawPath);
            }
            Path normalized = path.toAbsolutePath().normalize();
            Path skillParentDirectory = skillRootDirectory.getParent();
            if (skillParentDirectory != null && !normalized.startsWith(skillParentDirectory)) {
                throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                        "技能路径超出技能根目录范围: " + normalized);
            }
            results.add(normalized);
        }
        return results;
    }

    private String buildPrompt(String trainingGoal,
                               List<TrainingSourceRequest> sources,
                               String skillDirName,
                               Path skillRootDirectory,
                               Path versionDirectory) {
        AppInfoSource appInfoSource = findAppInfoSource(sources);
        TrainingPromptContext context = new TrainingPromptContext(
                trainingGoal,
                sources == null ? List.of() : List.copyOf(sources),
                skillDirName,
                skillRootDirectory,
                versionDirectory,
                resolveConfiguredSpecSkillPath(),
                appInfoSource == null ? null : appInfoSource.jarsDirectory()
        );
        return resolveTrainingPromptStrategy(context).buildPrompt(context);
    }

    private com.databuff.digitalexpert.service.prompt.TrainingPromptStrategy resolveTrainingPromptStrategy(
            TrainingPromptContext context) {
        if (appInfoTrainingPromptStrategy.supports(context)) {
            return appInfoTrainingPromptStrategy;
        }
        if (gitTrainingPromptStrategy.supports(context)) {
            return gitTrainingPromptStrategy;
        }
        throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "Unsupported training source type");
    }

    private Path resolveConfiguredSpecSkillPath() {
        String configuredPath = properties.getTraining().getSpecSkillPath();
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }
        Path path = Path.of(configuredPath).normalize();
        if (!Files.isRegularFile(path.resolve("SKILL.md"))) {
            log.warn("配置的规范 skill 路径不可用，未找到 SKILL.md, path={}", path);
        }
        return path;
    }

    private Path resolveExpectedSkillDirectory(ExpertTrainingTaskEntity task, Path versionDirectory) {
        return resolveSkillRootDirectory(versionDirectory);
    }

    private Path resolveSkillRootDirectory(Long expertId, String skillDirName) {
        Path expertRootDirectory = getTrainingOutputRoot()
                .resolve(String.valueOf(expertId))
                .normalize();
        Path skillRootDirectory = expertRootDirectory.resolve(skillDirName).normalize();
        if (!skillRootDirectory.startsWith(expertRootDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能目录超出专家训练根目录范围: " + skillRootDirectory);
        }
        return skillRootDirectory;
    }

    private Path resolveSkillRootDirectory(Path versionDirectory) {
        Path normalizedVersionDirectory = versionDirectory.toAbsolutePath().normalize();
        Path skillRootDirectory = normalizedVersionDirectory.getParent();
        if (skillRootDirectory == null) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "无法从版本目录解析技能根目录: " + versionDirectory);
        }
        return skillRootDirectory;
    }

    private void validateSkillDirectory(Path skillRootDirectory, Path versionDirectory) {
        if (!Files.exists(skillRootDirectory) || !Files.isDirectory(skillRootDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能根目录不存在: " + skillRootDirectory);
        }
        Path skillMdPath = skillRootDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMdPath)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "技能根目录缺少 SKILL.md: " + skillRootDirectory);
        }
        if (!Files.exists(versionDirectory) || !Files.isDirectory(versionDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "版本目录不存在: " + versionDirectory);
        }
        Path staticPackageDirectory = versionDirectory.resolve("static_package");
        if (!Files.exists(staticPackageDirectory) || !Files.isDirectory(staticPackageDirectory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "版本目录缺少 static_package: " + versionDirectory);
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
                || message.contains("技能根目录不存在")
                || message.contains("缺少 SKILL.md")
                || message.contains("版本目录不存在")
                || message.contains("缺少 static_package")
                || message.contains("无法从版本目录解析技能根目录");
    }

    private String resolveSkillDirectoryName(List<TrainingSourceRequest> sources, String taskId, String expertName) {
        if (sources != null) {
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
        }
        String normalizedExpertName = normalizeSkillDirectoryName(expertName);
        if (StringUtils.hasText(normalizedExpertName)) {
            return normalizedExpertName;
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
            } else {
                result.add(source.sourceValue());
            }
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
            if (!Files.isDirectory(appInfoDirectory)) {
                return null;
            }
            Path appJsonPath = appInfoDirectory.resolve("app.json");
            Path jarsDirectory = appInfoDirectory.resolve("jars");
            if (!Files.isRegularFile(appJsonPath) || !Files.isDirectory(jarsDirectory) || !containsJarFile(jarsDirectory)) {
                return null;
            }
            JSONObject appInfoJson = readJsonObject(appJsonPath);
            String appName = resolveAppName(appInfoDirectory, appInfoJson);
            String serviceVersion = resolveServiceVersion(appInfoJson);
            return new AppInfoSource(appInfoDirectory, jarsDirectory, appName, serviceVersion);
        } catch (Exception ex) {
            log.warn("解析 app_info 训练源失败, sourceValue={}", source.sourceValue(), ex);
            return null;
        }
    }

    private boolean containsJarFile(Path jarsDirectory) {
        try (var stream = Files.list(jarsDirectory)) {
            return stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName() != null
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"));
        } catch (IOException ex) {
            log.warn("检查 jars 目录失败, path={}", jarsDirectory, ex);
            return false;
        }
    }

    private JSONObject readJsonObject(Path path) {
        try {
            return JSON.parseObject(Files.readString(path), JSONObject.class);
        } catch (Exception ex) {
            log.warn("读取 JSON 文件失败, path={}", path, ex);
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

    private String resolveServiceVersion(JSONObject appInfoJson) {
        if (appInfoJson == null) {
            return null;
        }
        return normalizeVersionDirectoryName(appInfoJson.getString("serviceVersion"));
    }

    private String extractFileNameFromUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (StringUtils.hasText(uri.getPath())) {
                return extractLastPathSegment(uri.getPath());
            }
        } catch (Exception ignored) {
            // 解析 URL 失败时，继续按原始字符串提取文件名
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

    private String normalizeVersionDirectoryName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            return null;
        }
        String normalized = rawName.trim()
                .replaceAll("[^\\p{L}\\p{N}._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        return StringUtils.hasText(normalized) ? normalized : null;
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
                    "训练前专家已被禁用: " + expertId);
        }
        if (!ExpertStatus.STARTED.name().equals(expert.getStatus())) {
            expert.setStatus(ExpertStatus.TRAINING.name());
        }
        expert.setLastTrainingTaskId(taskId);
        expert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(expert);
    }

    private void markTaskSucceeded(String taskId) {
        ExpertTrainingTaskEntity task = findTask(taskId);
        if (task == null) {
            return;
        }
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
        log.info("训练任务成功, taskId={}, expertId={}, sessionId={}",
                task.getTaskId(), task.getExpertId(), task.getSessionId());
        triggerPostProcess(task.getTaskId(), TrainingTaskStatus.SUCCEEDED);
    }

    private void markTaskFailed(String taskId, String reason) {
        ExpertTrainingTaskEntity task = findTask(taskId);
        if (task == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.FAILED.name());
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        cleanupTaskOutputSafely(task);
        restoreExpertStatus(task);
        log.warn("训练任务失败, taskId={}, expertId={}, reason={}",
                task.getTaskId(), task.getExpertId(), truncateReason(reason));
        triggerPostProcess(task.getTaskId(), TrainingTaskStatus.FAILED);
    }

    private void markTaskTimeout(String taskId, String reason) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.TIMEOUT.name());
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        cleanupTaskOutputSafely(task);
        restoreExpertStatus(task);
        log.warn("训练任务超时, taskId={}, expertId={}, reason={}",
                task.getTaskId(), task.getExpertId(), truncateReason(reason));
        triggerPostProcess(task.getTaskId(), TrainingTaskStatus.TIMEOUT);
    }

    private void triggerPostProcess(String taskId, TrainingTaskStatus status) {
        try {
            trainingDispatcher.dispatch(buildPostProcessContext(taskId, status));
        } catch (Exception ex) {
            log.error("触发训练后置处理失败, taskId={}, status={}", taskId, status, ex);
        }
    }

    private TrainingContext buildPostProcessContext(String taskId, TrainingTaskStatus status) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
        return new TrainingContext(
                status,
                task,
                expert,
                loadCurrentSkillIds(task.getExpertId()),
                loadCurrentStaticPackageIds(task.getExpertId())
        );
    }

    private List<Long> loadCurrentSkillIds(Long expertId) {
        return expertSkillBindingMapper.selectList(new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                        .eq(ExpertSkillBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertSkillBindingEntity::getSortNo, ExpertSkillBindingEntity::getId))
                .stream()
                .map(ExpertSkillBindingEntity::getSkillId)
                .toList();
    }

    private List<Long> loadCurrentStaticPackageIds(Long expertId) {
        return expertStaticPackageBindingMapper.selectList(new LambdaQueryWrapper<ExpertStaticPackageBindingEntity>()
                        .eq(ExpertStaticPackageBindingEntity::getExpertId, expertId)
                        .orderByAsc(ExpertStaticPackageBindingEntity::getSortNo, ExpertStaticPackageBindingEntity::getId))
                .stream()
                .map(ExpertStaticPackageBindingEntity::getStaticPackageId)
                .toList();
    }

    private boolean hasActiveTrainingTask(Long expertId) {
        Long count = expertTrainingTaskMapper.selectCount(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getExpertId, expertId)
                .in(ExpertTrainingTaskEntity::getStatus, TrainingTaskStatus.activeTaskStatuses()));
        return count != null && count > 0;
    }

    private boolean supportsAbort(String status) {
        return TrainingTaskStatus.PENDING.name().equals(status)
                || TrainingTaskStatus.RUNNING.name().equals(status)
                || TrainingTaskStatus.VERIFYING_ARTIFACTS.name().equals(status);
    }

    private void cleanupTaskOutputSafely(ExpertTrainingTaskEntity task) {
        if (task == null || !StringUtils.hasText(task.getOutputDir())) {
            return;
        }
        try {
            Path trainingRoot = getTrainingOutputRoot().toAbsolutePath().normalize();
            Path outputDirectory = Path.of(task.getOutputDir()).toAbsolutePath().normalize();
            if (!outputDirectory.startsWith(trainingRoot)) {
                log.warn("跳过清理训练输出目录，路径超出训练根目录, taskId={}, outputDir={}",
                        task.getTaskId(), outputDirectory);
                return;
            }
            deleteTrainingOutputDirectory(outputDirectory);
            cleanupEmptySkillRoot(task, outputDirectory, trainingRoot);
            log.info("训练任务收尾清理完成, taskId={}, outputDir={}", task.getTaskId(), outputDirectory);
        } catch (Exception ex) {
            log.warn("训练任务收尾清理失败, taskId={}, outputDir={}",
                    task == null ? null : task.getTaskId(),
                    task == null ? null : task.getOutputDir(),
                    ex);
        }
    }

    private void cleanupEmptySkillRoot(ExpertTrainingTaskEntity task, Path outputDirectory, Path trainingRoot) {
        if (task == null || task.getExpertId() == null) {
            return;
        }
        Long currentSkillCount = expertSkillBindingMapper.selectCount(new LambdaQueryWrapper<ExpertSkillBindingEntity>()
                .eq(ExpertSkillBindingEntity::getExpertId, task.getExpertId()));
        if (currentSkillCount != null && currentSkillCount > 0) {
            return;
        }
        Path skillRootDirectory = resolveSkillRootDirectory(outputDirectory);
        if (!skillRootDirectory.startsWith(trainingRoot) || !Files.exists(skillRootDirectory)) {
            return;
        }
        deleteTrainingOutputDirectory(skillRootDirectory);
        deleteParentDirectoryIfEmpty(skillRootDirectory.getParent(), trainingRoot);
    }

    private void deleteParentDirectoryIfEmpty(Path directory, Path trainingRoot) {
        if (directory == null || trainingRoot == null) {
            return;
        }
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path normalizedTrainingRoot = trainingRoot.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedTrainingRoot)
                || normalizedDirectory.equals(normalizedTrainingRoot)
                || !Files.isDirectory(normalizedDirectory)) {
            return;
        }
        try (var stream = Files.list(normalizedDirectory)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "检查训练父目录是否为空失败: " + normalizedDirectory);
        }
        try {
            Files.deleteIfExists(normalizedDirectory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "删除空训练父目录失败: " + normalizedDirectory);
        }
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

    private void restoreExpertStatusAfterAbort(ExpertTrainingTaskEntity task) {
        DigitalExpertEntity expert = expertConfigService.requireExpert(task.getExpertId());
        String previousStatus = task.getPreviousExpertStatus();
        if (StringUtils.hasText(previousStatus)) {
            expert.setStatus(previousStatus);
        } else if (ExpertStatus.TRAINING.name().equals(expert.getStatus())) {
            expert.setStatus(ExpertStatus.DRAFT.name());
        }
        if (Objects.equals(expert.getLastTrainingTaskId(), task.getTaskId())) {
            expert.setLastTrainingTaskId(null);
        }
        if (StringUtils.hasText(task.getSessionId())
                && Objects.equals(expert.getLastTrainingSessionId(), task.getSessionId())) {
            expert.setLastTrainingSessionId(null);
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
                    "输出目录不能为空");
        }
        return Path.of(outputPath).normalize();
    }

    private Path resolveTrainingOutputDirectory(Path skillRootDirectory,
                                                AppInfoSource appInfoSource,
                                                List<TrainingSourceRequest> sources) {
        String versionDirectoryName = resolveVersionDirectoryName(skillRootDirectory, appInfoSource, sources);
        return skillRootDirectory.resolve(versionDirectoryName).normalize();
    }

    private String resolveVersionDirectoryName(Path skillRootDirectory,
                                               AppInfoSource appInfoSource,
                                               List<TrainingSourceRequest> sources) {
        String requestedVersionDirectoryName = resolveRequestedVersionDirectoryName(sources);
        if (StringUtils.hasText(requestedVersionDirectoryName)) {
            return requestedVersionDirectoryName;
        }
        if (appInfoSource != null && StringUtils.hasText(appInfoSource.serviceVersion())) {
            return appInfoSource.serviceVersion();
        }
        return resolveNextAutoVersionDirectoryName(skillRootDirectory);
    }

    private String resolveRequestedVersionDirectoryName(List<TrainingSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        for (TrainingSourceRequest source : sources) {
            if (source == null || !StringUtils.hasText(source.sourceVersion())) {
                continue;
            }
            String normalized = normalizeVersionDirectoryName(source.sourceVersion());
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String resolveNextAutoVersionDirectoryName(Path skillRootDirectory) {
        if (!Files.exists(skillRootDirectory) || !Files.isDirectory(skillRootDirectory)) {
            return "v1";
        }
        try (var stream = Files.list(skillRootDirectory)) {
            int maxVersion = stream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .filter(java.util.Objects::nonNull)
                    .map(Path::toString)
                    .map(this::extractAutoVersion)
                    .filter(version -> version > 0)
                    .max(Integer::compareTo)
                    .orElse(0);
            return "v" + (maxVersion + 1);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "扫描技能版本目录失败: " + skillRootDirectory);
        }
    }

    private int extractAutoVersion(String directoryName) {
        if (!StringUtils.hasText(directoryName)) {
            return -1;
        }
        Matcher matcher = AUTO_VERSION_PATTERN.matcher(directoryName.trim());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private Path getTrainingOutputRoot() {
        String configuredRoot = properties.getTraining().getOutputRoot();
        if (!StringUtils.hasText(configuredRoot)) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "未配置训练输出根目录");
        }
        return Path.of(configuredRoot).normalize();
    }

    private void ensureSkillRootDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "创建技能根目录失败: " + directory);
        }
    }

    private void recreateTrainingVersionDirectory(Path directory) {
        deleteTrainingOutputDirectory(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "创建训练版本目录失败: " + directory);
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
                    "删除训练输出目录失败: " + path);
        }
    }

    private List<TrainingSourceRequest> normalizeSources(List<TrainingSourceRequest> sources) {
        if (sources == null || sources.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "训练源不能为空");
        }
        List<TrainingSourceRequest> result = new ArrayList<>();
        for (TrainingSourceRequest source : sources) {
            if (source == null) {
                continue;
            }
            String type = source.sourceType() == null ? "" : source.sourceType().trim().toUpperCase(Locale.ROOT);
            String value = source.sourceValue() == null ? "" : source.sourceValue().trim();
            String version = source.sourceVersion() == null ? null : source.sourceVersion().trim();
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
            result.add(new TrainingSourceRequest(type, value, StringUtils.hasText(version) ? version : null));
        }
        if (result.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "训练源不能为空");
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
            String version = item.getString("sourceVersion");
            if (StringUtils.hasText(type) && StringUtils.hasText(value)) {
                result.add(new TrainingSourceRequest(type, value, StringUtils.hasText(version) ? version : null));
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
        ExpertTrainingTaskEntity task = findTask(taskId);
        if (task == null) {
            throw BusinessException.notFound(ErrorCode.TRAINING_TASK_NOT_FOUND,
                    "训练任务不存在: " + taskId);
        }
        return task;
    }

    private ExpertTrainingTaskEntity findTask(String taskId) {
        return expertTrainingTaskMapper.selectOne(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .eq(ExpertTrainingTaskEntity::getTaskId, taskId));
    }

    private String truncateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "未知错误";
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
            String appName,
            String serviceVersion
    ) {
    }
}
