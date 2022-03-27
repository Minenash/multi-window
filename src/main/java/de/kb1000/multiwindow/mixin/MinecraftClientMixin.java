package de.kb1000.multiwindow.mixin;

import de.kb1000.multiwindow.MultiWindow;
import de.kb1000.multiwindow.WindowAPI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class)
@Environment(EnvType.CLIENT)
public class MinecraftClientMixin {

    @Shadow @Final private Window window;

    @Inject(method = "onResolutionChanged", at = @At("HEAD"))
    private void setContext(CallbackInfo info) {
        GLFW.glfwMakeContextCurrent(window.getHandle());
    }

    @ModifyArg(method = "<init>", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/WindowProvider;createWindow(Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/client/util/Window;"))
    private WindowSettings getWindowSettings(WindowSettings settings) {
        MultiWindow.glfwWindowSettings = settings;
        return settings;
    }


    @Inject(method = "render", at = @At(value = "INVOKE_STRING", args = "ldc=yield", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"))
    private void renderWindows(@NotNull CallbackInfo ci) {
        var allWindows = WindowAPI.ID_TO_WINDOW.values().iterator();

        while (allWindows.hasNext()) {
            var window = allWindows.next();

            if (!window.isClosing())
                window.render();
            else {
                window.destroy();
                allWindows.remove();
            }

        }
    }
}
