
package dev.proo.precorder;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import java.io.File;
import java.io.IOException;

public final class PrecorderVideoEncoder {
    private SequenceEncoder encoder;
    private int width;
    private int height;

    public void start(File output, int width, int height, int fps)
            throws IOException {

        if (encoder != null) {
            throw new IllegalStateException("Encoder already started");
        }

        if (output == null || width <= 0 || height <= 0 || fps <= 0) {
            throw new IllegalArgumentException("Invalid encoder settings");
        }

        if ((width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("Video dimensions must be even");
        }

        File parent = output.getAbsoluteFile().getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory");
        }

        this.width = width;
        this.height = height;
        this.encoder = SequenceEncoder.createSequenceEncoder(output, fps);
    }

    public void addFrame(PrecorderCapture.CapturedFrame frame)
            throws IOException {

        if (frame == null) {
            throw new IllegalArgumentException("Frame is required");
        }

        if (frame.width() != width || frame.height() != height) {
            throw new IllegalArgumentException("Frame dimensions changed");
        }

        addFrame(frame.rgba());
    }

    public void addFrame(byte[] rgba) throws IOException {
        if (encoder == null) {
            throw new IllegalStateException("Encoder not started");
        }

        if (rgba == null || rgba.length != (long) width * height * 4L) {
            throw new IllegalArgumentException("Invalid RGBA frame");
        }

        Picture picture = Picture.create(width, height, ColorSpace.RGB);
        byte[] rgb = picture.getPlaneData(0);

        for (int y = 0; y < height; y++) {
            int sourceY = height - 1 - y;

            for (int x = 0; x < width; x++) {
                int src = (sourceY * width + x) * 4;
                int dst = (y * width + x) * 3;

                rgb[dst] = (byte) ((rgba[src] & 255) - 128);
                rgb[dst + 1] = (byte) ((rgba[src + 1] & 255) - 128);
                rgb[dst + 2] = (byte) ((rgba[src + 2] & 255) - 128);
            }
        }

        encoder.encodeNativeFrame(picture);
    }

    public void stop() throws IOException {
        if (encoder == null) {
            return;
        }

        SequenceEncoder current = encoder;
        encoder = null;
        current.finish();
    }

    public boolean isStarted() {
        return encoder != null;
    }
    }
