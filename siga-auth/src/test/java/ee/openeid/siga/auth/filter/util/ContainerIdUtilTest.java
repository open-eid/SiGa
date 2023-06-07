package ee.openeid.siga.auth.filter.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerIdUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/containers",
            "/path/to/containers",
            "something/containers",

            "/hashcodecontainers",
            "/path/to/hashcodecontainers",
            "something/hashcodecontainers"
    })
    void isNewContainerRequest_WhenRequestUriEndsWithNewContainerPath_ReturnsTrue(String requestUri) {
        boolean result = ContainerIdUtil.isNewContainerRequest(requestUri);

        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "containers",
            "/containers/",
            "/containers/something",

            "hashcodecontainers",
            "/hashcodecontainers/",
            "/hashcodecontainers/something",

            "unrelated",
            "/unrelated",
            "/not/related/at/all",
            StringUtils.EMPTY,
            StringUtils.SPACE
    })
    void isNewContainerRequest_WhenRequestUriIsNotNewContainerPath_ReturnsFalse(String requestUri) {
        boolean result = ContainerIdUtil.isNewContainerRequest(requestUri);

        assertFalse(result);
    }

    @Test
    void isNewContainerRequest_WhenRequestUriIsNull_ReturnsFalse() {
        boolean result = ContainerIdUtil.isNewContainerRequest(null);

        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/containers/container-id-value",
            "/containers/container-id-value/",
            "/containers/container-id-value/path-continues",
            "/containers/container-id-value/path-continues/more",
            "/containers/container-id-value/containers/another-id-value/1",
            "/containers/container-id-value/hashcodecontainers/another-id-value/2",
            "/path/to/containers/container-id-value",
            "something/containers/container-id-value",

            "/hashcodecontainers/container-id-value",
            "/hashcodecontainers/container-id-value/",
            "/hashcodecontainers/container-id-value/path-continues",
            "/hashcodecontainers/container-id-value/path-continues/more",
            "/hashcodecontainers/container-id-value/containers/another-id-value/1",
            "/hashcodecontainers/container-id-value/hashcodecontainers/another-id-value/2",
            "/path/to/hashcodecontainers/container-id-value",
            "something/hashcodecontainers/container-id-value"
    })
    void findContainerIdFromRequestURI_WhenRequestUriContainsContainerId_ReturnsContainerId(String requestUri) {
        String result = ContainerIdUtil.findContainerIdFromRequestURI(requestUri);

        assertEquals("container-id-value", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            StringUtils.EMPTY,
            StringUtils.SPACE,
            "something-unrelated",
            "/path/to/unrelated",

            "/containers",
            "/containers/",
            "/containers//",
            "/containers/validationreport",
            "containers/container-id-value",

            "/hashcodecontainers",
            "/hashcodecontainers/",
            "/hashcodecontainers//",
            "/hashcodecontainers/validationreport",
            "hashcodecontainers/container-id-value"
    })
    void findContainerIdFromRequestURI_WhenRequestUriDoesNotContainContainerId_ReturnsNull(String requestUri) {
        String result = ContainerIdUtil.findContainerIdFromRequestURI(requestUri);

        assertNull(result);
    }

    @Test
    void findContainerIdFromRequestURI_WhenRequestUriIsNull_ReturnsNull() {
        String result = ContainerIdUtil.findContainerIdFromRequestURI(null);

        assertNull(result);
    }

}
