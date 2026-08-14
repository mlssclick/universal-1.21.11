package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.MultiModeSetting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class Animations extends Module {
    public static final String TAB = "TAB";
    public static final String PERSPECTIVE = "Perspective Switching";
    public static final String RAISED_HOTBAR = "Raised Hotbar";
    public static final String HOTBAR_SLOT = "Hotbar Slot";
    public static final String MESSAGES = "Message Appearance";
    public static final String ITEMS = "Items";

    private static Animations instance;

    private final MultiModeSetting elements = register(new MultiModeSetting(
            "Animate",
            "Selected vanilla interface animations.",
            new String[]{TAB, PERSPECTIVE, RAISED_HOTBAR, HOTBAR_SLOT, MESSAGES, ITEMS},
            TAB, PERSPECTIVE, RAISED_HOTBAR, HOTBAR_SLOT, MESSAGES, ITEMS
    ));

    private final DeltaChannel tab = new DeltaChannel(10.0F);
    private final DeltaChannel hotbarLift = new DeltaChannel(9.0F);
    private final DeltaChannel perspective = new DeltaChannel(7.0F);
    private final Map<Object, DeltaChannel> itemChannels = Collections.synchronizedMap(new WeakHashMap<>());
    private float selectedSlot = -1.0F;
    private long selectedSlotUpdateNanos;

    public Animations() {
        super("Animations", "Animates selected vanilla interface elements.", ModuleCategory.RENDER);
        instance = this;
    }

    public static Animations getInstance() {
        return instance;
    }

    public static boolean active(String element) {
        Animations module = instance;
        return module != null && module.isEnabled() && module.elements.isSelected(element);
    }

    @Override
    protected void onEnable() {
        selectedSlot = -1.0F;
        selectedSlotUpdateNanos = 0L;
    }

    @Override
    protected void onDisable() {
        tab.snap(0.0F);
        hotbarLift.snap(0.0F);
        perspective.snap(0.0F);
        itemChannels.clear();
        selectedSlot = -1.0F;
        selectedSlotUpdateNanos = 0L;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) {
            return;
        }
        hotbarLift.setTarget(client.screen instanceof ChatScreen);
        perspective.setTarget(client.options != null && !client.options.getCameraType().isFirstPerson());
    }

    public void setTabVisible(boolean visible) {
        tab.setTarget(visible);
    }

    public float tabProgress() {
        return tab.value();
    }

    public float hotbarLiftProgress() {
        return hotbarLift.value();
    }

    public float perspectiveProgress() {
        return perspective.value();
    }

    public float itemScale(Object slot, boolean focused) {
        if (slot == null) {
            return 1.0F;
        }
        DeltaChannel channel = itemChannels.computeIfAbsent(slot, ignored -> new DeltaChannel(focused ? 25.0F : 15.0F, 1.0F));
        channel.setSpeed(focused ? 25.0F : 15.0F);
        channel.setTarget(focused ? 1.25F : 1.0F);
        return channel.value();
    }

    public float selectedSlot(float target) {
        long now = System.nanoTime();
        if (selectedSlot < 0.0F || !Float.isFinite(selectedSlot)) {
            selectedSlot = target;
            selectedSlotUpdateNanos = now;
            return selectedSlot;
        }
        float delta = selectedSlotUpdateNanos == 0L ? 0.0F : Math.min(0.1F, (now - selectedSlotUpdateNanos) / 1_000_000_000.0F);
        selectedSlotUpdateNanos = now;
        selectedSlot += (target - selectedSlot) * Math.min(1.0F, delta * 25.0F);
        if (Math.abs(target - selectedSlot) < 0.001F) {
            selectedSlot = target;
        }
        return selectedSlot;
    }

    private static final class DeltaChannel {
        private float value;
        private float target;
        private float speed;
        private long updateNanos = System.nanoTime();

        private DeltaChannel(float speed) {
            this(speed, 0.0F);
        }

        private DeltaChannel(float speed, float initialValue) {
            this.speed = speed;
            this.value = initialValue;
            this.target = initialValue;
        }

        private void setTarget(boolean expanded) {
            setTarget(expanded ? 1.0F : 0.0F);
        }

        private void setTarget(float target) {
            update();
            this.target = target;
        }

        private void setSpeed(float speed) {
            this.speed = speed;
        }

        private float value() {
            update();
            return value;
        }

        private void snap(float value) {
            this.value = value;
            this.target = value;
            this.updateNanos = System.nanoTime();
        }

        private void update() {
            long now = System.nanoTime();
            float delta = Math.min(0.1F, Math.max(0.0F, (now - updateNanos) / 1_000_000_000.0F));
            updateNanos = now;
            float step = speed * delta;
            if (value < target) {
                value = Math.min(target, value + step);
            } else if (value > target) {
                value = Math.max(target, value - step);
            }
        }
    }
}
