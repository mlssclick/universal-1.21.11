package universalmod.api.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.HandOffsetEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.hand.HandsRenderer;

public class ViewModel extends Module {
    private static final double SCALE_SCROLL_STEP = 0.05D;
    private static final double EDITOR_MIN_SCALE = 0.10D;
    private static final double EDITOR_MAX_SCALE = 2.60D;

    private static final double EDITOR_MIN_GRAB_SIZE = 56.0D;
    private static final double EDITOR_MAX_GRAB_SIZE = 360.0D;
    private static final double EDITOR_HITBOX_PADDING = 10.0D;
    private static final float EDITOR_ITEM_EXTENT = 1.35F;

    private static final int XY_SOLVER_ITERATIONS = 7;
    private static final double XY_SOLVER_EPSILON = 0.01D;
    private static final double XY_SOLVER_MAX_STEP = 0.85D;
    private static final double XY_SOLVER_TARGET_EPSILON = 0.20D;
    private static final double XY_SOLVER_DET_EPSILON = 1.0E-7D;

    private static ViewModel instance;

    private final BooleanSetting betterViewModel = register(new BooleanSetting(
            "Better View Model",
            "Lets you drag first-person hands in chat. Ctrl + mouse wheel changes size.",
            false
    ));

    private final NumberSetting mainHandX = register(new NumberSetting("Main Hand X", "Main hand X offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting mainHandY = register(new NumberSetting("Main Hand Y", "Main hand Y offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting mainHandZ = register(new NumberSetting("Main Hand Z", "Main hand Z offset.", 0.0, -2.5, 2.5, 0.05));
    private final NumberSetting itemSizeMainHand = register(new NumberSetting(
            "Item Size Main Hand",
            "Rendered item size in the main hand.",
            1.0,
            0.1,
            2.6,
            0.05
    ));

    private final NumberSetting offHandX = register(new NumberSetting("Off Hand X", "Off hand X offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting offHandY = register(new NumberSetting("Off Hand Y", "Off hand Y offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting offHandZ = register(new NumberSetting("Off Hand Z", "Off hand Z offset.", 0.0, -2.5, 2.5, 0.05));
    private final NumberSetting itemSizeOffHand = register(new NumberSetting(
            "Item Size Off Hand",
            "Rendered item size in the off hand.",
            1.0,
            0.1,
            2.6,
            0.05
    ));

    private InteractionHand draggedHand;
    private double dragCursorX;
    private double dragCursorY;
    private double dragGrabOffsetX;
    private double dragGrabOffsetY;

    private final Matrix4f editorProjection = new Matrix4f();
    private long editorProjectionGeneration;

    private final HandBasePose mainHandBasePose = new HandBasePose();
    private final HandBasePose offHandBasePose = new HandBasePose();

    private final HandScreenSample mainHandScreenSample = new HandScreenSample();
    private final HandScreenSample offHandScreenSample = new HandScreenSample();

    public ViewModel() {
        super("View Model", "Changes first-person hand offsets and held item size.", ModuleCategory.RENDER);
        instance = this;
        betterViewModel.addListener((setting, oldValue, newValue) -> {
            if (!Boolean.TRUE.equals(newValue)) {
                finishEditorDrag();
                invalidateEditorSamples();
            }
        });
    }

    public static ViewModel getInstance() {
        return instance;
    }

    public boolean isBetterViewModelEnabled() {
        return isEnabled() && betterViewModel.getValue();
    }

    public boolean isEditorDragging() {
        return draggedHand != null;
    }

    public boolean handleChatMouseClicked(MouseButtonEvent event) {
        if (!isBetterViewModelEnabled() || event == null
                || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || mc.player == null) {
            return false;
        }

        ScreenPoint mouse = liveMouse(event.x(), event.y());
        InteractionHand hand = hoveredHand(mouse.x, mouse.y);
        if (hand == null) {
            return false;
        }

        draggedHand = hand;
        dragCursorX = mouse.x;
        dragCursorY = mouse.y;

        HandScreenSample sample = usableScreenSample(hand);
        if (sample != null) {
            
            dragGrabOffsetX = mouse.x - sample.anchorX;
            dragGrabOffsetY = mouse.y - sample.anchorY;
        } else {
            ScreenPoint fallback = fallbackHandAnchor(hand);
            dragGrabOffsetX = mouse.x - fallback.x;
            dragGrabOffsetY = mouse.y - fallback.y;
        }

        Hands hands = Hands.getInstance();
        if (hands == null || !hands.isEnabled()) {
            HandsRenderer.setEditorOutlineSide(screenSide(hand));
        } else {
            HandsRenderer.clearEditorOutline();
        }
        return true;
    }

    public boolean handleChatMouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!isBetterViewModelEnabled() || draggedHand == null || event == null
                || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        ScreenPoint mouse = liveMouse(event.x(), event.y());
        return updateEditorDrag(mouse.x, mouse.y);
    }

    public boolean handleChatMouseDragged(MouseButtonEvent event) {
        return handleChatMouseDragged(event, 0.0D, 0.0D);
    }

    public boolean handleChatMouseMoved(double mouseX, double mouseY) {
        if (!isBetterViewModelEnabled() || draggedHand == null) {
            return false;
        }
        return updateEditorDrag(mouseX, mouseY);
    }

    private boolean updateEditorDrag(double mouseX, double mouseY) {
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY) || draggedHand == null) {
            return false;
        }
        dragCursorX = mouseX;
        dragCursorY = mouseY;
        return true;
    }

    private void applyEditorDragFrame(double mouseX, double mouseY) {
        if (draggedHand == null || mc.getWindow() == null
                || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) {
            return;
        }

        HandScreenSample sample = usableScreenSample(draggedHand);
        if (sample == null) {
            
            return;
        }

        double targetX = mouseX - dragGrabOffsetX;
        double targetY = mouseY - dragGrabOffsetY;

        NumberSetting xSetting = xSetting(draggedHand);
        NumberSetting ySetting = ySetting(draggedHand);
        NumberSetting zSetting = zSetting(draggedHand);

        XY solved = sample.solveXY(
                targetX,
                targetY,
                xSetting.getValue(),
                ySetting.getValue(),
                zSetting.getValue(),
                xSetting.getMin(),
                xSetting.getMax(),
                ySetting.getMin(),
                ySetting.getMax()
        );

        if (solved != null) {
            xSetting.setValue(solved.x);
            ySetting.setValue(solved.y);
        }

    }

    public boolean handleChatMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isBetterViewModelEnabled() || mc.player == null || scrollY == 0.0D) {
            return false;
        }

