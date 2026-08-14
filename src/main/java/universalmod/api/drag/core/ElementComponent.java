package universalmod.api.drag.core;

public final class ElementComponent {
    private static final float DEFAULT_MIN_WIDTH = 12.0f;
    private static final float DEFAULT_MIN_HEIGHT = 12.0f;
    private static final float DEFAULT_SCREEN_MARGIN = 4.0f;
    private static final float SNAP_DISTANCE = 10.0f;
    private static final float LERP_SPEED = 0.19f;
    private static final float MAX_TILT_DEGREES = 25.0f;
    private static final float TILT_FROM_MOUSE_DELTA = 4.0f;
    private static final float DRAG_TILT_LERP = 0.14f;
    private static final float RELEASE_TILT_LERP = 0.10f;
    private static final float TILT_DELTA_SMOOTHING = 0.18f;
    private static final float TILT_TARGET_SMOOTHING = 0.22f;
    private static final float TILT_DEADZONE = 0.18f;
    private static final float DRAG_SCALE_MULTIPLIER = 1.01f;
    private static final float DRAG_SCALE_LERP = 0.10f;
    private static final float RELEASE_SCALE_LERP = 0.02f;

    private final String id;
    private final String title;
    private final float defaultX;
    private final float defaultY;
    private float targetX;
    private float targetY;
    private float x;
    private float y;
    private float visualOffsetX;
    private float visualOffsetY;
    private float width = DEFAULT_MIN_WIDTH;
    private float height = DEFAULT_MIN_HEIGHT;
    private float minWidth = DEFAULT_MIN_WIDTH;
    private float minHeight = DEFAULT_MIN_HEIGHT;
    private float hitExpandLeft;
    private float hitExpandTop;
    private float hitExpandRight;
    private float hitExpandBottom;
    private float screenMarginLeft = DEFAULT_SCREEN_MARGIN;
    private float screenMarginTop = DEFAULT_SCREEN_MARGIN;
    private float screenMarginRight = DEFAULT_SCREEN_MARGIN;
    private float screenMarginBottom = DEFAULT_SCREEN_MARGIN;
    private boolean visible = true;
    private boolean locked;
    private boolean moving;
    private boolean positionCustomized;
    private float pointerOffsetX;
    private float pointerOffsetY;
    private float dragTiltDegrees;
    private float targetTiltDegrees;
    private float smoothedMouseDeltaX;
    private float previousMouseX;
    private boolean hasPreviousMouseX;
    private float dragScale = 1.0f;
    private float targetDragScale = 1.0f;
    private float lineAlpha;
    private boolean snapVertical;
    private boolean snapHorizontal;
    private int order;

