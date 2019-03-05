package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.Event;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Component
@RequestScope
@Getter
@CommonsLog
@FieldDefaults(level = PRIVATE)
public class EventLogger {

    List<Event> events = new ArrayList<>();

    public void logEvents() {
        events.forEach(log::info);
    }
}
