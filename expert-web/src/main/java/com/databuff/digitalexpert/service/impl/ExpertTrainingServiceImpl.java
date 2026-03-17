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
import com.databuff.digitalexpert.util.AgentSessionId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
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

        List<TrainingSourceRequest> normalizedSources = normalizeSources(request.sources());
        String taskId = AgentSessionId.generate();
        Path outputDirectory = sharedStorageService.resolveExpertTrainingOutputDirectory(expertId, taskId);

        LocalDateTime now = LocalDateTime.now();
        ExpertTrainingTaskEntity task = new ExpertTrainingTaskEntity();
        task.setTaskId(taskId);
        task.setExpertId(expertId);
        task.setStatus(TrainingTaskStatus.PENDING.name());
        task.setActiveTaskKey(String.valueOf(expertId));
        task.setPreviousExpertStatus(expert.getStatus());
        task.setSourceManifestJson(JSON.toJSONString(normalizedSources));
        task.setOutputDir(sharedStorageService.toStoragePath(outputDirectory));
        task.setManifestPath(sharedStorageService.toStoragePath(outputDirectory.resolve("manifest.json")));
        task.setPollCount(0);
        task.setArtifactVerified(0);
        task.setRequestedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            expertTrainingTaskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict(ErrorCode.ACTIVE_TRAINING_TASK_EXISTS,
                    "专家存在进行中的训练任务: " + expertId);
        }
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
            sharedStorageService.recreateDirectory(outputDir);

            List<TrainingSourceRequest> sources = parseSources(task.getSourceManifestJson());
            String prompt = buildPrompt(task, trainingGoal, sources);
            TrainingProxyClient.ProxySubmitResult submitResult = trainingProxyClient.submitTraining(
                    taskId,
                    prompt,
                    sources.stream().map(TrainingSourceRequest::sourceValue).toList(),
                    outputDir.toString()
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
        if (TrainingTaskStatus.RELEASING.name().equals(task.getStatus())) {
            handleReleasingTask(task);
        }
    }

    private void handleRunningTask(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        if (pollCount > properties.getTraining().getMaxPollCount()) {
            markTaskTimeout(task.getTaskId(), "训练会话轮询超时");
            return;
        }
        if (!StringUtils.hasText(task.getSessionId())) {
            return;
        }
        if (!trainingProxyClient.isSessionFinished(task.getSessionId())) {
            return;
        }

        task = requireTask(task.getTaskId());
        task.setStatus(TrainingTaskStatus.VERIFYING_ARTIFACTS.name());
        task.setUpdatedAt(LocalDateTime.now());
        expertTrainingTaskMapper.updateById(task);

        try {
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
            task.setUpdatedAt(LocalDateTime.now());

            ExpertReleaseTaskResponse releaseTask = expertReleaseService.submitReleaseTask(task.getExpertId());
            task.setReleaseTaskId(releaseTask.taskId());
            expertTrainingTaskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("训练产物校验或入库失败, taskId={}", task.getTaskId(), ex);
            markTaskFailed(task.getTaskId(), ex.getMessage());
        }
    }

    private void handleReleasingTask(ExpertTrainingTaskEntity task) {
        int pollCount = increasePollCount(task);
        if (pollCount > properties.getTraining().getMaxPollCount()) {
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
            markTaskSucceeded(task.getTaskId());
            return;
        }
        if (ReleaseTaskStatus.FAILED.name().equals(releaseTask.getStatus())) {
            markTaskFailed(task.getTaskId(), releaseTask.getFailureReason());
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
        Path manifestPath = Path.of(task.getManifestPath()).toAbsolutePath().normalize();
        if (Files.exists(manifestPath) && Files.isRegularFile(manifestPath)) {
            directories.addAll(loadSkillDirectoriesFromManifest(manifestPath, outputDir));
        }

        if (directories.isEmpty()) {
            try (var children = Files.list(outputDir)) {
                children.filter(Files::isDirectory)
                        .filter(path -> Files.exists(path.resolve("SKILL.md")))
                        .forEach(path -> directories.add(path.toAbsolutePath().normalize()));
            } catch (IOException ex) {
                throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                        "扫描训练输出目录失败: " + outputDir);
            }
        }

        Path rootSkillMd = outputDir.resolve("SKILL.md");
        if (directories.isEmpty() && Files.isRegularFile(rootSkillMd)) {
            directories.add(outputDir);
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

    private List<Path> loadSkillDirectoriesFromManifest(Path manifestPath, Path outputDir) {
        try {
            String text = Files.readString(manifestPath, StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(text);
            if (root == null) {
                return List.of();
            }
            JSONArray skills = root.getJSONArray("skills");
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
                            "manifest 中的技能路径超出输出目录: " + normalized);
                }
                results.add(normalized);
            }
            return results;
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "读取 manifest 文件失败: " + manifestPath);
        }
    }

    private String buildPrompt(ExpertTrainingTaskEntity task,
                               String trainingGoal,
                               List<TrainingSourceRequest> sources) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是数字员工训练代理，必须调用 skill-creator 生成 skills。\n");
        if (StringUtils.hasText(trainingGoal)) {
            builder.append("训练目标: ").append(trainingGoal.trim()).append("\n");
        }
        builder.append("输入源列表:\n");
        for (int i = 0; i < sources.size(); i++) {
            TrainingSourceRequest source = sources.get(i);
            builder.append(i + 1)
                    .append(". [")
                    .append(source.sourceType())
                    .append("] ")
                    .append(source.sourceValue())
                    .append("\n");
        }

        builder.append("输出要求:\n")
                .append("1. 只能在 output_dir 中产出内容，禁止写入其他目录。\n")
                .append("2. 每个 skill 必须是独立目录，且包含 SKILL.md。\n")
                .append("3. SKILL.md 必须包含 name 和 description 字段。\n")
                .append("4. 生成 manifest.json，格式: {\"skills\":[{\"path\":\"<相对或绝对路径>\",\"name\":\"...\",\"description\":\"...\"}]}。\n")
                .append("5. 无法产出有效 skill 时，请给出失败原因。\n")
                .append("output_dir: ")
                .append(task.getOutputDir())
                .append("\n");
        return builder.toString();
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
        expert.setStatus(ExpertStatus.TRAINING.name());
        expert.setLastTrainingTaskId(taskId);
        expert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(expert);
    }

    private void markTaskSucceeded(String taskId) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.SUCCEEDED.name());
        task.setActiveTaskKey(null);
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
    }

    private void markTaskFailed(String taskId, String reason) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.FAILED.name());
        task.setActiveTaskKey(null);
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        restoreExpertStatus(task);
    }

    private void markTaskTimeout(String taskId, String reason) {
        ExpertTrainingTaskEntity task = requireTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TrainingTaskStatus.TIMEOUT.name());
        task.setActiveTaskKey(null);
        task.setFailureReason(truncateReason(reason));
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        expertTrainingTaskMapper.updateById(task);
        restoreExpertStatus(task);
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
        task.setUpdatedAt(LocalDateTime.now());
        expertTrainingTaskMapper.updateById(task);
        return pollCount;
    }

    private Path normalizeAndCheckOutputPath(String outputPath) {
        if (!StringUtils.hasText(outputPath)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "输出目录为空");
        }
        Path normalized = Path.of(outputPath).toAbsolutePath().normalize();
        Path root = sharedStorageService.getSharedRoot();
        if (!normalized.startsWith(root)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID,
                    "输出目录超出共享根目录: " + normalized);
        }
        return normalized;
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
}