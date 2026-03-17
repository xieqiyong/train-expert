package com.databuff.digitalexpert.service.proxy;

import java.util.List;

public interface TrainingProxyClient {

    ProxySubmitResult submitTraining(String taskId, String prompt, List<String> filePaths, String outputDir);

    boolean isSessionFinished(String sessionId);

    record ProxySubmitResult(String sessionId, String requestId) {
    }
}
