package ee.openeid.siga.test.helper;

import java.util.Map;

import org.apache.commons.collections4.MapUtils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoggingFilter implements Filter {

    private final int characterSplitLimit;

    @Override
    public Response filter(FilterableRequestSpecification reqSpec,
            FilterableResponseSpecification resSpec,
            FilterContext filterContext) {
        StringBuilder requestSb = new StringBuilder();

        // initial line shows "POST https://test.com"
        requestSb.append(reqSpec.getMethod());
        requestSb.append(' ');
        requestSb.append(reqSpec.getURI());
        requestSb.append('\n');

        // indent header section
        requestSb.append("  Request Headers:\n");
        appendHeaders(requestSb, reqSpec.getHeaders());

        if (MapUtils.isNotEmpty(reqSpec.getQueryParams())) {
            requestSb.append("  Request Query Parameters:\n");
            appendMap(requestSb, reqSpec.getQueryParams());
        }

        if (MapUtils.isNotEmpty(reqSpec.getFormParams())) {
            requestSb.append("  Request Form Parameters:\n");
            appendMap(requestSb, reqSpec.getFormParams());
        }

        if (reqSpec.getBody() != null) {
            requestSb.append("  Request Body:\n");
            appendSplit(requestSb, reqSpec.getBody().toString());
        }

        // there will be a \n at the end regardless
        System.out.print(requestSb.toString());

        Response response = filterContext.next(reqSpec, resSpec);

        StringBuilder responseSb = new StringBuilder();
        responseSb.append("  Response Status Code:\n    ");
        responseSb.append(response.statusCode());
        responseSb.append('\n');

        responseSb.append("  Response Headers:\n");
        appendHeaders(responseSb, response.getHeaders());

        responseSb.append("  Response Body:\n");
        appendSplit(responseSb, response.getBody().asString());

        System.out.println(responseSb.toString());

        return response;
    }

    private static void appendMap(StringBuilder sb, Map<String, String> map) {
        map.forEach((key, val) -> {
            sb.append("    ");
            sb.append(key);
            sb.append(':');
            sb.append(val);
            sb.append('\n');
        });
    }

    private static void appendHeaders(StringBuilder sb, Headers headers) {
        for (Header header : headers.asList()) {
            sb.append("    ");
            sb.append(header.toString());
            sb.append('\n');
        }
    }

    private void appendSplit(StringBuilder sb, String content) {
        final int iterationLimit = content.length() / this.characterSplitLimit;
        for (int i = 0; i <= iterationLimit; i++) {
            final int start = i * this.characterSplitLimit;
            final int end = Math.min((i + 1) * this.characterSplitLimit, content.length());

            sb.append("    ");
            sb.append(content.substring(start, end));
            sb.append('\n');
        }
    }

}