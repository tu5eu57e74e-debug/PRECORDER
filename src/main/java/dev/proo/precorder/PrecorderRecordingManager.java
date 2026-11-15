
package dev.proo.precorder;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrecorderRecordingManager {

    private final ArrayBlockingQueue<PrecorderCapture.CapturedFrame> frames =
            new ArrayBlockingQueue<>(2);

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private volatile boolean active;
    private volatile boolean paused;
    private volatile String error;

    private int fps;
    private File output;
    private Thread worker;
    private long lastCaptureTime;

    public synchronized void start(PrecorderConfig config)
            throws IOException {

        if (config == null) {
            throw new IllegalArgumentException("Config is required");
        }

        if (active || (worker != null && worker.isAlive())) {
            throw new IllegalStateException("Recorder is busy");
        }

        fps = Math.max(1, config.fps);

        File recordingsDirectory = FabricLoader.getInstance()
                .getGameDir()
                .resolve("p-recordings")
                .toFile();

        if (!recordingsDirectory.exists()
                && !recordingsDirectory.mkdirs()) {
            throw new IOException("Could not create p-recordings folder");
        }

        output = new File(
                recordingsDirectory,
                "PRECORDER_" + System.currentTimeMillis() + ".mp4"
        );

        frames.clear();
        stopRequested.set(false);
        paused = false;
        error = null;
        lastCaptureTime = 0;
        active = true;

        worker = new Thread(this::encodeLoop, "PRECORDER-Encoder");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean shouldCapture() {
        if (!active || paused || stopRequested.get()) {
            return false;
        }

        long now = System.nanoTime();
        long interval = 1_000_000_000L / fps;

        if (now - lastCaptureTime < interval) {
            return false;
        }

        lastCaptureTime = now;
        return true;
    }

    public void submit(PrecorderCapture.CapturedFrame frame) {
        if (frame == null || !active || paused
                || stopRequested.get()) {
            return;
        }

        frames.offer(frame);
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    public synchronized void stop() {
        if (active) {
            active = false;
            stopRequested.set(true);
        }
    }

    private void encodeLoop() {
        PrecorderVideoEncoder encoder = new PrecorderVideoEncoder();

        try {
            while (!stopRequested.get() || !frames.isEmpty()) {
                PrecorderCapture.CapturedFrame frame =
                        frames.poll(100, TimeUnit.MILLISECONDS);

                if (frame == null) {
                    continue;
                }

                if (!encoder.isStarted()) {
                    encoder.start(
                            output,
                            frame.width(),
                            frame.height(),
                            fps
                    );
                }

                encoder.addFrame(frame);
            }

            if (encoder.isStarted()) {
                encoder.stop();
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            error = "Encoder interrupted";
        } catch (Exception exception) {
            error = exception.toString();
        } finally {
            active = false;
            stopRequested.set(true);
            frames.clear();
        }
    }

    public boolean isRecording() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getError() {
        return error;
    }
    }
