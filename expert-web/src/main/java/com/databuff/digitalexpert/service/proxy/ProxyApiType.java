package com.databuff.digitalexpert.service.proxy;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Method;

/**
 * 代理接口类型定义
 */
public enum ProxyApiType {
    CHAT("代理聊天接口", Method.POST, "/chat"),
    SESSION_FINISHED("会话状态接口", Method.GET, "/session/{sessionId}/finished"),
    ABORT_CONVERSATION("会话中止接口", Method.POST, "/session/{sessionId}/abort");

    /**
     * 接口名称（用于日志/异常）
     */
    private final String apiName;

    /**
     * HTTP方法
     */
    private final Method method;

    /**
     * 路径模板（支持占位符）
     */
    private final String pathTemplate;

    ProxyApiType(String apiName, Method method, String pathTemplate) {
        this.apiName = apiName;
        this.method = method;
        this.pathTemplate = pathTemplate;
    }

    public String apiName() {
        return apiName;
    }

    public Method method() {
        return method;
    }

    /**
     * 构建完整请求URL
     */
    public String buildUrl(String baseUrl, String sessionId) {
        // 1. 规范 baseUrl（去空格、去末尾 /）
        String normalizedBaseUrl = StrUtil.removeSuffix(StrUtil.trim(baseUrl), "/");
        // 2. 替换路径参数
        String resolvedPath = pathTemplate;
        if (StrUtil.contains(resolvedPath, "{sessionId}")) {
            if (StrUtil.isBlank(sessionId)) {
                throw new IllegalArgumentException("sessionId 不能为空");
            }
            resolvedPath = StrUtil.replace(resolvedPath, "{sessionId}", sessionId);
        }
        // 3. 如果本身就是完整URL，直接返回（支持特殊场景）
        if (StrUtil.startWithAny(resolvedPath, "http://", "https://")) {
            return resolvedPath;
        }
        // 4. 拼接完整URL
        if (!resolvedPath.startsWith("/")) {
            resolvedPath = "/" + resolvedPath;
        }
        return normalizedBaseUrl + resolvedPath;
    }
}