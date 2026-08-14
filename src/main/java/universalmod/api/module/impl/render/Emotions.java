package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.bind.InputType;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.screens.emotions.EmotionWheelScreen;
import universalmod.utils.repository.friend.FriendUtils;
import net.minecraft.world.entity.player.Player;

public final class Emotions extends Module {
    public static final String GREETING = "Приветствие";
    public static final String DANCE = "Танец";
    public static final String MASTURBATION = "Дрочка";
    public static final String ALPHA_WALK = "Альфа ходьба";
    public static final String ALPHA_MALE = "Альфа-Мужик";

    public static final String[] EMOTIONS = {
            GREETING,
            DANCE,
            MASTURBATION,
            ALPHA_WALK,
            ALPHA_MALE
    };

    private static Emotions instance;

    private final BindSetting wheelBind = register(new BindSetting("Кнопка колеса", "Нажмите, чтобы открыть колесо эмоций.", KeyBind.NONE));
    private final BooleanSetting onFriends = register(new BooleanSetting("На друзей", "Показывать выбранную эмоцию на друзьях.", false));
    private final BooleanSetting onSelf = register(new BooleanSetting("На себя", "Показывать выбранную эмоцию на себе.", true));

    private String selectedEmotion;
    private String forcedEmotion;
    private boolean wheelWasDown;

    public Emotions() {
        super("Emotions", "Колесо эмоций и позы игрока.", ModuleCategory.RENDER);
        instance = this;
    }

    public static Emotions getInstance() {
        return instance;
    }

    public String getSelectedEmotion() {
        return selectedEmotion;
    }

    public void selectEmotion(String emotion) {
        selectedEmotion = isKnownEmotion(emotion) ? emotion : null;
    }

    public void clearEmotion() {
        selectedEmotion = null;
    }

    public String getForcedEmotion() {
        return forcedEmotion;
    }

    public void setForcedEmotion(String emotion) {
        forcedEmotion = isKnownEmotion(emotion) ? emotion : null;
    }

    public boolean shouldAnimate(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isEnabled() || selectedEmotion == null || minecraft == null || minecraft.player == null || player == null) {
            return false;
        }
        boolean self = player == minecraft.player;
        if (self && onSelf.getValue()) {
            return true;
        }
        if (!self && onFriends.getValue() && FriendUtils.isFriend(player)) {
            return true;
        }
        return false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            wheelWasDown = false;
            return;
        }

        long window = client.getWindow().handle();
        boolean down = isPhysicallyDown(wheelBind.getValue(), window);

        if (down && !wheelWasDown) {
            if (client.screen == null) {
                client.setScreen(new EmotionWheelScreen(this));
            } else if (client.screen instanceof EmotionWheelScreen wheel && wheel.belongsTo(this)) {
                wheel.commitSelection();
            }
        }
        wheelWasDown = down;
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.screen instanceof EmotionWheelScreen wheel && wheel.belongsTo(this)) {
            client.setScreen(null);
        }
        wheelWasDown = false;
    }

    private static boolean isKnownEmotion(String value) {
        if (value == null) {
            return false;
        }
        for (String emotion : EMOTIONS) {
            if (emotion.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPhysicallyDown(KeyBind bind, long window) {
        if (bind == null || !bind.isBound() || window == 0L) {
            return false;
        }
        InputType type = bind.getType();
        return switch (type) {
            case KEYBOARD -> GLFW.glfwGetKey(window, bind.getCode()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, bind.getCode()) == GLFW.GLFW_PRESS;
            case NONE -> false;
        };
    }
}
