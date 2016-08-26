package com.github.teocci.javacvstream;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by teocci on 8/26/16.
 */
public class CameraManager
{
    private static final String TAG = "CameraManager";

    private Camera camera;
    private Context context;


    public CameraManager(Context contxt) {
        context = contxt;
        // Create an instance of Camera
        camera = getCameraInstance();
    }

    public Camera getCamera() {
        return camera;
    }

    private void releaseCamera() {
        if (camera != null) {
            // release the camera for other applications
            camera.release();
            camera = null;
        }
    }

    public void onPause() {
        releaseCamera();
    }

    public void onResume() {
        if (camera == null) {
            camera = getCameraInstance();
        }

        Toast.makeText(context,
                "preview size = " +
                        camera.getParameters().getPreviewSize().width + "x" +
                        camera.getParameters().getPreviewSize().height,
                Toast.LENGTH_LONG).show();
    }

    /** A safe way to get an instance of the Camera object. */
    private static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            Log.i(TAG, "cameara open");
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

}
