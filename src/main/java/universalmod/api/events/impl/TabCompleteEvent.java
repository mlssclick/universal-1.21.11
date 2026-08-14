package universalmod.api.events.impl;

import universalmod.api.events.CancellableEvent;

public final class TabCompleteEvent extends CancellableEvent {
    private final String prefix;
    private String[] completions;

    public TabCompleteEvent(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String[] getCompletions() {
        return completions;
    }

    public void setCompletions(String[] completions) {
        this.completions = completions == null ? new String[0] : completions;
    }
}
