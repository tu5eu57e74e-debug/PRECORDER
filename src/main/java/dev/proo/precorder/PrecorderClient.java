
package dev.proo.precorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class PrecorderClient implements ClientModInitializer {

    private static final PrecorderRecordingManager RECORDER =
            new PrecorderRecordingManager();

    private static String lastErrorShown;

    public static boolean toggleRecording() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (RECORDER.isRecording()) {
            RECORDER.stop();
            PrecorderState.stop();
            message(client, "Stopping recording...");
            return true;
        }

        try {
            PrecorderCapture.clearError();
            RECORDER.start(PrecorderConfig.load());
            PrecorderState.start();
            lastErrorShown = null;
            message(client, "Recording started");
            return true;

        } catch (Exception exception) {
            message(
                    client,
                    "Could not start: " + exception.getMessage()
            );
            return false;
        }
    }

    public static boolean togglePause() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!RECORDER.isRecording()) {
            message(client, "Start recording first");
            return false;
        }

        boolean pause = !RECORDER.isPaused();
        RECORDER.setPaused(pause);

        if (pause) {
            PrecorderState.pause();
        } else {
            PrecorderState.resume();
        }

        message(client, pause ? "Paused" : "Recording");
        return true;
    }

    @Override
    public void onInitializeClient() {
        PrecorderKeybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (PrecorderKeybinds.openMenu.wasPressed()) {
                client.setScreen(
                        new PrecorderScreen(client.currentScreen)
                );
            }

            while (PrecorderKeybinds.toggleRecording.wasPressed()) {
                toggleRecording();
            }

            while (PrecorderKeybinds.pauseRecording.wasPressed()) {
                togglePause();
            }

            String recorderError = RECORDER.getError();

            if (recorderError != null) {
                showCaptureError(client, recorderError);
            }

            String captureError = PrecorderCapture.getLastError();

            if (captureError != null) {
                showCaptureError(
                        client,
                        "Screen readback: " + captureError
                );
                RECORDER.stop();
                PrecorderState.stop();
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.of("precorder", "capture"),
                (drawContext, tickCounter) -> {

                    MinecraftClient client =
                            MinecraftClient.getInstance();

                    if (client.player == null
                            || !RECORDER.shouldCapture()) {
                        return;
                    }

                    try {
                        PrecorderCapture.captureFrame(
                                client,
                                RECORDER::submit
                        );

                    } catch (Throwable error) {
                        String details =
                                error.getClass().getSimpleName()
                                        + ": " + error.getMessage();

                        showCaptureError(client, details);

                        RECORDER.stop();
                        PrecorderState.stop();
                    }
                }
        );
    }

    private static void showCaptureError(
            MinecraftClient client,
            String error
    ) {
        if (error == null || error.equals(lastErrorShown)) {
            return;
        }

        lastErrorShown = error;

        message(client, "Recording error: " + error);
    }

    private static void message(
            MinecraftClient client,
            String text
    ) {
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("PRECORDER: " + text),
                    false
            );
        }
    }
}
