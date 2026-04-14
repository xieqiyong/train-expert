package com.databuff.digitalexpert.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SpaRouteController {

    @GetMapping({"/{path:[^\\.]*}", "/**/{path:[^\\.]*}"})
    public String forwardToIndex(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (isExcludedPath(requestUri)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }

    private boolean isExcludedPath(String requestUri) {
        return "/api".equals(requestUri)
                || requestUri.startsWith("/api/")
                || "/health".equals(requestUri)
                || "/assets".equals(requestUri)
                || requestUri.startsWith("/assets/")
                || "/webjars".equals(requestUri)
                || requestUri.startsWith("/webjars/");
    }
}
