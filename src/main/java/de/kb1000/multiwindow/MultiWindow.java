package de.kb1000.multiwindow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.WindowSettings;

@Environment(EnvType.CLIENT)
public class MultiWindow implements ClientModInitializer {
    public static WindowSettings glfwWindowSettings;

    @Override
    public void onInitializeClient() {

    }


}
