package universalmod.utils.player;

public final class PingColors {
    private static final int GREY = 0x535353;
    private static final int GREEN = 0x00E676;
    private static final int YELLOW = 0xD6CD30;
    private static final int RED = 0xE53935;

    private PingColors() {
    }

    public static int getColor(int ping) {
        if (ping < 0) {
            return GREY;
        }
        if (ping <= 150) {
            return blend(GREEN, YELLOW, ping / 150.0f);
        }
        if (ping <= 300) {
            return blend(YELLOW, RED, (ping - 150) / 150.0f);
        }
        return RED;
    }

    private static int blend(int start, int end, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int startR = (start >> 16) & 255;
        int startG = (start >> 8) & 255;
        int startB = start & 255;
        int endR = (end >> 16) & 255;
        int endG = (end >> 8) & 255;
        int endB = end & 255;
        int red = Math.round(startR + (endR - startR) * clamped);
        int green = Math.round(startG + (endG - startG) * clamped);
        int blue = Math.round(startB + (endB - startB) * clamped);
        return (red << 16) | (green << 8) | blue;
    }
}
