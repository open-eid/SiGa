package ee.openeid.siga.auth.filter.logging;

import ee.openeid.siga.auth.filter.util.ContainerIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ContainerIdForAccessLogFilter extends OncePerRequestFilter {

    private static final String REQUEST_ATTRIBUTE_NAME_CONTAINER_ID = "containerId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        findContainerIdFromUriAndStoreAsAttributeIfExists(request);
        filterChain.doFilter(request, response);
    }

    private static void findContainerIdFromUriAndStoreAsAttributeIfExists(HttpServletRequest request) {
        Optional
                .ofNullable(request.getRequestURI())
                .map(ContainerIdUtil::findContainerIdFromRequestURI)
                .ifPresent(containerId -> request.setAttribute(REQUEST_ATTRIBUTE_NAME_CONTAINER_ID, containerId));
    }

}
