package cz.voicebre.audiostream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;


public class MainActivity extends ActionBarActivity {
    private static String TAG = "AudioStream";

    // the server information
    private static final String SERVER = "xx.xx.xx.xx";
    private static final int PORT = 50005;

    // the audio recording options
    private static final int RECORDING_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // the audio recorder
    private AudioRecord recorder;

    // the minimum buffer size needed for audio recording
    private static int BUFFER_SIZE = 4*AudioRecord.getMinBufferSize(
            RECORDING_RATE, CHANNEL, FORMAT);

    // are we currently sending audio data
    private boolean audioStreaming = false;

    private boolean bluetoothOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onBluetoothButtonClick(View v) {
        if(!bluetoothOn) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            if(am.isBluetoothScoOn()) {
                Log.d(TAG, "Bluetooth is ON");
            }
            else {
                Log.d(TAG, "Bluetooth is OFF");
            }

            registerReceiver(new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                    Log.d(TAG, "Audio SCO state: " + state);

                    if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                        /*
                         * Now the connection has been established to the bluetooth device.
                         * Record audio or whatever (on another thread).With AudioRecord you can record with an object created like this:
                         * new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                         * AudioFormat.ENCODING_PCM_16BIT, audioBufferSize);
                         *
                         * After finishing, don't forget to unregister this receiver and
                         * to stop the bluetooth connection with am.stopBluetoothSco();
                         */
                        Log.d(TAG, "Audio SCO state: AudioManager.SCO_AUDIO_STATE_CONNECTED");

                        unregisterReceiver(this);
                    }

                }
            }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));


            Log.d(TAG, "Starting bluetooth");
            am.startBluetoothSco();
            am.setBluetoothScoOn(true);

            if(am.isBluetoothScoOn()) {
                Log.d(TAG, "Bluetooth is ON");
            }
            else {
                Log.d(TAG, "Bluetooth is OFF");
            }

            Button btn = (Button)findViewById(R.id.bluetooth_button);
            btn.setText(getString(R.string.turn_bluetooth_off_button));
            bluetoothOn = true;
        }
        else {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Log.d(TAG, "Stopping bluetooth");
            am.stopBluetoothSco();

            Button btn = (Button)findViewById(R.id.bluetooth_button);
            btn.setText(getString(R.string.turn_bluetooth_on_button));
            bluetoothOn = false;
        }

    }

    public void onRecordingButtonClick(View v) {
        if(!audioStreaming) {
            startStreamingAudio();
            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.stop_recording_button));
        }
        else {
            stopStreamingAudio();
            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.start_recording_button));
        }
    }

    private void startStreamingAudio() {
        Log.i(TAG, "Starting the audio stream");
        audioStreaming = true;
        startStreaming();
    }

    private void startStreaming() {
        Log.d(TAG, "Starting the background thread to stream the audio data");

        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Log.d(TAG, "Creating the buffer of size " + BUFFER_SIZE);
                    byte[] buffer = new byte[BUFFER_SIZE];

//                    Log.i(TAG, "Connecting to " + SERVER + ":" + PORT);
//                    final InetAddress serverAddress = InetAddress.getByName(SERVER);
//
//                    Log.i(TAG, "Creating the socket");
//                    Socket socket = new Socket(serverAddress, PORT);
//
//
//                    Log.i(TAG, "Assigning streams");
//                    DataInputStream dis = (DataInputStream) socket.getInputStream();
//                    DataOutputStream dos = (DataOutputStream) socket.getOutputStream();
//
                    ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);

                    Log.d(TAG, "Creating the AudioRecord");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);

                    Log.d(TAG, "AudioRecord start recording...");
                    recorder.startRecording();

                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                        Log.d(TAG, "AudioRecord init failed");

                    while (audioStreaming == true) {
                       // read the audio data into the buffer
                        int read = recorder.read(buffer, 0, buffer.length);

                        ShortBuffer shortBuf = ByteBuffer.wrap(buffer, 0, read).
                                order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                        short[] short_buffer = new short[shortBuf.remaining()];
                        shortBuf.get(short_buffer);

                        double e = 0.0;
                        for(int i = 0; i < short_buffer.length; i++) {
                            e += Math.abs(((double)short_buffer[i]))/256/50*100;
                        }
                        e /= short_buffer.length;

                        progressBar.setProgress((int)e);

                        // send the audio data to the server
//                        dos.write(buffer, 0, read);
                    }

                    Log.d(TAG, "AudioRecord finished recording");

                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });

        // start the thread
        streamThread.start();
    }

    private void stopStreamingAudio() {

        Log.i(TAG, "Stopping the audio stream");
        audioStreaming = false;
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
            Log.i(TAG, "AudioRecord init failed");
        recorder.release();
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }
}
