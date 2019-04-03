package ee.openeid.siga.common.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import eu.europa.esig.dss.validation.OCSPCertificateVerifier;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.impl.asic.SkDataLoader;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;

class LogObserver extends AppenderBase<ILoggingEvent> {
    private final Consumer<ILoggingEvent> logEventToSigaEvent;
    private final Logger logger;

    LogObserver(Class logClass, Consumer<ILoggingEvent> logEventToSigaEvent, Level level) {
        this.logEventToSigaEvent = logEventToSigaEvent;
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = lc.getLogger(logClass);
        logger.addAppender(this);
        logger.setLevel(level);
        logger.setAdditive(true);
        super.setContext(lc);
        super.start();
    }

    @Builder(buildMethodName = "buildForSkDataLoader")
    public static LogObserver buildForSkDataLoader(final SigaEventLogger sigaEventLogger, Level level) {
        Consumer<ILoggingEvent> eventConsumer = (loggingEvent) -> {
            String message = loggingEvent.getFormattedMessage();
            String errorUrl = StringUtils.substringBetween(loggingEvent.getFormattedMessage(), "<", ">");
            if (message.contains("Getting OCSP response from")) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, SigaEventName.EventParam.OCSP_URL, errorUrl));
            } else if (message.contains("Getting Timestamp response from")) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, SigaEventName.EventParam.TSA_URL, errorUrl));
            }
        };
        return new LogObserver(SkDataLoader.class, eventConsumer, level);
    }

    @Builder(buildMethodName = "buildForOCSPCertificateVerifier")
    public static LogObserver buildForOCSPCertificateVerifier(final SigaEventLogger sigaEventLogger, Level level) {
        Consumer<ILoggingEvent> eventConsumer = loggingEvent -> {
            final String message = loggingEvent.getFormattedMessage();
            final String errorUrl = StringUtils.substringBetween(loggingEvent.getFormattedMessage(), "'", "'");
            if (message.contains("OCSP DSS Exception: Unable to process GET call for url")) {
                sigaEventLogger.getLastMachingEvent(event -> SigaEventName.OCSP_REQUEST.equals(event.getEventName()) && event.containsParameterWithValue(errorUrl)).ifPresent(e -> {
                    e.setErrorCode(SIGNATURE_FINALIZING_REQUEST_ERROR);
                    e.setErrorMessage(message);
                    e.setResultType(EXCEPTION);
                });
            }
        };
        return new LogObserver(OCSPCertificateVerifier.class, eventConsumer, level);
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        logEventToSigaEvent.accept(iLoggingEvent);
    }

    public void detatch() {
        logger.detachAppender(this);
    }
}