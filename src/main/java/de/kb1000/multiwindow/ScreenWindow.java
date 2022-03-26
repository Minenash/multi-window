package de.kb1000.multiwindow;

import com.mojang.blaze3d.systems.RenderSystem;
import de.kb1000.multiwindow.gl.GlContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.GlfwUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL30;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

@Environment(EnvType.CLIENT)
public class ScreenWindow {
    public final GlContext context;
    private final WindowFramebuffer framebuffer;
    private @NotNull Screen screen;
    private double x;
    private double y;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private int controlLeftTicks;
    private int activeButton;
    private int field_1796;
    private double glfwTime;
    private boolean closing = false;
    private boolean customTitle = false;

    private float scaleFactor;
    private int scaledWidth, scaledHeight;

    public ScreenWindow(@NotNull Screen screen) {
        int initWidth = client.getWindow().getWidth();
        int initHeight = client.getWindow().getHeight();

        this.context = new GlContext(initWidth, initHeight, screen.getTitle().getString(), client.getWindow().getHandle());
        try (var ignored = this.context.setContext()) {
            this.framebuffer = new WindowFramebuffer(initWidth, initHeight);
        }
        this.screen = screen;

        calculateScaleValues(initWidth, initHeight);

        screen.init(client, scaledWidth, scaledHeight);

        this.context.onSizeChanged.register((width, height) -> {
            try (var ignored = context.setContext()) {
                this.framebuffer.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);

                calculateScaleValues(width, height);

                this.screen.resize(client, scaledWidth, scaledHeight);


            }
        });
        // TODO: make these run in render or update instead of on the main thread
        this.context.onMouseMove.register((x, y) -> this.client.execute(() -> this.onCursorPos(x, y)));
        this.context.onMouseButton.register((button, action, mods) -> this.client.execute(() -> this.onMouseButton(button, action, mods)));
        this.context.onMouseScroll.register((xOffset, yOffset) -> this.client.execute(() -> this.onMouseScroll(xOffset, yOffset)));
        this.context.onFilesDropped.register(files -> this.client.execute(() -> this.onFilesDropped(List.of(files))));
    }

    public void setTitle(String title) {
        customTitle = true;
        GLFW.glfwSetWindowTitle(context.getHandle(), title);
    }

    private void calculateScaleValues(int width, int height) {
        int i = 1;

        while (i != client.options.guiScale && i < width && i < height && width / (i+1) >= 320 && height / (i+1) >= 240) {
            i++;
        }

        scaleFactor = client.forcesUnicodeFont() && i % 2 != 0 ? ++i : i;
        this.scaledWidth = (int) Math.ceil(width / scaleFactor);
        this.scaledHeight = (int) Math.ceil(height / scaleFactor);
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
        this.screen.init(client, context.getWidth(), context.getHeight());
        if (!customTitle)
            GLFW.glfwSetWindowTitle(context.getHandle(), screen.getTitle().getString());
    }

    private void onCursorPos(double x, double y) {
        Screen.wrapScreenError(() -> screen.mouseMoved(x, y), "mouseMoved event handler", screen.getClass().getCanonicalName());
        if (this.activeButton != -1 && this.glfwTime > 0.0D) {
            Screen.wrapScreenError(
                () -> screen.mouseDragged(x, y, this.activeButton, x - this.x, y - this.y),
                "mouseDragged event handler", screen.getClass().getCanonicalName());
        }

        screen.applyMouseMoveNarratorDelay();

        this.x = x;
        this.y = y;
    }

    private void onMouseButton(int button, int action, int mods) {
        boolean isPress = action == GLFW_PRESS;
        if (MinecraftClient.IS_SYSTEM_MAC && button == 0) {
            if (isPress) {
                if ((mods & GLFW_MOD_CONTROL) == GLFW_MOD_CONTROL) {
                    button = 1;
                    ++this.controlLeftTicks;
                }
            } else if (this.controlLeftTicks > 0) {
                button = 1;
                --this.controlLeftTicks;
            }
        }

        if (isPress) {
            if (this.client.options.touchscreen && this.field_1796++ > 0)
                return;

            this.activeButton = button;
            this.glfwTime = GlfwUtil.getTime();
        } else if (this.activeButton != -1) {
            if (this.client.options.touchscreen && --this.field_1796 > 0)
                return;

            this.activeButton = -1;
        }

        final int finalButton = button;

        Screen.wrapScreenError(() -> this.screen.mouseClicked(x, y, finalButton), isPress ? "mouseClicked event handler" : "mouseReleased event handler", this.screen.getClass().getCanonicalName());


    }

    private void onMouseScroll(double xOffset, double yOffset) {
        double totalYScroll = (client.options.discreteMouseScroll ? Math.signum(yOffset) : yOffset) * client.options.mouseWheelSensitivity;

        this.screen.mouseScrolled(x, y, totalYScroll);
        this.screen.applyMousePressScrollNarratorDelay();
    }

    private void onFilesDropped(List<Path> names) {
        screen.filesDragged(names);
    }

    public void markAsClosing() {
        closing = true;
    }

    @Deprecated
    public void destroy() {
        context.destroy();
        screen.removed();
    }

    public void render() {
        try (var ignored = context.setContext()) {
            MatrixStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.push();

            context.getState().glRecord();

            framebuffer.beginWrite(true);

            RenderSystem.enableTexture();
            RenderSystem.enableCull();

            RenderSystem.clearColor(1, 1, 1, 1);
            RenderSystem.clear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

            RenderSystem.depthFunc(GL30.GL_LEQUAL);

            Matrix4f matrix4f = Matrix4f.projectionMatrix(0.0F, scaledWidth, 0.0F, scaledHeight, 1000.0F, 3000.0F);
            RenderSystem.setProjectionMatrix(matrix4f);
            modelViewStack.loadIdentity();
            modelViewStack.translate(0.0, 0.0, -2000.0);
            RenderSystem.applyModelViewMatrix();
            DiffuseLighting.enableGuiDepthLighting();
            MatrixStack stack = new MatrixStack();

            try {
                while (!queue.isEmpty())
                    queue.poll().run();

                screen.render(stack, (int) x, (int) y, this.client.getLastFrameDuration());

                while (!queue.isEmpty())
                    queue.poll().run();

            } catch (Throwable t) {
                throw new CrashException(createRenderCrashReport(t));
            }

            this.framebuffer.endWrite();
            modelViewStack.pop();

            modelViewStack.push();
            RenderSystem.applyModelViewMatrix();
            this.framebuffer.draw(context.getWidth(), context.getHeight());
            modelViewStack.pop();
            RenderSystem.flipFrame(context.getHandle());
        }
    }

    public boolean isClosing() {
        return context.getHandle() == 0 || closing || GLFW.glfwWindowShouldClose(context.getHandle());
    }

    private CrashReport createRenderCrashReport(Throwable t) {
        CrashReport crashReport = CrashReport.create(t, "Rendering screen");
        CrashReportSection crashReportSection = crashReport.addElement("Screen render details");
        crashReportSection.add("Screen name", () -> screen.getClass().getCanonicalName());
        crashReportSection.add(
                "Mouse location",
                () -> String.format(
                        Locale.ROOT, "Scaled: (%d, %d). Absolute: (%f, %f)", (int)x, (int)y, x, y
                )
        );
        // TODO: wrong, fix later
        crashReportSection.add(
                "Screen size",
                () -> String.format(
                        Locale.ROOT,
                        "Scaled: (%d, %d). Absolute: (%d, %d). Scale factor of %f",
                        this.client.getWindow().getScaledWidth(),
                        this.client.getWindow().getScaledHeight(),
                        this.client.getWindow().getFramebufferWidth(),
                        this.client.getWindow().getFramebufferHeight(),
                        this.client.getWindow().getScaleFactor()
                )
        );
        return crashReport;
    }

}
