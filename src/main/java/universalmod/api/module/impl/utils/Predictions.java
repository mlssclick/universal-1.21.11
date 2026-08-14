package universalmod.api.module.impl.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.mixin.accessor.ThrownTridentAccessor;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.theme.ThemeColors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Predictions extends Module {
    private static final String ARROWS = "Arrows";
    private static final String TRIDENTS = "Tridents";
    private static final String PEARLS = "Ender Pearls";
    private static final String POTIONS = "Potions";
    private static final int MAX_LIVE_TICKS = 140;
    private static final int MAX_HELD_TICKS = 130;

    private final MultiModeSetting trackedItems = register(new MultiModeSetting(
            "Tracked Items", "Projectile types rendered by predictions.",
            new String[]{ARROWS, TRIDENTS, PEARLS, POTIONS}, ARROWS, TRIDENTS, PEARLS, POTIONS
    ));
    private final BooleanSetting rainbow = register(new BooleanSetting(
            "Rainbow Color", "Uses a rainbow trajectory color.", false
    ));

    private final Map<Integer, SmoothAnimation> trajectoryAnimations = new HashMap<>();
    private final List<Vec3> pathScratch = new ArrayList<>(MAX_LIVE_TICKS + 2);

    public Predictions() {
        super("Predictions", "Predicts and displays trident, arrow, pearl and potion trajectories.", ModuleCategory.RENDER);
    }

    @Override
    protected void onDisable() {
        trajectoryAnimations.clear();
        pathScratch.clear();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            trajectoryAnimations.clear();
            return;
        }

        Set<Integer> activeIds = new HashSet<>();
        double range = Math.max(16.0D, mc.options.renderDistance().get() * 16.0D);
        double rangeSqr = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            LiveProjectile projectile = liveProjectile(entity);
            if (projectile == null || !trackedItems.isSelected(projectile.settingName())) {
                continue;
            }
            if (!isOwnedByLocalPlayer(entity) || !isMoving(entity) || isReturningTrident(entity)
                    || mc.player.distanceToSqr(entity) > rangeSqr) {
                continue;
            }
            renderLiveProjectile(entity, projectile, activeIds, event.getPartialTicks());
        }

        renderHeldPrediction(event);
        fadeMissingTrajectories(activeIds);
    }

    private void renderLiveProjectile(Entity entity, LiveProjectile projectile, Set<Integer> activeIds, float partialTick) {
        List<Vec3> path = simulateLive(entity, projectile, partialTick);
        if (path.size() < 2) {
            return;
        }

        int id = entity.getId();
        activeIds.add(id);
        SmoothAnimation animation = trajectoryAnimations.computeIfAbsent(id, ignored -> new SmoothAnimation());
        animate(animation, 1.0D);
        float progress = animation.get();
        int segmentCount = path.size() - 1;
        int primaryColor = ThemeColors.hudAccentColor(255);
        float hueBase = (entity.getUUID().getLeastSignificantBits() & 0xFFFFL) / 65535.0F;
        for (int i = 0; i < segmentCount; i++) {
            float reveal = Mth.clamp(progress * segmentCount - i, 0.0F, 1.0F);
            if (reveal <= 0.0F) {
                break;
            }
            Vec3 start = path.get(i);
            float hue = positiveModulo(hueBase + (float) ((start.x + start.z) * 0.05D), 1.0F);
            int base = rainbow.getValue()
                    ? 0xFF000000 | Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0x00FFFFFF
                    : primaryColor;
            int alpha = Math.clamp(Math.round(255.0F * (0.3F + 0.7F * (1.0F - (float) i / segmentCount)) * reveal * progress), 0, 255);
            int color = base & 0x00FFFFFF | alpha << 24;
            Render3D.drawLineGradient(start, path.get(i + 1), color, color, 1.5F, false);
        }
    }

    private List<Vec3> simulateLive(Entity entity, LiveProjectile projectile, float partialTick) {
        pathScratch.clear();
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 position = entity.getPosition(partialTick);
        pathScratch.add(position);
        boolean throwable = projectile == LiveProjectile.PEARL || projectile == LiveProjectile.POTION;
        double gravity = projectile == LiveProjectile.POTION ? 0.05D : throwable ? 0.03D : 0.05D;
        for (int tick = 0; tick < MAX_LIVE_TICKS && velocity.lengthSqr() >= 1.0E-6D && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) {
                velocity = new Vec3(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
            }
            Vec3 next = position.add(velocity);
            BlockHitResult hit = clip(position, next, entity);
            if (hit.getType() != HitResult.Type.MISS) {
                pathScratch.add(hit.getLocation());
                break;
            }
            position = next;
            if (!throwable) {
                velocity = new Vec3(velocity.x * drag, velocity.y * drag - 0.05D, velocity.z * drag);
            }
            pathScratch.add(position);
        }
        return List.copyOf(pathScratch);
    }

    private void fadeMissingTrajectories(Set<Integer> activeIds) {
        trajectoryAnimations.entrySet().removeIf(entry -> {
            if (activeIds.contains(entry.getKey())) {
                return false;
            }
            SmoothAnimation animation = entry.getValue();
            animate(animation, 0.0D);
            return animation.get() <= 0.001F && animation.isFinished();
        });
    }

    private void renderHeldPrediction(WorldRenderEvent event) {
        ItemStack mainStack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        Item main = mainStack.getItem();
        Item off = mc.player.getItemInHand(InteractionHand.OFF_HAND).getItem();
        float speed = 0.0F;
        boolean throwable = false;
        boolean potion = isPotion(main) || isPotion(off);
        if (main instanceof BowItem && trackedItems.isSelected(ARROWS)) {
            float pull = bowPull();
            if (pull < 0.1F) {
                return;
            }
            speed = pull * 3.0F;
        } else if (main instanceof CrossbowItem && trackedItems.isSelected(ARROWS)) {
            speed = 3.0F;
        } else if (main instanceof TridentItem && trackedItems.isSelected(TRIDENTS)) {
            speed = 2.5F;
        } else if ((main == Items.ENDER_PEARL || off == Items.ENDER_PEARL) && trackedItems.isSelected(PEARLS)) {
            speed = 1.5F;
            throwable = true;
        } else if (potion && trackedItems.isSelected(POTIONS)) {
            speed = 0.5F;
            throwable = true;
        }
        if (speed == 0.0F) {
            return;
        }

        float[] spreads = main instanceof CrossbowItem && hasMultishot(mainStack)
                ? new float[]{-10.0F, 0.0F, 10.0F}
                : new float[]{0.0F};
        for (float spread : spreads) {
            HeldResult result = simulateHeld(speed, throwable, potion ? -20.0F : 0.0F,
                    potion ? 0.05D : 0.03D, spread, event.getPartialTicks());
            if (result.path().size() < 2) {
                continue;
            }
            if (result.hitEntity() == null && result.hitLocation() != null && result.hitFace() != null) {
                drawLandingCircle(result.hitLocation(), result.hitFace(), 0.33D, ColorUtil.rgba(255, 255, 255, 210));
            }
        }
    }

    private HeldResult simulateHeld(float speed, boolean throwable, float pitchOffset, double gravity, float spreadDegrees, float partialTick) {
        float pitch = mc.player.getViewXRot(partialTick);
        float yaw = mc.player.getViewYRot(partialTick);
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(Math.toRadians(pitch + pitchOffset)),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        look = rotateAroundLaunchAxis(look, yawRad, spreadDegrees);
        Vec3 playerVelocity = mc.player.getDeltaMovement();
        Vec3 velocity = look.scale(speed).add(playerVelocity.x, mc.player.onGround() ? 0.0D : playerVelocity.y, playerVelocity.z);
        Vec3 position = mc.player.getEyePosition(partialTick);
        List<Vec3> path = new ArrayList<>(MAX_HELD_TICKS + 2);
        path.add(position);
        for (int tick = 0; tick < MAX_HELD_TICKS && velocity.lengthSqr() >= 1.0E-6D && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) {
                velocity = new Vec3(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
            }
            Vec3 next = position.add(velocity);
            BlockHitResult blockHit = clip(position, next, mc.player);
            Vec3 segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
            Entity entityHit = findEntityHit(position, segmentEnd);
            if (entityHit != null) {
                path.add(entityHit.getBoundingBox().inflate(0.3D).clip(position, segmentEnd).orElse(segmentEnd));
                return new HeldResult(path, null, null, entityHit);
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                path.add(blockHit.getLocation());
                return new HeldResult(path, blockHit.getLocation(), blockHit.getDirection(), null);
            }
            position = next;
            if (!throwable) {
                velocity = new Vec3(velocity.x * drag, velocity.y * drag - 0.05D, velocity.z * drag);
            }
            path.add(position);
        }
        return new HeldResult(path, null, null, null);
    }

    private Entity findEntityHit(Vec3 start, Vec3 end) {
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity candidate : mc.level.getEntities(mc.player, new AABB(start, end).inflate(1.0D))) {
            if (!(candidate instanceof LivingEntity) || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            Optional<Vec3> intersection = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (intersection.isPresent()) {
                double distance = start.distanceToSqr(intersection.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = candidate;
                }
            }
        }
        return closest;
    }

    private void drawLandingCircle(Vec3 center, Direction face, double radius, int color) {
        Direction.Axis axis = face.getAxis();
        Vec3 u = axis == Direction.Axis.Y ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 v = axis == Direction.Axis.X ? new Vec3(0.0D, 0.0D, 1.0D)
                : axis == Direction.Axis.Z ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
        double step = Math.PI * 2.0D / 8.0D;
        double controlRadius = radius / Math.cos(step * 0.5D);
        for (int i = 0; i < 8; i++) {
            Vec3 from = circlePoint(center, u, v, radius, step * i);
            Vec3 to = circlePoint(center, u, v, radius, step * (i + 1));
            Vec3 control = circlePoint(center, u, v, controlRadius, step * (i + 0.5D));
            drawQuadraticCurve(from, control, to, color);
        }
    }

    private static void drawQuadraticCurve(Vec3 from, Vec3 control, Vec3 to, int color) {
        Vec3 previous = from;
        for (int segment = 1; segment <= 6; segment++) {
            double t = segment / 6.0D;
            double inverse = 1.0D - t;
            Vec3 point = from.scale(inverse * inverse)
                    .add(control.scale(2.0D * inverse * t))
                    .add(to.scale(t * t));
            Render3D.drawLineGradient(previous, point, color, color, 1.5F, false);
            previous = point;
        }
    }

    private static Vec3 circlePoint(Vec3 center, Vec3 u, Vec3 v, double radius, double angle) {
        return center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
    }

    private Vec3 rotateAroundLaunchAxis(Vec3 look, double yawRad, float spreadDegrees) {
        if (spreadDegrees == 0.0F) {
            return look;
        }
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(look);
        right = right.lengthSqr() < 1.0E-10D
                ? new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad))
                : right.normalize();
        Vec3 axis = look.cross(right).normalize();
        double radians = Math.toRadians(spreadDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return look.scale(cosine).add(axis.cross(look).scale(sine)).add(axis.scale(axis.dot(look) * (1.0D - cosine)));
    }

    private boolean hasMultishot(ItemStack stack) {
        try {
            var registry = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var enchantment = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT);
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private float bowPull() {
        ItemStack active = mc.player.getUseItem();
        if (!mc.player.isUsingItem() || !(active.getItem() instanceof BowItem)) {
            return 0.0F;
        }
        return Math.min(BowItem.getPowerForTime(mc.player.getTicksUsingItem()), 1.0F);
    }

    private BlockHitResult clip(Vec3 start, Vec3 end, Entity source) {
        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
    }

    private double waterDrag(Vec3 position, boolean throwable) {
        return mc.level.getFluidState(BlockPos.containing(position)).is(FluidTags.WATER) ? throwable ? 0.8D : 0.6D : 0.99D;
    }

    private boolean insideBuildHeight(Vec3 position) {
        return position.y >= mc.level.getMinY() && position.y <= mc.level.getMaxY();
    }

    private boolean isOwnedByLocalPlayer(Entity entity) {
        return entity instanceof Projectile projectile && projectile.getOwner() == mc.player;
    }

    private static boolean isReturningTrident(Entity entity) {
        return entity instanceof ThrownTrident
                && ((ThrownTridentAccessor) entity).universalmod$getClientSideReturnTickCount() > 0;
    }

    private static boolean isMoving(Entity entity) {
        return entity.getDeltaMovement().lengthSqr() > 1.0E-6D;
    }

    private static boolean isPotion(Item item) {
        return item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private LiveProjectile liveProjectile(Entity entity) {
        if (entity instanceof ThrownTrident) return LiveProjectile.TRIDENT;
        if (entity instanceof ThrownEnderpearl) return LiveProjectile.PEARL;
        if (entity instanceof ThrownSplashPotion) return LiveProjectile.POTION;
        if (entity instanceof AbstractArrow) return LiveProjectile.ARROW;
        return null;
    }

    private static void animate(SmoothAnimation animation, double target) {
        if (Math.abs(animation.getToValue() - target) > 1.0E-6D) {
            animation.run(target, 0.25D, Easings.BACK_OUT);
        }
        animation.update();
    }

    private static float positiveModulo(float value, float divisor) {
        return (value % divisor + divisor) % divisor;
    }

    private enum LiveProjectile {
        ARROW(ARROWS), TRIDENT(TRIDENTS), PEARL(PEARLS), POTION(POTIONS);

        private final String settingName;

        LiveProjectile(String settingName) {
            this.settingName = settingName;
        }

        private String settingName() {
            return settingName;
        }

    }

    private record HeldResult(List<Vec3> path, Vec3 hitLocation, Direction hitFace, Entity hitEntity) {}
}
