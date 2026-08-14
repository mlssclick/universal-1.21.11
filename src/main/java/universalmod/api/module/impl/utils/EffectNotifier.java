package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.DrawEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class EffectNotifier extends Module {
    private static final int WARNING_TICKS = 100;
    private static final int ENDED_NOTICE_TICKS = 40;
    private static final float ICON_SIZE = 18.0F;
    private static final float ICON_SPACING = 2.0F;
    private static final float ICON_Y_OFFSET = 12.0F;

    private final Set<Holder<MobEffect>> previousBeneficialEffects = new HashSet<>();
    private final List<EndedNotice> endedNotices = new ArrayList<>();
    private final List<WarningNotice> warningNotices = new ArrayList<>();

    public EffectNotifier() {
        super("Effect Notifier", "Shows effect warning icons under the crosshair.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            reset();
            return;
        }

        Set<Holder<MobEffect>> currentBeneficialEffects = new HashSet<>();
        List<WarningNotice> currentWarnings = new ArrayList<>();
        for (MobEffectInstance instance : client.player.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            if (effect == null || !effect.value().isBeneficial()) {
                continue;
            }

            currentBeneficialEffects.add(effect);
            int duration = instance.getDuration();
            if (duration >= 0 && duration <= WARNING_TICKS) {
                currentWarnings.add(new WarningNotice(effect, duration));
            }
        }

        for (Holder<MobEffect> previousEffect : previousBeneficialEffects) {
            if (!currentBeneficialEffects.contains(previousEffect) && !hasEndedNotice(previousEffect)) {
                endedNotices.add(new EndedNotice(previousEffect, ENDED_NOTICE_TICKS));
            }
        }

        Iterator<EndedNotice> iterator = endedNotices.iterator();
        while (iterator.hasNext()) {
            EndedNotice notice = iterator.next();
            notice.remainingTicks--;
            if (notice.remainingTicks <= 0) {
                iterator.remove();
            }
        }

        previousBeneficialEffects.clear();
        previousBeneficialEffects.addAll(currentBeneficialEffects);
        currentWarnings.sort(Comparator.comparingInt(WarningNotice::remainingTicks));
        warningNotices.clear();
        warningNotices.addAll(currentWarnings);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (event.getLayer() != DrawEvent.Layer.GAME || mc.player == null || mc.options.hideGui) {
            return;
        }

        List<Holder<MobEffect>> iconEffects = new ArrayList<>();
        Set<Holder<MobEffect>> usedEffects = new HashSet<>();
        for (EndedNotice notice : endedNotices) {
            if (usedEffects.add(notice.effect)) {
                iconEffects.add(notice.effect);
            }
        }
        for (WarningNotice notice : warningNotices) {
            if (usedEffects.add(notice.effect)) {
                iconEffects.add(notice.effect);
            }
        }

        if (iconEffects.isEmpty()) {
            return;
        }

        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2.0F;
        float totalWidth = iconEffects.size() * ICON_SIZE + (iconEffects.size() - 1) * ICON_SPACING;
        float startX = centerX - totalWidth / 2.0F;
        float iconY = centerY + ICON_Y_OFFSET;
        int color = ColorUtil.rgba(255, 255, 255, 255);

        for (int index = 0; index < iconEffects.size(); index++) {
            Render2D.effectIcon(iconEffects.get(index), startX + index * (ICON_SIZE + ICON_SPACING), iconY, ICON_SIZE, color);
        }
    }

    private void reset() {
        previousBeneficialEffects.clear();
        endedNotices.clear();
        warningNotices.clear();
    }

    private boolean hasEndedNotice(Holder<MobEffect> effect) {
        for (EndedNotice notice : endedNotices) {
            if (notice.effect.equals(effect)) {
                return true;
            }
        }
        return false;
    }

    private static final class EndedNotice {
        private final Holder<MobEffect> effect;
        private int remainingTicks;

        private EndedNotice(Holder<MobEffect> effect, int remainingTicks) {
            this.effect = effect;
            this.remainingTicks = remainingTicks;
        }
    }

    private record WarningNotice(Holder<MobEffect> effect, int remainingTicks) {
    }
}
