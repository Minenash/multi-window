package de.kb1000.multiwindow.mixin;

import de.kb1000.multiwindow.accessor.ScreenAccessor;
import de.kb1000.multiwindow.ScreenWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Screen.class)
@Environment(EnvType.CLIENT)
public class ScreenMixin implements ScreenAccessor {
    @Unique
    private ScreenWindow window;

    @Override
    public ScreenWindow multi_window_getWindow() {
        if (window != null && !window.isClosing())
            return window;

        return window = new ScreenWindow((Screen) (Object) this);
    }

}
