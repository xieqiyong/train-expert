package com.databuff.digitalexpert.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口
 * 简单独立，不依赖数据库、OpenCode 等外部组件
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
