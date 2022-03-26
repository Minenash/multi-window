package de.kb1000.multiwindow.accessor;

import de.kb1000.multiwindow.ScreenWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public interface ScreenAccessor {
    @NotNull ScreenWindow multi_window_getWindow();
}
