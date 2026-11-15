
package dev.proo.precorder;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PrecorderRecordingManager {

    private final ArrayBlockingQueue<PrecorderCapture.CapturedFrame> frames =
            new ArrayBlockingQueue<>(2);

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicLong capturedFrames = new AtomicLong();
    private final AtomicLong encodedFrames = new AtomicLong();
    private final AtomicLong droppedFrames = new AtomicLong();

    private volatile boolean active;
    private volatile boolean paused;
    private volatile String error;

    private int fps;
    private File output;
    private File logFile;
    private Thread worker;
    private long lastCaptureTime;

    private File getRecordingsDirectory() throws IOException {
        ModContainer mod = FabricLoader.getInstance()
                .getModContainer("precorder")
                .orElseThrow(() ->
                        new IOException("PRECORDER mod container not found"));

        if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) {
            throw new IOException("Could not locate PRECORDER mod folder");
        }

        for (Path modPath : mod.getOrigin().getPaths()) {
            Path modsDirectory = modPath.getParent();

            if (modsDirectory == null
                    || modsDirectory.getFileName() == null
                    || !modsDirectory.getFileName()
                    .toString().equalsIgnoreCase("mods")) {
                continue;
            }

            Path versionDirectory = modsDirectory.getParent();

            if (versionDirectory != null) {
                return versionDirectory.resolve("p-recordings").toFile();
            }
        }

        throw new IOException("Could not locate active version mods folder");
    }

    public synchronized void start(PrecorderConfig config)
            throws IOException {

        if (config == null) {
            throw new IllegalArgumentException("Config is required");
        }

        if (active || (worker != null && worker.isAlive())) {
            throw new IllegalStateException("Recorder is busy");
        }

        fps = Math.max(1, config.fps);

        File directory = getRecordingsDirectory();

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create p-recordings folder");
        }

        output = new File(directory,
                "PRECORDER_" + System.currentTimeMillis() + ".mp4");

        logFile = new File(directory, "precorder-error.log");

        frames.clear();
        stopRequested.set(false);
        paused = false;
        error = null;

        capturedFrames.set(0);
        encodedFrames.set(0);
        droppedFrames.set(0);

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
        if (frame == null || !active || paused || stopRequested.get()) {
            return;
        }

        if (frames.offer(frame)) {
            capturedFrames.incrementAndGet();
        } else {
            droppedFrames.incrementAndGet();
        }
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
                encodedFrames.incrementAndGet();
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveError("Encoder interrupted", exception);

        } catch (Throwable exception) {
            saveError("Recording failed", exception);

        } finally {
            if (encoder.isStarted()) {
                try {
                    encoder.stop();
                } catch (Throwable exception) {
                    saveError("Could not finalize MP4", exception);
                }
            }

            if (error == null) {
                if (encodedFrames.get() == 0) {
                    saveError(
                            "No frames were encoded",
                            new IOException("No video frames reached the encoder")
                    );
                } else if (!output.exists() || output.length() == 0) {
                    saveError(
                            "Empty MP4 file",
                            new IOException("Encoder produced an empty file")
                    );
                }
            }

            active = false;
            stopRequested.set(true);
            frames.clear();
        }
    }

    private void saveError(String message, Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));

        error = message + ": " + exception;

        try {
            Files.writeString(
                    logFile.toPath(),
                    "\n\n--- " + java.time.Instant.now() + " ---\n"
                            + message + "\n" + writer,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException logError) {
            System.err.println("[PRECORDER] Could not write error log");
            logError.printStackTrace();
        }

        System.err.println("[PRECORDER] " + message);
        exception.printStackTrace();
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

    public long getCapturedFrames() {
        return capturedFrames.get();
    }

    public long getEncodedFrames() {
        return encodedFrames.get();
    }

    public long getDroppedFrames() {
        return droppedFrames.get();
    }
                }
