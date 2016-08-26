package com.github.teocci.javacvstream;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class JavaCVSActivity extends AppCompatActivity
{
    private static final String TAG = "JavaCVSActivity";
    private CameraPreview preview;
    private CameraManager cameraManager;

    private boolean started = true;

    private SocketVideo threadVideo;
    private SocketAudio threadAudio;

    final private static int FRAME_RATE = 30;
    final private static int GOP_LENGTH_IN_FRAMES = 60;

    private static long startTime = 0;
    private static long videoTS = 0;

    private Button button;
    private String remoteIP;
    private int remoteVideoPort;
    private int remoteAudioPort;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initLayout();

        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);

        remoteIP = sharedPref.getString(getString(R.string.ip), "192.168.1.160");
        remoteVideoPort = sharedPref.getInt(getString(R.string.video_port), 8880);
        remoteAudioPort = sharedPref.getInt(getString(R.string.audio_port), 8890);

        button = (Button) findViewById(R.id.button_capture);

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        if (started) {
                            if (remoteIP == null) {
                                threadVideo = new SocketVideo(preview);
                                threadAudio = new SocketAudio();
                            }
                            else {
                                threadVideo = new SocketVideo(preview, remoteIP, remoteVideoPort);
                                threadAudio = new SocketAudio(remoteIP, remoteAudioPort);
                            }

                            started = false;
                            button.setText(R.string.stop);
                        }
                        else {
                            closeSocketClient();
                            reset();
                        }
                    }
                }
        );
        cameraManager = new CameraManager(this);
        // Create our Preview view and set it as the content of our activity.
        preview = new CameraPreview(this, cameraManager.getCamera());
        FrameLayout flPreview = (FrameLayout) findViewById(R.id.camera_preview);
        flPreview.addView(preview);
        Log.i(TAG, "camera preview start: OK");
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        cameraManager.onResume();
        preview.setCamera(cameraManager.getCamera());
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeSocketClient();
        preview.onPause();
        cameraManager.onPause();              // release the camera immediately on pause event
        reset();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.av_streamer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                setting();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setting() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.server_setting, null);
        EditText ipEdit = (EditText)textEntryView.findViewById(R.id.ip_edit);
        EditText videoPortEdit = (EditText)textEntryView.findViewById(R.id.video_port_edit);
        EditText audioPortEdit = (EditText)textEntryView.findViewById(R.id.audio_port_edit);

        ipEdit.setText(remoteIP);
        videoPortEdit.setText("" + remoteVideoPort);
        audioPortEdit.setText("" + remoteAudioPort);

        AlertDialog dialog =  new AlertDialog.Builder(JavaCVSActivity.this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.setting_title)
                .setView(textEntryView)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        EditText ipEdit = (EditText)textEntryView.findViewById(R.id.ip_edit);
                        EditText videoPortEdit = (EditText)textEntryView.findViewById(R.id.video_port_edit);
                        EditText audioPortEdit = (EditText)textEntryView.findViewById(R.id.audio_port_edit);

                        remoteIP = ipEdit.getText().toString();
                        remoteVideoPort = Integer.parseInt(videoPortEdit.getText().toString());
                        remoteAudioPort = Integer.parseInt(audioPortEdit.getText().toString());

                        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preference_file_key),
                                Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.clear();
                        editor.putString(getString(R.string.ip), remoteIP);
                        editor.putInt(getString(R.string.video_port_key), remoteVideoPort);
                        editor.putInt(getString(R.string.audio_port_key), remoteAudioPort);
                        editor.commit();

                        Toast.makeText(JavaCVSActivity.this, "New address: " + remoteIP + ":" + remoteVideoPort + "|" + remoteAudioPort, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                    /* User clicked cancel so do some stuff */
                    }
                })
                .create();
        dialog.show();
    }

    private void reset() {
        button.setText(R.string.start);
        started = true;
    }


    private void closeSocketClient() {
        if (threadVideo == null)
            return;

        if (threadAudio == null)
            return;

        threadVideo.interrupt();
        threadAudio.interrupt();
        try {
            threadVideo.join();
            threadAudio.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        threadVideo = null;
        threadAudio = null;
    }
}