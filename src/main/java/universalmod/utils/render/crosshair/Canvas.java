package universalmod.utils.render.crosshair;

import java.util.Arrays;

public class Canvas {
    private final int width;
    private final int height;
    private final int centerX;
    private final int centerY;
    private final boolean[] pixels;

    public Canvas(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        this.pixels = new boolean[this.width * this.height];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public boolean[] getPixels() {
        return pixels;
    }

    public boolean isPixelActive(int x, int y) {
        return inBounds(x, y) && pixels[index(x, y)];
    }

    public void enablePixel(int x, int y) {
        if (inBounds(x, y)) {
            pixels[index(x, y)] = true;
        }
    }

    public void disablePixel(int x, int y) {
        if (inBounds(x, y)) {
            pixels[index(x, y)] = false;
        }
    }

    public void setPixel(int x, int y, boolean active) {
        if (active) {
            enablePixel(x, y);
        } else {
            disablePixel(x, y);
        }
    }

    public void togglePixel(int x, int y) {
        if (inBounds(x, y)) {
            pixels[index(x, y)] = !pixels[index(x, y)];
        }
    }

    public void enableFromCenter(int offsetX, int offsetY) {
        enablePixel(centerX + offsetX, centerY + offsetY);
    }

    public void disableFromCenter(int offsetX, int offsetY) {
        disablePixel(centerX + offsetX, centerY + offsetY);
    }

    public void clear() {
        Arrays.fill(pixels, false);
    }

    public void copyPixelsTo(Canvas other) {
        if (other == null) {
            return;
        }
        int length = Math.min(pixels.length, other.pixels.length);
        System.arraycopy(pixels, 0, other.pixels, 0, length);
        if (other.pixels.length > length) {
            Arrays.fill(other.pixels, length, other.pixels.length, false);
        }
    }

    public int getActivePixelCount() {
        int count = 0;
        for (boolean pixel : pixels) {
            if (pixel) {
                count++;
            }
        }
        return count;
    }

    private int index(int x, int y) {
        return x + width * y;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }
}
