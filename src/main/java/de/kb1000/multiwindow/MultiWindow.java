package de.kb1000.multiwindow;

import de.kb1000.multiwindow.accessor.ScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

@Environment(EnvType.CLIENT)
public class MultiWindow implements ClientModInitializer {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    public static WindowSettings settings;
    public static boolean setShare = false;

    public static final Map<String,ScreenWindow> ALL_WINDOWS = new TreeMap<>();

    @Override
    public void onInitializeClient() {}

    public static void open(String id, Screen screen) {
        MultiWindow.ALL_WINDOWS.put(id, ((ScreenAccessor) screen).multi_window_getWindow());
    }

    public static void open(String id, Screen screen, String title) {
        ScreenWindow window = ((ScreenAccessor) screen).multi_window_getWindow();
        window.setTitle(title);

        MultiWindow.ALL_WINDOWS.put(id, window);
    }

    public static void close(String id) {
        MultiWindow.ALL_WINDOWS.remove(id);
    }

    public static void setScreen(String id, Screen screen) {
        ScreenWindow window = ALL_WINDOWS.get(id);
        if (window == null)
            return;

        window.setScreen(screen);
    }

    public static ScreenWindow getWindow(String id) {
        return ALL_WINDOWS.get(id);
    }
}
