package com.github.teocci.javacvstream;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder.Exception;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketVideo extends Thread
{
    private static final String TAG = "socket_video";

    final private static int FRAME_RATE = 30;
    final private static int GOP_LENGTH_IN_FRAMES = 60;

    private static long startTime = 0;
    private static long videoTS = 0;

    private Socket socket;
    private CameraPreview cameraPreview;

    private String remoteIP;
    private int remotePort;

    public SocketVideo(CameraPreview preview, String ip, int port)
    {
        cameraPreview = preview;
        remoteIP = ip;
        remotePort = port;
        start();
    }

    public SocketVideo(CameraPreview preview)
    {
        cameraPreview = preview;
        start();
    }

    @Override
    public void run()
    {
        // TODO Auto-generated method stub
        super.run();

        try {

            int timeOut = 10000; // in milliseconds

            socket = new Socket();
            socket.connect(new InetSocketAddress(remoteIP, remotePort), timeOut);
            BufferedOutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
            BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());

            int captureWidth = cameraPreview.getPreviewWidth();
            int captureHeight = cameraPreview.getPreviewHeight();
            JsonObject jsonObj = new JsonObject();
            jsonObj.addProperty("type", "data");
            jsonObj.addProperty("length", cameraPreview.getPreviewLength());
            jsonObj.addProperty("width", captureWidth);
            jsonObj.addProperty("height", captureHeight);

            // org.bytedeco.javacv.FFmpegFrameRecorder.FFmpegFrameRecorder(String
            // filename, int imageWidth, int imageHeight, int audioChannels)
            // For each param, we're passing in...
            // filename = either a path to a local file we wish to create, or an
            // RTMP url to an FMS / Wowza server
            // imageWidth = width we specified for the grabber
            // imageHeight = height we specified for the grabber
            // audioChannels = 2, because we like stereo
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                    "rtmp://my-streaming-server/app_name_here/instance_name/stream_name",
                    captureWidth, captureHeight, 2);

            recorder.setInterleaved(true);

            // decrease "startup" latency in FFMPEG (see:
            // https://trac.ffmpeg.org/wiki/StreamingGuide)
            recorder.setVideoOption("tune", "zerolatency");
            // tradeoff between quality and encode speed
            // possible values are ultrafast,superfast, veryfast, faster, fast,
            // medium, slow, slower, veryslow
            // ultrafast offers us the least amount of compression (lower encoder
            // CPU) at the cost of a larger stream size
            // at the other end, veryslow provides the best compression (high
            // encoder CPU) while lowering the stream size
            // (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
            recorder.setVideoOption("preset", "ultrafast");
            // Constant Rate Factor (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
            recorder.setVideoOption("crf", "28");
            // 2000 kb/s, reasonable "sane" area for 720
            recorder.setVideoBitrate(2000000);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("flv");
            // FPS (frames per second)
            recorder.setFrameRate(FRAME_RATE);
            // Key frame interval, in our case every 2 seconds -> 30 (fps) * 2 = 60
            // (gop length)
            recorder.setGopSize(GOP_LENGTH_IN_FRAMES);

            // We don't want variable bitrate audio
            recorder.setAudioOption("crf", "0");
            // Highest quality
            recorder.setAudioQuality(0);
            // 192 Kbps
            recorder.setAudioBitrate(192000);
            recorder.setSampleRate(44100);
            recorder.setAudioChannels(2);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);

            recorder.start();

            byte[] buff = new byte[256];
            int len = 0;
            String msg = null;
            outputStream.write(jsonObj.toString().getBytes());
            outputStream.flush();

            while ((len = inputStream.read(buff)) != -1) {
                msg = new String(buff, 0, len);

                // JSON analysis
                JsonParser parser = new JsonParser();
                boolean isJSON = true;
                JsonElement element = null;
                try {
                    element = parser.parse(msg);
                } catch (JsonParseException e) {
                    Log.e(TAG, "exception: " + e);
                    isJSON = false;
                }
                if (isJSON && element != null) {
                    JsonObject obj = element.getAsJsonObject();
                    element = obj.get("state");
                    if (element != null && element.getAsString().equals("ok")) {
                        // send data
                        while (true) {
                            // Let's define our start time...
                            // This needs to be initialized as close to when we'll use it as
                            // possible,
                            // as the delta from assignment to computed time could be too high
                            if (startTime == 0)
                                startTime = System.currentTimeMillis();

                            // Create timestamp for this frame
                            videoTS = 1000 * (System.currentTimeMillis() - startTime);

                            // Check for AV drift
                            if (videoTS > recorder.getTimestamp())
                            {
                                System.out.println(
                                        "Lip-flap correction: "
                                                + videoTS + " : "
                                                + recorder.getTimestamp() + " -> "
                                                + (videoTS - recorder.getTimestamp()));

                                // We tell the recorder to write this frame at this timestamp
                                recorder.setTimestamp(videoTS);
                            }

                            Frame capturedFrame = null;

                            // Send the frame to the org.bytedeco.javacv.FFmpegFrameRecorder
                            recorder.record(cameraPreview.getImageBuffer());



                            outputStream.write();
                            outputStream.flush();

                            if (Thread.currentThread().isInterrupted())
                                break;
                        }

                        break;
                    }
                } else {
                    break;
                }
            }

            outputStream.close();
            inputStream.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
            Log.e(TAG, e.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void close()
    {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
