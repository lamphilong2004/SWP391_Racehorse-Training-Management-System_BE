package com.racehorse.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the common error envelope for failures raised inside the security filter chain (missing/invalid/expired
 * token → 401, role not allowed → 403). Messages are fixed strings and never echo token or exception details.
 */
@Component
public class SecurityErrorWriter {

    static final String UNAUTHORIZED_MESSAGE = "Authentication is required.";
    static final String FORBIDDEN_MESSAGE = "You do not have permission to perform this action.";

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", UNAUTHORIZED_MESSAGE);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", FORBIDDEN_MESSAGE);
    }

    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"details\":[]}}");
    }
}
