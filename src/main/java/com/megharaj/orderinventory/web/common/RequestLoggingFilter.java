package com.megharaj.orderinventory.web.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a single structured access log for every HTTP request handled by the service.
 *
 * <p>Runs just after {@link CorrelationIdFilter} so the {@code correlationId} is present in the
 * SLF4J MDC and appears on both entry and completion lines. Only the method, request URI, response
 * status, and elapsed time are logged; request bodies and headers (which can carry the
 * {@code Idempotency-Key} and other sensitive values) are deliberately excluded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        long startNanos = System.nanoTime();
        LOGGER.info("API request received: {} {}", method, uri);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            LOGGER.info(
                    "API request completed: {} {} -> {} in {} ms",
                    method,
                    uri,
                    response.getStatus(),
                    elapsedMillis);
        }
    }
}
