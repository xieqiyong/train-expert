package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.entity.ServiceVersionSnapshotEntity;
import com.databuff.digitalexpert.dao.mapper.ServiceVersionSnapshotMapper;
import com.databuff.digitalexpert.service.ServiceVersionSnapshotService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ServiceVersionSnapshotServiceImpl implements ServiceVersionSnapshotService {

    @Autowired
    private ServiceVersionSnapshotMapper serviceVersionSnapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(ServiceVersionSnapshotEntity snapshot) {
        ServiceVersionSnapshotEntity existing = serviceVersionSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ServiceVersionSnapshotEntity>()
                        .eq(ServiceVersionSnapshotEntity::getClusterId, snapshot.getClusterId())
                        .eq(ServiceVersionSnapshotEntity::getNamespace, snapshot.getNamespace())
                        .eq(ServiceVersionSnapshotEntity::getPodName, snapshot.getPodName())
                        .eq(ServiceVersionSnapshotEntity::getContainerName, snapshot.getContainerName())
                        .last("limit 1")
        );
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            snapshot.setCreatedAt(now);
            snapshot.setUpdatedAt(now);
            serviceVersionSnapshotMapper.insert(snapshot);
            return;
        }
        existing.setAppName(snapshot.getAppName());
        existing.setClusterName(snapshot.getClusterName());
        existing.setWorkloadName(snapshot.getWorkloadName());
        existing.setImageName(snapshot.getImageName());
        existing.setServiceVersion(snapshot.getServiceVersion());
        existing.setStatus(snapshot.getStatus());
        existing.setSourceTopic(snapshot.getSourceTopic());
        existing.setMessageOffset(snapshot.getMessageOffset());
        existing.setRawPayload(snapshot.getRawPayload());
        existing.setLastSeenAt(snapshot.getLastSeenAt());
        existing.setUpdatedAt(now);
        serviceVersionSnapshotMapper.updateById(existing);
    }

    @Override
    public Optional<String> findLatestServiceVersionByAppName(String appName) {
        if (!StringUtils.hasText(appName)) {
            return Optional.empty();
        }
        ServiceVersionSnapshotEntity snapshot = serviceVersionSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ServiceVersionSnapshotEntity>()
                        .eq(ServiceVersionSnapshotEntity::getAppName, appName.trim())
                        .orderByDesc(ServiceVersionSnapshotEntity::getLastSeenAt)
                        .orderByDesc(ServiceVersionSnapshotEntity::getId)
                        .last("limit 1")
        );
        if (snapshot == null || !StringUtils.hasText(snapshot.getServiceVersion())) {
            return Optional.empty();
        }
        return Optional.of(snapshot.getServiceVersion());
    }
}
