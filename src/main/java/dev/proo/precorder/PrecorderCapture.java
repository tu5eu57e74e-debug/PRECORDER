
package dev.proo.precorder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import java.nio.ByteBuffer;
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
            throw new IllegalArgumentException("Client and callback are required");
        }

        Framebuffer framebuffer = client.getFramebuffer();
        GpuTexture texture = framebuffer.getColorAttachment();

        if (texture == null) {
            throw new IllegalStateException("Framebuffer color texture is missing");
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Invalid framebuffer dimensions");
        }

        long size = (long) width * height * 4L;

        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "PRECORDER frame readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                size
        );

        try {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .copyTextureToBuffer(texture, buffer, 0L, () -> {
                        try {
                            var encoder = RenderSystem.getDevice()
                                    .createCommandEncoder();

                            try (MappedView mapped =
                                         encoder.mapBuffer(buffer, true, false)) {
                                ByteBuffer data = mapped.data();
                                byte[] pixels = new byte[data.remaining()];
                                data.get(pixels);

                                onCaptured.accept(
                                        new CapturedFrame(width, height, pixels)
                                );
                            }
                        } finally {
                            buffer.close();
                        }
                    }, 0);
        } catch (RuntimeException exception) {
            buffer.close();
            throw exception;
        }
    }
}
