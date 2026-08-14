package universalmod.api.module.impl.render.emotions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import universalmod.api.module.impl.render.Emotions;

/** Exact pose math port of the supplied Emotions implementation, renamed for this project. */
public final class EmotionModelAnimator {
    private EmotionModelAnimator() {
    }

    public static void apply(HumanoidModel<?> model, HumanoidRenderState state, Player player) {
        Emotions emotions = Emotions.getInstance();
        if (emotions == null || !emotions.isEnabled()) {
            return;
        }

        String forced = emotions.getForcedEmotion();
        if (forced != null) {
            resetHumanoidPose(model);
            applyStaticEmotion(model, forced, state);
            copyHeadToHat(model);
            return;
        }

        if (!emotions.shouldAnimate(player)) {
            return;
        }

        String selected = emotions.getSelectedEmotion();
        if (selected == null) {
            return;
        }

        float limbFrequency = state.walkAnimationPos;
        float limbAmplitude = state.walkAnimationSpeed;
        float movementDivisor = Math.max(1.0F, (float) player.getDeltaMovement().lengthSqr() / 0.2F);
        movementDivisor = movementDivisor * movementDivisor * movementDivisor;
        applyDynamicEmotion(selected, model, limbFrequency, limbAmplitude, movementDivisor);
        copyHeadToHat(model);
    }

    private static void applyAlphaWalk(HumanoidModel<?> model, float limbFrequency, float limbAmplitude, float divisor) {
        model.rightArm.xRot = Mth.cos(limbFrequency * 0.6662F + Mth.PI) * 2.0F * limbAmplitude / divisor;
        model.leftArm.xRot = Mth.cos(limbFrequency * 0.6662F) * 2.0F * limbAmplitude / divisor;
        float armRoll = (Mth.cos(limbFrequency * 0.2312F) + 1.0F) * limbAmplitude / divisor;
        model.rightArm.zRot = armRoll;
        model.leftArm.zRot = -armRoll;
        model.rightArm.yRot = 0.0F;
        model.leftArm.yRot = 0.0F;
        model.rightLeg.xRot = Mth.cos(limbFrequency * 0.6662F) * 1.4F * limbAmplitude / divisor;
        model.leftLeg.xRot = Mth.cos(limbFrequency * 0.6662F + Mth.PI) * 1.4F * limbAmplitude / divisor;
        float legRoll = Mth.cos(limbFrequency * 0.6662F) * 0.4F * limbAmplitude / divisor;
        model.rightLeg.zRot = legRoll;
        model.leftLeg.zRot = -legRoll;
        model.body.yRot = 0.0F;
        model.body.xRot = 0.0F;
        model.body.zRot = 0.0F;
    }

    private static void applyAlphaMale(HumanoidModel<?> model, float limbFrequency, float limbAmplitude, float divisor) {
        float time = (System.currentTimeMillis() % 10_000L) / 1000.0F;
        float amplitude = Math.max(0.25F, limbAmplitude);

        model.body.xRot = -0.1F + Mth.sin(time * 1.15F) * 0.04F;
        model.body.zRot = Mth.sin(time * 2.1F) * 0.14F;
        model.body.yRot = Mth.sin(time * 1.4F) * 0.09F;
        model.head.xRot = -0.18F + Mth.sin(time * 1.05F) * 0.05F;
        model.head.zRot = Mth.sin(time * 2.25F) * 0.11F;
        model.head.yRot = Mth.sin(time * 0.95F) * 0.13F;

        float phase = time % 2.8F / 2.8F;
        if (phase < 0.32F) {
            float wave = Mth.sin((phase / 0.32F) * Mth.PI);
            model.rightArm.xRot = -0.55F - wave * 1.05F;
            model.leftArm.xRot = -0.55F - wave * 1.05F;
            model.rightArm.yRot = -0.55F * wave;
            model.leftArm.yRot = 0.55F * wave;
            model.rightArm.zRot = -0.35F * wave;
            model.leftArm.zRot = 0.35F * wave;
        } else {
            float armTime = time * 2.0F;
            model.rightArm.xRot = -0.42F + Mth.sin(armTime) * 0.32F;
            model.leftArm.xRot = -0.32F + Mth.sin(armTime + Mth.PI) * 0.38F;
            model.rightArm.zRot = 0.22F + Mth.cos(armTime * 0.65F) * 0.22F;
            model.leftArm.zRot = -0.28F + Mth.cos(armTime * 0.65F) * 0.18F;
            model.rightArm.yRot = 0.28F + Mth.sin(armTime) * 0.18F;
            model.leftArm.yRot = -0.32F + Mth.sin(armTime) * 0.14F;
        }

        model.rightLeg.xRot = 0.12F + Mth.cos(limbFrequency * 0.6662F) * 1.15F * amplitude / divisor + Mth.sin(time * 3.2F) * 0.07F;
        model.leftLeg.xRot = 0.1F + Mth.cos(limbFrequency * 0.6662F + Mth.PI) * 1.15F * amplitude / divisor + Mth.sin(time * 3.2F) * 0.07F;
        model.rightLeg.zRot = 0.1F + Mth.sin(time * 2.05F) * 0.07F;
        model.leftLeg.zRot = -0.1F - Mth.sin(time * 2.05F) * 0.07F;
    }

