package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.ButtonSetting;
import universalmod.api.settings.impl.StringSetting;
import universalmod.screens.clickgui.impl.ClickGuiController;
import universalmod.utils.figura.FiguraBridge;
import universalmod.utils.figura.FiguraEntry;
import universalmod.utils.figura.FiguraRepository;

public final class FiguraModels extends Module {
    private static final int REAPPLY_INTERVAL_TICKS = 20;
    private static FiguraModels instance;
    private final StringSetting selectedModel = register(new StringSetting(
            "Selected Model",
            "Last selected Figura model id.",
            "",
            256
    ));
    private int reapplyCooldown;

    public FiguraModels() {
        super("Figura Models", "Figura local avatar models.", ModuleCategory.RENDER);
        instance = this;
        selectedModel.visibleWhen(() -> false);
        register(new ButtonSetting("Editor", "Open Figura models editor.", FiguraModels::openEditor));
    }

    public static void activateFromEditor() {
        if (instance != null && !instance.isEnabled()) {
            instance.setEnabled(true);
        }
    }

    public static void rememberFromEditor(String modelId) {
        if (instance != null) {
            instance.selectedModel.setValue(modelId == null ? "" : modelId);
        }
    }

    private static void openEditor() {
        ClickGuiController.openFiguraModelsEditor();
    }

    @Override
    public void onTick(Minecraft client) {
        ensureSelectedModelApplied(false);
        FiguraBridge.sanitizeCameraOverrides();
    }

    @Override
    protected void onEnable() {
        reapplyCooldown = 0;
        ensureSelectedModelApplied(true);
        FiguraBridge.sanitizeCameraOverrides();
    }

    @Override
    protected void onDisable() {
        FiguraBridge.clear();
    }

    private void ensureSelectedModelApplied(boolean force) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        String selectedId = selectedModel.getValue();
        if (selectedId == null || selectedId.isBlank()) {
            return;
        }
        if (!force) {
            if (reapplyCooldown > 0) {
                reapplyCooldown--;
                return;
            }
            reapplyCooldown = REAPPLY_INTERVAL_TICKS;
        }
        FiguraEntry entry = FiguraRepository.byId(selectedId);
        if (entry != null && (!FiguraBridge.isApplied(entry) || !FiguraBridge.hasLoadedAvatar())) {
            FiguraBridge.apply(entry);
        }
    }
}
