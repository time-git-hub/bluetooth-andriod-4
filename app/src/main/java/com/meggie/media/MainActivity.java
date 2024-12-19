package com.meggie.media;


import androidx.appcompat.app.AppCompatActivity;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";
    private TextView tvMusicName;
    private TextView tvMusicArtist;
    private TextView tvMusicAlbum;
    private TextView tvBlueDeviceName;
    private ImageView imgPlayOrPause;
    private Context mContext;

    private BluetoothAdapter bluetoothAdapter;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int playState = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        tvMusicName = (TextView) findViewById(R.id.tv_music_name);
        tvMusicArtist = (TextView) findViewById(R.id.tv_music_artist);
        tvMusicAlbum = (TextView) findViewById(R.id.tv_music_album);
        imgPlayOrPause = (ImageView) findViewById(R.id.m_img_play);
        tvBlueDeviceName = (TextView) findViewById(R.id.tv_bluetooth_device);

        Log.e(TAG, "蓝牙连接广播监听");
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        connectBluetoothDevice();
    }

    /**
     * 蓝牙连接状态广播监听
     */
    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "bluetoothReceiver action:" + action);
            if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                Log.e("bluetooth", "BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED");
            }
            int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, 0);
            switch (bluetoothState) {
                case BluetoothAdapter.STATE_CONNECTED://蓝牙连接
                    Log.i("bluetooth", "STATE_CONNECTED");
                    // Initialize MediaPlayer and AudioManager here
                    initializeMediaPlayer();
                    break;
                case BluetoothAdapter.STATE_CONNECTING:
                    Log.i("bluetooth", "STATE_CONNECTING");
                    break;
                case BluetoothAdapter.STATE_DISCONNECTED://断开蓝牙连接
                    Log.i("bluetooth", "STATE_DISCONNECTED");
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateMediaPlayerInfo(null);
                            imgPlayOrPause.setImageResource(R.drawable.icon_pause);
                            tvBlueDeviceName.setText(getString(R.string.str_bluetooth_device) + "Null");
                        }
                    });
                    Toast.makeText(mContext, "请连接蓝牙", Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothAdapter.STATE_DISCONNECTING:
                    Log.i("bluetooth", "STATE_DISCONNECTING");
                    break;
                default:
                    Log.i("bluetooth", "DEFAULT");
                    break;
            }
        }
    };

    /**
     * 检查是否有蓝牙设备连接成功，并绑定媒体浏览器服务
     */
    private void connectBluetoothDevice() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // 设备不支持蓝牙
            Toast.makeText(mContext, "该设备不支持蓝牙连接", Toast.LENGTH_SHORT).show();
            return;
        }
        //如果蓝牙当前已启用并可供使用，则返回 true
        if (!bluetoothAdapter.isEnabled()) {
            // 请求打开蓝牙
            Toast.makeText(mContext, "请求打开蓝牙", Toast.LENGTH_SHORT).show();
        } else {
            boolean isBtConnect = isBTConnected();//是否有设备连接
            if (isBtConnect) {//有设备连接后绑定媒体浏览器服务
                initializeMediaPlayer();
            } else {
                tvBlueDeviceName.setText(getString(R.string.str_bluetooth_device) + "Null");
                Toast.makeText(mContext, "请连接蓝牙", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 是否有设备连接蓝牙
     *
     * @return
     */
    @SuppressLint("MissingPermission")
    public boolean isBTConnected() {
        try {
            Class<BluetoothAdapter> bluetoothAdapterClass = BluetoothAdapter.class;
            Method method = bluetoothAdapterClass.getDeclaredMethod("getConnectionState", (Class[]) null);
            method.setAccessible(true);
            int state = (int) method.invoke(bluetoothAdapter, (Object[]) null);
            Log.e(TAG, "state===" + state); //注意：已连接蓝牙后但state仍然为0的情况下需要检查是否有权限，Android10以上设备需要动态申请权限
            if (state == BluetoothAdapter.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothAdapter.STATE_CONNECTED");
                Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();//注意要在AndroidManifest中配置权限
                Log.i(TAG, "devices:" + devices.size());

                for (BluetoothDevice device : devices) {
                    Method isConnectedMethod = BluetoothDevice.class.getDeclaredMethod("isConnected", (Class[]) null);
                    isConnectedMethod.setAccessible(true);
                    boolean isConnected = (boolean) isConnectedMethod.invoke(device, (Object[]) null);
                    if (isConnected) {
                        Log.i(TAG, "connected:" + device.getAddress());
                        tvBlueDeviceName.setText(getString(R.string.str_bluetooth_device) + device.getName());
                    }
                }
                return true;
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Initialize MediaPlayer and AudioManager
     */
    private void initializeMediaPlayer() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
                playState = 3; // Playing
                imgPlayOrPause.setImageResource(R.drawable.icon_play);
            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playState = 0; // Stopped
                imgPlayOrPause.setImageResource(R.drawable.icon_pause);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
    }

    public void onBaseClick(View view) {
        switch (view.getId()) {
            case R.id.m_img_prev:
                // Handle previous track logic
                break;
            case R.id.m_img_next:
                // Handle next track logic
                break;
            case R.id.m_img_play:
                if (mediaPlayer != null) {
                    handlePlayEvent();
                }
                break;
        }
    }

    private void handlePlayEvent() {
        if (playState == 3) { // Playing
            mediaPlayer.pause();
            playState = 2; // Paused
            imgPlayOrPause.setImageResource(R.drawable.icon_pause);
        } else {
            mediaPlayer.start();
            playState = 3; // Playing
            imgPlayOrPause.setImageResource(R.drawable.icon_play);
        }
    }

    private void updateMediaPlayerInfo(MediaDescription description) {
        Log.d(TAG, "updateMediaPlayerInfo ..description:" + description);
        if (null != description) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.e(TAG, "description.getTitle():" + description.getTitle() + "  getSubtitle:" + description.getSubtitle() + "  getDescription:" + description.getDescription());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tvMusicName.setText("" + (TextUtils.isEmpty(description.getTitle()) ? getString(R.string.unknown_song) : description.getTitle()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tvMusicArtist.setText("" + (TextUtils.isEmpty(description.getSubtitle()) ? getString(R.string.unknown_artist) : description.getSubtitle()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tvMusicAlbum.setText("" + (TextUtils.isEmpty(description.getDescription()) ? getString(R.string.unknown_album) : description.getDescription()));
            }
        } else {
            tvMusicName.setText("" + (getString(R.string.unknown_song)));
            tvMusicArtist.setText("" + getString(R.string.unknown_artist));
            tvMusicAlbum.setText("" + getString(R.string.unknown_album));
        }
    }
}