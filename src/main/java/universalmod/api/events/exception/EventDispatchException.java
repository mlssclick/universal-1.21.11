package universalmod.api.events.exception;

import universalmod.api.events.Event;

public class EventDispatchException extends EventException {
    public EventDispatchException(Event event, Throwable cause) {
        super("Failed to dispatch event: " + (event == null ? "null" : event.getClass().getName()), cause);
    }
}
