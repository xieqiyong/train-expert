package com.databuff.digitalexpert.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertTrainingTaskEntity;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.dao.mapper.ExpertTrainingTaskMapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AppUploadGuardService {

    private static final String KEY_PREFIX = "digital-expert:upload-train:app:";
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private ExpertTrainingTaskMapper expertTrainingTaskMapper;

    public String tryAcquireUploadLock(String appName) {
        String normalizedAppName = normalizeAppName(appName);
        String lockToken = UUID.randomUUID().toString();
        Duration lockTtl = Duration.ofMinutes(30);
        RBucket<String> bucket = redissonClient.getBucket(buildLockKey(normalizedAppName));
        boolean locked = bucket.trySet(lockToken, lockTtl.toMillis(), TimeUnit.MILLISECONDS);
        return locked ? lockToken : null;
    }

    public void releaseUploadLock(String appName, String lockToken) {
        String normalizedAppName = normalizeAppName(appName);
        if (!StringUtils.hasText(lockToken)) {
            return;
        }
        try {
            redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    RELEASE_LOCK_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    List.of(buildLockKey(normalizedAppName)),
                    lockToken
            );
        } catch (Exception ex) {
            log.warn("释放应用上传锁失败, appName={}", normalizedAppName, ex);
        }
    }

    public boolean hasActiveTrainingTask(String appName) {
        String normalizedAppName = normalizeAppName(appName);
        List<DigitalExpertEntity> experts = digitalExpertMapper.selectList(new LambdaQueryWrapper<DigitalExpertEntity>()
                .nested(wrapper -> wrapper
                        .eq(DigitalExpertEntity::getName, normalizedAppName)
                        .or()
                        .eq(DigitalExpertEntity::getAliasName, normalizedAppName)));
        List<Long> expertIds = experts.stream()
                .map(DigitalExpertEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (expertIds.isEmpty()) {
            return false;
        }
        Long activeTaskCount = expertTrainingTaskMapper.selectCount(new LambdaQueryWrapper<ExpertTrainingTaskEntity>()
                .in(ExpertTrainingTaskEntity::getExpertId, expertIds)
                .in(ExpertTrainingTaskEntity::getStatus, TrainingTaskStatus.activeTaskStatuses()));
        return activeTaskCount != null && activeTaskCount > 0;
    }

    private String buildLockKey(String appName) {
        return KEY_PREFIX + appName.toLowerCase();
    }

    private String normalizeAppName(String appName) {
        return StringUtils.hasText(appName) ? appName.trim() : "";
    }
}
