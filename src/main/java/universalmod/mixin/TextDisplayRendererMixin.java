package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.utils.TotemCounter;
import universalmod.api.module.impl.render.PingNametags;
import universalmod.utils.player.PingNametagHelper;

import java.util.ArrayList;
import java.util.List;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V", at = @At("RETURN"))
    private void universalmod$appendTotemCounter(Display.TextDisplay entity, TextDisplayEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        if ((!TotemCounter.isActive() && !CustomDonate.isActive() && !PingNametags.isActive()) || renderState.cachedInfo == null || !(entity.getVehicle() instanceof Player player)) {
            return;
        }
        if (!player.isAlive()) {
            TotemCounter.remove(player);
            return;
        }

        List<Display.TextDisplay.CachedLine> lines = renderState.cachedInfo.lines();
        for (int i = 0; i < lines.size(); i++) {
            Display.TextDisplay.CachedLine line = lines.get(i);
            String lineString = line.contents().toString();
            if (lineString.isBlank() || !lineString.contains(player.getScoreboardName())) {
                continue;
            }

            Component modified = CustomDonate.replaceDonate(player, Component.literal(lineString));
            modified = TotemCounter.appendCounter(player, modified);
            modified = PingNametagHelper.appendPing(player, modified);
            FormattedCharSequence modifiedSeq = modified.getVisualOrderText();
            int newLineWidth = Minecraft.getInstance().font.width(modified);
            List<Display.TextDisplay.CachedLine> newLines = new ArrayList<>(lines);
            newLines.set(i, new Display.TextDisplay.CachedLine(modifiedSeq, newLineWidth));
            int newMaxWidth = newLines.stream().mapToInt(Display.TextDisplay.CachedLine::width).max().orElse(renderState.cachedInfo.width());
            renderState.cachedInfo = new Display.TextDisplay.CachedInfo(newLines, newMaxWidth);
            return;
        }
    }
}
