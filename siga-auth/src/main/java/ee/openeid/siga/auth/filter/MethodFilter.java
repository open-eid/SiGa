package ee.openeid.siga.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.webapp.json.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.OutputStream;

@Component
public class MethodFilter extends OncePerRequestFilter {

    private static final String ALLOWED_REQUEST_METHOD_POST = "POST";
    private static final String ALLOWED_POST_URI = "/hashcodecontainers/validationreport";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        if (!ALLOWED_REQUEST_METHOD_POST.equals(method) && ALLOWED_POST_URI.equals(path)) {

            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            try (OutputStream out = response.getOutputStream()) {
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setErrorCode(ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name());
                errorResponse.setErrorMessage("Request method '" + method + "' not supported");
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(out, errorResponse);
                out.flush();
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}