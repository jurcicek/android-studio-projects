package cz.voicebre.audiostream;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class AudioStreamService extends Service {
    private static String TAG = "AudioStreamService";
    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.audio_streaming_service_started;
    final static int myID = 123422;

    // the server information
    private static final String SERVER = "xx.xx.xx.xx";
    private static final int PORT = 50005;

    // the audio recording options
    private static final int RECORDING_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // the audio recorder
    private AudioRecord recorder;
    private boolean bluetoothOn = false;

    // the minimum buffer size needed for audio recording
    private static int BUFFER_SIZE = 4*AudioRecord.getMinBufferSize(
            RECORDING_RATE, CHANNEL, FORMAT);

    PowerManager.WakeLock wakeLock;

    // are we currently sending audio data
    private boolean audioStreaming = false;

    private double max_energy = 0.0;
    private double energy = 0.0;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        AudioStreamService getService() {
            return AudioStreamService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, AudioStreamService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);

        PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 12456, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
        builder.setContentIntent(pendIntent)
                .setTicker("AudioStream")
                .setContentTitle("AudioStream")
                .setContentText("AudioStream streaming")
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH);
        Notification notification = builder.build();

        notification.flags |= Notification.FLAG_NO_CLEAR;

        startForeground(myID, notification);

        // Tell the user we started.
        Toast.makeText(this, R.string.audio_streaming_service_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.audio_streaming_service_stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public double getEnergy() {
        return energy;
    }

    public void startAudioStreaming() {
        Log.d(TAG, "Staring audio streaming");

        if (!audioStreaming) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    audioStreamingLoop();}
            }).start();
        }
        else {
            Log.d(TAG, " ... it was already running");
        }
    }

    public void stopAudioStreaming() {
        Log.d(TAG, "Stopping audio streaming");
        audioStreaming = false;
    }

    public boolean isAudioStreaming() {
        return audioStreaming;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();


    protected void audioStreamingLoop() {
        Log.d(TAG, "Starting the background thread to stream the audio data");

        audioStreaming = true;

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
                for (int i = 0; i < short_buffer.length; i++) {
                    e += Math.abs(((double) short_buffer[i])) / 256 * 100;
                }
                e /= short_buffer.length;

                max_energy = Math.max(e, max_energy);

                energy = e / max_energy;
//                Log.d(TAG, Double.toString(energy));

                // send the audio data to the server
//                dos.write(buffer, 0, read);
            }

            recorder.release();

            Log.d(TAG, "AudioRecord finished recording");
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
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

}
