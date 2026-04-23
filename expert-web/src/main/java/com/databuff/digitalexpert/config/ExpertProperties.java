package com.databuff.digitalexpert.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    private String attachmentPackage;

    /**
     * 训练相关配置。
     */
    private Training training = new Training();
    private Redis redis = new Redis();
    private Conversation conversation = new Conversation();
    private Kafka kafka = new Kafka();

    /**
     * 代理服务调用配置。
     */
    private Proxy proxy = new Proxy();

    /**
     * 跨域配置。
     */
    private Cors cors = new Cors();

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
        private int submitExecutorPoolSize = 50;

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
        private Duration sessionTimeout = Duration.ofMinutes(60);

        /**
         * 会话结束后的产物缓冲时间，单位毫秒。
         * 在这段时间内只等待产物落盘，不做后续导入。
         */
        private Duration artifactGracePeriod = Duration.ofMinutes(2);

        /**
         * 发布阶段最大等待时间，单位毫秒。
         */
        private Duration releaseTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class Redis {

        @NotBlank
        private String host = "redis";

        @Min(1)
        private int port = 6379;

        private String password;

        @Min(0)
        private int database = 0;

        private Duration connectTimeout = Duration.ofSeconds(5);

        private Duration timeout = Duration.ofSeconds(3);
    }

    @Getter
    @Setter
    public static class Conversation {

        @Min(0)
        private int bootstrapDelayMs = 300;

        @Min(200)
        private int pollIntervalMs = 1000;

        @Min(1000)
        private int syncTimeoutMs = 15 * 60 * 1000;
    }

    @Getter
    @Setter
    public static class Kafka {

        private boolean enabled = false;

        private Topics topics = new Topics();
    }

    @Getter
    @Setter
    public static class Topics {

        /**
         * K8s 涓婃姤涓婚锛岀敤浜庡悗缁В鏋?serviceVersion銆?         */
        @NotBlank
        private String dcDatabuffK8s = "dc_databuff_k8s";
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

    @Getter
    @Setter
    public static class Cors {

        /**
         * 是否启用全局跨域。
         */
        private boolean enabled = true;

        /**
         * 跨域生效的路径匹配规则。
         */
        @NotBlank
        private String pathPattern = "/api/**";

        /**
         * 允许的来源匹配列表。
         */
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

        /**
         * 允许的请求方法。
         */
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        /**
         * 允许的请求头。
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * 允许前端读取的响应头。
         */
        private List<String> exposedHeaders = new ArrayList<>(List.of("Content-Disposition"));

        /**
         * 是否允许携带凭证。
         */
        private boolean allowCredentials = false;

        /**
         * 预检请求缓存时间，单位秒。
         */
        @Min(0)
        private long maxAgeSeconds = 3600;
    }
}
