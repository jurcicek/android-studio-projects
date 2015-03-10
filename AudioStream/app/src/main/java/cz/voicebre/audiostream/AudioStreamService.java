package cz.voicebre.audiostream;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
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

        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendIntent = PendingIntent.getActivity(this, 0, i, 0);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker("AudioStream").setContentTitle("AudioStream").setContentText("AudioStream streaming")
                .setWhen(System.currentTimeMillis()).setAutoCancel(false)
                .setOngoing(true).setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendIntent);
        Notification notification = builder.build();

        notification.flags |= Notification.FLAG_NO_CLEAR;
        startForeground(myID, notification);

        // Tell the user we started.
        Toast.makeText(this, R.string.audio_streaming_service_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start id " + startId + ": " + intent);
        super.onStartCommand(intent, flags, startId);

        if (! t.isAlive()) {
            t.start();
        }

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
        Log.d(TAG, "Received onBind");

        if (! t.isAlive()) {
            t.start();
        }

        return mBinder;
    }

    public double getEnergy() {
        return energy;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    Thread t = new Thread(new Runnable() {
        @Override
        public void run() {audioStreaming();}
    });

    protected void audioStreaming() {
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

}
