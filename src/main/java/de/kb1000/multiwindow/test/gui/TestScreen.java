package de.kb1000.multiwindow.test.gui;

import de.kb1000.multiwindow.WindowAPI;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class TestScreen extends Screen {

    public TestScreen(String title) {
        super(new LiteralText(title));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        renderBackground(matrices);

        drawCenteredText(matrices, client.textRenderer, title, width / 2, height / 2, 65535);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        WindowAPI.getWindow(this).setScreen(new TestScreen(mouseX + " " + mouseY));

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
