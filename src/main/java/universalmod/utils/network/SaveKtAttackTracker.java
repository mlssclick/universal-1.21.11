package universalmod.utils.network;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SaveKtAttackTracker {
    private static final ScheduledExecutorService ATTACK_SERVICE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "savektleave-attack-reset");
        thread.setDaemon(true);
        return thread;
    });

    private static ScheduledFuture<?> currentTask;
    private static boolean initialized;

    private SaveKtAttackTracker() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            setAttackState();
            return InteractionResult.PASS;
        });
    }

    private static void setAttackState() {
        SaveKtManager.lastAttack = true;
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }
        createResetTask();
    }

    private static void createResetTask() {
        currentTask = ATTACK_SERVICE.schedule(() -> {
            SaveKtManager.lastAttack = false;
            return false;
        }, (long) SaveKtManager.getPing(), TimeUnit.MILLISECONDS);
    }
}
