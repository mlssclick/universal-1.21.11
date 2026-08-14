package universalmod.utils.render.animation;

public final class Easings {
    public static final Easing LINEAR = new Easing() {
        @Override
        public double ease(double value) {
            return value;
        }
    };

    public static final Easing QUAD_OUT = new Easing() {
        @Override
        public double ease(double value) {
            return 1.0 - Math.pow(1.0 - value, 2);
        }
    };

    public static final Easing CUBIC_OUT = new Easing() {
        @Override
        public double ease(double value) {
            return 1.0 - Math.pow(1.0 - value, 3);
        }
    };

    public static final Easing EXPO_IN = new Easing() {
        @Override
        public double ease(double value) {
            return value == 0 ? 0 : Math.pow(2.0, 10.0 * value - 10.0);
        }
    };

    public static final Easing EXPO_OUT = new Easing() {
        @Override
        public double ease(double value) {
            return value == 1 ? 1 : 1.0 - Math.pow(2.0, -10.0 * value);
        }
    };

    public static final Easing EXPO_IN_OUT = new Easing() {
        @Override
        public double ease(double value) {
            if (value == 0 || value == 1) {
                return value;
            }
            return value < 0.5
                    ? Math.pow(2.0, 20.0 * value - 10.0) / 2.0
                    : (2.0 - Math.pow(2.0, -20.0 * value + 10.0)) / 2.0;
        }
    };

    public static final Easing SINE_OUT = new Easing() {
        @Override
        public double ease(double value) {
            return Math.sin(value * Math.PI / 2.0);
        }
    };

    /** Matches EasingList.i from the imported ClickGUI reference. */
    public static final Easing CIRC_IN = new Easing() {
        @Override
        public double ease(double value) {
            double clamped = Math.max(0.0, Math.min(1.0, value));
            return 1.0 - Math.sqrt(1.0 - clamped * clamped);
        }
    };

    public static final Easing BACK_OUT = new Easing() {
        @Override
        public double ease(double value) {
            double c1 = 1.70158;
            double c3 = c1 + 1;
            return 1.0 + c3 * Math.pow(value - 1.0, 3.0) + c1 * Math.pow(value - 1.0, 2.0);
        }
    };

    public static final Easing BAKEK = cubicBezier(0.45, 1.45, 0.49, 1.15);

    public static final Easing FIGMA_EASE_IN_OUT = cubicBezier(0.42, 0.0, 0.58, 1.0);

    private static Easing cubicBezier(double x1, double y1, double x2, double y2) {
        return value -> {
            double progress = Math.max(0.0, Math.min(1.0, value));
            double t = progress;
            for (int i = 0; i < 8; i++) {
                double omt = 1.0 - t;
                double x = 3.0 * omt * omt * t * x1 + 3.0 * omt * t * t * x2 + t * t * t;
                double dx = 3.0 * (omt * (1.0 - 3.0 * t) * x1 + (2.0 * t - 3.0 * t * t) * x2) + 3.0 * t * t;
                if (Math.abs(x - progress) < 1.0E-5 || Math.abs(dx) < 1.0E-6) break;
                t -= (x - progress) / dx;
                t = Math.max(0.0, Math.min(1.0, t));
            }
            double omt = 1.0 - t;
            return 3.0 * omt * omt * t * y1 + 3.0 * omt * t * t * y2 + t * t * t;
        };
    }

    private Easings() {
    }
}
