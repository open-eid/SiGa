package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.SigaEvent;
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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static java.time.Instant.now;
import static java.util.Arrays.stream;

@Aspect
@Component
public class SigaEventLoggingAspect {

    @Autowired
    SigaEventLogger sigaEventLogger;


    @Pointcut("@annotation(sigaEventLog)")
    public void callAt(SigaEventLog sigaEventLog) {
    }

    @Around("callAt(sigaEventLog)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint, SigaEventLog sigaEventLog) throws Throwable {

        Instant start = now();
        try {
            SigaEvent startEvent = sigaEventLogger.logStartEvent(sigaEventLog.eventType());
            logMethodParameters(sigaEventLog, startEvent, joinPoint);

            start = now();
            Object proceed = joinPoint.proceed();
            Instant finish = now();

            long executionTimeInMilli = Duration.between(start, finish).toMillis();
            sigaEventLogger.logEndEvent(sigaEventLog.eventType(), executionTimeInMilli);
            return proceed;
        } catch (Throwable e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            sigaEventLogger.logExceptionEvent(sigaEventLog.eventType(), executionTimeInMilli);
            throw e;
        }
    }

    private void logMethodParameters(SigaEventLog sigaEventLog, SigaEvent sigaEvent, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        if (sigaEventLog.logPathVariables()) {
            AtomicInteger parameterIndex = new AtomicInteger(-1);
            stream(parameterAnnotations).flatMap(Arrays::stream)
                    .filter(a -> {
                        parameterIndex.incrementAndGet();
                        return a.annotationType().isAssignableFrom(PathVariable.class);
                    })
                    .map(a -> (PathVariable) a)
                    .forEach(a -> {
                        String name = LOWER_CAMEL.to(LOWER_UNDERSCORE, a.value());
                        sigaEvent.addStartParameter(name, args[parameterIndex.get()].toString());
                    });
        }
        if (sigaEventLog.logRequestBodyXPath().length > 0) {
            AtomicInteger parameterIndex = new AtomicInteger(-1);
            stream(parameterAnnotations).flatMap(Arrays::stream)
                    .filter(a -> {
                        parameterIndex.incrementAndGet();
                        return a.annotationType().isAssignableFrom(RequestBody.class);
                    })
                    .map(a -> (RequestBody) a)
                    .findFirst().ifPresent(requestBody -> {
                final Object requestBodyObject = args[parameterIndex.get()];
                stream(sigaEventLog.logRequestBodyXPath()).forEach(parameterNameXPath -> {
                    Object value = JXPathContext.newContext(requestBodyObject).getValue(parameterNameXPath.xpath());
                    sigaEvent.addStartParameter(parameterNameXPath.parameterName(), value.toString());
                });
            });
        }
    }
}

