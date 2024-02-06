package ee.openeid.siga.auth.filter.logging;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Ensure that logging attributes are set as early as possible.
public class TraceIdLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ATTRIBUTE_NAME_REQUEST_ID = "requestId";
    private static final String TRACE_ID = "trace.id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestTraceId = MDC.get(TRACE_ID);
        boolean traceIdExists = StringUtils.isNotEmpty(requestTraceId);
        if (!traceIdExists) {
            // Use same format as Elastic APM Agent.
            requestTraceId = RandomStringUtils.random(32, "0123456789abcdef");
        }

        // Set traceId also as HttpServletRequest attribute to make it accessible for Tomcat's AccessLogValve.
        request.setAttribute(REQUEST_ATTRIBUTE_NAME_REQUEST_ID, requestTraceId);

        if (!traceIdExists) {
            MDC.put(TRACE_ID, requestTraceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!traceIdExists) {
                MDC.remove(TRACE_ID);
            }
        }
    }

}
