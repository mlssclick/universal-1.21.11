package universalmod.utils.render.crosshair;

public enum CrosshairCanvasPreset {
    DEFAULT(buildDefault()),
    DEFAULT_WITH_DOT(buildDefaultWithDot()),
    DEFAULT_WITH_GAP(buildDefaultWithGap()),
    CROSS(buildCross()),
    CROSS_WITH_DOT(buildCrossWithDot()),
    CROSS_WITH_GAP(buildCrossWithGap()),
    CIRCLE(buildCircle()),
    CIRCLE_WITH_DOT(buildCircleWithDot()),
    CROSSHAIR(buildCrosshair()),
    CROSSHAIR_WITH_DOT(buildCrosshairWithDot());

    private final CrosshairCanvas canvas;

    CrosshairCanvasPreset(CrosshairCanvas canvas) {
        this.canvas = canvas;
    }

    public CrosshairCanvas getCanvas() {
        return canvas.copy();
    }

    public static CrosshairCanvasPreset of(CrosshairCanvas canvas) {
        if (canvas == null) {
            return DEFAULT;
        }
        for (CrosshairCanvasPreset preset : values()) {
            if (samePixels(preset.canvas, canvas)) {
                return preset;
            }
        }
        return DEFAULT;
    }

    private static boolean samePixels(CrosshairCanvas first, CrosshairCanvas second) {
        boolean[] firstPixels = first.getPixels();
        boolean[] secondPixels = second.getPixels();
        if (firstPixels.length != secondPixels.length) {
            return false;
        }
        for (int i = 0; i < firstPixels.length; i++) {
            if (firstPixels[i] != secondPixels[i]) {
                return false;
            }
        }
        return true;
    }

    private static CrosshairCanvas buildDefault() {
        CrosshairCanvas canvas = new CrosshairCanvas();
        for (int offset = -4; offset <= 4; offset++) {
            canvas.enableFromCenter(0, offset);
            if (offset != 0) {
                canvas.enableFromCenter(offset, 0);
            }
        }
        return canvas;
    }

    private static CrosshairCanvas buildDefaultWithDot() {
        CrosshairCanvas canvas = DEFAULT.getCanvas();
        canvas.disableFromCenter(-1, 0);
        canvas.disableFromCenter(0, 1);
        canvas.disableFromCenter(1, 0);
        canvas.disableFromCenter(0, -1);
        return canvas;
    }

    private static CrosshairCanvas buildDefaultWithGap() {
        CrosshairCanvas canvas = DEFAULT_WITH_DOT.getCanvas();
        canvas.disableFromCenter(0, 0);
        canvas.enableFromCenter(-5, 0);
        canvas.enableFromCenter(0, -5);
        canvas.enableFromCenter(5, 0);
        canvas.enableFromCenter(0, 5);
        return canvas;
    }

    private static CrosshairCanvas buildCross() {
        CrosshairCanvas canvas = DEFAULT.getCanvas();
        int[][] points = {
                {-3, -2}, {-3, -3}, {-2, -3},
                {2, -3}, {3, -3}, {3, -2},
                {3, 2}, {3, 3}, {2, 3},
                {-2, 3}, {-3, 3}, {-3, 2}
        };
        for (int[] point : points) {
            canvas.enableFromCenter(point[0], point[1]);
        }
        return canvas;
    }

    private static CrosshairCanvas buildCrossWithDot() {
        CrosshairCanvas canvas = CROSS.getCanvas();
        canvas.disableFromCenter(-1, 0);
        canvas.disableFromCenter(0, 1);
        canvas.disableFromCenter(1, 0);
        canvas.disableFromCenter(0, -1);
        return canvas;
    }

    private static CrosshairCanvas buildCrossWithGap() {
        CrosshairCanvas canvas = CROSS_WITH_DOT.getCanvas();
        canvas.disableFromCenter(0, 0);
        return canvas;
    }

    private static CrosshairCanvas buildCircle() {
        CrosshairCanvas canvas = new CrosshairCanvas();
        int[][] points = {
                {-3, 0}, {-3, -1}, {-2, -2}, {-1, -3},
                {0, -3}, {1, -3}, {2, -2}, {3, -1},
                {3, 0}, {3, 1}, {2, 2}, {1, 3},
                {0, 3}, {-1, 3}, {-2, 2}, {-3, 1}
        };
        for (int[] point : points) {
            canvas.enableFromCenter(point[0], point[1]);
        }
        return canvas;
    }

    private static CrosshairCanvas buildCircleWithDot() {
        CrosshairCanvas canvas = CIRCLE.getCanvas();
        canvas.enableFromCenter(0, 0);
        return canvas;
    }

    private static CrosshairCanvas buildCrosshair() {
        CrosshairCanvas canvas = DEFAULT_WITH_GAP.getCanvas();
        int[][] points = {
                {-4, 2}, {-4, 1}, {-4, -1}, {-4, -2},
                {-2, -4}, {-1, -4}, {1, -4}, {2, -4},
                {4, -2}, {4, -1}, {4, 1}, {4, 2},
                {-2, 4}, {-1, 4}, {1, 4}, {2, 4},
                {-3, -3}, {3, -3}, {3, 3}, {-3, 3}
        };
        for (int[] point : points) {
            canvas.enableFromCenter(point[0], point[1]);
        }
        return canvas;
    }

    private static CrosshairCanvas buildCrosshairWithDot() {
        CrosshairCanvas canvas = CROSSHAIR.getCanvas();
        canvas.enableFromCenter(0, 0);
        return canvas;
    }
}
