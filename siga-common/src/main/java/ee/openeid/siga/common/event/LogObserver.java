package ee.openeid.siga.common.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.impl.SkDataLoader;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static ee.openeid.siga.common.event.SigaEventName.EventParam.REQUEST_URL;

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
        final Thread initialThread = Thread.currentThread();
        Consumer<ILoggingEvent> eventConsumer = loggingEvent -> {
            if (Thread.currentThread() != initialThread) {
                return;
            }
            String message = loggingEvent.getFormattedMessage();
            String requestUrl = StringUtils.substringBetween(loggingEvent.getFormattedMessage(), "<", ">");
            if (message.contains("Getting OCSP response from") || message.contains("Getting AIA_OCSP response from")) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, REQUEST_URL, requestUrl));
            } else if (message.contains("Getting TSP response from")) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, REQUEST_URL, requestUrl));
            }
        };
        return new LogObserver(SkDataLoader.class, eventConsumer, level);
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        logEventToSigaEvent.accept(iLoggingEvent);
    }

    public void detatch() {
        logger.detachAppender(this);
    }
}
