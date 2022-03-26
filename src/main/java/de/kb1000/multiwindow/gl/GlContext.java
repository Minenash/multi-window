package de.kb1000.multiwindow.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import de.kb1000.multiwindow.gl.events.*;
import io.netty.util.internal.ResourcesUtil;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlDebug;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GlContext {
    private static final SavedGlState MAIN = new SavedGlState();

    static {
        MAIN.glRecord();
    }

    private long handle;
    private int width;
    private int height;
    private final Map<VertexFormat, Integer> vertexArrays = new HashMap<>();
    private final SavedGlState state;

    public final Event<SizeChangedCallback> onSizeChanged = EventFactory.createArrayBacked(SizeChangedCallback.class, handlers -> (width1, height1) -> {
        for (var handler : handlers) {
            handler.onSizeChanged(width1, height1);
        }
    });
    public final Event<MouseMoveCallback> onMouseMove = EventFactory.createArrayBacked(MouseMoveCallback.class, handlers -> (x, y) -> {
        for (var handler : handlers) {
            handler.onMouseMove(x, y);
        }
    });
    public final Event<MouseButtonCallback> onMouseButton = EventFactory.createArrayBacked(MouseButtonCallback.class, handlers -> (button, action, mods) -> {
        for (var handler : handlers) {
            handler.onMouseButton(button, action, mods);
        }
    });
    public final Event<MouseScrollCallback> onMouseScroll = EventFactory.createArrayBacked(MouseScrollCallback.class, handlers -> (xOffset, yOffset) -> {
        for (var handler : handlers) {
            handler.onMouseScroll(xOffset, yOffset);
        }
    });
    public final Event<FilesDroppedCallback> onFilesDropped = EventFactory.createArrayBacked(FilesDroppedCallback.class, handlers -> (files) -> {
        for (var handler : handlers) {
            handler.onFilesDropped(files);
        }
    });

    public GlContext(int width, int height, String name, long sharedWith) {
        this.width = width;
        this.height = height;

        try (var ignored = GlUtils.setContextRaw(NULL)) {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, 1);
            handle = GLFW.glfwCreateWindow(width, height, name, NULL, sharedWith);

            if (handle == NULL)
                throw new IllegalStateException("Failed to create GlWindow!");

            GLFW.glfwMakeContextCurrent(handle);
            GlDebug.enableDebug(MinecraftClient.getInstance().options.glDebugVerbosity, true);
            state = new SavedGlState();
            state.glRecord();
        }

        GLFW.glfwSetFramebufferSizeCallback(handle, this::sizeChanged);
        GLFW.glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
            onMouseMove.invoker().onMouseMove(xpos, ypos);
        });
        GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            onMouseButton.invoker().onMouseButton(button, action, mods);
        });
        GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            onMouseScroll.invoker().onMouseScroll(xoffset, yoffset);
        });
        GLFW.glfwSetDropCallback(handle, (window, count, names) -> {
            Path[] paths = new Path[count];

            for (int j = 0; j < count; ++j) {
                paths[j] = Paths.get(GLFWDropCallback.getName(names, j));
            }

            onFilesDropped.invoker().onFilesDropped(paths);
        });
    }

    private void sizeChanged(long window, int width, int height) {
        this.width = width;
        this.height = height;

        onSizeChanged.invoker().onSizeChanged(width, height);
    }

    public void destroy() {
        if (this.handle == 0) {
            throw new IllegalStateException("Trying to destroy context that was already destroyed!");
        }

        try (var ignored = setContext()) {
            for (Integer vertexArray : vertexArrays.values()) {
                GlStateManager._glDeleteVertexArrays(vertexArray);
            }
        }
        GLFW.glfwDestroyWindow(this.handle);
        this.handle = 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getHandle() {
        return handle;
    }

    public SavedGlState getState() {
        return state;
    }

    public int getVertexArrayFor(VertexFormat vertexFormat) {
        return vertexArrays.computeIfAbsent(vertexFormat, fmt -> GlStateManager._glGenVertexArrays());
    }

    public ContextRestorer setContext() {
        long old = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(handle);
        var prevCtx = GlContextTracker.getCurrentContext();
        GlContextTracker.pushContext(this);

        if (prevCtx != null) {
            prevCtx.state.record();
        } else {
            MAIN.record();
        }
        state.apply();

        return new ContextRestorer(old);
    }

    public class ContextRestorer implements AutoCloseable {
        private final long oldContext;

        public ContextRestorer(long oldContext) {
            this.oldContext = oldContext;
        }

        @Override
        public void close() {
            GlContextTracker.popContext();
            GLFW.glfwMakeContextCurrent(oldContext);

            state.record();

            var newCtx = GlContextTracker.getCurrentContext();

            if (newCtx != null) {
                newCtx.state.apply();
            } else {
                MAIN.apply();
            }

        }
    }

    public void setIcon(Identifier icon16, Identifier icon32) {

        try {
            InputStream icon16Stream = MinecraftClient.getInstance().getResourceManager().getResource(icon16).getInputStream();
            InputStream icon32Stream = MinecraftClient.getInstance().getResourceManager().getResource(icon32).getInputStream();

            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                if (icon16Stream == null)
                    throw new FileNotFoundException(icon16.toString());

                if (icon32Stream == null)
                    throw new FileNotFoundException(icon32.toString());

                IntBuffer intBuffer = memoryStack.mallocInt(1);
                IntBuffer intBuffer2 = memoryStack.mallocInt(1);
                IntBuffer intBuffer3 = memoryStack.mallocInt(1);
                GLFWImage.Buffer buffer = GLFWImage.mallocStack(2, memoryStack);
                ByteBuffer byteBuffer = this.readImage(icon16Stream, intBuffer, intBuffer2, intBuffer3);
                if (byteBuffer == null)
                    throw new IllegalStateException("Could not load icon: " + STBImage.stbi_failure_reason());

                buffer.position(0);
                buffer.width(intBuffer.get(0));
                buffer.height(intBuffer2.get(0));
                buffer.pixels(byteBuffer);
                ByteBuffer byteBuffer2 = this.readImage(icon32Stream, intBuffer, intBuffer2, intBuffer3);
                if (byteBuffer2 == null)
                    throw new IllegalStateException("Could not load icon: " + STBImage.stbi_failure_reason());

                buffer.position(1);
                buffer.width(intBuffer.get(0));
                buffer.height(intBuffer2.get(0));
                buffer.pixels(byteBuffer2);
                buffer.position(0);
                GLFW.glfwSetWindowIcon(this.handle, buffer);
                STBImage.stbi_image_free(byteBuffer);
                STBImage.stbi_image_free(byteBuffer2);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private ByteBuffer readImage(InputStream in, IntBuffer x, IntBuffer y, IntBuffer channels) throws IOException {
        ByteBuffer byteBuffer = null;

        ByteBuffer var6;
        try {

            if (in instanceof FileInputStream) {
                FileInputStream fileInputStream = (FileInputStream)in;
                FileChannel fileChannel = fileInputStream.getChannel();
                byteBuffer = MemoryUtil.memAlloc((int)fileChannel.size() + 1);

                while(fileChannel.read(byteBuffer) != -1) {}
            } else {
                byteBuffer = MemoryUtil.memAlloc(8192);
                ReadableByteChannel readableByteChannel = Channels.newChannel(in);

                while(readableByteChannel.read(byteBuffer) != -1) {
                    if (byteBuffer.remaining() == 0) {
                        byteBuffer = MemoryUtil.memRealloc(byteBuffer, byteBuffer.capacity() * 2);
                    }
                }
            }


            byteBuffer.rewind();
            var6 = STBImage.stbi_load_from_memory(byteBuffer, x, y, channels, 0);
        } finally {
            if (byteBuffer != null) {
                MemoryUtil.memFree(byteBuffer);
            }

        }

        return var6;
    }
}
