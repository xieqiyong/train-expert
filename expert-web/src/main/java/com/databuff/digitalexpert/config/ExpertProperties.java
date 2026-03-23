package com.databuff.digitalexpert.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Paths;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "digital-expert")
public class ExpertProperties {

    /**
     * 共享存储根目录。
     * 主要用于技能包、静态资源包、专家发布产物等共享文件。
     */
    private String staticPackage;

    /**
     * 发布相关配置。
     */
    private Release release = new Release();

    /**
     * 训练相关配置。
     */
    private Training training = new Training();

    /**
     * 代理服务调用配置。
     */
    private Proxy proxy = new Proxy();

    @Getter
    @Setter
    public static class Release {

        /**
         * 发布任务线程池大小。
         */
        @Min(1)
        private int executorPoolSize = 2;

        /**
         * 发布任务线程池队列容量。
         */
        @Min(1)
        private int executorQueueCapacity = 50;
    }

    @Getter
    @Setter
    public static class Training {

        /**
         * 训练输出根目录。
         * 生成的 skill 目录会落在该目录下。
         */
        @NotBlank
        private String outputRoot = Paths.get(System.getProperty("user.dir"), "data", "training-output")
                .toAbsolutePath()
                .normalize()
                .toString();

        /**
         * 训练时提供给代理的规范 skill 路径。
         * 该路径必须是代理服务自身可访问的真实目录。
         */
        private String specSkillPath;

        /**
         * 训练提交线程池大小。
         */
        @Min(1)
        private int submitExecutorPoolSize = 5;

        /**
         * 训练提交线程池队列容量。
         */
        @Min(1)
        private int submitExecutorQueueCapacity = 100;

        /**
         * 训练轮询线程池大小。
         */
        @Min(1)
        private int pollExecutorPoolSize = 4;

        /**
         * 训练轮询线程池队列容量。
         */
        @Min(1)
        private int pollExecutorQueueCapacity = 200;

        /**
         * 训练任务轮询间隔，单位毫秒。
         */
        @Min(1000)
        private int pollIntervalMs = 5000;

        /**
         * 训练会话最大等待时间，单位毫秒。
         * 提交 chat 后，轮询会话结束接口的最长等待时间。
         */
        @Min(1000)
        private int sessionTimeoutMs = 30 * 60 * 1000;

        /**
         * 会话结束后的产物缓冲时间，单位毫秒。
         * 在这段时间内只等待产物落盘，不做后续导入。
         */
        @Min(1000)
        private int artifactGracePeriodMs = 2 * 60 * 1000;

        /**
         * 发布阶段最大等待时间，单位毫秒。
         */
        @Min(1000)
        private int releaseTimeoutMs = 30 * 60 * 1000;
    }

    @Getter
    @Setter
    public static class Proxy {

        /**
         * 代理服务基础地址。
         */
        @NotBlank
        private String baseUrl = "http://192.168.50.18:19300";

        /**
         * 代理服务单次 HTTP 请求超时时间，单位毫秒。
         */
        @Min(1000)
        private int timeoutMs = 15000;
    }
}
