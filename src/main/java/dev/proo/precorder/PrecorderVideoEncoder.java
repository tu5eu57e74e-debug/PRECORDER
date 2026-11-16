
package dev.proo.precorder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class PrecorderVideoEncoder {

    private FFmpegFrameRecorder recorder;
    private Frame frame;

    private int width;
    private int height;
    private int fps;
    private int bitrateMbps;

    private long frameNumber;
    private long firstTimestampNanos = -1;
    private long lastTimestampMicros = -1;

    public void start(File output, int width, int height, int fps)
            throws IOException {
        start(output, width, height, fps, 12);
    }

    public void start(
            File output,
            int width,
            int height,
            int fps,
            int bitrateMbps
    ) throws IOException {

        if (isStarted()) {
            throw new IllegalStateException("Encoder already started");
        }

        if (output == null || width <= 0 || height <= 0 || fps <= 0) {
            throw new IllegalArgumentException("Invalid encoder settings");
        }

        if ((width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException(
                    "Video dimensions must be even"
            );
        }

        File parent = output.getAbsoluteFile().getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory");
        }

        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrateMbps = Math.max(2, Math.min(50, bitrateMbps));

        this.frameNumber = 0;
        this.firstTimestampNanos = -1;
        this.lastTimestampMicros = -1;

        FFmpegFrameRecorder newRecorder = null;
        Frame newFrame = null;

        try {
            newRecorder = new FFmpegFrameRecorder(
                    output,
                    width,
                    height
            );

            newRecorder.setFormat("mp4");
            newRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            newRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            newRecorder.setFrameRate(fps);
            newRecorder.setVideoBitrate(this.bitrateMbps * 1_000_000);
            newRecorder.setGopSize(fps * 2);
            newRecorder.setMaxBFrames(0);

            newFrame = new Frame(
                    width,
                    height,
                    Frame.DEPTH_UBYTE,
                    3
            );

            if (newFrame.imageStride < width * 3) {
                throw new IOException("Invalid FFmpeg frame stride");
            }

            newRecorder.start();

            this.recorder = newRecorder;
            this.frame = newFrame;

        } catch (Throwable error) {
            if (newRecorder != null) {
                try {
                    newRecorder.release();
                } catch (Throwable ignored) {
                }
            }

            if (newFrame != null) {
                try {
                    newFrame.close();
                } catch (Throwable ignored) {
                }
            }

            if (error instanceof IOException io) {
                throw io;
            }

            throw new IOException(
                    "Could not initialize FFmpeg H264 encoder",
                    error
            );
        }
    }

    public void addFrame(PrecorderCapture.CapturedFrame captured)
            throws IOException {

        if (captured == null) {
            throw new IllegalArgumentException("Frame is required");
        }

        encodeRgba(
                captured.rgba(),
                captured.width(),
                captured.height(),
                captured.timestampNanos()
        );
    }

    public void addFrame(byte[] rgba) throws IOException {
        long syntheticTimestamp =
                frameNumber * 1_000_000_000L / fps;

        encodeRgba(
                rgba,
                width,
                height,
                syntheticTimestamp
        );
    }

    private void encodeRgba(
            byte[] rgba,
            int sourceWidth,
            int sourceHeight,
            long timestampNanos
    ) throws IOException {

        if (!isStarted()) {
            throw new IllegalStateException("Encoder not started");
        }

        if (rgba == null
                || sourceWidth <= 0
                || sourceHeight <= 0
                || rgba.length != (long) sourceWidth * sourceHeight * 4L) {
            throw new IllegalArgumentException("Invalid RGBA frame");
        }

        if (timestampNanos < 0) {
            throw new IllegalArgumentException("Invalid frame timestamp");
        }

        ByteBuffer destination = (ByteBuffer) frame.image[0];

        int rowBytes = width * 3;
        int stride = frame.imageStride;

        if (stride < rowBytes) {
            throw new IOException("Invalid frame row stride");
        }

        destination.clear();

        for (int y = 0; y < height; y++) {

            int sourceY = (int) (
                    (long) (height - 1 - y) * sourceHeight / height
            );

            int rowStart = y * stride;
            destination.position(rowStart);

            for (int x = 0; x < width; x++) {

                int sourceX = (int) (
                        (long) x * sourceWidth / width
                );

                int sourceIndex =
                        (sourceY * sourceWidth + sourceX) * 4;

                // Convert RGBA to BGR.
                destination.put(rgba[sourceIndex + 2]);
                destination.put(rgba[sourceIndex + 1]);
                destination.put(rgba[sourceIndex]);
            }

            while (destination.position() < rowStart + stride) {
                destination.put((byte) 0);
            }
        }

        destination.position(0);
        destination.limit(height * stride);

        if (firstTimestampNanos < 0) {
            firstTimestampNanos = timestampNanos;
        }

        long elapsedNanos = Math.max(
                0L,
                timestampNanos - firstTimestampNanos
        );

        long timestampMicros = elapsedNanos / 1_000L;

        // Keep timestamps strictly increasing.
        if (timestampMicros <= lastTimestampMicros) {
            timestampMicros = lastTimestampMicros + 1;
        }

        try {
            // Apply the real capture timestamp to FFmpeg.
            recorder.setTimestamp(timestampMicros);
            recorder.record(frame);

            lastTimestampMicros = timestampMicros;
            frameNumber++;

        } catch (Exception error) {
            throw new IOException(
                    "FFmpeg failed to encode frame " + frameNumber,
                    error
            );
        }
    }

    public void stop() throws IOException {

        FFmpegFrameRecorder current = recorder;
        Frame currentFrame = frame;

        if (current == null) {
            return;
        }

        recorder = null;
        frame = null;

        IOException failure = null;

        try {
            current.stop();
        } catch (Exception error) {
            failure = new IOException(
                    "FFmpeg could not finalize MP4",
                    error
            );
        } finally {
            try {
                current.release();
            } catch (Exception error) {
                if (failure == null) {
                    failure = new IOException(
                            "Could not release FFmpeg resources",
                            error
                    );
                } else {
                    failure.addSuppressed(error);
                }
            }

            if (currentFrame != null) {
                try {
                    currentFrame.close();
                } catch (Exception error) {
                    if (failure == null) {
                        failure = new IOException(
                                "Could not release frame memory",
                                error
                        );
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    public boolean isStarted() {
        return recorder != null;
    }
            }
                
