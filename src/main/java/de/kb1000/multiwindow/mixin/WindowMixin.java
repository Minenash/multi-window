package de.kb1000.multiwindow.mixin;

import de.kb1000.multiwindow.MultiWindow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Window.class)
public class WindowMixin {

    @ModifyArg(method = "<init>", index = 4, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    public long modifyShare(long share) {
        return MultiWindow.setShare ? MinecraftClient.getInstance().getWindow().getHandle() : share;
    }

}
