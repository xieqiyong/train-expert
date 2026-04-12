package com.databuff.digitalexpert.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(ExpertProperties properties) {
        ExpertProperties.Redis redis = properties.getRedis();
        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getPort())
                .setDatabase(redis.getDatabase())
                .setConnectTimeout(Math.toIntExact(redis.getConnectTimeout().toMillis()))
                .setTimeout(Math.toIntExact(redis.getTimeout().toMillis()));
        if (StringUtils.hasText(redis.getPassword())) {
            singleServer.setPassword(redis.getPassword().trim());
        }
        return Redisson.create(config);
    }
}
