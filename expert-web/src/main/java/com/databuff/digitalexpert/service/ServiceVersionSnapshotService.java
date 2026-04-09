package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.entity.ServiceVersionSnapshotEntity;
import java.util.Optional;

public interface ServiceVersionSnapshotService {

    void saveOrUpdate(ServiceVersionSnapshotEntity snapshot);

    Optional<String> findLatestServiceVersionByAppName(String appName);
}
