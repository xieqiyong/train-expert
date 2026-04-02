package com.databuff.digitalexpert.service.proxy.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.config.ExpertProperties;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.proxy.ProxyApiType;
import com.databuff.digitalexpert.service.proxy.TrainingProxyClient;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TrainingProxyClientImpl implements TrainingProxyClient {

    @Autowired
    private ExpertProperties properties;

    /**
     * 提交训练任务
     */
    @Override
    public ProxySubmitResult submitTraining(String taskId,
                                            String prompt,
                                            List<String> filePaths,
                                            String outputDir) {

        Map<String, Object> requestBody = Map.of(
                "userSessionId", taskId,
                "message", prompt,
                "filePaths", filePaths == null ? List.of() : filePaths,
                "imagePaths", List.of(),
                "metadata", Map.of("outputDir", outputDir)
        );
        HttpResponsePayload response = execute(
                ProxyApiType.CHAT,
                taskId,
                null,
                JSON.toJSONString(requestBody)
        );
        JSONObject root = parseJsonResponse(ProxyApiType.CHAT, response);
        // 解析 sessionId
        String sessionId = extractString(root, "sessionId", "session_id", "userSessionId");
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "代理聊天接口返回缺少 sessionId"
            );
        }
        // 解析 requestId
        String requestId = extractString(root, "requestId", "request_id", "id");
        return new ProxySubmitResult(sessionId, requestId);
    }

    /**
     * 判断会话是否完成
     */
    @Override
    public boolean isSessionFinished(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "会话标识不能为空"
            );
        }
        HttpResponsePayload response = execute(
                ProxyApiType.SESSION_FINISHED,
                sessionId,
                sessionId,
                null
        );
        ensureSuccess(ProxyApiType.SESSION_FINISHED, response);
        String body = response.body();
        if (!StringUtils.hasText(body)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "会话状态接口返回为空"
            );
        }
        String trimmed = body.trim();
        // 兼容直接返回 true/false
        if (Boolean.TRUE.toString().equalsIgnoreCase(trimmed) || Boolean.FALSE.toString().equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        throw BusinessException.badRequest(
                ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                "会话状态接口响应调用失败!"
        );
    }

    /**
     * 执行HTTP请求
     */
    @Override
    public void abortConversation(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "会话标识不能为空"
            );
        }
        HttpResponsePayload response = execute(
                ProxyApiType.ABORT_CONVERSATION,
                sessionId,
                sessionId,
                null
        );
        ensureSuccess(ProxyApiType.ABORT_CONVERSATION, response);
    }

    private HttpResponsePayload execute(ProxyApiType apiType, String traceId, String sessionId, String jsonBody) {
        String url = apiType.buildUrl(requireBaseUrl(), sessionId);
        HttpRequest request = HttpUtil.createRequest(apiType.method(), url)
                .method(apiType.method())
                .timeout(properties.getProxy().getTimeoutMs());
        if (jsonBody != null) {
            request.body(jsonBody, ContentType.JSON.getValue());
        }
        try (HttpResponse response = request.execute()) {
            return new HttpResponsePayload(response.getStatus(), response.body());
        } catch (HttpException ex) {
            log.error("调用{}失败, traceId={}，", apiType.apiName(), traceId, ex);
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "调用" + apiType.apiName() + "失败: " + ex.getMessage()
            );
        }
    }

    /**
     * 解析JSON响应
     */
    private JSONObject parseJsonResponse(ProxyApiType apiType, HttpResponsePayload response) {
        ensureSuccess(apiType, response);
        String body = response.body();
        if (!StringUtils.hasText(body)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    apiType.apiName() + "返回为空"
            );
        }
        try {
            JSONObject root = JSON.parseObject(body);
            if (root == null) {
                throw new IllegalArgumentException("JSON为空");
            }
            return root;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    apiType.apiName() + "返回JSON格式非法"
            );
        }
    }

    /**
     * 校验HTTP状态码
     */
    private void ensureSuccess(ProxyApiType apiType, HttpResponsePayload response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw BusinessException.badRequest(
                ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                apiType.apiName() + "调用失败，状态码: " + response.statusCode()
        );
    }

    /**
     * 从JSON中提取字符串（支持多key）
     */
    private String extractString(JSONObject root, String... keys) {

        for (String key : keys) {
            String value = root.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        JSONObject data = root.getJSONObject("data");
        if (data != null) {
            for (String key : keys) {
                String value = data.getString(key);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * 获取基础URL
     */
    private String requireBaseUrl() {
        String baseUrl = properties.getProxy().getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "未配置代理服务地址"
            );
        }
        return baseUrl;
    }

    /**
     * HTTP响应封装
     */
    private record HttpResponsePayload(int statusCode, String body) {
    }
}