        InteractionHand hand = draggedHand != null ? draggedHand : hoveredHand(mouseX, mouseY);
        if (hand == null) {
            return false;
        }

        if (!controlDown()) {
            return false;
        }

        NumberSetting scale = scaleSetting(hand);
        double nextScale = clamp(
                scale.getValue() + Math.signum(scrollY) * SCALE_SCROLL_STEP,
                EDITOR_MIN_SCALE,
                EDITOR_MAX_SCALE
        );
        scale.setValue(nextScale);
        return true;
    }

    public void finishEditorDrag() {
        draggedHand = null;
        HandsRenderer.clearEditorOutline();
    }

    public void pollEditorDragFrame() {
        if (draggedHand == null) {
            return;
        }
        if (!isBetterViewModelEnabled() || !(mc.screen instanceof ChatScreen) || !leftMouseDown()) {
            finishEditorDrag();
            return;
        }
        if (mc.mouseHandler == null || mc.getWindow() == null) {
            return;
        }

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        if (Double.isFinite(mouseX) && Double.isFinite(mouseY)) {
            dragCursorX = mouseX;
            dragCursorY = mouseY;
            applyEditorDragFrame(dragCursorX, dragCursorY);
        }
    }

    private InteractionHand hoveredHand(double mouseX, double mouseY) {
        if (mc.player == null || mc.getWindow() == null) {
            return null;
        }

        HandScreenSample main = usableScreenSample(InteractionHand.MAIN_HAND);
        HandScreenSample off = usableScreenSample(InteractionHand.OFF_HAND);
        boolean inMain = main != null && main.contains(mouseX, mouseY);
        boolean inOff = off != null && off.contains(mouseX, mouseY);

        if (inMain || inOff) {
            if (inMain && inOff) {
                return main.distanceSquared(mouseX, mouseY) <= off.distanceSquared(mouseX, mouseY)
                        ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            }
            return inMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        }

        boolean fallbackMain = main == null
                && mc.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && fallbackContains(InteractionHand.MAIN_HAND, mouseX, mouseY);
        boolean fallbackOff = off == null
                && mc.player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()
                && fallbackContains(InteractionHand.OFF_HAND, mouseX, mouseY);

        if (fallbackMain && fallbackOff) {
            float width = Math.max(1.0F, mc.getWindow().getGuiScaledWidth());
            return mouseX < width * 0.5F
                    ? (screenSide(InteractionHand.MAIN_HAND) < 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
                    : (screenSide(InteractionHand.MAIN_HAND) > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
        return fallbackMain ? InteractionHand.MAIN_HAND : (fallbackOff ? InteractionHand.OFF_HAND : null);
    }

    private boolean fallbackContains(InteractionHand hand, double mouseX, double mouseY) {
        ScreenPoint center = fallbackHandAnchor(hand);
        double half = EDITOR_MIN_GRAB_SIZE * 0.5D;
        return mouseX >= center.x - half && mouseX <= center.x + half
                && mouseY >= center.y - half && mouseY <= center.y + half;
    }

    private ScreenPoint fallbackHandAnchor(InteractionHand hand) {
        float width = mc.getWindow() == null ? 1.0F : Math.max(1.0F, mc.getWindow().getGuiScaledWidth());
        float height = mc.getWindow() == null ? 1.0F : Math.max(1.0F, mc.getWindow().getGuiScaledHeight());
        boolean right = screenSide(hand) > 0;
        return new ScreenPoint(
                width * (right ? 0.77D : 0.23D),
                height * 0.74D
        );
    }

    public void captureEditorProjection(Matrix4f projection) {
        if (projection == null) {
            return;
        }
        editorProjection.set(projection);
        editorProjectionGeneration++;
    }

    private void captureEditorBasePose(InteractionHand hand, Matrix4f pose, boolean hasItem) {
        if (!isBetterViewModelEnabled() || hand == null || pose == null || editorProjectionGeneration <= 0L) {
            return;
        }
        basePose(hand).set(
                pose,
                xSetting(hand).getValue(),
                ySetting(hand).getValue(),
                zSetting(hand).getValue(),
                editorProjectionGeneration
        );
        if (!hasItem) {
            screenSample(hand).invalidate();
        }
    }

    public void captureRenderedHandPose(InteractionHand hand, Matrix4f finalPose) {
        if (!isBetterViewModelEnabled() || hand == null || finalPose == null || mc.getWindow() == null
                || editorProjectionGeneration <= 0L) {
            return;
        }

        HandBasePose base = basePose(hand);
        if (!base.valid || base.generation != editorProjectionGeneration) {
            return;
        }

        Matrix4f offsetPose = new Matrix4f(base.pose)
                .translate((float) base.x, (float) base.y, (float) base.z);
        Matrix4f inverseOffset = new Matrix4f(offsetPose);
        float determinant = inverseOffset.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return;
        }
        inverseOffset.invert();
        Matrix4f renderTail = inverseOffset.mul(new Matrix4f(finalPose));

        float width = Math.max(1.0F, mc.getWindow().getGuiScaledWidth());
        float height = Math.max(1.0F, mc.getWindow().getGuiScaledHeight());

        screenSample(hand).set(
                editorProjection,
                base.pose,
                renderTail,
                base.x,
                base.y,
                base.z,
                width,
                height,
                editorProjectionGeneration
        );
    }

    private HandScreenSample usableScreenSample(InteractionHand hand) {
        HandScreenSample sample = screenSample(hand);
        return sample.validForEditor(editorProjectionGeneration) ? sample : null;
    }

    private HandBasePose basePose(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandBasePose : offHandBasePose;
    }

    private HandScreenSample screenSample(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandScreenSample : offHandScreenSample;
    }

    private void invalidateEditorSamples() {
        mainHandBasePose.invalidate();
        offHandBasePose.invalidate();
        mainHandScreenSample.invalidate();
        offHandScreenSample.invalidate();
    }

    private ScreenPoint liveMouse(double fallbackX, double fallbackY) {
        if (mc.mouseHandler != null && mc.getWindow() != null) {
            double x = mc.mouseHandler.getScaledXPos(mc.getWindow());
            double y = mc.mouseHandler.getScaledYPos(mc.getWindow());
            if (Double.isFinite(x) && Double.isFinite(y)) {
                return new ScreenPoint(x, y);
            }
        }
        return new ScreenPoint(fallbackX, fallbackY);
    }

    private int screenSide(InteractionHand hand) {
        boolean mainRight = mc.player == null || mc.player.getMainArm() == HumanoidArm.RIGHT;
        boolean right = hand == InteractionHand.MAIN_HAND ? mainRight : !mainRight;
        return right ? 1 : -1;
    }

    private boolean controlDown() {
        if (mc.getWindow() == null) {
            return false;
        }
        long handle = mc.getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean leftMouseDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private NumberSetting xSetting(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandX : offHandX;
    }

    private NumberSetting ySetting(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandY : offHandY;
    }

    private NumberSetting zSetting(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandZ : offHandZ;
    }

    private NumberSetting scaleSetting(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? itemSizeMainHand : itemSizeOffHand;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finiteClip(Vector4f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y)
                && Float.isFinite(value.z) && Float.isFinite(value.w);
    }

    private static final class HandBasePose {
        private final Matrix4f pose = new Matrix4f();
        private double x;
        private double y;
        private double z;
        private long generation = -1L;
        private boolean valid;

        private void set(Matrix4f pose, double x, double y, double z, long generation) {
            this.pose.set(pose);
            this.x = x;
            this.y = y;
            this.z = z;
            this.generation = generation;
            this.valid = true;
        }

        private void invalidate() {
            this.valid = false;
            this.generation = -1L;
        }
    }

    private static final class HandScreenSample {
        private final Matrix4f projection = new Matrix4f();
        private final Matrix4f basePose = new Matrix4f();
        private final Matrix4f renderTail = new Matrix4f();
        private double width;
        private double height;
        private double anchorX;
        private double anchorY;
        private double minX;
        private double minY;
        private double maxX;
        private double maxY;
        private long generation = -1L;
        private boolean valid;

        private void set(
                Matrix4f projection,
                Matrix4f basePose,
                Matrix4f renderTail,
                double x,
                double y,
                double z,
                double width,
                double height,
                long generation
        ) {
            this.projection.set(projection);
            this.basePose.set(basePose);
            this.renderTail.set(renderTail);
            this.width = width;
            this.height = height;
            this.generation = generation;

            ScreenPoint anchor = project(x, y, z, 0.0F, 0.0F, 0.0F);
            if (anchor == null) {
                invalidate();
                return;
            }
            this.anchorX = anchor.x;
            this.anchorY = anchor.y;

            double rawMinX = Double.POSITIVE_INFINITY;
            double rawMinY = Double.POSITIVE_INFINITY;
            double rawMaxX = Double.NEGATIVE_INFINITY;
            double rawMaxY = Double.NEGATIVE_INFINITY;
            int projectedCorners = 0;
            float[] corners = {-EDITOR_ITEM_EXTENT, EDITOR_ITEM_EXTENT};
            for (float px : corners) {
                for (float py : corners) {
                    for (float pz : corners) {
                        ScreenPoint point = project(x, y, z, px, py, pz);
                        if (point == null) {
                            continue;
                        }
                        rawMinX = Math.min(rawMinX, point.x);
                        rawMinY = Math.min(rawMinY, point.y);
                        rawMaxX = Math.max(rawMaxX, point.x);
                        rawMaxY = Math.max(rawMaxY, point.y);
                        projectedCorners++;
                    }
                }
            }

            double minHalf = EDITOR_MIN_GRAB_SIZE * 0.5D;
            double maxHalf = EDITOR_MAX_GRAB_SIZE * 0.5D;
            if (projectedCorners > 0) {
                double left = Math.min(minHalf, maxHalf);
                double right = left;
                double top = left;
                double bottom = left;

                if (Double.isFinite(rawMinX) && Double.isFinite(rawMaxX)) {
                    left = clamp(anchorX - rawMinX + EDITOR_HITBOX_PADDING, minHalf, maxHalf);
                    right = clamp(rawMaxX - anchorX + EDITOR_HITBOX_PADDING, minHalf, maxHalf);
                }
                if (Double.isFinite(rawMinY) && Double.isFinite(rawMaxY)) {
                    top = clamp(anchorY - rawMinY + EDITOR_HITBOX_PADDING, minHalf, maxHalf);
                    bottom = clamp(rawMaxY - anchorY + EDITOR_HITBOX_PADDING, minHalf, maxHalf);
                }

                this.minX = anchorX - left;
                this.maxX = anchorX + right;
                this.minY = anchorY - top;
                this.maxY = anchorY + bottom;
            } else {
                this.minX = anchorX - minHalf;
                this.maxX = anchorX + minHalf;
                this.minY = anchorY - minHalf;
                this.maxY = anchorY + minHalf;
            }
            this.valid = true;
        }

        private ScreenPoint project(double x, double y, double z, float localX, float localY, float localZ) {
            Matrix4f pose = new Matrix4f(basePose)
                    .translate((float) x, (float) y, (float) z)
                    .mul(renderTail);
            Matrix4f combined = new Matrix4f(projection).mul(pose);
            Vector4f clip = new Vector4f(localX, localY, localZ, 1.0F);
            combined.transform(clip);
            if (!finiteClip(clip) || clip.w <= 0.0001F) {
                return null;
            }
            double ndcX = clip.x / clip.w;
            double ndcY = clip.y / clip.w;
            double screenX = (ndcX * 0.5D + 0.5D) * width;
            double screenY = (1.0D - (ndcY * 0.5D + 0.5D)) * height;
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                return null;
            }
            return new ScreenPoint(screenX, screenY);
        }

        private XY solveXY(
                double targetX,
                double targetY,
                double initialX,
                double initialY,
                double z,
                double minX,
                double maxX,
                double minY,
                double maxY
        ) {
            double x = clamp(initialX, minX, maxX);
            double y = clamp(initialY, minY, maxY);

            for (int i = 0; i < XY_SOLVER_ITERATIONS; i++) {
                ScreenPoint current = project(x, y, z, 0.0F, 0.0F, 0.0F);
                if (current == null) {
                    return null;
                }

                double errorX = targetX - current.x;
                double errorY = targetY - current.y;
                if (Math.hypot(errorX, errorY) <= XY_SOLVER_TARGET_EPSILON) {
                    break;
                }

                ScreenPoint plusX = project(x + XY_SOLVER_EPSILON, y, z, 0.0F, 0.0F, 0.0F);
                ScreenPoint plusY = project(x, y + XY_SOLVER_EPSILON, z, 0.0F, 0.0F, 0.0F);
                if (plusX == null || plusY == null) {
                    return null;
                }

                double j00 = (plusX.x - current.x) / XY_SOLVER_EPSILON;
                double j10 = (plusX.y - current.y) / XY_SOLVER_EPSILON;
                double j01 = (plusY.x - current.x) / XY_SOLVER_EPSILON;
                double j11 = (plusY.y - current.y) / XY_SOLVER_EPSILON;
                double determinant = j00 * j11 - j01 * j10;

                if (!Double.isFinite(determinant) || Math.abs(determinant) < XY_SOLVER_DET_EPSILON) {
                    return null;
                }

                double dx = (errorX * j11 - j01 * errorY) / determinant;
                double dy = (j00 * errorY - errorX * j10) / determinant;
                if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
                    return null;
                }

                dx = clamp(dx, -XY_SOLVER_MAX_STEP, XY_SOLVER_MAX_STEP);
                dy = clamp(dy, -XY_SOLVER_MAX_STEP, XY_SOLVER_MAX_STEP);

                double nextX = clamp(x + dx, minX, maxX);
                double nextY = clamp(y + dy, minY, maxY);
                if (Math.abs(nextX - x) < 1.0E-8D && Math.abs(nextY - y) < 1.0E-8D) {
                    break;
                }
                x = nextX;
                y = nextY;
            }
            return new XY(x, y);
        }

        private boolean validForEditor(long currentGeneration) {
            return valid && generation > 0L && generation <= currentGeneration
                    && Double.isFinite(anchorX) && Double.isFinite(anchorY)
                    && Double.isFinite(minX) && Double.isFinite(minY)
                    && Double.isFinite(maxX) && Double.isFinite(maxY)
                    && maxX >= minX && maxY >= minY;
        }

        private boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        private double distanceSquared(double x, double y) {
            double dx = x - anchorX;
            double dy = y - anchorY;
            return dx * dx + dy * dy;
        }

        private void invalidate() {
            this.valid = false;
            this.generation = -1L;
        }
    }

    private record ScreenPoint(double x, double y) {
    }

    private record XY(double x, double y) {
    }

    @SubscribeEvent
    private void onHandOffset(HandOffsetEvent event) {
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;

        event.setScale(mainHand ? itemSizeMainHand.getFloat() : itemSizeOffHand.getFloat());

        PoseStack matrices = event.getMatrices();
        if (betterViewModel.getValue()) {
            captureEditorBasePose(event.getHand(), new Matrix4f(matrices.last().pose()), !event.getStack().isEmpty());
        }

        if (mainHand && event.getStack().getItem() instanceof CrossbowItem && !betterViewModel.getValue()) {
            return;
        }

        if (mainHand) {
            matrices.translate(mainHandX.getValue(), mainHandY.getValue(), mainHandZ.getValue());
        } else {
            matrices.translate(offHandX.getValue(), offHandY.getValue(), offHandZ.getValue());
        }
    }

    @Override
    protected void onDisable() {
        finishEditorDrag();
        invalidateEditorSamples();
    }
}
