package com.databuff.digitalexpert.util;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TaskIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_SEQUENCE = 999;
    private static final String NODE_SUFFIX = initNodeSuffix();

    private static long lastMillis = -1L;
    private static int sequence = 0;

    private TaskIdGenerator() {
    }

    public static String nextReleaseTaskId() {
        return next("release");
    }

    public static synchronized String next(String prefix) {
        long now = System.currentTimeMillis();
        if (now < lastMillis) {
            now = lastMillis;
        }
        if (now == lastMillis) {
            sequence++;
            if (sequence > MAX_SEQUENCE) {
                now = waitNextMillis(now);
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        lastMillis = now;

        String normalizedPrefix = normalizePrefix(prefix);
        String timePart = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                .format(FORMATTER);
        return normalizedPrefix + "_" + timePart + "_" + NODE_SUFFIX + "_" + String.format(Locale.ROOT, "%03d", sequence);
    }

    private static long waitNextMillis(long currentMillis) {
        long now = System.currentTimeMillis();
        while (now <= currentMillis) {
            now = System.currentTimeMillis();
        }
        return now;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "task";
        }
        return prefix.trim().replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private static String initNodeSuffix() {
        String source;
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String runtime = ManagementFactory.getRuntimeMXBean().getName();
            source = host + "-" + runtime;
        } catch (Exception ex) {
            source = String.valueOf(System.nanoTime());
        }
        int hash = Math.abs(source.hashCode());
        return String.format(Locale.ROOT, "%04x", hash & 0xffff);
    }
}
