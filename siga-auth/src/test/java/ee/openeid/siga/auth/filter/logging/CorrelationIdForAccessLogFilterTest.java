package ee.openeid.siga.auth.filter.logging;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CorrelationIdForAccessLogFilterTest {

    @InjectMocks
    private CorrelationIdForAccessLogFilter filter;

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private FilterChain filterChain;

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd012345"})
    void doFilterInternal_WhenGivenSpanIdInMdc_CorrelationIdIsStoredAsRequestAttribute(
            String correlationId
    ) throws Exception {
        try (
                MDC.MDCCloseable spanIdScope = MDC.putCloseable("spanId", correlationId)
        ) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(httpServletRequest).setAttribute("requestId", correlationId);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd012345"})
    void doFilterInternal_WhenGivenTraceIdInMdc_CorrelationIdIsStoredAsRequestAttribute(
            String correlationId
    ) throws Exception {
        try (
                MDC.MDCCloseable traceIdScope = MDC.putCloseable("traceId", correlationId)
        ) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(httpServletRequest).setAttribute("requestId", correlationId);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcd012345"})
    void doFilterInternal_WhenGivenTraceDotIdInMdc_CorrelationIdIsStoredAsRequestAttribute(
            String correlationId
    ) throws Exception {
        try (
                MDC.MDCCloseable traceDotIdScope = MDC.putCloseable("trace.id", correlationId)
        ) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(httpServletRequest).setAttribute("requestId", correlationId);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {StringUtils.EMPTY, StringUtils.SPACE})
    void doFilterInternal_WhenBlankCorrelationIdInMdc_NoCorrelationIdIsStored(String blank) throws Exception {
        try (
                MDC.MDCCloseable spanIdScope = MDC.putCloseable("spanId", blank);
                MDC.MDCCloseable traceIdScope = MDC.putCloseable("traceId", blank);
                MDC.MDCCloseable traceDotIdScope = MDC.putCloseable("trace.id", blank)
        ) {
            filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);
        }

        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(filterChain);
        verifyNoInteractions(httpServletRequest, httpServletResponse);
    }

    @Test
    void doFilterInternal_WhenNoCorrelationIdInMdc_NoCorrelationIdIsStored() throws Exception {
        filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(filterChain);
        verifyNoInteractions(httpServletRequest, httpServletResponse);
    }

}
