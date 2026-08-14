package universalmod.utils.figura;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;

public final class FiguraBridge {
    private static final String AVATAR_MANAGER = "org.figuramc.figura.avatar.AvatarManager";

    private static Boolean available;
    private static Method loadLocalAvatar;
    private static Method clearAvatars;
    private static Method getLoadedAvatar;
    private static Field luaRuntimeField;
    private static Field rendererField;
    private static Field cameraPosField;
    private static Field cameraPivotField;
    private static Field cameraOffsetPivotField;
    private static Field cameraRotField;
    private static Field cameraOffsetRotField;
    private static Field cameraMatField;
    private static Field cameraNormalField;
    private static Field eyeOffsetField;
    private static Method figuraGetEntityId;
    private static String appliedId = "";

    private FiguraBridge() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = resolve();
        }
        return available;
    }

    private static boolean resolve() {
        if (!FabricLoader.getInstance().isModLoaded("figura")) {
            return false;
        }
        try {
            Class<?> manager = Class.forName(AVATAR_MANAGER);
            loadLocalAvatar = manager.getMethod("loadLocalAvatar", Path.class);
            clearAvatars = manager.getMethod("clearAvatars", UUID.class);
            getLoadedAvatar = manager.getMethod("getLoadedAvatar", UUID.class);
            Class<?> avatar = Class.forName("org.figuramc.figura.avatar.Avatar");
            Class<?> runtime = Class.forName("org.figuramc.figura.lua.FiguraLuaRuntime");
            Class<?> renderer = Class.forName("org.figuramc.figura.lua.api.RendererAPI");
            luaRuntimeField = avatar.getField("luaRuntime");
            rendererField = runtime.getField("renderer");
            cameraPosField = renderer.getField("cameraPos");
            cameraPivotField = renderer.getField("cameraPivot");
            cameraOffsetPivotField = renderer.getField("cameraOffsetPivot");
            cameraRotField = renderer.getField("cameraRot");
            cameraOffsetRotField = renderer.getField("cameraOffsetRot");
            cameraMatField = renderer.getField("cameraMat");
            cameraNormalField = renderer.getField("cameraNormal");
            eyeOffsetField = renderer.getField("eyeOffset");
            try {
                Class<?> stateExtension = Class.forName("org.figuramc.figura.ducks.FiguraEntityRenderStateExtension");
                figuraGetEntityId = stateExtension.getMethod("figura$getEntityId");
            } catch (Throwable ignored) {
                figuraGetEntityId = null;
            }
            return true;
        } catch (Throwable ignored) {
            loadLocalAvatar = null;
            clearAvatars = null;
            getLoadedAvatar = null;
            luaRuntimeField = null;
            rendererField = null;
            cameraPosField = null;
            cameraPivotField = null;
            cameraOffsetPivotField = null;
            cameraRotField = null;
            cameraOffsetRotField = null;
            cameraMatField = null;
            cameraNormalField = null;
            eyeOffsetField = null;
            figuraGetEntityId = null;
            return false;
        }
    }

    public static String appliedId() {
        return appliedId;
    }

    public static boolean isApplied(FiguraEntry entry) {
        return entry != null && entry.id().equals(appliedId);
    }

    public static boolean hasLoadedAvatar() {
        if (!isAvailable()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }
        try {
            return getLoadedAvatar.invoke(null, mc.player.getUUID()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean apply(FiguraEntry entry) {
        if (entry == null || !isAvailable()) {
            return false;
        }
        try {
            loadLocalAvatar.invoke(null, entry.folder());
            appliedId = entry.id();
            sanitizeCameraOverrides();
            return true;
        } catch (Throwable throwable) {
            System.err.println("[UniversalMod] Failed to apply Figura model " + entry.id() + ": " + throwable);
            return false;
        }
    }

    public static boolean clear() {
        appliedId = "";
        if (!isAvailable()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }
        try {
            clearAvatars.invoke(null, mc.player.getUUID());
            return true;
        } catch (Throwable throwable) {
            System.err.println("[UniversalMod] Failed to clear Figura model: " + throwable);
            return false;
        }
    }

    public static void sanitizeCameraOverrides() {
        if (!isAvailable()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        try {
            Object avatar = getLoadedAvatar.invoke(null, mc.player.getUUID());
            if (avatar == null || luaRuntimeField == null || rendererField == null) {
                return;
            }
            Object runtime = luaRuntimeField.get(avatar);
            if (runtime == null) {
                return;
            }
            Object renderer = rendererField.get(runtime);
            if (renderer == null) {
                return;
            }
            cameraPosField.set(renderer, null);
            cameraPivotField.set(renderer, null);
            cameraOffsetPivotField.set(renderer, null);
            cameraRotField.set(renderer, null);
            cameraOffsetRotField.set(renderer, null);
            cameraMatField.set(renderer, null);
            cameraNormalField.set(renderer, null);
            eyeOffsetField.set(renderer, null);
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldHideVanillaArmor(Object renderState) {
        if (appliedId.isBlank() || renderState == null || !isAvailable() || figuraGetEntityId == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return false;
        }
        try {
            if (!figuraGetEntityId.getDeclaringClass().isInstance(renderState)) {
                return false;
            }
            Object idValue = figuraGetEntityId.invoke(renderState);
            if (!(idValue instanceof Integer entityId)) {
                return false;
            }
            return mc.level.getEntity(entityId) == mc.player;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
