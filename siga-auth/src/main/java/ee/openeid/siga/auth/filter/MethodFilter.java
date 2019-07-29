package ee.openeid.siga.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.webapp.json.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

@Component
public class MethodFilter extends OncePerRequestFilter {

    private final static String NOT_ALLOWED_REQUEST_METHOD = "OPTIONS";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (request.getMethod().equals(NOT_ALLOWED_REQUEST_METHOD)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try (OutputStream out = response.getOutputStream()) {
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setErrorCode(ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name());
                errorResponse.setErrorMessage("Request method '" + NOT_ALLOWED_REQUEST_METHOD + "' not supported");
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(out, errorResponse);
                out.flush();
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
