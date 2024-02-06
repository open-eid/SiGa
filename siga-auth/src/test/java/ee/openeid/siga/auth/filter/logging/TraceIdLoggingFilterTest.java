package ee.openeid.siga.auth.filter.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TraceIdLoggingFilterTest {

    @InjectMocks
    private TraceIdLoggingFilter filter;

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private FilterChain filterChain;

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd012345", " "})
    void doFilterInternal_WhenGivenTraceIdInMdc_RequestIdIsStoredAsRequestAttribute(String correlationId) throws Exception {
        try (MDC.MDCCloseable traceIdScope = MDC.putCloseable("trace.id", correlationId)) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(httpServletRequest).setAttribute("requestId", correlationId);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @Test
    void doFilterInternal_WhenEmptyTraceIdInMdc_RequestIdIsGeneratedAndStoredAsRequestAttribute() throws Exception {
        try (MDC.MDCCloseable traceIdScope = MDC.putCloseable("trace.id", "")) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(httpServletRequest).setAttribute(eq("requestId"), anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @Test
    void doFilterInternal_WhenNoCorrelationIdInMdc_RequestIdIsGeneratedAndStoredAsRequestAttribute() throws Exception {
        filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(httpServletRequest).setAttribute(eq("requestId"), anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

}
