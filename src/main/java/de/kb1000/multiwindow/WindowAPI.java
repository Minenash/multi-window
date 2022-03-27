package de.kb1000.multiwindow;

import net.minecraft.client.gui.screen.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class WindowAPI {

    public static final Map<String, ScreenWindow> ID_TO_WINDOW = new TreeMap<>();
    public static final Map<Screen, String> SCREEN_TO_ID = new HashMap<>();

    public static void open(String id, Screen screen) {
        ScreenWindow window = new ScreenWindow(screen);
        ID_TO_WINDOW.put(id, window);
        SCREEN_TO_ID.put(screen, id);
    }

    public static void open(String id, Screen screen, String title) {
        ScreenWindow window = new ScreenWindow(screen);
        window.setTitle(title);

        ID_TO_WINDOW.put(id, window);
        SCREEN_TO_ID.put(screen, id);
    }

    public static void close(String id) {
        ScreenWindow window = ID_TO_WINDOW.get(id);
        SCREEN_TO_ID.remove(window.getScreen());
        ID_TO_WINDOW.remove(id).destroy();
    }

    public static void close(Screen screen) {
        String id = SCREEN_TO_ID.get(screen);
        ID_TO_WINDOW.remove(id).destroy();
        SCREEN_TO_ID.remove(screen);
    }

    public static ScreenWindow getWindow(String id) {
        return ID_TO_WINDOW.get(id);
    }

    public static ScreenWindow getWindow(Screen screen) {
        return ID_TO_WINDOW.get(SCREEN_TO_ID.get(screen));
    }

}
