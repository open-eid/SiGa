package ee.openeid.siga.auth.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;

@Component
public class MethodInterceptor implements HandlerInterceptor {

    private static final Set<String> NOT_ALLOWED_REQUEST_METHODS = Set.of("OPTIONS", "HEAD", "TRACE", "PATCH");
    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;

    public MethodInterceptor(@Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappingProvider) {
        this.mappingProvider = mappingProvider;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        RequestMappingHandlerMapping handlerMapping = mappingProvider.getIfAvailable();
        if (handlerMapping == null) {
            return false;
        }

        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase();


        boolean urlExists = handlerMapping.getHandlerMethods().keySet().stream()
                .anyMatch(info -> info.getPatternValues().stream()
                        .anyMatch(pattern -> handlerMapping.getPathMatcher().match(pattern, path)));

        if (!urlExists) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        if (NOT_ALLOWED_REQUEST_METHODS.contains(method)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return false;
        }

        return true;
    }
}


