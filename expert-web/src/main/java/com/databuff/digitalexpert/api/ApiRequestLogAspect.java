package com.databuff.digitalexpert.api;

import com.alibaba.fastjson2.JSON;
import com.databuff.digitalexpert.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLogAspect {

    private static final int MAX_TEXT_LENGTH = 1200;

    @Around("within(com.databuff.digitalexpert.api..*) && @within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        HttpServletResponse response = attributes == null ? null : attributes.getResponse();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String requestId = buildRequestId();
        long startTime = System.currentTimeMillis();

        log.info("""
                +---------------- API Request ----------------
                | id={}
                | handler={}
                | method={}
                | uri={}
                | clientIp={}
                | contentType={}
                | args={}
                +---------------------------------------------""",
                requestId,
                signature.getDeclaringType().getSimpleName() + "." + signature.getName(),
                request == null ? "N/A" : request.getMethod(),
                request == null ? "N/A" : buildRequestUri(request),
                request == null ? "N/A" : resolveClientIp(request),
                request == null ? "N/A" : defaultText(request.getContentType()),
                buildArguments(signature, joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            log.info("""
                    +---------------- API Response ---------------
                    | id={}
                    | status={}
                    | cost={}ms
                    | result={}
                    +---------------------------------------------""",
                    requestId,
                    response == null ? "N/A" : response.getStatus(),
                    System.currentTimeMillis() - startTime,
                    summarizeResult(result));
            return result;
        } catch (Throwable ex) {
            String message = ex.getMessage();
            if (!StringUtils.hasText(message)) {
                message = ex.getClass().getSimpleName();
            }
            String summary = limitText(message);
            if (ex instanceof BusinessException) {
                log.warn("""
                        +---------------- API Error ------------------
                        | id={}
                        | status={}
                        | cost={}ms
                        | errorType={}
                        | message={}
                        +---------------------------------------------""",
                        requestId,
                        response == null ? "N/A" : response.getStatus(),
                        System.currentTimeMillis() - startTime,
                        ex.getClass().getSimpleName(),
                        summary);
            } else {
                log.error("""
                        +---------------- API Error ------------------
                        | id={}
                        | status={}
                        | cost={}ms
                        | errorType={}
                        | message={}
                        +---------------------------------------------""",
                        requestId,
                        response == null ? "N/A" : response.getStatus(),
                        System.currentTimeMillis() - startTime,
                        ex.getClass().getSimpleName(),
                        summary,
                        ex);
            }
            throw ex;
        }
    }

    private String buildRequestUri(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String buildArguments(MethodSignature signature, Object[] args) {
        String[] parameterNames = signature.getParameterNames();
        if (args == null || args.length == 0) {
            return "[]";
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (shouldSkip(arg)) {
                continue;
            }
            String parameterName = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            values.add(parameterName + "=" + summarizeObject(arg));
        }
        return values.isEmpty() ? "[]" : limitText(values.toString());
    }

    private boolean shouldSkip(Object arg) {
        return arg == null
                || arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof BindingResult;
    }

    private String summarizeObject(Object value) {
        if (value instanceof MultipartFile file) {
            return summarizeMultipartFile(file);
        }
        if (value instanceof MultipartFile[] files) {
            List<String> parts = new ArrayList<>();
            for (MultipartFile file : files) {
                parts.add(summarizeMultipartFile(file));
            }
            return parts.toString();
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object item : collection) {
                parts.add(item instanceof MultipartFile file ? summarizeMultipartFile(file) : simpleText(item));
            }
            return limitText(parts.toString());
        }
        if (value instanceof byte[] bytes) {
            return "<bytes:" + bytes.length + ">";
        }
        if (value instanceof Resource resource) {
            return "Resource(type=" + resource.getClass().getSimpleName() + ", filename=" + defaultText(resource.getFilename()) + ")";
        }
        return simpleText(value);
    }

    private String summarizeMultipartFile(MultipartFile file) {
        return "MultipartFile(name=" + defaultText(file.getName())
                + ", originalFilename=" + defaultText(file.getOriginalFilename())
                + ", size=" + file.getSize()
                + ", contentType=" + defaultText(file.getContentType()) + ")";
    }

    private String summarizeResult(Object result) {
        if (result == null) {
            return "null";
        }
        return simpleText(result);
    }

    private String simpleText(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return limitText(String.valueOf(value));
        }
        try {
            return limitText(JSON.toJSONString(value));
        } catch (Exception ex) {
            return limitText(value.getClass().getSimpleName());
        }
    }

    private String buildRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String limitText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
