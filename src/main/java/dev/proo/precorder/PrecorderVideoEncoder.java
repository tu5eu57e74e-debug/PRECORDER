
package dev.proo.precorder;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.Codec;
import org.jcodec.common.MuxerTrack;
import org.jcodec.common.VideoEncoder.EncodedFrame;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Packet.FrameType;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class PrecorderVideoEncoder {
    private MP4Muxer muxer;
    private SeekableByteChannel channel;
    private MuxerTrack videoTrack;
    private H264Encoder h264Encoder;
    private Transform rgbToYuv;
    private Picture rgbPicture;
    private Picture yuvPicture;
    private ByteBuffer outputBuffer;

    private int width;
    private int height;
    private int fps;
    private long frameNumber;

    public void start(File output, int width, int height, int fps)
            throws IOException {

        if (muxer != null) {
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
        this.fps = fps;
        this.frameNumber = 0L;

        SeekableByteChannel openedChannel = NIOUtils.writableChannel(output);
        try {
            MP4Muxer openedMuxer =
                    MP4Muxer.createMP4MuxerToChannel(openedChannel);
            H264Encoder openedEncoder = H264Encoder.createH264Encoder();

            ColorSpace encoderColor =
                    openedEncoder.getSupportedColorSpaces()[0];

            this.channel = openedChannel;
            this.muxer = openedMuxer;
            this.h264Encoder = openedEncoder;
            this.rgbToYuv =
                    ColorUtil.getTransform(ColorSpace.RGB, encoderColor);
            this.rgbPicture =
                    Picture.create(width, height, ColorSpace.RGB);
            this.yuvPicture =
                    Picture.create(width, height, encoderColor);
            this.outputBuffer = ByteBuffer.allocate(width * height * 6);
            this.videoTrack =
                    openedMuxer.addVideoTrack(Codec.H264, null);

        } catch (RuntimeException | IOException error) {
            NIOUtils.closeQuietly(openedChannel);
            throw error;
        }
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
        if (muxer == null || h264Encoder == null) {
            throw new IllegalStateException("Encoder not started");
        }

        if (rgba == null || rgba.length != (long) width * height * 4L) {
            throw new IllegalArgumentException("Invalid RGBA frame");
        }

        byte[] rgb = rgbPicture.getPlaneData(0);

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

        rgbToYuv.transform(rgbPicture, yuvPicture);

        outputBuffer.clear();
        EncodedFrame encoded =
                h264Encoder.encodeFrame(yuvPicture, outputBuffer);

        Packet packet = Packet.createPacket(
                encoded.getData(),
                frameNumber,
                fps,
                1,
                frameNumber,
                encoded.isKeyFrame() ? FrameType.KEY : FrameType.INTER,
                null
        );

        videoTrack.addFrame(packet);
        frameNumber++;
    }

    public void stop() throws IOException {
        if (muxer == null) {
            return;
        }

        MP4Muxer currentMuxer = muxer;
        SeekableByteChannel currentChannel = channel;

        muxer = null;
        channel = null;
        videoTrack = null;
        h264Encoder = null;
        rgbToYuv = null;
        rgbPicture = null;
        yuvPicture = null;
        outputBuffer = null;

        try {
            currentMuxer.finish();
        } finally {
            NIOUtils.closeQuietly(currentChannel);
        }
    }

    public boolean isStarted() {
        return muxer != null;
    }
            }
