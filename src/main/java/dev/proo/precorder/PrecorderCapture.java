
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

    public static void captureFrame(
            MinecraftClient client,
            Consumer<byte[]> onCaptured
    ) {
        RenderSystem.assertOnRenderThread();

        Framebuffer framebuffer = client.getFramebuffer();
        GpuTexture texture = framebuffer.getColorAttachment();

        if (texture == null) {
            throw new IllegalStateException("No framebuffer texture");
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        long size = (long) width * height * 4L;

        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "PRECORDER frame readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                size
        );

        try {
            RenderSystem.getDevice().createCommandEncoder()
                    .copyTextureToBuffer(texture, buffer, 0L, () -> {
                        try {
                            var encoder = RenderSystem.getDevice()
                                    .createCommandEncoder();

                            try (MappedView mapped =
                                         encoder.mapBuffer(buffer, true, false)) {
                                ByteBuffer data = mapped.data();
                                byte[] pixels = new byte[data.remaining()];
                                data.get(pixels);
                                onCaptured.accept(pixels);
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
