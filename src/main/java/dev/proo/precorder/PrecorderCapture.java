
package dev.proo.precorder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class PrecorderCapture {
    private PrecorderCapture() {}

    public record CapturedFrame(int width, int height, byte[] rgba) {}

    private static final int BUFFER_COUNT = 2;
    private static final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];
    private static final boolean[] busy = new boolean[BUFFER_COUNT];

    private static int width = -1;
    private static int height = -1;
    private static int nextSlot;

    public static void captureFrame(
            MinecraftClient client,
            Consumer<CapturedFrame> onCaptured
    ) {
        RenderSystem.assertOnRenderThread();

        if (client == null || onCaptured == null) {
            throw new IllegalArgumentException("Missing capture arguments");
        }

        Framebuffer framebuffer = client.getFramebuffer();
        GpuTexture texture = framebuffer.getColorAttachment();

        if (texture == null) {
            throw new IllegalStateException("Framebuffer texture is missing");
        }

        int w = framebuffer.textureWidth;
        int h = framebuffer.textureHeight;

        if (w <= 0 || h <= 0) {
            throw new IllegalStateException("Invalid frame dimensions");
        }

        if (w != width || h != height) {
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (busy[i]) {
                    return;
                }
            }

            releaseBuffers();

            long size = (long) w * h * 4L;

            for (int i = 0; i < BUFFER_COUNT; i++) {
                buffers[i] = RenderSystem.getDevice().createBuffer(
                        () -> "PRECORDER readback " + i,
                        GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                        size
                );
            }

            width = w;
            height = h;
            nextSlot = 0;
        }

        int slot = -1;

        for (int i = 0; i < BUFFER_COUNT; i++) {
            int candidate = (nextSlot + i) % BUFFER_COUNT;

            if (!busy[candidate]) {
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
        final long expectedSize = (long) w * h * 4L;

        GpuBuffer buffer = buffers[selectedSlot];
        busy[selectedSlot] = true;

        try {
            CommandEncoder encoder =
                    RenderSystem.getDevice().createCommandEncoder();

            encoder.copyTextureToBuffer(
                    texture,
                    buffer,
                    0L,
                    () -> {
                        try (MappedView mapped =
                                     encoder.mapBuffer(buffer, true, false)) {

                            ByteBuffer source = mapped.data();

                            if (source.remaining() < expectedSize) {
                                throw new IllegalStateException(
                                        "Incomplete GPU frame: "
                                                + source.remaining()
                                                + "/" + expectedSize
                                );
                            }

                            byte[] pixels = new byte[(int) expectedSize];
                            source.get(pixels);

                            onCaptured.accept(
                                    new CapturedFrame(
                                            frameWidth,
                                            frameHeight,
                                            pixels
                                    )
                            );

                        } catch (Throwable error) {
                            System.err.println(
                                    "[PRECORDER] Readback error:"
                            );
                            error.printStackTrace();
                        } finally {
                            busy[selectedSlot] = false;
                        }
                    },
                    0
            );

            nextSlot = (selectedSlot + 1) % BUFFER_COUNT;

        } catch (Throwable error) {
            busy[selectedSlot] = false;
            throw new RuntimeException("GPU capture failed", error);
        }
    }

    private static void releaseBuffers() {
        for (int i = 0; i < BUFFER_COUNT; i++) {
            if (buffers[i] != null) {
                buffers[i].close();
                buffers[i] = null;
            }

            busy[i] = false;
        }
    }
}
