package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.SigaEvent;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
@FieldDefaults(level = PRIVATE)
public class SigaEventLoggerTests {

    @Spy
    SigaEventLogger sigaEventLogger;

    @InjectMocks
    SigaEventLoggingAspect eventLoggingAspect;

    SigaEventLogAnnotatedComponentStub sigaEventLogAnnotatedComponentStub;

    @Before
    public void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SigaEventLogAnnotatedComponentStub());
        factory.addAspect(eventLoggingAspect);
        sigaEventLogAnnotatedComponentStub = factory.getProxy();
    }

    @Test
    public void invokesMethodAndLogsParametersAndReturnObject() {
        SigaEventLogAnnotatedComponentStub.Parameter parameter1 = SigaEventLogAnnotatedComponentStub.Parameter.builder().value("value1").build();
        SigaEventLogAnnotatedComponentStub.Parameter parameter2 = SigaEventLogAnnotatedComponentStub.Parameter.builder().value("value2").build();
        sigaEventLogAnnotatedComponentStub.annotatedMethod(parameter1, parameter2);
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals("REQUEST", startEvent.getEventName().name());
        assertEquals("REQUEST", endEvent.getEventName().name());
        Map<String, String> startEventParameters = startEvent.getEventParameters();
        Map<String, String> endEventParameters = endEvent.getEventParameters();
        assertEquals("value1", startEventParameters.get("parameter_1"));
        assertEquals("value2", startEventParameters.get("parameter_2"));
        assertEquals("value1", endEventParameters.get("return_value_1"));
        assertEquals("value2", endEventParameters.get("return_value_2"));
        assertEquals("SUCCESS", endEvent.getResultType().name());
    }

    @Test
    public void invokesMethodAndLogsLoggableExceptionWithSpecifiedMessage() {
        try {
            sigaEventLogAnnotatedComponentStub.annotatedMethodThrowsLoggableException();
        } catch (SigaEventLogAnnotatedComponentStub.ExceptionToLog ex) {
            assertNotNull(ex);
        }
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals("REQUEST", startEvent.getEventName().name());
        assertEquals("REQUEST", endEvent.getEventName().name());
        assertEquals("EXCEPTION", endEvent.getResultType().name());
        assertEquals("Loggable exception message", endEvent.getErrorMessage());
    }

    @Test
    public void invokesMethodAndLogsAnyExceptionWithConstantMessage() {
        try {
            sigaEventLogAnnotatedComponentStub.annotatedMethodThrowsException();
        } catch (NullPointerException ex) {
            assertNotNull(ex);
        }
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals("REQUEST", startEvent.getEventName().name());
        assertEquals("REQUEST", endEvent.getEventName().name());
        assertEquals("EXCEPTION", endEvent.getResultType().name());
        assertEquals("Internal server error", endEvent.getErrorMessage());
    }
}
