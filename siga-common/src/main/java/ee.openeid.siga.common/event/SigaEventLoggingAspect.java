package ee.openeid.siga.common.event;

import ee.openeid.siga.common.exception.SigaApiException;
import lombok.experimental.FieldDefaults;
import org.apache.commons.jxpath.JXPathContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Aspect
@Component
@FieldDefaults(level = PRIVATE)
public class SigaEventLoggingAspect {

    @Autowired
    SigaEventLogger sigaEventLogger;

    @Pointcut("@annotation(sigaEventLog)")
    public void callAt(SigaEventLog sigaEventLog) {
    }

    @Around("callAt(eventLog)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint, SigaEventLog eventLog) throws Throwable {

        Instant start = now();
        try {
            SigaEvent startEvent = sigaEventLogger.logStartEvent(eventLog.eventName());

            start = now();
            Object proceed = joinPoint.proceed();
            Instant finish = now();

            long executionTimeInMilli = Duration.between(start, finish).toMillis();
            SigaEvent endEvent = sigaEventLogger.logEndEvent(eventLog.eventName(), executionTimeInMilli);

            logMethodParameters(joinPoint, eventLog, startEvent, endEvent);
            if (eventLog.logReturnObject().length != 0) {
                // FIXME: Possible parameter name collision, when method parameters are logged.
                logObject(eventLog.logReturnObject(), proceed, endEvent);
            }
            return proceed;
        } catch (SigaApiException e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            sigaEventLogger.logExceptionEvent(eventLog.eventName(), e.getErrorCode(), e.getMessage(), executionTimeInMilli);
            throw e;
        } catch (Throwable e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            sigaEventLogger.logExceptionEvent(eventLog.eventName(), "INTERNAL_SERVER_ERROR", "Internal server error", executionTimeInMilli);
            throw e;
        }
    }

    private void logMethodParameters(ProceedingJoinPoint joinPoint, SigaEventLog eventLog, SigaEvent startEvent, SigaEvent endEvent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        Param[] logParameters = eventLog.logParameters();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            containsAnnotation(parameterAnnotations[i], PathVariable.class).ifPresent(annotation -> {
                String name = LOWER_CAMEL.to(LOWER_UNDERSCORE, ((PathVariable) annotation).value());
                startEvent.addEventParameter(name, arg.toString());
                endEvent.addEventParameter(name, arg.toString());
            });
            if (eventLog.logParameters().length != 0) {
                for (Param p : logParameters) {
                    if (p.index() == i) {
                        if (p.fields().length > 0) {
                            logObject(p.fields(), arg, startEvent, endEvent);
                        } else {
                            String parameterName = isBlank(p.name()) ? "parameter_" + i : p.name();
                            startEvent.addEventParameter(parameterName, arg.toString());
                        }
                    }
                }
            }
        }
    }

    private void logObject(XPath[] xPaths, Object returnObject, SigaEvent... events) {
        JXPathContext xc = JXPathContext.newContext(returnObject);
        for (XPath p : xPaths) {
            Object value = xc.getValue(p.xpath());
            for (SigaEvent event : events) {
                event.addEventParameter(p.name(), value != null ? value.toString() : "null");
            }
        }
    }

    private Optional<Annotation> containsAnnotation(Annotation[] annotations, Class annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().isAssignableFrom(annotationClass)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }
}

