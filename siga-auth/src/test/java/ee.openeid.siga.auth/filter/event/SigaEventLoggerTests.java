package ee.openeid.siga.auth.filter.event;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventLoggingAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEvent.EventResultType.SUCCESS;
import static ee.openeid.siga.common.event.SigaEventName.REQUEST;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class SigaEventLoggerTests {

    Logger logger = (Logger) LoggerFactory.getLogger(SigaEventLogger.class);

    @Mock
    Appender<ILoggingEvent> appender;

    @Captor
    ArgumentCaptor<ILoggingEvent> captor;

    @Spy
    SigaEventLogger sigaEventLogger;

    @InjectMocks
    SigaEventLoggingAspect eventLoggingAspect;

    SigaEventLogAnnotatedComponentStub sigaEventLogAnnotatedComponentStub;

    @BeforeEach
    void setUp() {
        logger.addAppender(appender);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SigaEventLogAnnotatedComponentStub());
        factory.addAspect(eventLoggingAspect);
        sigaEventLogAnnotatedComponentStub = factory.getProxy();
    }

    @AfterEach
    void teardown() {
        logger.detachAppender(appender);
    }

    @Test
    void shouldLogMethodArgumentsAndReturnObject() {
        SigaEventLogAnnotatedComponentStub.Parameter parameter1 = SigaEventLogAnnotatedComponentStub.Parameter.builder().value("value1 with \"characters\" that should be escaped").build();
        SigaEventLogAnnotatedComponentStub.Parameter parameter2 = SigaEventLogAnnotatedComponentStub.Parameter.builder().value("value2").build();
        sigaEventLogAnnotatedComponentStub.annotatedMethod(parameter1, parameter2);
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals(REQUEST.name(), startEvent.getEventName().name());
        assertEquals(REQUEST.name(), endEvent.getEventName().name());
        assertEquals("value1 with \\\"characters\\\" that should be escaped", startEvent.getEventParameter("parameter_1"));
        assertEquals("value2", startEvent.getEventParameter("parameter_2"));
        assertEquals("value1 with \\\"characters\\\" that should be escaped", endEvent.getEventParameter("return_value_1"));
        assertEquals("value2", endEvent.getEventParameter("return_value_2"));
        assertEquals(SUCCESS.name(), endEvent.getResultType().name());

        sigaEventLogger.logEvents();
        verify(appender, times(2)).doAppend(captor.capture());
        ILoggingEvent startLoggingEvent = captor.getAllValues().get(0);
        ILoggingEvent endLoggingEvent = captor.getAllValues().get(1);

        assertEquals(startEvent.toString(), startLoggingEvent.getMessage());
        assertEquals(endEvent.toString(), endLoggingEvent.getMessage());

        assertThat(startLoggingEvent.getMessage(), containsString("parameter_1=\"value1 with \\\"characters\\\" that should be escaped\", parameter_2=value2"));
        assertThat(endLoggingEvent.getMessage(), containsString("return_value_2=value2, return_value_1=\"value1 with \\\"characters\\\" that should be escaped\""));
    }

    @Test
    void shouldLogLoggableExceptionWithSpecifiedMessage() {
        try {
            sigaEventLogAnnotatedComponentStub.annotatedMethodThrowsLoggableException();
        } catch (SigaEventLogAnnotatedComponentStub.ExceptionToLog ex) {
            assertNotNull(ex);
        }
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals(REQUEST.name(), startEvent.getEventName().name());
        assertEquals(REQUEST.name(), endEvent.getEventName().name());
        assertEquals(EXCEPTION.name(), endEvent.getResultType().name());

        sigaEventLogger.logEvents();
        verify(appender, times(2)).doAppend(captor.capture());
        ILoggingEvent endLoggingEvent = captor.getAllValues().get(1);
        assertThat(endLoggingEvent.getMessage(), containsString("Loggable \\\"exception\\\" message with characters that should be escaped"));
    }

    @Test
    void shouldLogAnyExceptionWithConstantMessage() {
        try {
            sigaEventLogAnnotatedComponentStub.annotatedMethodThrowsException();
        } catch (NullPointerException ex) {
            assertNotNull(ex);
        }
        SigaEvent startEvent = sigaEventLogger.getEvent(0);
        SigaEvent endEvent = sigaEventLogger.getEvent(1);
        assertNotNull(startEvent);
        assertNotNull(endEvent);
        assertEquals(REQUEST.name(), startEvent.getEventName().name());
        assertEquals(REQUEST.name(), endEvent.getEventName().name());
        assertEquals(EXCEPTION.name(), endEvent.getResultType().name());
        assertEquals("Internal server error", endEvent.getErrorMessage());
    }
}
