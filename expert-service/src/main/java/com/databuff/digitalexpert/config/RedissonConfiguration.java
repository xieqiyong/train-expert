package com.databuff.digitalexpert.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(ExpertProperties properties, Environment environment) {
        ExpertProperties.Redis redis = resolveRedisProperties(properties.getRedis(), environment);
        String selectedHost = selectRedisHost(redis);
        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress("redis://" + selectedHost + ":" + redis.getPort())
                .setDatabase(redis.getDatabase())
                .setConnectTimeout(Math.toIntExact(redis.getConnectTimeout().toMillis()))
                .setTimeout(Math.toIntExact(redis.getTimeout().toMillis()));
        if (StringUtils.hasText(redis.getPassword())) {
            singleServer.setPassword(redis.getPassword().trim());
        }
        return Redisson.create(config);
    }

    private ExpertProperties.Redis resolveRedisProperties(ExpertProperties.Redis defaults, Environment environment) {
        Binder binder = Binder.get(environment);
        ExpertProperties.Redis resolved = new ExpertProperties.Redis();
        // 优先使用专家独立配置，未显式配置时兼容 Spring 现有 Redis 前缀。
        resolved.setHost(bindFirst(binder, String.class, defaults.getHost(),
                "digital-expert.redis.host",
                "spring.data.redis.host",
                "spring.redis.host"));
        resolved.setPort(bindFirst(binder, Integer.class, defaults.getPort(),
                "digital-expert.redis.port",
                "spring.data.redis.port",
                "spring.redis.port"));
        resolved.setPassword(bindFirst(binder, String.class, defaults.getPassword(),
                "digital-expert.redis.password",
                "spring.data.redis.password",
                "spring.redis.password"));
        resolved.setDatabase(bindFirst(binder, Integer.class, defaults.getDatabase(),
                "digital-expert.redis.database",
                "spring.data.redis.database",
                "spring.redis.database"));
        resolved.setConnectTimeout(bindFirst(binder, Duration.class, defaults.getConnectTimeout(),
                "digital-expert.redis.connect-timeout",
                "spring.data.redis.connect-timeout",
                "spring.redis.connect-timeout",
                "digital-expert.redis.timeout",
                "spring.data.redis.timeout",
                "spring.redis.timeout"));
        resolved.setTimeout(bindFirst(binder, Duration.class, defaults.getTimeout(),
                "digital-expert.redis.timeout",
                "spring.data.redis.timeout",
                "spring.redis.timeout"));
        return resolved;
    }

    private <T> T bindFirst(Binder binder, Class<T> targetType, T defaultValue, String... keys) {
        for (String key : keys) {
            T value = binder.bind(key, Bindable.of(targetType)).orElse(null);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    private String selectRedisHost(ExpertProperties.Redis redis) {
        List<String> hosts = Arrays.stream(redis.getHost().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (hosts.isEmpty()) {
            return redis.getHost();
        }
        if (hosts.size() == 1) {
            return hosts.get(0);
        }
        int connectTimeoutMs = Math.toIntExact(redis.getConnectTimeout().toMillis());
        for (String host : hosts) {
            if (canConnect(host, redis.getPort(), connectTimeoutMs)) {
                log.info("检测到多 Redis 地址，expert-service 当前选择可连节点 {}:{}", host, redis.getPort());
                return host;
            }
        }
        log.warn("检测到多 Redis 地址，但均未在启动时探测成功，expert-service 默认回退第一个节点 {}:{}", hosts.get(0), redis.getPort());
        return hosts.get(0);
    }

    private boolean canConnect(String host, int port, int connectTimeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            return true;
        } catch (Exception ex) {
            log.warn("Redis 节点连通性探测失败 {}:{}，继续尝试其他节点", host, port);
            return false;
        }
    }
}
