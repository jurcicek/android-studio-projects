package cz.voicebre.audiostream;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Handler;
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
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {
    private static String TAG = "AudioStreamActivity";

    AudioStreamService mAudioStreamService;
    int energy = 0;

    private boolean bluetoothOn = false;
    // are we currently sending audio data
    private boolean audioStreaming = false;

    private Timer timer = new Timer();
    private boolean isTimerRunning = true;

    private ProgressBar progressBar;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioStreamService.LocalBinder binder = (AudioStreamService.LocalBinder) service;
            mAudioStreamService = binder.getService();
            audioStreaming = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            audioStreaming = false;
        }
    };


    public Handler mGuiRefreshHandler = new Handler() {
        public void handleMessage(Message msg) {
            if(progressBar != null)
                progressBar.setProgress(energy);
                progressBar.setBackgroundColor(Color.rgb(255, 255-energy, 255-energy));
        }
    };

    private void startServiceSyncTimer() {
        if(isTimerRunning) {
            isTimerRunning = true;
            timer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    try {
                        // update all you need to update from all services
                        if (mAudioStreamService != null) {
                            energy = (int) (mAudioStreamService.getEnergy() * 255.0);
                        }
                    } catch (java.lang.NullPointerException e) {
                        Log.e(TAG, "Exception: " + e);
                    }
                    mGuiRefreshHandler.obtainMessage(0).sendToTarget();
                }
            }, 0, 300);
        }
    }

    private void stopServiceSyncTimer() {
        isTimerRunning = false;
        timer.cancel();
    }
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
    protected void onResume() {
        super.onResume();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(256);
        startServiceSyncTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopServiceSyncTimer();
        unbindService(mConnection);
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
        if(!isBluetoothOn()) {
            startBluetooth();
        }
        else {
            stopBluetooth();

            Button btn = (Button) findViewById(R.id.bluetooth_button);
            btn.setText(getString(R.string.turn_bluetooth_on_button));
        }
    }

    public void onRecordingButtonClick(View v) {
        if(!audioStreaming) {
            Log.i(TAG, "Starting the audio stream");
            audioStreaming = true;
//            startService(new Intent(this, AudioStreamService.class));
            bindService(new Intent(this, AudioStreamService.class), mConnection, Context.BIND_AUTO_CREATE);

            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.stop_recording_button));
        }
        else {
            Log.i(TAG, "Stopping the audio stream");
            audioStreaming = false;
//            stopService(new Intent(this, AudioStreamService.class));
            unbindService(mConnection);

            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.start_recording_button));
        }
    }

    public void startBluetooth() {
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
                    Log.d(TAG, "Audio SCO state: AudioManager.SCO_AUDIO_STATE_CONNECTED");

                    Button btn = (Button) findViewById(R.id.bluetooth_button);
                    btn.setText(getString(R.string.turn_bluetooth_off_button));
                }
                if (AudioManager.SCO_AUDIO_STATE_CONNECTING == state) {
                    Log.d(TAG, "Audio SCO state: AudioManager.SCO_AUDIO_STATE_CONNECTING");
                }
                if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                    Log.d(TAG, "Audio SCO state: AudioManager.SCO_AUDIO_STATE_DISCONNECTED");

                    bluetoothOn = false;
                    Toast.makeText(getApplicationContext(), R.string.failed_to_turn_bluetooth_on, Toast.LENGTH_SHORT).show();
                }
                if (AudioManager.SCO_AUDIO_STATE_ERROR == state) {
                    Log.d(TAG, "Audio SCO state: AudioManager.SCO_AUDIO_STATE_ERROR");
                }

                unregisterReceiver(this);
            }
        }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

        Log.d(TAG, "Starting bluetooth");
        am.startBluetoothSco();
        am.setBluetoothScoOn(true);

        if(am.isBluetoothScoOn()) {
            Log.d(TAG, "Bluetooth is ON");
            bluetoothOn = true;
        }
        else {
            Log.d(TAG, "Bluetooth is OFF");
            bluetoothOn = false;
        }

    }
    public void stopBluetooth() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "Stopping bluetooth");
        am.stopBluetoothSco();
        bluetoothOn = false;
    }

    public boolean isBluetoothOn() {
        return bluetoothOn;
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
