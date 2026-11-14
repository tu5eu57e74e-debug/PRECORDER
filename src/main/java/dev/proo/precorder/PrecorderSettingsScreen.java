
package dev.proo.precorder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class PrecorderSettingsScreen extends Screen {
    private final Screen parent;
    private final PrecorderConfig config = PrecorderConfig.load();

    public PrecorderSettingsScreen(Screen parent) {
        super(Text.literal("PRECORDER Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 55;

        this.addDrawableChild(ButtonWidget.builder(resolutionText(), button -> {
            config.resolution = next(config.resolution, new int[]{400, 700, 1080});
            button.setMessage(resolutionText());
            config.save();
        }).dimensions(x, y, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(fpsText(), button -> {
            config.fps = next(config.fps, new int[]{30, 60, 120});
            button.setMessage(fpsText());
            config.save();
        }).dimensions(x, y + 26, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(bitrateText(), button -> {
            config.bitrateMbps += 2;
            if (config.bitrateMbps > 20) {
                config.bitrateMbps = 4;
            }
            button.setMessage(bitrateText());
            config.save();
        }).dimensions(x, y + 52, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(hudText(), button -> {
            config.showHud = !config.showHud;
            button.setMessage(hudText());
            config.save();
        }).dimensions(x, y + 78, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            config.save();
            this.client.setScreen(this.parent);
        }).dimensions(x, y + 104, 200, 20).build());
    }

    private Text resolutionText() {
        return Text.literal("Resolution: " + config.resolution + "p");
    }

    private Text fpsText() {
        return Text.literal("FPS: " + config.fps);
    }

    private Text bitrateText() {
        return Text.literal("Bitrate: " + config.bitrateMbps + " Mbps");
    }

    private Text hudText() {
        return Text.literal("Show HUD: " + (config.showHud ? "ON" : "OFF"));
    }

    private static int next(int current, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 80, 0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Settings are saved in config/precorder.json"),
                this.width / 2, this.height / 2 + 85, 0xA0A0A0
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        config.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
