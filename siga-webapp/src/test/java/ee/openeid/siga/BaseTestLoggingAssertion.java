package ee.openeid.siga;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ch.qos.logback.classic.Level.*;
import static java.util.List.of;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class BaseTestLoggingAssertion {

    private ListAppender<ILoggingEvent> memoryLogAppender;

    @BeforeEach
    public void addMemoryLogAppender() {
        memoryLogAppender = new ListAppender<>();
        ((Logger) getLogger(ROOT_LOGGER_NAME)).addAppender(memoryLogAppender);
        memoryLogAppender.start();
    }

    @AfterEach
    public void removeMemoryLogAppender() {
        ((Logger) getLogger(ROOT_LOGGER_NAME)).detachAppender(memoryLogAppender);
    }

    protected List<ILoggingEvent> assertInfoIsLoggedOnce(String... messagePatternsInRelativeOrder) {
        return assertMessagesAreLoggedOnceInRelativeOrder(null, INFO, messagePatternsInRelativeOrder);
    }

    protected List<ILoggingEvent> assertWarningIsLoggedOnce(String... messagePatternsInRelativeOrder) {
        return assertMessagesAreLoggedOnceInRelativeOrder(null, WARN, messagePatternsInRelativeOrder);
    }

    protected List<ILoggingEvent> assertErrorIsLoggedOnce(String... messagePatternsInRelativeOrder) {
        return assertMessagesAreLoggedOnceInRelativeOrder(null, ERROR, messagePatternsInRelativeOrder);
    }

    @SuppressWarnings("unchecked")
    protected List<ILoggingEvent> assertMessagesAreLoggedOnceInRelativeOrder(Class<?> loggerClass, Level loggingLevel, String... messagePatternsInRelativeOrder) {
        List<String> expectedMessages = of(messagePatternsInRelativeOrder);
        List<ILoggingEvent> events = memoryLogAppender.list.stream()
                .filter(e -> loggingLevel == null || e.getLevel() == loggingLevel)
                .filter(e -> loggerClass == null || e.getLoggerName().equals(loggerClass.getCanonicalName()))
                .filter(e -> expectedMessages.stream().anyMatch(expected -> e.getFormattedMessage().matches(expected)))
                .collect(toList());
        List<String> messages = events.stream().map(ILoggingEvent::getFormattedMessage).collect(toList());
        String expected = expectedMessages.stream().map(s -> s + "\n\t").collect(joining());
        String actual = messages.stream().map(s -> s + "\n\t").collect(joining());
        String reason = String.format("Expected log messages not found or out of order.\n\t" +
                "Expected log patterns in relative order: \n\t%s\n\tActual log messages in relative order: \n\t%s\n\t", expected, actual);
        assertThat(reason, messages, containsInRelativeOrder(expectedMessages.stream().map(Matchers::matchesPattern).toArray(Matcher[]::new)));
        Set<String> distinct = new HashSet<>();
        List<String> duplicates = messages.stream().filter(e -> !distinct.add(e)).collect(toList());
        assertTrue(duplicates.isEmpty(), "Log contains duplicate messages: " + duplicates);
        memoryLogAppender.list.removeAll(events);
        return events;
    }
}
