package ee.openeid.siga.auth.filter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContainerIdUtil {

    private static final String ASIC_CONTAINERS_ENDPOINT = "/containers";
    private static final String HASHCODE_CONTAINERS_ENDPOINT = "/hashcodecontainers";
    private static final String[] NEW_CONTAINER_ENDPOINTS_ARRAY = {
            ASIC_CONTAINERS_ENDPOINT,
            HASHCODE_CONTAINERS_ENDPOINT
    };

    private static final String ASIC_CONTAINERS_ENDPOINT_PREFIX = ASIC_CONTAINERS_ENDPOINT + '/';
    private static final String HASHCODE_CONTAINERS_ENDPOINT_PREFIX = HASHCODE_CONTAINERS_ENDPOINT + '/';
    private static final List<String> CONTAINER_ENDPOINT_PREFIXES_LIST = List.of(
            ASIC_CONTAINERS_ENDPOINT_PREFIX,
            HASHCODE_CONTAINERS_ENDPOINT_PREFIX
    );

    private static final String VALIDATIONREPORT = "validationreport";

    public static boolean isNewContainerRequest(String requestURI) {
        return StringUtils.endsWithAny(requestURI, NEW_CONTAINER_ENDPOINTS_ARRAY);
    }

    public static String findContainerIdFromRequestURI(String requestURI) {
        return CONTAINER_ENDPOINT_PREFIXES_LIST.stream()
                .mapToInt(containerIdIndexFinderFromRequestURI(requestURI))
                .filter(ContainerIdUtil::nonNegative)
                .min().stream()
                .mapToObj(containerIdParserFromRequestURI(requestURI))
                .filter(StringUtils::isNotEmpty)
                .findFirst()
                .orElse(null);
    }

    private static ToIntFunction<String> containerIdIndexFinderFromRequestURI(String requestURI) {
        return containerPrefix -> {
            int prefixIndex = StringUtils.indexOf(requestURI, containerPrefix);
            if (nonNegative(prefixIndex)) {
                int containerIdIndex = prefixIndex + containerPrefix.length();
                if (isNotValidationReportRequestURI(requestURI, containerIdIndex)) {
                    return containerIdIndex;
                }
            }
            return -1;
        };
    }

    private static boolean isNotValidationReportRequestURI(String requestURI, int containerIdIndex) {
        return (containerIdIndex + VALIDATIONREPORT.length()) != StringUtils.length(requestURI) ||
                StringUtils.lastIndexOf(requestURI, VALIDATIONREPORT, containerIdIndex) != containerIdIndex;
    }

    private static IntFunction<String> containerIdParserFromRequestURI(String requestURI) {
        return containerIdIndex -> {
            int suffixIndex = StringUtils.indexOf(requestURI, '/', containerIdIndex);
            return nonNegative(suffixIndex) && (containerIdIndex <= suffixIndex)
                    ? StringUtils.substring(requestURI, containerIdIndex, suffixIndex)
                    : StringUtils.substring(requestURI, containerIdIndex);
        };
    }

    private static boolean nonNegative(int value) {
        return value >= 0;
    }

}
