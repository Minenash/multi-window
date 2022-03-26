package de.kb1000.multiwindow.gl.events;

@FunctionalInterface
public interface MouseScrollCallback {
    void onMouseScroll(double xOffset, double yOffset);
}