    private static void applyStaticEmotion(HumanoidModel<?> model, String emotion, HumanoidRenderState state) {
        float limbFrequency = state.walkAnimationPos;
        float limbAmplitude = state.walkAnimationSpeed;
        switch (emotion) {
            case Emotions.GREETING -> {
                model.rightArm.xRot = -3.0F;
                model.rightArm.zRot = -0.5F;
            }
            case Emotions.DANCE -> {
                model.body.yRot = 0.2F;
                model.head.yRot = 0.3F;
                model.rightArm.xRot = -1.5F;
                model.rightArm.zRot = 0.5F;
                model.leftArm.xRot = -1.2F;
                model.leftArm.zRot = -0.5F;
                model.rightLeg.xRot = 0.3F;
                model.leftLeg.xRot = -0.3F;
            }
            case Emotions.MASTURBATION -> {
                model.rightArm.xRot = -0.3F;
                model.rightArm.zRot = -0.5F;
                model.head.xRot = 0.3F;
            }
            case Emotions.ALPHA_WALK -> applyAlphaWalk(model, limbFrequency, limbAmplitude, 1.0F);
            case Emotions.ALPHA_MALE -> applyAlphaMale(model, limbFrequency, limbAmplitude, 1.0F);
            default -> {
            }
        }
    }

