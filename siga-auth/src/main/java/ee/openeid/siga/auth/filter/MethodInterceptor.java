package ee.openeid.siga.auth.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.pattern.PathPatternParser;

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
                        .anyMatch(pattern -> PathPatternParser.defaultInstance.parse(pattern).matches(PathContainer.parsePath(path))));

        if (!urlExists) {
            HttpMethod httpMethod = HttpMethod.valueOf(method);
            throw new NoResourceFoundException(httpMethod, path, path);
        }

        if (NOT_ALLOWED_REQUEST_METHODS.contains(method)) {
            throw new HttpRequestMethodNotSupportedException(method);
        }

        return true;
    }
}
