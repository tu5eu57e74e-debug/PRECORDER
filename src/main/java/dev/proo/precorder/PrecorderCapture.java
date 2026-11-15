
package dev.proo.precorder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class PrecorderCapture {
    private PrecorderCapture() {}

    public record CapturedFrame(int width, int height, byte[] rgba) {}

    private static final int BUFFER_COUNT = 2;

    private static final GpuBuffer[] buffers =
            new GpuBuffer[BUFFER_COUNT];

    private static final AtomicBoolean[] busy = {
            new AtomicBoolean(false),
            new AtomicBoolean(false)
    };

    private static final AtomicInteger pendingCaptures =
            new AtomicInteger(0);

    private static final AtomicBoolean diagnosticPrinted =
            new AtomicBoolean(false);

    private static volatile String lastError;

    private static int width = -1;
    private static int height = -1;
    private static int nextSlot;

    public static String getLastError() {
        return lastError;
    }

    public static void clearError() {
        lastError = null;
        diagnosticPrinted.set(false);
    }

    public static int getPendingCaptures() {
        return pendingCaptures.get();
    }

    public static void captureFrame(
            MinecraftClient client,
            Consumer<CapturedFrame> onCaptured
    ) {
        RenderSystem.assertOnRenderThread();

        if (client == null || onCaptured == null) {
            throw new IllegalArgumentException(
                    "Missing capture arguments"
            );
        }

        Framebuffer framebuffer = client.getFramebuffer();
        GpuTexture texture = framebuffer.getColorAttachment();

        if (texture == null) {
            throw new IllegalStateException(
                    "Framebuffer texture is missing"
            );
        }

        if (texture.getFormat() != TextureFormat.RGBA8) {
            throw new IllegalStateException(
                    "Unsupported framebuffer format: "
                            + texture.getFormat()
            );
        }

        int w = texture.getWidth(0);
        int h = texture.getHeight(0);

        if (w <= 0 || h <= 0) {
            throw new IllegalStateException(
                    "Invalid frame dimensions: " + w + "x" + h
            );
        }

        long size = (long) w * h * 4L;

        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Frame is too large"
            );
        }

        if (w != width || h != height) {

            for (AtomicBoolean state : busy) {
                if (state.get()) {
                    return;
                }
            }

            releaseBuffers();

            for (int i = 0; i < BUFFER_COUNT; i++) {
                final int index = i;

                buffers[i] = RenderSystem.getDevice().createBuffer(
                        () -> "PRECORDER readback " + index,
                        GpuBuffer.USAGE_COPY_DST
                                | GpuBuffer.USAGE_MAP_READ,
                        size
                );
            }

            width = w;
            height = h;
            nextSlot = 0;
        }

        int slot = -1;

        for (int i = 0; i < BUFFER_COUNT; i++) {
            int candidate =
                    (nextSlot + i) % BUFFER_COUNT;

            if (!busy[candidate].get()) {
                slot = candidate;
                break;
            }
        }

        if (slot == -1) {
            return;
        }

        final int selectedSlot = slot;
        final int frameWidth = w;
        final int frameHeight = h;
        final int expectedSize = (int) size;

        GpuBuffer buffer = buffers[selectedSlot];

        if (buffer == null
                || !busy[selectedSlot].compareAndSet(false, true)) {
            return;
        }

        CommandEncoder encoder =
                RenderSystem.getDevice().createCommandEncoder();

        pendingCaptures.incrementAndGet();

        try {
            encoder.copyTextureToBuffer(
                    texture,
                    buffer,
                    0L,
                    () -> {
                        try (MappedView mapped =
                                     encoder.mapBuffer(
                                             buffer,
                                             true,
                                             false
                                     )) {

                            ByteBuffer source = mapped.data();

                            source.position(0);

                            if (source.remaining() < expectedSize) {
                                throw new IllegalStateException(
                                        "Incomplete GPU frame: "
                                                + source.remaining()
                                                + "/"
                                                + expectedSize
                                );
                            }

                            byte[] pixels =
                                    new byte[expectedSize];

                            source.get(pixels);

                            // Diagnose the first captured frame.
                            if (diagnosticPrinted.compareAndSet(
                                    false,
                                    true
                            )) {
                                long nonBlackPixels = 0;

                                int minValue = 255;
                                int maxValue = 0;

                                for (int i = 0;
                                     i < pixels.length;
                                     i += 4) {

                                    int red =
                                            pixels[i] & 0xFF;

                                    int green =
                                            pixels[i + 1] & 0xFF;

                                    int blue =
                                            pixels[i + 2] & 0xFF;

                                    if (red != 0
                                            || green != 0
                                            || blue != 0) {
                                        nonBlackPixels++;
                                    }

                                    minValue = Math.min(
                                            minValue,
                                            Math.min(
                                                    red,
                                                    Math.min(green, blue)
                                            )
                                    );

                                    maxValue = Math.max(
                                            maxValue,
                                            Math.max(red, Math.max(green, blue))
                                    );
                                }

                                System.out.println(
                                        "[PRECORDER] CAPTURE DIAGNOSTIC"
                                );

                                System.out.println(
                                        "[PRECORDER] Texture format: "
                                                + texture.getFormat()
                                );

                                System.out.println(
                                        "[PRECORDER] Texture usage: "
                                                + texture.usage()
                                );

                                System.out.println(
                                        "[PRECORDER] COPY_SRC enabled: "
                                                + ((texture.usage()
                                                & GpuTexture.USAGE_COPY_SRC) != 0)
                                );

                                System.out.println(
                                        "[PRECORDER] Dimensions: "
                                                + frameWidth
                                                + "x"
                                                + frameHeight
                                );

                                System.out.println(
                                        "[PRECORDER] Non-black pixels: "
                                                + nonBlackPixels
                                                + " / "
                                                + ((long) frameWidth
                                                * frameHeight)
                                );

                                System.out.println(
                                        "[PRECORDER] RGB range: "
                                                + minValue
                                                + " - "
                                                + maxValue
                                );

                                System.out.println(
                                        "[PRECORDER] First 16 bytes: "
                                                + bytesToHex(pixels, 16)
                                );
                            }

                            onCaptured.accept(
                                    new CapturedFrame(
                                            frameWidth,
                                            frameHeight,
                                            pixels
                                    )
                            );

                            lastError = null;

                        } catch (Throwable error) {

                            lastError =
                                    error.getClass().getSimpleName()
                                            + ": "
                                            + error.getMessage();

                            System.err.println(
                                    "[PRECORDER] GPU readback failed: "
                                            + lastError
                            );

                            error.printStackTrace();

                        } finally {
                            busy[selectedSlot].set(false);
                            pendingCaptures.decrementAndGet();
                        }
                    },
                    0
            );

            nextSlot =
                    (selectedSlot + 1) % BUFFER_COUNT;

        } catch (Throwable error) {

            busy[selectedSlot].set(false);
            pendingCaptures.decrementAndGet();

            lastError =
                    error.getClass().getSimpleName()
                            + ": "
                            + error.getMessage();

            throw new RuntimeException(
                    "GPU capture failed",
                    error
            );
        }
    }

    private static String bytesToHex(
            byte[] data,
            int count
    ) {
        StringBuilder result = new StringBuilder();

        int length = Math.min(data.length, count);

        for (int i = 0; i < length; i++) {
            if (i > 0) {
                result.append(' ');
            }

            result.append(
                    String.format("%02X", data[i] & 0xFF)
            );
        }

        return result.toString();
    }

    private static void releaseBuffers() {
        for (int i = 0; i < BUFFER_COUNT; i++) {

            if (buffers[i] != null) {
                buffers[i].close();
                buffers[i] = null;
            }

            busy[i].set(false);
        }
    }
                                    }
