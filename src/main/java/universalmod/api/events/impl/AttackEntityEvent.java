package universalmod.api.events.impl;

import net.minecraft.world.entity.Entity;
import universalmod.api.events.Event;

public record AttackEntityEvent(Entity target) implements Event {
}
