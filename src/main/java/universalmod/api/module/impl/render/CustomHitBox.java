package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.CameraType;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;

import java.awt.Color;

public final class CustomHitBox extends Module {
    private static CustomHitBox instance;

    private final BooleanSetting eyeHitbox = register(new BooleanSetting("Eye Hitbox", "Draw eye height plane.", true));
    private final ColorSetting eyeColor = register(new ColorSetting("Eye Color", "Eye hitbox color.", new Color(255, 0, 0, 255)));
    private final BooleanSetting viewingLine = register(new BooleanSetting("Viewing Line", "Draw entity look vector.", true));
    private final ColorSetting viewingLineColor = register(new ColorSetting("Viewing Line Color", "Look vector color.", new Color(0, 0, 255, 255)));

    private final BooleanSetting players = register(new BooleanSetting("Players", "Render player hitboxes.", true));
    private final ColorSetting playersColor = register(new ColorSetting("Players Color", "Player hitbox color.", new Color(255, 255, 255, 255)));
    private final BooleanSetting animals = register(new BooleanSetting("Animals", "Render passive mob hitboxes.", true));
    private final ColorSetting animalsColor = register(new ColorSetting("Animals Color", "Animal hitbox color.", new Color(255, 255, 255, 255)));
    private final BooleanSetting monsters = register(new BooleanSetting("Monsters", "Render monster hitboxes.", true));
    private final ColorSetting monstersColor = register(new ColorSetting("Monsters Color", "Monster hitbox color.", new Color(255, 255, 255, 255)));
    private final BooleanSetting throwables = register(new BooleanSetting("Throwables", "Render projectile hitboxes.", true));
    private final ColorSetting throwablesColor = register(new ColorSetting("Throwables Color", "Projectile hitbox color.", new Color(255, 255, 255, 255)));
    private final BooleanSetting drop = register(new BooleanSetting("Drop", "Render dropped item hitboxes.", true));
    private final ColorSetting dropColor = register(new ColorSetting("Drop Color", "Dropped item hitbox color.", new Color(255, 255, 255, 255)));
    private final BooleanSetting misc = register(new BooleanSetting("Misc", "Render miscellaneous entity hitboxes.", true));
    private final ColorSetting miscColor = register(new ColorSetting("Misc Color", "Miscellaneous hitbox color.", new Color(255, 255, 255, 255)));

    public CustomHitBox() {
        super("Custom HitBox", "Shows entity hitboxes.", ModuleCategory.RENDER);
        instance = this;
        eyeColor.visibleWhen(eyeHitbox::getValue);
        viewingLineColor.visibleWhen(viewingLine::getValue);
        playersColor.visibleWhen(players::getValue);
        animalsColor.visibleWhen(animals::getValue);
        monstersColor.visibleWhen(monsters::getValue);
        throwablesColor.visibleWhen(throwables::getValue);
        dropColor.visibleWhen(drop::getValue);
        miscColor.visibleWhen(misc::getValue);
    }

    public static void emitCustomGizmos(Minecraft client, Frustum frustum, float tickDelta) {
        CustomHitBox module = instance;
        if (module == null || !module.isEnabled() || !shouldReplaceVanilla(client) || client == null || client.level == null || client.player == null) {
            return;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!module.shouldRender(client, entity, frustum)) {
                continue;
            }

            Color color = module.colorFor(entity);
            if (color == null) {
                continue;
            }

            module.drawEntityBox(entity, color, tickDelta);
            if (module.eyeHitbox.getValue() && entity instanceof net.minecraft.world.entity.LivingEntity) {
                module.drawEyeHitbox(entity, module.eyeColor.getValue(), tickDelta);
            }
            if (module.viewingLine.getValue()) {
                module.drawViewingLine(entity, module.viewingLineColor.getValue(), tickDelta);
            }
        }
    }

    private boolean shouldRender(Minecraft client, Entity entity, Frustum frustum) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entity.isInvisible()) {
            return false;
        }
        if (frustum != null && !frustum.isVisible(entity.getBoundingBox())) {
            return false;
        }
        if (entity == client.getCameraEntity() && client.options.getCameraType() == CameraType.FIRST_PERSON) {
            return false;
        }
        return colorFor(entity) != null;
    }

    public static boolean shouldReplaceVanilla(Minecraft client) {
        return instance != null
                && instance.isEnabled()
                && client != null
                && client.debugEntries != null
                && client.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    private Color colorFor(Entity entity) {
        if (entity instanceof Player) {
            return players.getValue() ? playersColor.getValue() : null;
        }
        if (isAnimal(entity)) {
            return animals.getValue() ? animalsColor.getValue() : null;
        }
        if (entity instanceof Monster) {
            return monsters.getValue() ? monstersColor.getValue() : null;
        }
        if (entity instanceof Projectile) {
            return throwables.getValue() ? throwablesColor.getValue() : null;
        }
        if (entity instanceof ItemEntity) {
            return drop.getValue() ? dropColor.getValue() : null;
        }
        return misc.getValue() ? miscColor.getValue() : null;
    }

    private boolean isAnimal(Entity entity) {
        return entity instanceof Animal
                || entity instanceof WaterAnimal
                || entity instanceof AmbientCreature
                || entity instanceof AbstractVillager;
    }

    private void drawEntityBox(Entity entity, Color color, float tickDelta) {
        AABB box = interpolatedBoundingBox(entity, tickDelta);
        Gizmos.cuboid(box, GizmoStyle.stroke(color.getRGB()));
    }

    private void drawEyeHitbox(Entity entity, Color color, float tickDelta) {
        AABB box = interpolatedBoundingBox(entity, tickDelta);
        double eyeY = box.minY + entity.getEyeHeight();
        double eyeMinY = eyeY - 0.01D;
        double eyeMaxY = eyeY + 0.01D;
        AABB eyeBox = new AABB(box.minX, eyeMinY, box.minZ, box.maxX, eyeMaxY, box.maxZ);
        Gizmos.cuboid(eyeBox, GizmoStyle.stroke(color.getRGB()));
    }

    private void drawViewingLine(Entity entity, Color color, float tickDelta) {
        Vec3 start = entity.getPosition(tickDelta).add(0.0D, entity.getEyeHeight(), 0.0D);
        Vec3 look = entity.getViewVector(tickDelta);
        if (look.lengthSqr() <= 1.0E-8D) {
            return;
        }
        Vec3 end = start.add(look.scale(2.0D));
        Gizmos.line(start, end, color.getRGB());
    }

    private AABB interpolatedBoundingBox(Entity entity, float tickDelta) {
        AABB box = entity.getBoundingBox();
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return box.move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }
}
