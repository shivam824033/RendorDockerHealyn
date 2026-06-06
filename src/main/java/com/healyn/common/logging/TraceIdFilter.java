package com.healyn.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(TraceContext.HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String traceId = UUID.randomUUID().toString();
        MDC.put(TraceContext.MDC_REQUEST_ID, requestId);
        MDC.put(TraceContext.MDC_TRACE_ID, traceId);
        response.setHeader(TraceContext.HEADER_REQUEST_ID, requestId);
        response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_REQUEST_ID);
            MDC.remove(TraceContext.MDC_TRACE_ID);
            MDC.remove(TraceContext.MDC_ACCOUNT_ID);
        }
    }
}
