package universalmod.api.command.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import universalmod.api.command.Command;
import universalmod.utils.waypoints.WaypointDefinition;
import universalmod.utils.waypoints.WaypointManager;

import java.util.Arrays;
import java.util.stream.Stream;

public final class GpsCommand extends Command {
    public GpsCommand() {
        super("gps", "Adds and manages waypoints.", "waypoint", "waypoints");
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            usage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> add(args);
            case "list" -> list();
            case "remove", "delete", "del" -> remove(args);
            case "clear" -> clear(args);
            default -> usage();
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Stream.of("add", "list", "remove", "clear").filter(value -> value.startsWith(prefix));
        }
        return Stream.empty();
    }

    private void add(String[] args) {
        if (args.length < 4) {
            logDirect("Usage: .gps add <x> <y> <z> [name]", ChatFormatting.RED);
            return;
        }
        Double x = parseCoordinate(args[1]);
        Double y = parseCoordinate(args[2]);
        Double z = parseCoordinate(args[3]);
        if (x == null || y == null || z == null) {
            logDirect("Coordinates must be numbers.", ChatFormatting.RED);
            return;
        }
        String name = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "Waypoint";
        WaypointDefinition waypoint = WaypointManager.getInstance().addManual(x, y, z, name, Minecraft.getInstance());
        logDirect(Component.literal("Waypoint added: ").withStyle(ChatFormatting.GREEN).append(WaypointManager.formatWaypoint(waypoint)));
    }

    private void list() {
        var waypoints = WaypointManager.getInstance().manualWaypoints();
        if (waypoints.isEmpty()) {
            logDirect("No waypoints.", ChatFormatting.RED);
            return;
        }
        logDirect("Waypoints:", ChatFormatting.GREEN);
        for (WaypointDefinition waypoint : waypoints) {
            logDirect(Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY).append(WaypointManager.formatWaypoint(waypoint)));
        }
    }

    private void remove(String[] args) {
        if (args.length < 2) {
            logDirect("Usage: .gps remove <name|uuid>", ChatFormatting.RED);
            return;
        }
        int removed = WaypointManager.getInstance().removeManual(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (removed == 0) {
            logDirect("Waypoint not found.", ChatFormatting.RED);
        } else {
            logDirect("Removed " + removed + " waypoint(s).", ChatFormatting.GREEN);
        }
    }

    private void clear(String[] args) {
        if (args.length > 1 && "auto".equalsIgnoreCase(args[1])) {
            int removed = WaypointManager.getInstance().clearAutomatic();
            logDirect("Cleared " + removed + " auto waypoint(s).", ChatFormatting.GREEN);
            return;
        }
        int removed = WaypointManager.getInstance().clearManual();
        logDirect("Cleared " + removed + " waypoint(s).", ChatFormatting.GREEN);
    }

    private void usage() {
        logDirect("Usage: .gps add <x> <y> <z> [name]");
        logDirect("Also: .gps list, .gps remove <name|uuid>, .gps clear, .gps clear auto");
    }

    private static Double parseCoordinate(String value) {
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
