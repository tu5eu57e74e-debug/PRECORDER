
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
                    client.player.sendMessage(
                            Text.literal("PRECORDER: " + PrecorderState.getStatus()),
                            false
                    );
                }
            }

            while (PrecorderKeybinds.pauseRecording.wasPressed()) {
                if (!PrecorderState.isRecording()) {
                    if (client.player != null) {
                        client.player.sendMessage(
                                Text.literal("PRECORDER: Start recording first"),
                                false
                        );
                    }
                    continue;
                }

                PrecorderState.togglePause();

                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("PRECORDER: " + PrecorderState.getStatus()),
                            false
                    );
                }
            }
        });
    }
}
