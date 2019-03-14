package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.LoggableException;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class SigaEventLogAnnotatedComponentStub {

    @SigaEventLog(eventName = SigaEventName.REQUEST, logParameters = {@Param(index = 0, fields = {@XPath(name = "parameter_1", xpath = "value")}),
            @Param(index = 1, fields = {@XPath(name = "parameter_2", xpath = "value")})}, logReturnObject = {@XPath(name = "return_value_1", xpath = "returnValue1"),
            @XPath(name = "return_value_2", xpath = "returnValue2")})
    public ReturnObject annotatedMethod(Parameter parameter1, Parameter parameter2) {
        return ReturnObject.builder()
                .returnValue1(parameter1.getValue())
                .returnValue2(parameter2.getValue())
                .build();
    }

    @SigaEventLog(eventName = SigaEventName.REQUEST)
    public void annotatedMethodThrowsLoggableException() {
        throw new ExceptionToLog("Loggable \"exception\" message with characters that should be escaped");
    }

    @SigaEventLog(eventName = SigaEventName.REQUEST)
    public void annotatedMethodThrowsException() {
        throw new NullPointerException("NPE Exception");
    }

    public static class ExceptionToLog extends RuntimeException implements LoggableException {
        public ExceptionToLog(String message) {
            super(message);
        }
    }

    @Data
    @Builder
    public static class Parameter {
        private String value;
    }

    @Data
    @Builder
    public static class ReturnObject {
        private String returnValue1;
        private String returnValue2;
    }

}
