
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

    private static int sourceWidth = -1;
    private static int sourceHeight = -1;
    private static int outputWidth = -1;
    private static int outputHeight = -1;
    private static int outputResolution = 1080;

    private static int scaleTexture;
    private static int scaleFramebuffer;
    private static int writeIndex;

    private static boolean pboSupported = true;
    private static boolean hasPendingFrame;
    private static long pendingTimestampNanos;

    private static volatile String lastError;
    private static final AtomicBoolean resetRequested =
            new AtomicBoolean(false);

    public static String getLastError() {
        return lastError;
    }

    public static void clearError() {
        lastError = null;
    }

    public static void beginRecordingSession(int resolution) {
        if (resolution == 480 || resolution == 720 || resolution == 1080) {
            outputResolution = resolution;
        } else {
            outputResolution = 1080;
        }
        resetRequested.set(true);
    }

    private static void resetSession() {
        if (!resetRequested.compareAndSet(true, false)) {
            return;
        }

        deletePbos();
        deleteScaleTarget();
        writeIndex = 0;
        hasPendingFrame = false;
        pendingTimestampNanos = 0L;
        sourceWidth = -1;
        sourceHeight = -1;
    }

    public static int getPendingCaptures() {
        return hasPendingFrame ? 1 : 0;
    }

    public static void captureFrame(
            MinecraftClient client,
            Consumer<CapturedFrame> onCaptured
    ) {
        if (client == null || onCaptured == null) {
            return;
        }

        resetSession();

        int frameWidth = client.getWindow().getFramebufferWidth();
        int frameHeight = client.getWindow().getFramebufferHeight();

        if (frameWidth <= 0 || frameHeight <= 0) {
            lastError = "Invalid framebuffer dimensions";
            return;
        }

        int targetHeight = outputResolution;
        int targetWidth = Math.max(
                2,
                ((int) Math.round(
                        (double) frameWidth / frameHeight * targetHeight
                )) & ~1
        );

        long byteCountLong = (long) targetWidth * targetHeight * 4L;
        if (byteCountLong > Integer.MAX_VALUE) {
            lastError = "Frame is too large";
            return;
        }

        int byteCount = (int) byteCountLong;

        try {
            if (frameWidth != sourceWidth
                    || frameHeight != sourceHeight
                    || targetWidth != outputWidth
                    || targetHeight != outputHeight) {
                deletePbos();
                deleteScaleTarget();

                sourceWidth = frameWidth;
                sourceHeight = frameHeight;
                outputWidth = targetWidth;
                outputHeight = targetHeight;
                writeIndex = 0;
                hasPendingFrame = false;
            }

            ensureScaleTarget();
            scaleFramebuffer(frameWidth, frameHeight);

            long timestamp = System.nanoTime();

            if (pboSupported) {
                try {
                    captureWithPbo(byteCount, timestamp, onCaptured);
                    lastError = null;
                    return;
                } catch (Throwable error) {
                    lastError = "PBO capture failed: " + error;
                    pboSupported = false;
                    deletePbos();
                    hasPendingFrame = false;
                }
            }

            captureSynchronously(byteCount, timestamp, onCaptured);
            lastError = null;

        } catch (Throwable error) {
            lastError = "Screen capture failed: "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage();
            error.printStackTrace();
        }
    }

    private static void ensureScaleTarget() {
        if (scaleFramebuffer != 0 && scaleTexture != 0) {
            return;
        }

        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        try {
            scaleTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, scaleTexture);

            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,
                    GL11.GL_LINEAR
            );
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER,
                    GL11.GL_LINEAR
            );
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S,
                    GL12Compat.CLAMP_TO_EDGE
            );
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T,
                    GL12Compat.CLAMP_TO_EDGE
            );

            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    outputWidth,
                    outputHeight,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    0L
            );

            scaleFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(
                    GL30.GL_FRAMEBUFFER,
                    scaleFramebuffer
            );

            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    scaleTexture,
                    0
            );

            int status = GL30.glCheckFramebufferStatus(
                    GL30.GL_FRAMEBUFFER
            );

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException(
                        "Scale framebuffer incomplete: 0x"
                                + Integer.toHexString(status)
                );
            }
        } finally {
            GL30.glBindFramebuffer(
                    GL30.GL_FRAMEBUFFER,
                    oldFramebuffer
            );
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
        }
    }

    private static void scaleFramebuffer(
            int frameWidth,
            int frameHeight
    ) {
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);

            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    scaleFramebuffer
            );

            GL30.glBlitFramebuffer(
                    0, 0, frameWidth, frameHeight,
                    0, 0, outputWidth, outputHeight,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR
            );

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(
                        "GPU scaling error: 0x"
                                + Integer.toHexString(error)
                );
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL11.glReadBuffer(oldReadBuffer);
        }
    }

    private static void captureWithPbo(
            int byteCount,
            long timestamp,
            Consumer<CapturedFrame> onCaptured
    ) {
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int oldPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);

        try {
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER,
                    scaleFramebuffer
            );
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
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
                    throw new IllegalStateException("Could not map PBO");
                }

                byte[] pixels = new byte[byteCount];

                try {
                    mapped.position(0);
                    mapped.get(pixels);
                } finally {
                    if (!GL15.glUnmapBuffer(
                            GL21.GL_PIXEL_PACK_BUFFER
                    )) {
                        throw new IllegalStateException("PBO unmap failed");
                    }
                }

                onCaptured.accept(new CapturedFrame(
                        outputWidth,
                        outputHeight,
                        pixels,
                        pendingTimestampNanos
                ));
            }

            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    pbo[writeIndex]
            );

            GL11.glReadPixels(
                    0,
                    0,
                    outputWidth,
                    outputHeight,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    0L
            );

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(
                        "PBO read error: 0x" + Integer.toHexString(error)
                );
            }

            pendingTimestampNanos = timestamp;
            hasPendingFrame = true;
            writeIndex = (writeIndex + 1) % BUFFER_COUNT;

        } finally {
            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    oldPack
            );
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, oldAlignment);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
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
                throw new IllegalStateException("Could not create PBO");
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
            int byteCount,
            long timestamp,
            Consumer<CapturedFrame> onCaptured
    ) {
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int oldPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);

        ByteBuffer pixels = null;

        try {
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER,
                    scaleFramebuffer
            );
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

            pixels = MemoryUtil.memAlloc(byteCount);

            GL11.glReadPixels(
                    0,
                    0,
                    outputWidth,
                    outputHeight,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels
            );

            byte[] rgba = new byte[byteCount];
            pixels.position(0);
            pixels.get(rgba);

            onCaptured.accept(new CapturedFrame(
                    outputWidth,
                    outputHeight,
                    rgba,
                    timestamp
            ));
        } finally {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }

            GL15.glBindBuffer(
                    GL21.GL_PIXEL_PACK_BUFFER,
                    oldPack
            );
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, oldAlignment);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL11.glReadBuffer(oldReadBuffer);
        }
    }

    private static void deletePbos() {
        for (int i = 0; i < BUFFER_COUNT; i++) {
            if (pbo[i] != 0) {
                GL15.glDeleteBuffers(pbo[i]);
                pbo[i] = 0;
            }
        }
    }

    private static void deleteScaleTarget() {
        if (scaleFramebuffer != 0) {
            GL30.glDeleteFramebuffers(scaleFramebuffer);
            scaleFramebuffer = 0;
        }

        if (scaleTexture != 0) {
            GL11.glDeleteTextures(scaleTexture);
            scaleTexture = 0;
        }
    }
    }
