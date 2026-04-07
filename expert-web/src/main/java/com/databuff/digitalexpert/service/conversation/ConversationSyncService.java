package com.databuff.digitalexpert.service.conversation;

import com.alibaba.fastjson2.JSONObject;
import com.databuff.digitalexpert.config.ExpertProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationSyncService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSyncService.class);
    private static final int IDLE_STABLE_ROUNDS = 2;
    private static final int IDLE_CONFIRM_ATTEMPTS = 3;
    private static final int SNAPSHOT_WRITE_MAX_ATTEMPTS = 3;

    private final ConversationOpencodeClient conversationOpencodeClient;
    private final ConversationStorageService conversationStorageService;
    private final ExpertProperties expertProperties;
    private final Executor conversationSyncExecutor;
    private final Set<String> runningSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public ConversationSyncService(
            ConversationOpencodeClient conversationOpencodeClient,
            ConversationStorageService conversationStorageService,
            ExpertProperties expertProperties,
            @Qualifier("conversationSyncExecutor") Executor conversationSyncExecutor
    ) {
        this.conversationOpencodeClient = conversationOpencodeClient;
        this.conversationStorageService = conversationStorageService;
        this.expertProperties = expertProperties;
        this.conversationSyncExecutor = conversationSyncExecutor;
    }

    public void scheduleSync(String sessionId, String directory, String workspace) {
        ConversationSessionContext context = conversationStorageService.resolveContext(sessionId, directory, workspace);
        if (!runningSessions.add(sessionId)) {
            return;
        }
        conversationSyncExecutor.execute(() -> {
            try {
                syncLoop(context);
            } finally {
                runningSessions.remove(sessionId);
            }
        });
    }

    public void syncOnce(String sessionId, String directory, String workspace) {
        ConversationSessionContext context = conversationStorageService.resolveContext(sessionId, directory, workspace);
        withSessionLock(context.sessionId(), () -> {
            syncOnce(context);
            return null;
        });
    }

    public boolean syncUntilSettled(String sessionId, String directory, String workspace) {
        ConversationSessionContext context = conversationStorageService.resolveContext(sessionId, directory, workspace);
        return withSessionLock(context.sessionId(), () -> syncUntilSettled(context));
    }

    private void syncLoop(ConversationSessionContext context) {
        ExpertProperties.Conversation conversation = expertProperties.getConversation();
        sleep(conversation.getBootstrapDelayMs());
        long deadline = System.currentTimeMillis() + conversation.getSyncTimeoutMs();
        while (true) {
            if (withSessionLock(context.sessionId(), () -> syncUntilSettled(context))) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                withSessionLock(context.sessionId(), () -> {
                    conversationStorageService.markStatus(
                        context.sessionId(),
                        ConversationStorageService.STATUS_FAILED,
                        "会话同步超时"
                    );
                    return null;
                });
                return;
            }
            sleep(conversation.getPollIntervalMs());
        }
    }

    private void syncOnce(ConversationSessionContext context) {
        SnapshotResult snapshot = fetchSnapshot(context);
        if (snapshot == null) {
            return;
        }
        String localStatus = ConversationStorageService.STATUS_IDLE.equals(snapshot.status())
                ? ConversationStorageService.STATUS_RUNNING
                : snapshot.status();
        persistSnapshot(context, localStatus, snapshot.rawMessages());
    }

    private boolean syncUntilSettled(ConversationSessionContext context) {
        String lastIdleFingerprint = null;
        int idleRounds = 0;
        for (int attempt = 0; attempt < IDLE_CONFIRM_ATTEMPTS; attempt++) {
            SnapshotResult snapshot = fetchSnapshot(context);
            if (snapshot == null) {
                return ConversationStorageService.STATUS_FAILED.equals(
                        conversationStorageService.getSessionStatus(context.sessionId())
                );
            }
            if (ConversationStorageService.STATUS_IDLE.equals(snapshot.status())) {
                if (!hasAssistantProgress(snapshot.rawMessages())) {
                    persistSnapshot(context, ConversationStorageService.STATUS_RUNNING, snapshot.rawMessages());
                    return false;
                }
                if (Objects.equals(snapshot.fingerprint(), lastIdleFingerprint)) {
                    idleRounds++;
                } else {
                    lastIdleFingerprint = snapshot.fingerprint();
                    idleRounds = 1;
                }
                if (idleRounds >= IDLE_STABLE_ROUNDS) {
                    persistSnapshot(context, ConversationStorageService.STATUS_IDLE, snapshot.rawMessages());
                    return true;
                }
                persistSnapshot(context, ConversationStorageService.STATUS_RUNNING, snapshot.rawMessages());
            } else {
                persistSnapshot(context, snapshot.status(), snapshot.rawMessages());
                return isTerminal(snapshot.status());
            }
            if (attempt + 1 < IDLE_CONFIRM_ATTEMPTS) {
                sleep(expertProperties.getConversation().getPollIntervalMs());
            }
        }
        return false;
    }

    private SnapshotResult fetchSnapshot(ConversationSessionContext context) {
        try {
            String status = detectStatus(context);
            List<Map<String, Object>> rawMessages = conversationOpencodeClient.listMessages(
                    context.sessionId(),
                    null,
                    context.directory(),
                    context.workspace()
            );
            return new SnapshotResult(status, rawMessages, buildFingerprint(rawMessages));
        } catch (Exception ex) {
            String message = ex.getMessage();
            log.warn("conversation snapshot sync failed, sessionId={}, message={}", context.sessionId(), message);
            withSessionLock(context.sessionId(), () -> {
                conversationStorageService.markStatus(
                        context.sessionId(),
                        ConversationStorageService.STATUS_FAILED,
                        message
                );
                return null;
            });
            return null;
        }
    }

    private void persistSnapshot(ConversationSessionContext context, String status, List<Map<String, Object>> rawMessages) {
        for (int attempt = 1; attempt <= SNAPSHOT_WRITE_MAX_ATTEMPTS; attempt++) {
            try {
                conversationStorageService.replaceSnapshot(context.sessionId(), status, rawMessages);
                return;
            } catch (Exception ex) {
                if (isRetryableWriteConflict(ex) && attempt < SNAPSHOT_WRITE_MAX_ATTEMPTS) {
                    log.warn(
                            "conversation snapshot persist conflicted, retrying. sessionId={}, attempt={}, message={}",
                            context.sessionId(),
                            attempt,
                            ex.getMessage()
                    );
                    sleep(100L * attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    private String detectStatus(ConversationSessionContext context) {
        String storedStatus = conversationStorageService.getSessionStatus(context.sessionId());
        if (ConversationStorageService.STATUS_ABORTED.equals(storedStatus)) {
            return ConversationStorageService.STATUS_ABORTED;
        }
        try {
            Map<String, Object> status = conversationOpencodeClient.getSessionStatus(
                    context.sessionId(),
                    context.directory(),
                    context.workspace()
            );
            if (status == null || status.isEmpty()) {
                conversationOpencodeClient.ensureSessionExists(context.sessionId(), context.directory(), context.workspace());
                return ConversationStorageService.STATUS_IDLE;
            }
            String type = stringValue(status.get("type"));
            if ("idle".equalsIgnoreCase(type)) {
                return ConversationStorageService.STATUS_IDLE;
            }
            return ConversationStorageService.STATUS_RUNNING;
        } catch (Exception ex) {
            log.warn("conversation status check failed, sessionId={}, message={}", context.sessionId(), ex.getMessage());
            return StringUtils.hasText(storedStatus) ? storedStatus : ConversationStorageService.STATUS_RUNNING;
        }
    }

    private boolean isTerminal(String status) {
        return ConversationStorageService.STATUS_IDLE.equals(status)
                || ConversationStorageService.STATUS_ABORTED.equals(status)
                || ConversationStorageService.STATUS_FAILED.equals(status);
    }

    private String buildFingerprint(List<Map<String, Object>> rawMessages) {
        return JSONObject.toJSONString(rawMessages == null ? List.of() : rawMessages);
    }

    private boolean hasAssistantProgress(List<Map<String, Object>> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return false;
        }
        for (Map<String, Object> message : rawMessages) {
            if (message == null) {
                continue;
            }
            Object infoObject = message.get("info");
            if (infoObject instanceof Map<?, ?> infoMap) {
                Object role = infoMap.get("role");
                if (role != null && !"user".equalsIgnoreCase(String.valueOf(role))) {
                    return true;
                }
            }
        }
        return rawMessages.size() > 1;
    }

    private boolean isRetryableWriteConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (
                    message.contains("Deadlock found when trying to get lock")
                            || message.contains("Lock wait timeout exceeded")
                            || message.contains("try restarting transaction")
            )) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T withSessionLock(String sessionId, Supplier<T> action) {
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record SnapshotResult(String status, List<Map<String, Object>> rawMessages, String fingerprint) {
    }
}
