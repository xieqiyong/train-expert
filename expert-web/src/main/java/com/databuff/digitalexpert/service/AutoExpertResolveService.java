package com.databuff.digitalexpert.service;

public interface AutoExpertResolveService {

    ResolvedExpert resolveOrCreateByServiceName(String serviceName, String rawServiceName);

    record ResolvedExpert(Long expertId, String expertName, boolean created) {
    }
}