    private static void applyDynamicEmotion(String emotion, HumanoidModel<?> model, float limbFrequency, float limbAmplitude, float divisor) {
        float time = (System.currentTimeMillis() % 10_000L) / 1000.0F;
        switch (emotion) {
            case Emotions.GREETING -> {
                float speed = 10.0F;
                float amount = 0.2F;
                model.rightArm.xRot = -3.0F;
                model.rightArm.yRot = Mth.sin(time * speed) * amount;
                model.rightArm.zRot = -0.5F + Mth.sin(time * speed) * amount;
                model.leftArm.xRot = 0.0F;
                model.rightLeg.xRot = Mth.cos(limbFrequency * 0.6662F) * 1.4F * limbAmplitude / divisor;
                model.leftLeg.xRot = Mth.cos(limbFrequency * 0.6662F + Mth.PI) * 1.4F * limbAmplitude / divisor;
                model.head.xRot = 0.0F;
            }
            case Emotions.DANCE -> {
                float danceTime = (System.currentTimeMillis() % 6000L) / 1000.0F;
                model.body.yRot = Mth.sin(danceTime * 2.0F) * 0.3F;
                model.body.xRot = Mth.sin(danceTime * 1.5F) * 0.1F;
                model.head.yRot = Mth.sin(danceTime * 1.8F) * 0.4F;
                model.head.xRot = Mth.sin(danceTime * 2.2F) * 0.2F;
                model.rightArm.xRot = -1.5F + Mth.sin(danceTime * 3.0F) * 0.8F;
                model.rightArm.yRot = Mth.sin(danceTime * 1.5F) * 0.5F;
                model.rightArm.zRot = Mth.sin(danceTime * 2.5F) * 0.7F;
                model.leftArm.xRot = -1.5F + Mth.sin(danceTime * 3.0F + Mth.PI) * 0.8F;
                model.leftArm.yRot = Mth.sin(danceTime * 1.5F + Mth.PI) * 0.5F;
                model.leftArm.zRot = Mth.sin(danceTime * 2.5F + Mth.PI) * 0.7F;
                model.rightLeg.xRot = 0.5F + Mth.sin(danceTime * 2.0F) * 0.6F;
                model.leftLeg.xRot = 0.5F + Mth.sin(danceTime * 2.0F + Mth.PI) * 0.6F;

                float phase = danceTime % 6.0F;
                if (phase > 3.0F && phase < 4.0F) {
                    float offset = phase - 3.0F;
                    model.body.y = offset * 2.0F;
                    model.rightLeg.y = 12.0F + offset * 2.0F;
                    model.leftLeg.y = 12.0F + offset * 2.0F;
                    model.rightArm.y = 2.0F + offset * 2.0F;
                    model.leftArm.y = 2.0F + offset * 2.0F;
                } else {
                    model.body.y = 0.0F;
                    model.rightLeg.y = 12.0F;
                    model.leftLeg.y = 12.0F;
                    model.rightArm.y = 2.0F;
                    model.leftArm.y = 2.0F;
                }
                if (phase > 4.5F && phase < 5.0F) {
                    float jump = Mth.sin((phase - 4.5F) * Mth.PI * 2.0F);
                    model.body.y = -jump * 1.5F;
                    model.head.y = jump * 1.5F;
                }
            }
            case Emotions.MASTURBATION -> {
                float speed = 30.0F;
                float amount = 0.2F;
                model.rightArm.xRot = -1.1F + Mth.sin(time * speed) * amount;
                model.rightArm.yRot = Mth.sin(time * speed) * amount;
                model.rightArm.zRot = -0.5F + Mth.sin(time * speed) * amount;
                model.leftArm.xRot = 0.0F;
                model.rightLeg.xRot = Mth.cos(limbFrequency * 0.6662F) * 1.4F * limbAmplitude / divisor;
                model.leftLeg.xRot = Mth.cos(limbFrequency * 0.6662F + Mth.PI) * 1.4F * limbAmplitude / divisor;
                model.head.xRot = 0.0F;
            }
            case Emotions.ALPHA_WALK -> applyAlphaWalk(model, limbFrequency, limbAmplitude, divisor);
            case Emotions.ALPHA_MALE -> applyAlphaMale(model, limbFrequency, limbAmplitude, divisor);
            default -> {
            }
        }
    }

    private static void resetHumanoidPose(HumanoidModel<?> model) {
        model.head.setRotation(0.0F, 0.0F, 0.0F);
        model.body.setRotation(0.0F, 0.0F, 0.0F);
        model.rightArm.setRotation(0.0F, 0.0F, 0.0F);
        model.leftArm.setRotation(0.0F, 0.0F, 0.0F);
        model.rightLeg.setRotation(0.0F, 0.0F, 0.0F);
        model.leftLeg.setRotation(0.0F, 0.0F, 0.0F);
        model.head.y = 0.0F;
        model.body.y = 0.0F;
        model.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        model.leftArm.setPos(5.0F, 2.0F, 0.0F);
        model.rightLeg.setPos(model.rightLeg.x, 12.0F, 0.1F);
        model.leftLeg.setPos(model.leftLeg.x, 12.0F, 0.1F);
    }

    private static void copyHeadToHat(HumanoidModel<?> model) {
        ModelPart head = model.head;
        ModelPart hat = model.hat;
        hat.x = head.x;
        hat.y = head.y;
        hat.z = head.z;
        hat.xRot = head.xRot;
        hat.yRot = head.yRot;
        hat.zRot = head.zRot;
        hat.xScale = head.xScale;
        hat.yScale = head.yScale;
        hat.zScale = head.zScale;
    }
}
