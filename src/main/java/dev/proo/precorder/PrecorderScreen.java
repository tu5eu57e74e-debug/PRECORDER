package dev.proo.precorder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class PrecorderScreen extends Screen {
    private final Screen parent;

    public PrecorderScreen(Screen parent) {
        super(Text.literal("Precorder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 48;

        this.addDrawableChild(ButtonWidget.builder(recordText(), button -> {
            PrecorderState.toggleRecording();
            button.setMessage(recordText());
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal(
                        PrecorderState.isRecording()
                                ? "Precorder: recording state ON (capture backend not connected)"
                                : "Precorder: recording state OFF"
                ), false);
            }
        }).dimensions(x, y, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(pauseText(), button -> {
            PrecorderState.togglePause();
            button.setMessage(pauseText());
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal(
                        !PrecorderState.isRecording()
                                ? "Precorder: start first"
                                : PrecorderState.isPaused() ? "Precorder: paused" : "Precorder: resumed"
                ), false);
            }
        }).dimensions(x, y + 26, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Settings"),
                button -> this.client.setScreen(new PrecorderSettingsScreen(this)))
                .dimensions(x, y + 52, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"),
                button -> this.close()).dimensions(x, y + 78, 200, 20).build());
    }

    private Text recordText() {
        return Text.literal(PrecorderState.isRecording() ? "Stop" : "Start");
    }

    private Text pauseText() {
        if (!PrecorderState.isRecording()) return Text.literal("Pause (start first)");
        return Text.literal(PrecorderState.isPaused() ? "Resume" : "Pause");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 78, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("F8 Menu  |  F9 Start/Stop  |  F10 Pause"),
                this.width / 2, this.height / 2 - 62, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
