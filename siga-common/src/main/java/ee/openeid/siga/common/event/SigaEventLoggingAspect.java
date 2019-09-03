package ee.openeid.siga.common.event;

import ee.openeid.siga.common.exception.SigaApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jxpath.ClassFunctions;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static java.time.Instant.now;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Aspect
@Component
public class SigaEventLoggingAspect {

    @Autowired
    private SigaEventLogger sigaEventLogger;

    @Autowired
    private ConfigurableBeanFactory configurableBeanFactory;

    @Pointcut("@annotation(sigaEventLog)")
    public void callAt(SigaEventLog sigaEventLog) {
    }

    @Around("callAt(eventLog)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint, SigaEventLog eventLog) throws Throwable {
        SigaEvent startEvent = sigaEventLogger.logStartEvent(eventLog.eventName());
        Instant start = now();
        try {
            Object proceed = joinPoint.proceed();
            Instant finish = now();
            long executionTimeInMilli = Duration.between(start, finish).toMillis();
            SigaEvent endEvent = sigaEventLogger.logEndEvent(eventLog.eventName(), executionTimeInMilli);
            logMethodParameters(joinPoint, eventLog, startEvent, endEvent);
            logStaticParameters(eventLog.logStaticParameters(), startEvent, endEvent);
            if (eventLog.logReturnObject().length != 0) {
                // FIXME: Possible parameter name collision, when method parameters are logged.
                logObject(eventLog.logReturnObject(), proceed, endEvent);
            }
            return proceed;
        } catch (SigaApiException e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            SigaEvent endEvent = sigaEventLogger.logExceptionEvent(eventLog.eventName(), e.getErrorCode(), e.getMessage(), executionTimeInMilli);
            logMethodParameters(joinPoint, eventLog, startEvent, endEvent);
            logStaticParameters(eventLog.logStaticParameters(), startEvent, endEvent);
            throw e;
        } catch (Throwable e) {
            long executionTimeInMilli = Duration.between(start, now()).toMillis();
            SigaEvent endEvent = sigaEventLogger.logExceptionEvent(eventLog.eventName(), "INTERNAL_SERVER_ERROR", "Internal server error", executionTimeInMilli);
            logMethodParameters(joinPoint, eventLog, startEvent, endEvent);
            logStaticParameters(eventLog.logStaticParameters(), startEvent, endEvent);
            throw e;
        }
    }

    private void logMethodParameters(ProceedingJoinPoint joinPoint, SigaEventLog eventLog, SigaEvent startEvent, SigaEvent endEvent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        Param[] logMethodExecutionParameters = eventLog.logParameters();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            createEventParameterForAnnotation(parameterAnnotations[i], PathVariable.class).ifPresent(annotation -> {
                String name = LOWER_CAMEL.to(LOWER_UNDERSCORE, ((PathVariable) annotation).value());
                startEvent.addEventParameter(name, arg.toString());
                endEvent.addEventParameter(name, arg.toString());
            });
            if (logMethodExecutionParameters.length != 0) {
                actOnEventCreation(logMethodExecutionParameters, i, startEvent, endEvent, arg);
            }
        }
    }

    private void actOnEventCreation(Param[] logMethodExecutionParameters, int i, SigaEvent startEvent, SigaEvent endEvent, Object arg) {
        for (Param param : logMethodExecutionParameters) {
            if (param.index() == i) {
                boolean parameterIsPrimitiveType = param.fields().length == 0;
                if (parameterIsPrimitiveType) {
                    String parameterName = isBlank(param.name()) ? "parameter_" + i : param.name();
                    startEvent.addEventParameter(parameterName, arg.toString());
                } else {
                    logObject(param.fields(), arg, startEvent, endEvent);
                }
            }
        }
    }

    private void logObject(XPath[] xPaths, Object returnObject, SigaEvent... events) {
        JXPathContext xc = JXPathContext.newContext(returnObject);
        xc.setFunctions(new ClassFunctions(JXPathHelperFunctions.class, "helper"));
        try {
            for (XPath p : xPaths) {
                Object value = xc.getValue(p.xpath());
                for (SigaEvent event : events) {
                    event.addEventParameter(p.name(), value != null ? value.toString() : "null");
                }
            }
        } catch (JXPathException e) {
            log.error("XPath not found when logging method execution: ", e.getMessage());
        }
    }

    private Optional<Annotation> createEventParameterForAnnotation(Annotation[] annotations, Class annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().isAssignableFrom(annotationClass)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    private void logStaticParameters(LogParam[] parameters, SigaEvent... events) {
        for (LogParam parameter : parameters) {
            String value = configurableBeanFactory.resolveEmbeddedValue(parameter.value());
            for (SigaEvent event : events) {
                event.addEventParameter(parameter.name(), value);
            }
        }
    }
}

