
package dev.proo.precorder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PrecorderCapture {
    private PrecorderCapture() {}

    public record CapturedFrame(int width, int height, byte[] rgba) {}

    public static void captureFrame(
            MinecraftClient client,
            Consumer<CapturedFrame> onCaptured
    ) {
        RenderSystem.assertOnRenderThread();

        if (client == null || onCaptured == null) {
            throw new IllegalArgumentException(
                    "Client and callback are required"
            );
        }

        Framebuffer framebuffer = client.getFramebuffer();
        GpuTexture texture = framebuffer.getColorAttachment();

        if (texture == null) {
            throw new IllegalStateException(
                    "Framebuffer color texture is missing"
            );
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        if (width <= 0 || height <= 0) {
            throw new IllegalStateException(
                    "Invalid framebuffer dimensions: "
                            + width + "x" + height
            );
        }

        long expectedSize = (long) width * height * 4L;

        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "PRECORDER frame readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                expectedSize
        );

        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable closeBuffer = () -> {
            if (closed.compareAndSet(false, true)) {
                buffer.close();
            }
        };

        try {
            CommandEncoder copyEncoder =
                    RenderSystem.getDevice().createCommandEncoder();

            copyEncoder.copyTextureToBuffer(
                    texture,
                    buffer,
                    0L,
                    () -> {
                        try {
                            CommandEncoder readEncoder =
                                    RenderSystem.getDevice()
                                            .createCommandEncoder();

                            try (MappedView mapped =
                                         readEncoder.mapBuffer(
                                                 buffer, true, false
                                         )) {

                                ByteBuffer data = mapped.data();

                                if (data.remaining() < expectedSize) {
                                    throw new IllegalStateException(
                                            "GPU returned incomplete frame: "
                                                    + data.remaining()
                                                    + " / " + expectedSize
                                    );
                                }

                                byte[] pixels =
                                        new byte[(int) expectedSize];

                                data.get(pixels);

                                onCaptured.accept(
                                        new CapturedFrame(
                                                width, height, pixels
                                        )
                                );
                            }
                        } catch (Throwable error) {
                            System.err.println(
                                    "[PRECORDER] Frame readback failed:"
                            );
                            error.printStackTrace();
                        } finally {
                            closeBuffer.run();
                        }
                    },
                    0
            );

        } catch (Throwable error) {
            closeBuffer.run();
            throw new RuntimeException(
                    "Could not copy framebuffer to readback buffer",
                    error
            );
        }
    }
            }
