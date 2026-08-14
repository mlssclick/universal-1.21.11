package universalmod.api.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.AttackEntityEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.render.richdog.PetBrain;
import universalmod.api.module.impl.render.richdog.PetModel;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.StringSetting;
import universalmod.utils.render.Render3D;
import universalmod.utils.timer.TimerUtil;

public final class RichDog extends Module {
    private static final long TARGET_TIMEOUT_MS = 20_000L;
    private static final Identifier TEX_JACK_RUSSELL =
            Identifier.fromNamespaceAndPath("dog", "textures/djekrussel.png");
    private static final Identifier TEX_DACHSHUND =
            Identifier.fromNamespaceAndPath("dog", "textures/taksa.png");

    private final PetModel model = new PetModel();
    private final PetBrain brain = new PetBrain();

    private final ModeSetting skin = register(new ModeSetting(
            "Skin", "Dog breed.", "Jack Russell", "Jack Russell", "Dachshund"));
    private final BooleanSetting showNameTag = register(new BooleanSetting(
            "Show Name Tag", "Displays the name tag above the dog.", true));
    private final StringSetting nameTag = createNameTagSetting();
    private final BooleanSetting attackPlayers = register(new BooleanSetting(
            "Attack Player", "Let the dog attack the player you manually hit.", true));

    private Player targetPlayer;
    private final TimerUtil targetTimer = new TimerUtil();

    public RichDog() {
        super("Pesik", "A loyal follower dog.", ModuleCategory.RENDER);
    }

    @Override
    protected void onDisable() {
        targetPlayer = null;
        targetTimer.resetCounter();
        brain.setEntity(null);
        brain.setTarget(null);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        brain.setEntity(client.player);
        brain.setTarget(resolveTarget(client.player));
        brain.onUpdate();
    }

    @SubscribeEvent
    private void onAttack(AttackEntityEvent event) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return;
        }
        if (event.target() instanceof Player player
                && player != mc.player
                && !player.isSpectator()
                && !isInvisibleWithoutArmor(player)) {
            targetPlayer = player;
            targetTimer.resetCounter();
        }
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        brain.setEntity(mc.player);
        brain.setTarget(resolveTarget(mc.player));

        PoseStack pose = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 render = brain.getPos().subtract(cam);

        Identifier texture = skin.is("Dachshund") ? TEX_DACHSHUND : TEX_JACK_RUSSELL;
        RenderType renderType = RenderTypes.entityCutoutNoCull(texture);
        VertexConsumer consumer = provider.getBuffer(renderType);

        pose.pushPose();
        pose.translate(render.x, render.y, render.z);
        model.setupAnim(mc.player.tickCount + event.getPartialTicks(), brain);
        model.render(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, brain);
        pose.popPose();
        provider.endBatch(renderType);
        renderVanillaNameTag(pose, provider, cam);
        provider.endBatch();
    }

    private void renderVanillaNameTag(PoseStack pose, MultiBufferSource.BufferSource provider, Vec3 cameraPos) {
        if (!showNameTag.getValue() || mc.font == null) {
            return;
        }
        String text = nameTag.getValue();
        if (text == null || text.isBlank()) {
            return;
        }
        Vec3 dogPos = brain.getPos();
        if (!isFinite(dogPos) || dogPos.distanceToSqr(cameraPos) > 4096.0D) {
            return;
        }

        Component component = CustomDonate.parseFormatted(text);
        Font font = mc.font;
        float textX = -font.width(component) / 2.0F;
        float backgroundOpacity = mc.options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;
        Vec3 render = dogPos.subtract(cameraPos);

        pose.pushPose();
        pose.translate(render.x, render.y + 1.35F, render.z);
        pose.mulPose(Render3D.lastCameraRotation);
        pose.scale(0.025F, -0.025F, 0.025F);

        font.drawInBatch(
                component,
                textX,
                0.0F,
                -1,
                false,
                pose.last().pose(),
                provider,
                Font.DisplayMode.SEE_THROUGH,
                backgroundColor,
                LightTexture.FULL_BRIGHT
        );
        font.drawInBatch(
                component,
                textX,
                0.0F,
                -1,
                false,
                pose.last().pose(),
                provider,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
        pose.popPose();
    }

    private static boolean isFinite(Vec3 position) {
        return position != null
                && Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private StringSetting createNameTagSetting() {
        StringSetting setting = new StringSetting(
                "Name Tag",
                "Displayed name above the dog.",
                "Pesik",
                512
        );
        setting.visibleWhen(showNameTag::getValue);
        return register(setting);
    }

    private Player resolveTarget(Player owner) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer != null && targetTimer.isReached(TARGET_TIMEOUT_MS)) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer == null || targetPlayer.isRemoved() || !targetPlayer.isAlive() || targetPlayer.level() != owner.level()) {
            targetPlayer = null;
            return null;
        }
        if (isInvisibleWithoutArmor(targetPlayer)) {
            targetPlayer = null;
            return null;
        }
        return targetPlayer;
    }

    private boolean isInvisibleWithoutArmor(Player player) {
        if (player == null || !player.isInvisible()) {
            return false;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }
}
