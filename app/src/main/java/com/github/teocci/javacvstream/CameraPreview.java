package com.github.teocci.javacvstream;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by teocci on 8/26/16.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
{
    private static final String TAG = "CameraPreview";

    private SurfaceHolder holder;
    private Camera camera;

    private boolean isPreviewOn = false;

    private Size previewSize;

    private int imageWidth = 320;
    private int imageHeight = 240;
    private int frameRate = 30;


    private LinkedList<byte[]> queue = new LinkedList<>();
    private static final int MAX_BUFFER = 15;
    private byte[] lastFrame = null;
    private int frameLength;

    public CameraPreview(Context context, Camera cam)
    {
        super(context);

        Log.w(TAG, "Camera init");

        camera = cam;

        holder = getHolder();
        holder.addCallback(CameraPreview.this);
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        /*Parameters params = camera.getParameters();
        List<Size> sizes = params.getSupportedPreviewSizes();
        for (Size s : sizes) {
            Log.i(TAG, "preview size = " + s.width + ", " + s.height);
        }

        params.setPreviewSize(640, 480); // set preview size. smaller is better
        camera.setParameters(params);

        previewSize = camera.getParameters().getPreviewSize();
        Log.i(TAG, "preview size = " + previewSize.width + ", " + previewSize.height);

        int format = camera.getParameters().getPreviewFormat();
        frameLength = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel
                (format) / 8;*/
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        try {
            stopPreview();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
            camera.release();
            camera = null;
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {}

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
    {

        if (holder.getSurface() == null)
            return;

        Parameters params = camera.getParameters();
        List<Size> sizes = params.getSupportedPreviewSizes();

        // Sort the list in ascending order
        Collections.sort(sizes, new Comparator<Size>() {
            public int compare(final Size a, final Size b) {
                return a.width * a.height - b.width * b.height;
            }
        });

        /*for (Size s : sizes) {
            Log.i(TAG, "preview size = " + s.width + ", " + s.height);
        }*/

        // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
        // reach the initial settings of imageWidth/imageHeight.
        int cont = 0;
        for (Size size : sizes) {
            if((size.width >= imageWidth && size.height >= imageHeight) || cont == sizes.size() - 1) {
                imageWidth = size.width;
                imageHeight = size.height;
                Log.v(TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                break;
            }
            cont ++;
        }

        //params.setPreviewSize(640, 480); // set preview size. smaller is better
        params.setPreviewSize(imageWidth, imageHeight);

        Log.v(TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);
        camera.setParameters(params);

        previewSize = camera.getParameters().getPreviewSize();
        Log.i(TAG, "preview size = " + previewSize.width + ", " + previewSize.height);

        format = camera.getParameters().getPreviewFormat();
        frameLength = previewSize.width * previewSize.height *
                ImageFormat.getBitsPerPixel(format) / 8;

        try {
            stopPreview();
            resetBuff();
        } catch (Exception e) {}

        try {
            camera.setPreviewCallback(previewCallback);
            camera.setPreviewDisplay(holder);
            startPreview();

        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void setCamera(Camera cam)
    {
        camera = cam;
    }

    public byte[] getImageBuffer()
    {
        synchronized (queue) {
            if (queue.size() > 0) {
                lastFrame = queue.poll();
            }
        }

        return lastFrame;
    }

    private void resetBuff()
    {
        synchronized (queue) {
            queue.clear();
            lastFrame = null;
        }
    }

    public void startPreview() {
        if(!isPreviewOn && camera != null) {
            isPreviewOn = true;
            camera.startPreview();
        }
    }

    public void stopPreview() {
        if(isPreviewOn && camera != null) {
            isPreviewOn = false;
            camera.stopPreview();
        }
    }

    public void onPause()
    {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
        }
        resetBuff();
    }

    public int getPreviewLength()
    {
        return frameLength;
    }

    public int getPreviewWidth()
    {
        return previewSize.width;
    }

    public int getPreviewHeight()
    {
        return previewSize.height;
    }

    private PreviewCallback previewCallback = new PreviewCallback()
    {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera)
        {
            // TODO Auto-generated method stub
            synchronized (queue) {
                if (queue.size() == MAX_BUFFER) {
                    queue.poll();
                }
                queue.add(data);
            }
        }
    };
}