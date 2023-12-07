package ee.openeid.siga.auth.filter.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ContainerIdForAccessLogFilterTest {

    @InjectMocks
    private ContainerIdForAccessLogFilter filter;

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private FilterChain filterChain;

    @ParameterizedTest
    @ValueSource(strings = {
            "/containers/550e8400-e29b-41d4-a716-446655440000",
            "/containers/550e8400-e29b-41d4-a716-446655440000/",
            "/containers/550e8400-e29b-41d4-a716-446655440000/more",
            "/path/to/containers/550e8400-e29b-41d4-a716-446655440000",

            "/hashcodecontainers/550e8400-e29b-41d4-a716-446655440000",
            "/hashcodecontainers/550e8400-e29b-41d4-a716-446655440000/",
            "/hashcodecontainers/550e8400-e29b-41d4-a716-446655440000/more",
            "/path/to/hashcodecontainers/550e8400-e29b-41d4-a716-446655440000"
    })
    void doFilterInternal_WhenRequestUriContainsValidContainerId_ContainerIdIsStoredAsRequestAttribute(
            String requestUri
    ) throws Exception {
        doReturn(requestUri).when(httpServletRequest).getRequestURI();

        filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(httpServletRequest).setAttribute("containerId", "550e8400-e29b-41d4-a716-446655440000");
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/path/to/containers",
            "/path/to/containers/",
            "/path/to/containers//",
            "/path/to/containers/validationreport",

            "/path/to/hashcodecontainers",
            "/path/to/hashcodecontainers/",
            "/path/to/hashcodecontainers//",
            "/path/to/hashcodecontainers/validationreport"
    })
    void doFilterInternal_WhenRequestUriDoesNotContainContainerId_NoContainerIdIsStored(
            String requestUri
    ) throws Exception {
        doReturn(requestUri).when(httpServletRequest).getRequestURI();

        filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

    @Test
    void doFilterInternal_WhenRequestUriIsNull_NoContainerIdIsStored() throws Exception {
        doReturn(null).when(httpServletRequest).getRequestURI();

        filter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(httpServletRequest, filterChain);
        verifyNoInteractions(httpServletResponse);
    }

}
