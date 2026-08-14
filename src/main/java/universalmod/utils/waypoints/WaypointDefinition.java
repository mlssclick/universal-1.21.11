package universalmod.utils.waypoints;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public final class WaypointDefinition {
    public enum Source {
        MANUAL,
        DEATH,
        AUTO_EVENT,
        AUTO_TREASURE
    }

    private final UUID id;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final String dimension;
    private final String serverKey;
    private final Source source;
    private final long createdAtMillis;

    public WaypointDefinition(UUID id, String name, double x, double y, double z, String dimension, String serverKey, Source source, long createdAtMillis) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = sanitizeName(name, source);
        this.x = sanitizeCoordinate(x);
        this.y = sanitizeCoordinate(y);
        this.z = sanitizeCoordinate(z);
        this.dimension = sanitizeDimension(dimension);
        this.serverKey = serverKey == null ? "" : serverKey.trim().toLowerCase();
        this.source = source == null ? Source.MANUAL : source;
        this.createdAtMillis = Math.max(0L, createdAtMillis);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String dimension() {
        return dimension;
    }

    public String serverKey() {
        return serverKey;
    }

    public Source source() {
        return source;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public boolean manual() {
        return source == Source.MANUAL;
    }

    public Vec3 anchorPosition() {
        return new Vec3(x + 0.5d, y + 1.15d, z + 0.5d);
    }

    public WaypointDefinition copy() {
        return new WaypointDefinition(id, name, x, y, z, dimension, serverKey, source, createdAtMillis);
    }

    public boolean sameLocationScope(WaypointDefinition other) {
        return other != null
                && Objects.equals(dimension, other.dimension)
                && Objects.equals(serverKey, other.serverKey)
                && source == other.source;
    }

    public static String sanitizeDimension(String value) {
        String sanitized = value == null ? "" : value.trim().toLowerCase();
        return sanitized.isEmpty() ? "minecraft:overworld" : sanitized;
    }

    private static String sanitizeName(String value, Source source) {
        String sanitized = value == null ? "" : value.trim();
        if (!sanitized.isEmpty()) {
            return sanitized;
        }
        return switch (source == null ? Source.MANUAL : source) {
            case AUTO_TREASURE -> "[auto] Сокровище";
            case AUTO_EVENT -> "[auto] Event";
            case DEATH -> "Death";
            case MANUAL -> "Waypoint";
        };
    }

    private static double sanitizeCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0d ? value : 0.0d;
    }
}
