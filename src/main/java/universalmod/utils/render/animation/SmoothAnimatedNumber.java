package universalmod.utils.render.animation;

import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public final class SmoothAnimatedNumber {
    private final FontType font;
    private final float size;
    private final float offset;
    private final long speedMs;
    private final Easing easing;
    private final String[] numbers = {"", ""};
    private final String[] previous = {"", ""};
    private final long[] changedAt = {0L, 0L};
    private boolean drawZero;
    private float lastOffset;

    public SmoothAnimatedNumber(FontType font, float size, float offset, long speedMs, Easing easing, boolean drawZero) {
        this.font = font;
        this.size = size;
        this.offset = offset;
        this.speedMs = Math.max(1L, speedMs);
        this.easing = easing == null ? Easings.FIGMA_EASE_IN_OUT : easing;
        this.drawZero = drawZero;
    }

    public void update(int updated) {
        int safe = Math.max(0, updated);
        String first = String.valueOf(safe / 10);
        String second = String.valueOf(safe % 10);
        long now = System.currentTimeMillis();

        if (!second.equals(numbers[1])) {
            String oldFull = numbers[0] + numbers[1];
            lastOffset = oldFull.isEmpty() ? 0.0F
                    : Render2D.textGlyphPositionX(font, oldFull, Math.max(0, numbers[0].length()), size);
            previous[1] = numbers[1];
            numbers[1] = second;
            changedAt[1] = now;
        }

        String displayedFirst = drawZero ? first : ("0".equals(first) ? "" : first);
        if (!displayedFirst.equals(numbers[0])) {
            previous[0] = numbers[0];
            numbers[0] = displayedFirst;
            changedAt[0] = now;
        }

    }

    public void render(float x, float y, int color) {
        float first = progress(0);
        float second = progress(1);

        draw(previous[0], x, y + offset * first, color, 1.0F - first);
        draw(numbers[0], x, y - offset + offset * first, color, first);
        draw(previous[1], x + lastOffset, y + offset * second, color, 1.0F - second);
        String fullCurrent = numbers[0] + numbers[1];
        float currentSecondX = fullCurrent.isEmpty() ? 0.0F
                : Render2D.textGlyphPositionX(font, fullCurrent, Math.max(0, numbers[0].length()), size);
        draw(numbers[1], x + currentSecondX, y - offset + offset * second, color, second);
    }

    public void renderFixedTwoDigits(float x, float y, int color) {
        String firstCurrent = normalizedDigit(numbers[0], true);
        String secondCurrent = normalizedDigit(numbers[1], false);
        String pair = firstCurrent + secondCurrent;
        float secondX = Render2D.textGlyphPositionX(font, pair, 1, size);

        float firstProgress = progress(0);
        float secondProgress = progress(1);

        draw(previous[0], x, y + offset * firstProgress, color, 1.0F - firstProgress);
        draw(firstCurrent, x, y - offset + offset * firstProgress, color, firstProgress);
        float previousSecondX = previous[1] == null || previous[1].isEmpty() ? secondX : lastOffset;
        draw(previous[1], x + previousSecondX, y + offset * secondProgress, color, 1.0F - secondProgress);
        draw(secondCurrent, x + secondX, y - offset + offset * secondProgress, color, secondProgress);
    }

    public void renderTimer(String prefix, float x, float y, int color) {
        String safePrefix = prefix == null ? "" : prefix;
        String seconds = currentTwoDigitText();
        if (seconds.length() < 2) {
            seconds = "0" + seconds;
        }
        String full = safePrefix + seconds;
        int firstIndex = Math.max(0, full.length() - 2);
        int secondIndex = Math.max(0, full.length() - 1);
        Render2D.animatedGlyphText(
                font,
                full,
                x,
                y,
                size,
                color,
                firstIndex,
                previous[0],
                progress(0),
                secondIndex,
                previous[1],
                progress(1),
                offset
        );
    }

    public float timerWidth(String prefix) {
        String safePrefix = prefix == null ? "" : prefix;
        String seconds = currentTwoDigitText();
        if (seconds.length() < 2) {
            seconds = "0" + seconds;
        }
        return Render2D.textWidth(font, safePrefix + seconds, size);
    }

    public float fixedTwoDigitWidth() {
        return Render2D.textWidth(font, currentTwoDigitText(), size);
    }

    public String currentTwoDigitText() {
        String firstCurrent = numbers[0] == null || numbers[0].isEmpty() ? (drawZero ? "0" : "") : numbers[0];
        String secondCurrent = numbers[1] == null || numbers[1].isEmpty() ? "0" : numbers[1];
        return firstCurrent + secondCurrent;
    }

    private String normalizedDigit(String value, boolean first) {
        if (value == null || value.isEmpty()) {
            return first && !drawZero ? "" : "0";
        }
        return value;
    }

    public float width() {
        return Render2D.textWidth(font, numbers[0] + numbers[1], size);
    }

    public void setDrawZero(boolean drawZero) {
        this.drawZero = drawZero;
    }

    private float progress(int digit) {
        long start = changedAt[digit];
        if (start <= 0L) {
            return 1.0F;
        }
        double raw = Math.max(0.0, Math.min(1.0, (System.currentTimeMillis() - start) / (double) speedMs));
        return (float) easing.ease(raw);
    }

    private void draw(String text, float x, float y, int color, float alphaMultiplier) {
        if (text == null || text.isEmpty() || alphaMultiplier <= 0.001F) {
            return;
        }
        int baseAlpha = (color >>> 24) & 0xFF;
        int alpha = Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, alphaMultiplier)));
        Render2D.text(font, text, x, y, size, (color & 0x00FFFFFF) | (alpha << 24));
    }
}
