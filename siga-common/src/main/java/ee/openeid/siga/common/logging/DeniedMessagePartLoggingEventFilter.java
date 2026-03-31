package ee.openeid.siga.common.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.apache.commons.lang3.StringUtils;

public class DeniedMessagePartLoggingEventFilter extends Filter<ILoggingEvent> {
    private final List<String> deniedMessageParts = new ArrayList<>();

    public void addDeniedMessagePart(String deniedMessagePart) {
      Optional
              .ofNullable(deniedMessagePart)
              .map(String::trim)
              .filter(StringUtils::isNotEmpty)
              .ifPresent(deniedMessageParts::add);
    }

    public void setDeniedMessageParts(String deniedMessageParts) {
        this.deniedMessageParts.clear();
        Stream
              .ofNullable(deniedMessageParts)
              .flatMap(parts -> Stream.of(StringUtils.split(parts, ',')))
              .forEach(this::addDeniedMessagePart);
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
      String message = Optional.ofNullable(event).map(ILoggingEvent::getFormattedMessage).orElse(null);
      if (message == null) {
          return FilterReply.NEUTRAL;
      }

      for (String deniedMessagePart : deniedMessageParts) {
          if (message.contains(deniedMessagePart)) {
              return FilterReply.DENY;
          }
      }

      return FilterReply.NEUTRAL;
    }
}
