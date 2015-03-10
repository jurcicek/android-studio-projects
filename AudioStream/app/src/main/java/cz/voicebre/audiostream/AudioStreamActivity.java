package cz.voicebre.audiostream;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

import java.util.Timer;
import java.util.TimerTask;


public class AudioStreamActivity extends ActionBarActivity {
    private static String TAG = "AudioStreamActivity";

    AudioStreamService mAudioStreamService;
    int energy = 0;

    // are we currently sending audio data
    private boolean mBound = false;

    private Timer timer = new Timer();
    private boolean isTimerRunning = false;

    private ProgressBar progressBar;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioStreamService.LocalBinder binder = (AudioStreamService.LocalBinder) service;
            mAudioStreamService = binder.getService();
            mBound = true;

            if(mAudioStreamService.isAudioStreaming()) {
                Button btn = (Button)findViewById(R.id.recording_button);
                btn.setText(getString(R.string.stop));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    public Handler mGuiRefreshHandler = new Handler() {
        public void handleMessage(Message msg) {
            if(progressBar != null) {
                progressBar.setProgress(energy);
//                progressBar.setBackgroundColor(Color.rgb(255, 255 - energy, 255 - energy));
            }
        }
    };

    private void startServiceSyncTimer() {
        if(!isTimerRunning) {
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
            }, 0, 100);
        }
    }

    private void stopServiceSyncTimer() {
//        isTimerRunning = false;
//        timer.cancel();
//        timer.purge();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate");

        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        startService(new Intent(this, AudioStreamService.class));
        bindService(new Intent(this, AudioStreamService.class), mConnection, Context.BIND_ABOVE_CLIENT);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.i(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(256);
        startServiceSyncTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.i(TAG, "onPause");

        stopServiceSyncTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "onDestroy");

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
        if(!mAudioStreamService.isBluetoothOn()) {
            mAudioStreamService.startBluetooth();

            Button btn = (Button) findViewById(R.id.bluetooth_button);
            btn.setText(getString(R.string.turn_bluetooth_off));
        }
        else {
            mAudioStreamService.stopBluetooth();

            Button btn = (Button) findViewById(R.id.bluetooth_button);
            btn.setText(getString(R.string.turn_bluetooth_on));
        }
    }

    public void onRecordingButtonClick(View v) {
        if(!mAudioStreamService.isAudioStreaming()) {
            Log.i(TAG, "Starting the audio stream");

            mAudioStreamService.startAudioStreaming();

            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.stop));
        }
        else {
            Log.i(TAG, "Stopping the audio stream");

            mAudioStreamService.stopAudioStreaming();

            Button btn = (Button)findViewById(R.id.recording_button);
            btn.setText(getString(R.string.start));
        }
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
