package dev.proo.precorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;

public final class PrecorderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PrecorderKeybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (PrecorderKeybinds.openMenu.wasPressed()) {
                client.setScreen(new PrecorderScreen(client.currentScreen));
            }

            while (PrecorderKeybinds.toggleRecording.wasPressed()) {
                PrecorderState.toggleRecording();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            PrecorderState.isRecording()
                                    ? "Precorder: recording state ON (capture backend not connected)"
                                    : "Precorder: recording state OFF"
                    ), false);
                }
            }

            while (PrecorderKeybinds.pauseRecording.wasPressed()) {
                PrecorderState.togglePause();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            !PrecorderState.isRecording()
                                    ? "Precorder: start first"
                                    : PrecorderState.isPaused() ? "Precorder: paused" : "Precorder: resumed"
                    ), false);
                }
            }
        });
    }
}
