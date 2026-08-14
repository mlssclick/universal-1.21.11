package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.events.impl.ChatEvent;
import universalmod.api.module.impl.render.Waypoints;
import universalmod.api.module.impl.utils.SaveKtLeave;
import universalmod.api.module.impl.utils.TotemCounter;
import universalmod.manager.Manager;
import universalmod.utils.network.AnarchySwitcher;
import universalmod.utils.network.ClientPlayNetworkHandlerHelper;
import universalmod.utils.network.SaveKtManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin implements ClientPlayNetworkHandlerHelper {
    @Unique
    private boolean universalmod$sendingFinalCommand;

    @Unique
    private final ScheduledExecutorService universalmod$service = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "savektleave-command");
        thread.setDaemon(true);
        return thread;
    });

    @Unique
    private ScheduledFuture<?> universalmod$currentTask;

    @Shadow
    public abstract void sendCommand(String command);

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void universalmod$onSendChat(String message, CallbackInfo ci) {
        ChatEvent event = Manager.postEvent(new ChatEvent(message));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void universalmod$onSendChatCommand(String command, CallbackInfo ci) {
        SaveKtLeave module = SaveKtLeave.getInstance();
        if (module != null && module.isEnabled()
                && SaveKtManager.COMMANDS_FOR_LEAVE.contains(command)
                && !universalmod$sendingFinalCommand) {
            ci.cancel();
            universalmod$coolProtectFromHub(command);
        }
    }

    @Inject(method = "handleSetHealth", at = @At("RETURN"))
    private void universalmod$onHealthUpdate(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (packet.getHealth() <= 0.0F) {
            SaveKtManager.reset();
            Waypoints.handleDeath();
        }
    }

    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void universalmod$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.content() != null) {
            String message = packet.content().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handleDisguisedChat", at = @At("TAIL"))
    private void universalmod$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.message() != null) {
            String message = packet.message().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("TAIL"))
    private void universalmod$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.unsignedContent() != null) {
            String message = packet.unsignedContent().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
    private void universalmod$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity entity = packet.getEntity(client.level);
        if (entity instanceof RemotePlayer player) {
            TotemCounter.recordPop(player);
        }
    }

    @Unique
    private void universalmod$sendConfirmScreen(String command) {
        SaveKtManager.reset();
        Minecraft.getInstance().execute(() -> SaveKtManager.checkLeaveCommand(command));
    }

    @Unique
    private void universalmod$coolProtectFromHub(String command) {
        universalmod$currentTask = universalmod$service.scheduleWithFixedDelay(() -> {
            if (SaveKtManager.lastAttack && SaveKtManager.pvp) {
                universalmod$sendConfirmScreen(command);
                universalmod$removeTask();
            } else if (!SaveKtManager.lastAttack) {
                if (SaveKtManager.pvp) {
                    universalmod$sendConfirmScreen(command);
                    universalmod$removeTask();
                } else {
                    sendFinalCommand(command);
                    universalmod$removeTask();
                }
            }
        }, 0L, 1L, TimeUnit.MILLISECONDS);
    }

    @Unique
    private void universalmod$removeTask() {
        if (universalmod$currentTask != null) {
            universalmod$currentTask.cancel(true);
            universalmod$currentTask = null;
        }
    }

    @Override
    public void sendFinalCommand(String command) {
        Minecraft.getInstance().execute(() -> {
            universalmod$sendingFinalCommand = true;
            try {
                sendCommand(command);
            } finally {
                universalmod$sendingFinalCommand = false;
            }
        });
    }
}
