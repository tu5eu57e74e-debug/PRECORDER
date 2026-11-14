package dev.proo.precorder;

public final class RecorderState {
    private static boolean recording;

    private RecorderState() {}

    public static boolean isRecording() {
        return recording;
    }

    public static void toggle() {
        recording = !recording;
    }
}
