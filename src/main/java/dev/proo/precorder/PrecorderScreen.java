package dev.proo.precorder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Main Precorder menu. Recording controls are currently UI placeholders until
 * a real capture backend is integrated.
 */
public final class PrecorderScreen extends Screen {
    private final Screen parent;

    public PrecorderScreen(Screen parent) {
        super(Text.literal("Precorder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 52;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Start / Stop"),
                button -> this.client.player.sendMessage(
                        Text.literal("Precorder: capture engine is not connected yet."), false))
                .dimensions(centerX - 100, y, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Pause / Resume"),
                button -> this.client.player.sendMessage(
                        Text.literal("Precorder: pause control will work after capture engine integration."), false))
                .dimensions(centerX - 100, y + 26, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Settings"),
                button -> this.client.setScreen(new PrecorderSettingsScreen(this)))
                .dimensions(centerX - 100, y + 52, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"),
                button -> this.close())
                .dimensions(centerX - 100, y + 78, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 85, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Gameplay recorder controls"), this.width / 2,
                this.height / 2 - 69, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}

