
package dev.proo.precorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class PrecorderClient implements ClientModInitializer {

    private static final PrecorderRecordingManager RECORDER =
            new PrecorderRecordingManager();

    private static String lastErrorShown;

    @Override
    public void onInitializeClient() {
        PrecorderKeybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (PrecorderKeybinds.openMenu.wasPressed()) {
                client.setScreen(new PrecorderScreen(client.currentScreen));
            }

            while (PrecorderKeybinds.toggleRecording.wasPressed()) {
                if (RECORDER.isRecording()) {
                    RECORDER.stop();
                    PrecorderState.stop();
                    message(client, "Stopping recording...");
                } else {
                    try {
                        RECORDER.start(PrecorderConfig.load());
                        PrecorderState.start();
                        message(client, "Recording started");
                    } catch (Exception exception) {
                        message(client, "Could not start: "
                                + exception.getMessage());
                    }
                }
            }

            while (PrecorderKeybinds.pauseRecording.wasPressed()) {
                if (!RECORDER.isRecording()) {
                    message(client, "Start recording first");
                    continue;
                }

                boolean pause = !RECORDER.isPaused();
                RECORDER.setPaused(pause);

                if (pause) {
                    PrecorderState.pause();
                } else {
                    PrecorderState.resume();
                }

                message(client, pause ? "Paused" : "Recording");
            }

            String error = RECORDER.getError();

            if (error != null && !error.equals(lastErrorShown)) {
                lastErrorShown = error;
                message(client, "Recording error: " + error);
            }
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();

            if (!RECORDER.shouldCapture()) {
                return;
            }

            PrecorderCapture.captureFrame(
                    client,
                    RECORDER::submit
            );
        });
    }

    private static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("PRECORDER: " + text),
                    false
            );
        }
    }
}
