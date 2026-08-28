package com.megharaj.orderinventory.web.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @Mock private FilterChain filterChain;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsRequestReceivedAndCompletedAroundTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(event -> event.getLevel() == Level.INFO);
        assertThat(events.get(0).getFormattedMessage())
                .isEqualTo("API request received: POST /api/v1/orders");
        assertThat(events.get(1).getFormattedMessage())
                .startsWith("API request completed: POST /api/v1/orders -> 201 in ");
    }

    @Test
    void logsCompletionEvenWhenTheChainFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        org.mockito.Mockito.doThrow(new java.io.IOException("boom"))
                .when(filterChain)
                .doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } catch (java.io.IOException expected) {
            // propagated after logging completion
        }

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getFormattedMessage())
                .startsWith("API request completed: GET /api/v1/products -> 500 in ");
    }
}
