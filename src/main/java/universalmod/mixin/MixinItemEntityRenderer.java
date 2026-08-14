package universalmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.ItemPhysics;

import java.util.HashMap;
import java.util.WeakHashMap;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer {
    @Unique
    private static final WeakHashMap<ItemEntityRenderState, Boolean> universalmod$groundStateMap = new WeakHashMap<>();
    @Unique
    private static final HashMap<Integer, Integer> universalmod$groundHoldMap = new HashMap<>();
    @Unique
    private ItemEntityRenderState universalmod$currentState;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("HEAD"), require = 0)
    private void universalmod$captureGroundState(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        universalmod$groundStateMap.put(state, universalmod$resolveGroundState(entity, entity.onGround()));
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), require = 0)
    private void universalmod$translate(PoseStack matrices, float x, float y, float z, ItemEntityRenderState state, PoseStack matricesArg, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        universalmod$currentState = state;
        boolean enabled = universalmod$isItemPhysicsEnabled();
        boolean stableGround = universalmod$groundStateMap.getOrDefault(state, false);
        if (enabled || stableGround) {
            AABB box = state.item.getModelBoundingBox();
            matrices.translate(x, -((float) box.minY) + 0.0625F, z);
        } else {
            matrices.translate(x, y, z);
        }
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"), require = 0)
    private void universalmod$renderPhysics(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, ItemClusterRenderState stackState, RandomSource random, AABB box) {
        if (universalmod$isItemPhysicsEnabled() && universalmod$currentState != null) {
            float age = universalmod$currentState.ageInTicks;
            float offset = universalmod$currentState.bobOffset;
            boolean onGround = universalmod$groundStateMap.getOrDefault(universalmod$currentState, false);
            matrices.mulPose(Axis.YP.rotation(-ItemEntity.getSpin(age, offset)));
            if (onGround) {
                matrices.mulPose(Axis.XP.rotationDegrees(90.0F));
                matrices.translate(0.0F, -((float) box.getYsize() / 2.0F) + 0.0625F, 0.0F);
            } else {
                matrices.mulPose(Axis.XP.rotationDegrees((age * 15.0F + offset * 360.0F) % 360.0F));
            }
        }
        ItemEntityRenderer.submitMultipleFromCount(matrices, submitNodeCollector, light, stackState, random, box);
    }

    @Unique
    private static boolean universalmod$isItemPhysicsEnabled() {
        ItemPhysics itemPhysics = ItemPhysics.getInstance();
        return itemPhysics != null && itemPhysics.isEnabled();
    }

    @Unique
    private static boolean universalmod$resolveGroundState(ItemEntity entity, boolean onGround) {
        int entityId = entity.getId();
        if (onGround) {
            universalmod$groundHoldMap.put(entityId, 8);
            universalmod$trimGroundMap();
            return true;
        }

        Integer holdTicks = universalmod$groundHoldMap.get(entityId);
        if (holdTicks == null || holdTicks <= 0) {
            return false;
        }

        Vec3 velocity = entity.getDeltaMovement();
        boolean lowMotion = Math.abs(velocity.x) <= 0.08D
                && Math.abs(velocity.y) <= 0.08D
                && Math.abs(velocity.z) <= 0.08D;
        if (!lowMotion) {
            universalmod$groundHoldMap.remove(entityId);
            return false;
        }

        if (holdTicks == 1) {
            universalmod$groundHoldMap.remove(entityId);
        } else {
            universalmod$groundHoldMap.put(entityId, holdTicks - 1);
        }
        return true;
    }

    @Unique
    private static void universalmod$trimGroundMap() {
        if (universalmod$groundHoldMap.size() > 4096) {
            universalmod$groundHoldMap.clear();
        }
    }
}