    ElementComponent(String id, String title, float defaultX, float defaultY, int order) {
        this.id = id;
        this.title = title;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.targetX = defaultX;
        this.targetY = defaultY;
        this.x = defaultX;
        this.y = defaultY;
        this.order = order;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public float x() {
        return x + visualOffsetX;
    }

    public float y() {
        return y + visualOffsetY;
    }

    public float baseX() {
        return x;
    }

    public float baseY() {
        return y;
    }

    public float targetX() {
        return targetX;
    }

    public float targetY() {
        return targetY;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public boolean visible() {
        return visible;
    }

    public boolean locked() {
        return locked;
    }

    public boolean moving() {
        return moving;
    }

    public boolean positionCustomized() {
        return positionCustomized;
    }

    public int order() {
        return order;
    }

    public float dragTiltDegrees() {
        return dragTiltDegrees;
    }

    public float dragScale() {
        return dragScale;
    }

    public float lineAlpha() {
        return lineAlpha;
    }

    public boolean snapVertical() {
        return snapVertical;
    }

    public boolean snapHorizontal() {
        return snapHorizontal;
    }

    public float centerX() {
        return x() + width * 0.5f;
    }

    public float centerY() {
        return y() + height * 0.5f;
    }

    public ElementBounds bounds() {
        return new ElementBounds(x(), y(), width, height);
    }

    public ElementBounds dragBounds() {
        return new ElementBounds(x() - hitExpandLeft, y() - hitExpandTop, width + hitExpandLeft + hitExpandRight, height + hitExpandTop + hitExpandBottom);
    }

    public ElementComponent size(float width, float height) {
        if (Float.isFinite(width)) {
            this.width = Math.max(minWidth, width);
        }
        if (Float.isFinite(height)) {
            this.height = Math.max(minHeight, height);
        }
        return this;
    }

    public ElementComponent minimumSize(float minWidth, float minHeight) {
        if (Float.isFinite(minWidth)) {
            this.minWidth = Math.max(1.0f, minWidth);
        }
        if (Float.isFinite(minHeight)) {
            this.minHeight = Math.max(1.0f, minHeight);
        }
        return size(width, height);
    }

    public ElementComponent hitExpansion(float left, float top, float right, float bottom) {
        hitExpandLeft = safeExpansion(left);
        hitExpandTop = safeExpansion(top);
        hitExpandRight = safeExpansion(right);
        hitExpandBottom = safeExpansion(bottom);
        return this;
    }

    public ElementComponent screenMargins(float left, float top, float right, float bottom) {
        screenMarginLeft = safeExpansion(left);
        screenMarginTop = safeExpansion(top);
        screenMarginRight = safeExpansion(right);
        screenMarginBottom = safeExpansion(bottom);
        return this;
    }

    public ElementComponent position(float x, float y) {
        if (Float.isFinite(x)) {
            targetX = x;
            this.x = x;
        }
        if (Float.isFinite(y)) {
            targetY = y;
            this.y = y;
        }
        return this;
    }

    public ElementComponent visualOffset(float offsetX, float offsetY) {
        visualOffsetX = Float.isFinite(offsetX) ? offsetX : 0.0F;
        visualOffsetY = Float.isFinite(offsetY) ? offsetY : 0.0F;
        return this;
    }

    public ElementComponent clearVisualOffset() {
        visualOffsetX = 0.0F;
        visualOffsetY = 0.0F;
        return this;
    }

    public float visualOffsetX() {
        return visualOffsetX;
    }

    public float visualOffsetY() {
        return visualOffsetY;
    }

    public ElementComponent visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public ElementComponent locked(boolean locked) {
        this.locked = locked;
        if (locked) {
            moving = false;
        }
        return this;
    }

    public ElementComponent resetPosition() {
        positionCustomized = false;
        return position(defaultX, defaultY);
    }

    void order(int order) {
        this.order = order;
    }

    boolean hit(float mouseX, float mouseY, float padding) {
        return visible && !locked && dragBounds().contains(mouseX, mouseY, padding);
    }

    void beginMove(float mouseX, float mouseY) {
        moving = true;
        positionCustomized = true;
        pointerOffsetX = mouseX - x();
        pointerOffsetY = mouseY - y();
        previousMouseX = mouseX;
        hasPreviousMouseX = true;
        smoothedMouseDeltaX = 0.0f;
        targetDragScale = DRAG_SCALE_MULTIPLIER;
    }

    void moveTo(float mouseX, float mouseY, ElementScreen screen) {
        if (!moving) {
            return;
        }

        targetX = mouseX - pointerOffsetX - visualOffsetX;
        targetY = mouseY - pointerOffsetY - visualOffsetY;
        snap(screen);
        targetX = clampedX(screen, targetX);
        targetY = clampedY(screen, targetY);
        x = lerp(x, targetX, LERP_SPEED);
        y = lerp(y, targetY, LERP_SPEED);

        float mouseDeltaX = hasPreviousMouseX ? mouseX - previousMouseX : 0.0f;
        previousMouseX = mouseX;
        hasPreviousMouseX = true;
        smoothedMouseDeltaX = lerp(smoothedMouseDeltaX, mouseDeltaX, TILT_DELTA_SMOOTHING);
        float desiredTilt = Math.abs(smoothedMouseDeltaX) <= TILT_DEADZONE ? 0.0f : clamp(smoothedMouseDeltaX * TILT_FROM_MOUSE_DELTA, -MAX_TILT_DEGREES, MAX_TILT_DEGREES);
        targetTiltDegrees = lerp(targetTiltDegrees, desiredTilt, TILT_TARGET_SMOOTHING);
        dragTiltDegrees = lerp(dragTiltDegrees, targetTiltDegrees, DRAG_TILT_LERP);
        targetDragScale = DRAG_SCALE_MULTIPLIER;
        dragScale = lerp(dragScale, targetDragScale, DRAG_SCALE_LERP);
    }

    void endMove() {
        moving = false;
        hasPreviousMouseX = false;
        targetTiltDegrees = 0.0f;
        targetDragScale = 1.0f;
    }

    void applyState(float x, float y, float width, float height, boolean visible, int order, boolean positionCustomized) {
        position(x, y);
        size(width, height);
        this.visible = visible;
        this.order = order;
        this.positionCustomized = positionCustomized;
    }

    public void clamp(ElementScreen screen) {
        if (screen == null || !screen.valid()) {
            return;
        }
        if (moving) {
            targetX = clampedX(screen, targetX);
            targetY = clampedY(screen, targetY);
            x = clampedX(screen, x);
            y = clampedY(screen, y);
            return;
        }
        x = clampedX(screen, targetX);
        y = clampedY(screen, targetY);
    }

    public void commitClamp(ElementScreen screen) {
        if (screen == null || !screen.valid()) {
            return;
        }
        targetX = clampedX(screen, targetX);
        targetY = clampedY(screen, targetY);
        x = targetX;
        y = targetY;
    }

    void updateAnimation() {
        if (!moving) {
            smoothedMouseDeltaX = lerp(smoothedMouseDeltaX, 0.0f, TILT_DELTA_SMOOTHING);
            targetTiltDegrees = lerp(targetTiltDegrees, 0.0f, TILT_TARGET_SMOOTHING);
            dragTiltDegrees = lerp(dragTiltDegrees, targetTiltDegrees, RELEASE_TILT_LERP);
            dragScale = lerp(dragScale, 1.0f, RELEASE_SCALE_LERP);
            if (Math.abs(dragTiltDegrees) < 0.01f) {
                dragTiltDegrees = 0.0f;
            }
            if (Math.abs(dragScale - 1.0f) < 0.0005f) {
                dragScale = 1.0f;
            }
        }
        float targetAlpha = moving && (snapVertical || snapHorizontal) ? 1.0f : 0.0f;
        lineAlpha = lerp(lineAlpha, targetAlpha, moving ? 0.20f : 0.10f);
        if (lineAlpha < 0.01f) {
            lineAlpha = 0.0f;
        }
        if (!moving && lineAlpha == 0.0f) {
            snapVertical = false;
            snapHorizontal = false;
        }
    }

    private void snap(ElementScreen screen) {
        if (screen == null || !screen.valid()) {
            return;
        }

        snapVertical = false;
        snapHorizontal = false;
        float currentCenterX = targetX + width * 0.5f;
        float currentCenterY = targetY + height * 0.5f;
        float[] snapX = {
                screen.width() * 0.5f,
                screen.width() * 0.25f,
                screen.width() * 0.125f,
                screen.width() / 1.15f,
                screen.width() / 1.35f
        };
        float[] snapY = {
                screen.height() * 0.5f,
                screen.height() * 0.25f,
                screen.height() * 0.125f,
                screen.height() / 1.15f,
                screen.height() / 1.35f
        };

        for (float snap : snapX) {
            if (Math.abs(currentCenterX - snap) <= SNAP_DISTANCE) {
                targetX = snap - width * 0.5f;
                snapVertical = true;
                break;
            }
        }
        for (float snap : snapY) {
            if (Math.abs(currentCenterY - snap) <= SNAP_DISTANCE) {
                targetY = snap - height * 0.5f;
                snapHorizontal = true;
                break;
            }
        }
    }

    private float clampedX(ElementScreen screen, float value) {
        float minimum = screenMarginLeft + hitExpandLeft;
        float maximum = Math.max(minimum, screen.width() - width - hitExpandRight - screenMarginRight);
        return clamp(value, minimum, maximum);
    }

    private float clampedY(ElementScreen screen, float value) {
        float minimum = screenMarginTop + hitExpandTop;
        float maximum = Math.max(minimum, screen.height() - height - hitExpandBottom - screenMarginBottom);
        return clamp(value, minimum, maximum);
    }

    private static float safeExpansion(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * speed;
    }
}
