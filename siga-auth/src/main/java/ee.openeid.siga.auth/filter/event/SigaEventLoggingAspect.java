package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.exception.LoggableException;
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
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;

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
            SigaEvent startEvent = sigaEventLogger.logStartEvent(eventLog.eventType());
            if (eventLog.logPathVariables() || eventLog.logRequestBody().length != 0) {
                logMethodParameters(eventLog, startEvent, joinPoint);
            }

            start = now();
            Object proceed = joinPoint.proceed();
            Instant finish = now();

            long executionTimeInMilli = Duration.between(start, finish).toMillis();
            SigaEvent endEvent = sigaEventLogger.logEndEvent(eventLog.eventType(), executionTimeInMilli);
            if (eventLog.logReturnObject().length != 0) {
                logObject(endEvent, eventLog.logReturnObject(), proceed);
            }
            return proceed;
        } catch (Throwable e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            if (e instanceof LoggableException) {
                sigaEventLogger.logExceptionEvent(eventLog.eventType(), e.getMessage(), executionTimeInMilli);
            } else {
                sigaEventLogger.logExceptionEvent(eventLog.eventType(), executionTimeInMilli);
            }
            throw e;
        }
    }

    private void logMethodParameters(SigaEventLog eventLog, SigaEvent event, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (eventLog.logPathVariables()) {
                containsAnnotation(parameterAnnotations[i], PathVariable.class).ifPresent(annotation -> {
                    String name = LOWER_CAMEL.to(LOWER_UNDERSCORE, ((PathVariable) annotation).value());
                    event.addEventParameter(name, arg.toString());
                });
            }
            if (eventLog.logRequestBody().length != 0) {
                containsAnnotation(parameterAnnotations[i], RequestBody.class).ifPresent(annotation -> logObject(event,
                        eventLog.logRequestBody(), arg));
            }
        }
    }

    private void logObject(SigaEvent event, JXPath[] jxPaths, Object returnObject) {
        JXPathContext xc = JXPathContext.newContext(returnObject);
        for (JXPath p : jxPaths) {
            Object value = xc.getValue(p.xpath());
            event.addEventParameter(p.logName(), value.toString());
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

