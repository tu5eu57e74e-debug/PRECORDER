
package dev.proo.precorder;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PrecorderCapture {
    private PrecorderCapture() {}

    public record CapturedFrame(
            int width,
            int height,
            byte[] rgba,
            long timestampNanos
    ) {}

    private static final int BUFFER_COUNT = 2;
    private static final int[] pbo = new int[BUFFER_COUNT];

    private static int width = -1;
    private static int height = -1;
    private static int writeIndex = 0;

    private static boolean pboSupported = true;
    private static volatile boolean hasPendingFrame = false;

    private static long pendingTimestampNanos;
    private static volatile String lastError;

    private static final AtomicBoolean sessionResetRequested =
            new AtomicBoolean(false);

    public static String getLastError() {
        return lastError;
    }

    public static void clearError() {
        lastError = null;
    }

    // Request a reset. OpenGL operations happen later on the render thread.
    public static void beginRecordingSession() {
        sessionResetRequested.set(true);
    }

    private static void resetForNewSession() {
        if (!sessionResetRequested.compareAndSet(true, false)) {
            return;
        }

        // Delete the actual GPU buffers to discard old session data.
        deletePbos();

        writeIndex = 0;
        hasPendingFrame = false;
        pendingTimestampNanos = 0L;
    }

    public static int getPendingCaptures() {
        return 0;
    }

    public static void captureFrame(
            MinecraftClient client,
            Consumer<CapturedFrame> onCaptured
    ) {
        if (client == null || onCaptured == null) {
            throw new IllegalArgumentException("Missing capture arguments");
        }

        // captureFrame runs while Minecraft's render context is current.
        resetForNewSession();

        long captureTimestampNanos = System.nanoTime();

        int frameWidth = client.getWindow().getFramebufferWidth();
        int frameHeight = client.getWindow().getFramebufferHeight();

        if (frameWidth <= 0 || frameHeight <= 0) {
            lastError = "Invalid framebuffer dimensions: "
                    + frameWidth + "x" + frameHeight;
            return;
        }

        long byteCountLong = (long) frameWidth * frameHeight * 4L;

        if (byteCountLong > Integer.MAX_VALUE) {
            lastError = "Frame is too large";
            return;
        }

        int byteCount = (int) byteCountLong;

        if (frameWidth != width || frameHeight != height) {
            deletePbos();

            width = frameWidth;
            height = frameHeight;
            writeIndex = 0;
            hasPendingFrame = false;
            pendingTimestampNanos = 0L;
        }

        if (pboSupported) {
            try {
                captureWithPbo(
                        frameWidth,
                        frameHeight,
                        byteCount,
                        captureTimestampNanos,
                        onCaptured
                );
                return;
            } catch (Throwable error) {
                lastError = "PBO capture failed: "
                        + error.getClass().getSimpleName()
                        + ": " + error.getMessage();

                System.err.println("[PRECORDER] " + lastError);
                error.printStackTrace();

                pboSupported = false;
                deletePbos();
                hasPendingFrame = false;
                pendingTimestampNanos = 0L;
            }
        }

        captureSynchronously(
                frameWidth,
                frameHeight,
                byteCount,
                captureTimestampNanos,
                onCaptured
        );
    }

    private static void captureWithPbo(
            int frameWidth,
            int frameHeight,
            int byteCount,
            long captureTimestampNanos,
            Consumer<CapturedFrame> onCaptured
    ) {
        int oldReadFramebuffer =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        int oldReadBuffer =
                GL11.glGetInteger(GL11.GL_READ_BUFFER);

        int oldPackBuffer =
                GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        int oldPackAlignment =
                GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);

        try {
            clearGlErrors();

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

            ensurePbos(byteCount);

            if (hasPendingFrame) {
                int readIndex = (writeIndex + 1) % BUFFER_COUNT;

                GL15.glBindBuffer(
                        GL21.GL_PIXEL_PACK_BUFFER,
                        pbo[readIndex]
                );

                ByteBuffer mapped = GL15.glMapBuffer(
                        GL21.GL_PIXEL_PACK_BUFFER,
                        GL15.GL_READ_ONLY,
                        byteCount,
                        null
                );

                if (mapped == null) {
                    throw new IllegalStateException(
                            "Could not map previous PBO frame"
                    );
                }

                byte[] pixels = new byte[byteCount];

                try {
                    mapped.position(0);
                    mapped.get(pixels);
                } finally {
                    if (!GL15.glUnmapBuffer(
                            GL21.GL_PIXEL_PACK_BUFFER
                    )) {
                        throw new IllegalStateException(
                                "PBO data became invalid while unmapping"
                        );
                    }
                }

                onCaptured.accept(
                        new CapturedFrame(
                                frameWidth,
                                frameHeight,
                                pixels,
                                pendingTimestampNanos
                        )
                );
            }

            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    pbo[writeIndex]
            );

            GL11.glReadPixels(
                    0,
                    0,
                    frameWidth,
                    frameHeight,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    0L
            );

            int error = GL11.glGetError();

            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(
                        "glReadPixels returned OpenGL error 0x"
                                + Integer.toHexString(error)
                );
            }

            hasPendingFrame = true;
            pendingTimestampNanos = captureTimestampNanos;
            writeIndex = (writeIndex + 1) % BUFFER_COUNT;
            lastError = null;

        } finally {
            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    oldPackBuffer
            );

            GL11.glPixelStorei(
                    GL11.GL_PACK_ALIGNMENT,
                    oldPackAlignment
            );

            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER,
                    oldReadFramebuffer
            );

            GL11.glReadBuffer(oldReadBuffer);
        }
    }

    private static void ensurePbos(int byteCount) {
        if (pbo[0] != 0 && pbo[1] != 0) {
            return;
        }

        for (int i = 0; i < BUFFER_COUNT; i++) {
            pbo[i] = GL15.glGenBuffers();

            if (pbo[i] == 0) {
                throw new IllegalStateException(
                        "Could not create PBO " + i
                );
            }

            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    pbo[i]
            );

            GL15.glBufferData(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    (long) byteCount,
                    GL15.GL_STREAM_READ
            );
        }

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
    }

    private static void captureSynchronously(
            int frameWidth,
            int frameHeight,
            int byteCount,
            long captureTimestampNanos,
            Consumer<CapturedFrame> onCaptured
    ) {
        int oldReadFramebuffer =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        int oldReadBuffer =
                GL11.glGetInteger(GL11.GL_READ_BUFFER);

        int oldPackBuffer =
                GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        int oldPackAlignment =
                GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);

        ByteBuffer pixels = null;

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

            pixels = MemoryUtil.memAlloc(byteCount);

            GL11.glReadPixels(
                    0,
                    0,
                    frameWidth,
                    frameHeight,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels
            );

            int error = GL11.glGetError();

            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(
                        "Synchronous glReadPixels error 0x"
                                + Integer.toHexString(error)
                );
            }

            byte[] rgba = new byte[byteCount];
            pixels.position(0);
            pixels.get(rgba);

            onCaptured.accept(
                    new CapturedFrame(
                            frameWidth,
                            frameHeight,
                            rgba,
                            captureTimestampNanos
                    )
            );

            lastError = null;

        } catch (Throwable error) {
            lastError = "Screen capture failed: "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage();

            System.err.println("[PRECORDER] " + lastError);
            error.printStackTrace();

        } finally {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }

            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    oldPackBuffer
            );

            GL11.glPixelStorei(
                    GL11.GL_PACK_ALIGNMENT,
                    oldPackAlignment
            );

            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER,
                    oldReadFramebuffer
            );

            GL11.glReadBuffer(oldReadBuffer);
        }
    }

    private static void deletePbos() {
        for (int i = 0; i < BUFFER_COUNT; i++) {
            if (pbo[i] != 0) {
                try {
                    GL15.glDeleteBuffers(pbo[i]);
                } catch (Throwable ignored) {
                }

                pbo[i] = 0;
            }
        }
    }

    private static void clearGlErrors() {
        for (int i = 0; i < 16; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                break;
            }
        }
    }
            }
