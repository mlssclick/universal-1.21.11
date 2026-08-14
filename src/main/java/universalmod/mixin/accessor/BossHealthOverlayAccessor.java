package universalmod.mixin.accessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

    @Accessor("BAR_BACKGROUND_SPRITES")
    static Identifier[] universalmod$getBarBackgroundSprites() {
        throw new AssertionError();
    }

    @Accessor("BAR_PROGRESS_SPRITES")
    static Identifier[] universalmod$getBarProgressSprites() {
        throw new AssertionError();
    }

    @Accessor("OVERLAY_BACKGROUND_SPRITES")
    static Identifier[] universalmod$getOverlayBackgroundSprites() {
        throw new AssertionError();
    }

    @Accessor("OVERLAY_PROGRESS_SPRITES")
    static Identifier[] universalmod$getOverlayProgressSprites() {
        throw new AssertionError();
    }
    @Accessor("events")
    Map<UUID, LerpingBossEvent> universalmod$getEvents();

    @Invoker("drawBar")
    void universalmod$drawBar(GuiGraphics graphics, int x, int y, BossEvent event);
}
