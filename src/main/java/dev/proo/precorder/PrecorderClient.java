
package dev.proo.precorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacpp.Loader;

public final class PrecorderClient implements ClientModInitializer {

    private static final PrecorderRecordingManager RECORDER =
            new PrecorderRecordingManager();

    private static String lastErrorShown;

    private static void checkFFmpeg() {
        try {
            Loader.load(avcodec.class);

            AVCodec encoder =
                    avcodec.avcodec_find_encoder(avcodec.AV_CODEC_ID_H264);

            if (encoder == null || encoder.isNull()) {
                throw new IllegalStateException(
                        "H264 encoder is unavailable"
                );
            }

            System.out.println("[PRECORDER] FFmpeg loaded.");
            System.out.println("[PRECORDER] H264 encoder available.");

        } catch (Throwable error) {
            System.err.println(
                    "[PRECORDER] FFmpeg check failed: " + error
            );
            error.printStackTrace();
        }
    }

    public static boolean toggleRecording() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (RECORDER.isSaving()) {
            message(client, "Video is still saving!");
            return false;
        }

        if (RECORDER.isRecording()) {
            RECORDER.stop();
            PrecorderState.stop();

            message(client, "Saving video...");
            return true;
        }

        try {
            PrecorderCapture.clearError();

            RECORDER.start(PrecorderConfig.load());

            // Request a clean PBO session.
            PrecorderCapture.beginRecordingSession();

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
            // Reset delayed frames when recording resumes.
            PrecorderCapture.beginRecordingSession();
            PrecorderState.resume();
        }

        message(client, pause ? "Paused" : "Recording");
        return true;
    }

    public static void captureFrame() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || !RECORDER.shouldCapture()) {
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

    @Override
    public void onInitializeClient() {
        checkFFmpeg();

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

            if (RECORDER.consumeSaveComplete()) {
                message(client, "Video saved successfully!");
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
