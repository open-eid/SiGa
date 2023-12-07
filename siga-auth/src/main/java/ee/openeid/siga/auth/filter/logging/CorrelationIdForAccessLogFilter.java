package ee.openeid.siga.auth.filter.logging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * A filter that extracts correlation ID from the MDC (Mapped Diagnostic Context), if present,
 * and stores it inside the request as a request attribute named "requestId".
 * The correlation ID is expected to be stored in MDC as either "spanId", "traceId" or "trace.id".
 * <p>
 * NB: This filter must be placed after a filter that generates the correlation ID!
 * This filter does not generate the correlation ID by itself!
 * If no correlation ID is found a warning is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorrelationIdForAccessLogFilter extends OncePerRequestFilter {

    private static final String REQUEST_ATTRIBUTE_NAME_CORRELATION_ID = "requestId";
    private static final List<String> POSSIBLE_CORRELATION_ID_NAMES = List.of(
            "spanId",
            "traceId",
            "trace.id"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        findCorrelationIdAndStoreAsAttributeIfExists(request);
        filterChain.doFilter(request, response);
    }

    private static void findCorrelationIdAndStoreAsAttributeIfExists(HttpServletRequest request) {
        POSSIBLE_CORRELATION_ID_NAMES.stream()
                .map(CorrelationIdForAccessLogFilter::getFromMappedDiagnosticContextIfExists)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .ifPresentOrElse(
                        correlationId -> request.setAttribute(REQUEST_ATTRIBUTE_NAME_CORRELATION_ID, correlationId),
                        () -> log.warn("Failed to find correlation ID from Mapped Diagnostic Context")
                );
    }

    private static String getFromMappedDiagnosticContextIfExists(String key) {
        try {
            return MDC.get(key);
        } catch (Exception e) {
            log.error("Failed to retrieve entry '{}' from Mapped Diagnostic Context", key, e);
            return null;
        }
    }

}
