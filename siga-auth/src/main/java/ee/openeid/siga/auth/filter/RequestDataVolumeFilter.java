package ee.openeid.siga.auth.filter;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;

@Component
public class DataVolumeFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

//        request = new ContentCachingRequestWrapper(request);
        String test = IOUtils.toString(request.getInputStream(), Charset.defaultCharset());
        filterChain.doFilter(request, response);
    }
}
