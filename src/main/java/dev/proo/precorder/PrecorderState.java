
package dev.proo.precorder;

public final class PrecorderState {
    private static boolean recording = false;
    private static boolean paused = false;
    private static String status = "Ready";

    private PrecorderState() {}

    public static boolean isRecording() {
        return recording;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static String getStatus() {
        return status;
    }

    public static void start() {
        recording = true;
        paused = false;
        status = "Recording";
    }

    public static void stop() {
        recording = false;
        paused = false;
        status = "Ready";
    }

    public static void pause() {
        if (recording) {
            paused = true;
            status = "Paused";
        }
    }

    public static void resume() {
        if (recording) {
            paused = false;
            status = "Recording";
        }
    }

    public static void toggleRecording() {
        if (recording) {
            stop();
        } else {
            start();
        }
    }

    public static void togglePause() {
        if (recording) {
            if (paused) resume();
            else pause();
        }
    }
}
