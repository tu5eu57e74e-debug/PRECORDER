package dev.proo.precorder;

/**
 * Tracks the requested recorder state.
 * This does not capture video until a capture backend is connected.
 */
public final class PrecorderState {
    private static boolean recording = false;
    private static boolean paused = false;

    private PrecorderState() {}

    public static boolean isRecording() {
        return recording;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void start() {
        recording = true;
        paused = false;
    }

    public static void stop() {
        recording = false;
        paused = false;
    }

    public static void toggleRecording() {
        if (recording) stop();
        else start();
    }

    public static void togglePause() {
        if (recording) paused = !paused;
    }
}
