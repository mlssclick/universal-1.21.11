package universalmod.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.utils.network.SaveKtManager;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlaySaveKtMixin {
    @Unique
    private UUID universalmod$pvpUUID;

    @Unique
    private final ScheduledExecutorService universalmod$service = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "savektleave-bossbar");
        thread.setDaemon(true);
        return thread;
    });

    @Unique
    private ScheduledFuture<?> universalmod$currentTask;

    @Inject(method = "update", at = @At("HEAD"))
    private void universalmod$detectBossbar(ClientboundBossEventPacket packet, CallbackInfo ci) {
        // Observe the packet without redirecting BossHealthOverlay's vanilla dispatch call.
        // This avoids competing with other SaveKtLeave/BossBarHud redirect mixins.
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID uuid, Component name, float percent, BossEvent.BossBarColor color, BossEvent.BossBarOverlay style,
                            boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
                String lowered = name.getString().toLowerCase(Locale.ROOT);
                boolean hasPvPWord = SaveKtManager.PVP_TYPES.stream().anyMatch(lowered::contains);
                if (hasPvPWord) {
                    universalmod$pvpUUID = uuid;
                    SaveKtManager.pvp = true;
                    universalmod$removeTask();
                }
            }

            @Override
            public void remove(UUID uuid) {
                if (uuid.equals(universalmod$pvpUUID)) {
                    universalmod$removeOrClearAction();
                }
            }

            @Override
            public void updateProgress(UUID uuid, float percent) {
            }

            @Override
            public void updateName(UUID uuid, Component name) {
            }

            @Override
            public void updateStyle(UUID uuid, BossEvent.BossBarColor color, BossEvent.BossBarOverlay style) {
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
            }
        });
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void universalmod$clear(CallbackInfo ci) {
        universalmod$removeOrClearAction();
    }

    @Unique
    private void universalmod$removeOrClearAction() {
        universalmod$removeTask();
        universalmod$currentTask = universalmod$service.schedule(() -> {
            SaveKtManager.pvp = false;
            return false;
        }, (long) SaveKtManager.getPing(), TimeUnit.MILLISECONDS);
    }

    @Unique
    private void universalmod$removeTask() {
        if (universalmod$currentTask != null) {
            universalmod$currentTask.cancel(true);
            universalmod$currentTask = null;
        }
    }
}
