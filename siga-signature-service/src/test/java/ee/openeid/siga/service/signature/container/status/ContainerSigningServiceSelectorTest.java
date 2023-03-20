package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ContainerSigningServiceSelectorTest {

    @ParameterizedTest
    @MethodSource("combinationsOfSessionsAndServices")
    void getContainerSigningServiceFor_WhenGivenServicesAndSession_ExpectedServiceIsReturned(
            HashcodeContainerSigningService hashcodeContainerSigningService,
            AsicContainerSigningService asicContainerSigningService,
            Session session,
            ContainerSigningService expectedService
    ) {
        ContainerSigningServiceSelector containerSigningServiceSelector = new ContainerSigningServiceSelector(
                hashcodeContainerSigningService,
                asicContainerSigningService
        );

        ContainerSigningService selectedService = containerSigningServiceSelector
                .getContainerSigningServiceFor(session);

        assertSame(expectedService, selectedService);
        verifyNoInteractions(hashcodeContainerSigningService);
        if (asicContainerSigningService != null) {
            verifyNoInteractions(asicContainerSigningService);
        }
        if (session != null) {
            verifyNoInteractions(session);
        }
    }

    static Stream<Arguments> combinationsOfSessionsAndServices() {
        HashcodeContainerSigningService hashcodeContainerSigningService = mock(HashcodeContainerSigningService.class);
        AsicContainerSigningService asicContainerSigningService = mock(AsicContainerSigningService.class);

        return Stream.of(
                Arguments.of(
                        hashcodeContainerSigningService,
                        asicContainerSigningService,
                        mock(HashcodeContainerSession.class),
                        hashcodeContainerSigningService
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        asicContainerSigningService,
                        mock(AsicContainerSession.class),
                        asicContainerSigningService
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        asicContainerSigningService,
                        mock(UnrelatedSessionType.class),
                        null
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        asicContainerSigningService,
                        null,
                        null
                ),

                Arguments.of(
                        hashcodeContainerSigningService,
                        null,
                        mock(HashcodeContainerSession.class),
                        hashcodeContainerSigningService
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        null,
                        mock(AsicContainerSession.class),
                        null
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        null,
                        mock(UnrelatedSessionType.class),
                        null
                ),
                Arguments.of(
                        hashcodeContainerSigningService,
                        null,
                        null,
                        null
                )
        );
    }

    interface UnrelatedSessionType extends Session {
    }

}
