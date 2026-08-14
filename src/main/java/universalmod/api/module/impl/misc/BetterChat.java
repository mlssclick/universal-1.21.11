package universalmod.api.module.impl.misc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.utils.lang.LanguageManager;

import java.time.LocalTime;
import java.util.Locale;

public final class BetterChat extends Module {
    private static final long SMOOTH_CHAT_DURATION_MILLIS = 180L;
    private static final float SMOOTH_CHAT_OFFSET = 8.0F;
    private static BetterChat instance;

    private final BooleanSetting timestamps = register(new BooleanSetting("Timestamps", "Adds clean timestamps to incoming chat.", true));
    private final BooleanSetting antiSpam = register(new BooleanSetting("Anti Spam", "Merges repeated messages into one counted message.", true));
    private final BooleanSetting showSeconds = register(new BooleanSetting("Show Seconds", "Shows seconds in timestamps.", false));
    private final BooleanSetting twentyFourHourClock = register(new BooleanSetting("24 Hour Clock", "Uses 24 hour timestamp format.", true));
    private final BooleanSetting smoothChat = register(new BooleanSetting("Smooth Chat", "Slides new chat messages smoothly.", true));
    private final BooleanSetting antiClear = register(new BooleanSetting("Anti Clear", "Prevents servers from clearing chat.", false));

    private long lastMessageTimestampMillis;
    private String lastRawMessageKey = "";
    private int lastRawMessageCount;

    public BetterChat() {
        super("BetterChat", "Improves chat with clean timestamps and optional anti-clear protection.", ModuleCategory.MISC);
        instance = this;
    }

    public static ProcessedChatMessage prepareIncomingMessage(Component message) {
        BetterChat module = instance;
        if (module == null || !module.isEnabled()) {
            return new ProcessedChatMessage(message, false, false);
        }
        module.lastMessageTimestampMillis = System.currentTimeMillis();
        if (message == null) {
            return new ProcessedChatMessage(null, false, false);
        }

        String rawKey = message.getString();
        boolean replacePrevious = false;
        int duplicateCount = 1;
        if (module.antiSpam.getValue() && !rawKey.isBlank() && rawKey.equals(module.lastRawMessageKey)) {
            module.lastRawMessageCount++;
            duplicateCount = module.lastRawMessageCount;
            replacePrevious = duplicateCount > 1;
        } else {
            module.lastRawMessageKey = rawKey;
            module.lastRawMessageCount = 1;
        }

        Component formattedMessage = module.formatMessage(message, duplicateCount);
        return new ProcessedChatMessage(formattedMessage, replacePrevious, true);
    }

    public static boolean shouldCancelChatClear() {
        BetterChat module = instance;
        return module != null && module.isEnabled() && module.antiClear.getValue();
    }

    public static void resetSpamState() {
        BetterChat module = instance;
        if (module == null) {
            return;
        }
        module.lastRawMessageKey = "";
        module.lastRawMessageCount = 0;
    }

    public static float getSmoothOffset() {
        BetterChat module = instance;
        if (module == null || !module.isEnabled() || !module.smoothChat.getValue()) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - module.lastMessageTimestampMillis;
        if (elapsed <= 0L || elapsed >= SMOOTH_CHAT_DURATION_MILLIS) {
            return 0.0F;
        }
        float progress = (float) elapsed / (float) SMOOTH_CHAT_DURATION_MILLIS;
        float eased = 1.0F - progress;
        eased = eased * eased * (3.0F - 2.0F * eased);
        return SMOOTH_CHAT_OFFSET * eased;
    }

    private Component formatMessage(Component message, int duplicateCount) {
        MutableComponent content = message.copy();
        if (duplicateCount > 1) {
            content.append(Component.literal(LanguageManager.translateFormat("chat.duplicate", duplicateCount))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
        }
        if (!timestamps.getValue()) {
            return content;
        }

        MutableComponent prefix = Component.literal(buildTimestampPrefix())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY));
        return Component.empty().append(prefix).append(content);
    }

    private String buildTimestampPrefix() {
        LocalTime now = LocalTime.now();
        boolean withSeconds = showSeconds.getValue();
        if (twentyFourHourClock.getValue()) {
            return withSeconds
                    ? String.format(Locale.US, "[%02d:%02d:%02d] ", now.getHour(), now.getMinute(), now.getSecond())
                    : String.format(Locale.US, "[%02d:%02d] ", now.getHour(), now.getMinute());
        }

        int hour = now.getHour() % 12;
        if (hour == 0) {
            hour = 12;
        }
        String meridiem = now.getHour() >= 12 ? "PM" : "AM";
        return withSeconds
                ? String.format(Locale.US, "[%d:%02d:%02d %s] ", hour, now.getMinute(), now.getSecond(), meridiem)
                : String.format(Locale.US, "[%d:%02d %s] ", hour, now.getMinute(), meridiem);
    }

    public record ProcessedChatMessage(Component text, boolean replacePrevious, boolean moduleActive) {
    }
}
