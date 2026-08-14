package universalmod.utils.render.crosshair;

import java.util.Base64;

public final class CrosshairCanvas extends Canvas {
    public static final int SIZE = 15;
    private static final String PREFIX = "LMCH-";

    public CrosshairCanvas() {
        super(SIZE, SIZE);
    }

    public CrosshairCanvas copy() {
        CrosshairCanvas copy = new CrosshairCanvas();
        copyPixelsTo(copy);
        return copy;
    }

    public int getSize() {
        int minX = SIZE;
        int maxX = -1;
        int minY = SIZE;
        int maxY = -1;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!isPixelActive(x, y)) {
                    continue;
                }
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return 0;
        }

        return Math.max(maxX - minX + 1, maxY - minY + 1);
    }

    public String encode() {
        byte[] data = new byte[(SIZE * SIZE + 7) / 8];
        boolean[] pixels = getPixels();
        for (int i = 0; i < pixels.length; i++) {
            if (!pixels[i]) {
                continue;
            }
            data[i / 8] |= (byte) (1 << (i % 8));
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static CrosshairCanvas decode(String value) {
        if (value == null || value.isBlank()) {
            return CrosshairCanvasPreset.DEFAULT.getCanvas();
        }
        if (!value.startsWith(PREFIX)) {
            return CrosshairCanvasPreset.DEFAULT.getCanvas();
        }

        try {
            byte[] data = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            CrosshairCanvas canvas = new CrosshairCanvas();
            boolean[] pixels = canvas.getPixels();
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = i / 8 < data.length && ((data[i / 8] >> (i % 8)) & 1) == 1;
            }
            return canvas;
        } catch (IllegalArgumentException ignored) {
            return CrosshairCanvasPreset.DEFAULT.getCanvas();
        }
    }
}
