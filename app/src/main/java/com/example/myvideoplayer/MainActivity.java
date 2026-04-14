package com.example.myvideoplayer;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private VLCVideoLayout videoLayout;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    
    // Custom UI Controls
    private LinearLayout layoutControls;
    private ImageView btnPlayPause, btnRotate;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal;
    private Handler handler = new Handler();
    private boolean isTracking = false;
    private boolean isLandscape = false;

    // Feature: Gestures
    private AudioManager audioManager;
    private GestureDetector gestureDetector;
    private boolean isLeftHalf;
    private int initialVolume;
    private float initialBrightness;
    
    private android.os.ParcelFileDescriptor pfd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Android 16 & One UI 8.0 Immersive Display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        setContentView(R.layout.activity_main);

        // Map Views
        videoLayout = findViewById(R.id.video_layout);
        layoutControls = findViewById(R.id.layout_controls);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnRotate = findViewById(R.id.btn_rotate);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_time_current);
        tvTotal = findViewById(R.id.tv_time_total);

        // Get URI
        Uri videoUri = getIntent().getData() != null ? getIntent().getData() : Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");

        // Init LibVLC (Hardware decoding preferred, automatically falls back to powerful SW decoding for unsupported HEVC!)
        ArrayList<String> options = new ArrayList<>();
        options.add("--aout=opensles");
        options.add("--audio-time-stretch"); 
        options.add("-vvv"); // verbose
        
        try {
            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            
            Media media;
            if ("content".equals(videoUri.getScheme())) {
                pfd = getContentResolver().openFileDescriptor(videoUri, "r");
                media = new Media(libVLC, pfd.getFileDescriptor());
            } else if ("file".equals(videoUri.getScheme())) {
                media = new Media(libVLC, videoUri.getPath());
            } else {
                media = new Media(libVLC, videoUri);
            }
            
            media.setHWDecoderEnabled(true, false);
            mediaPlayer.setMedia(media);
            media.release();
            
            mediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(this, "VLC Engine Error", Toast.LENGTH_SHORT).show();
        }

        setupControls();
        setupGestures();
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            } else {
                mediaPlayer.play();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        });

        btnRotate.setOnClickListener(v -> {
            if (isLandscape) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                isLandscape = false;
            } else {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                isLandscape = true;
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float percentage = progress / 1000f;
                    long newTime = (long) (mediaPlayer.getLength() * percentage);
                    tvCurrent.setText(formatTime(newTime));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking = false;
                float percentage = seekBar.getProgress() / 1000f;
                mediaPlayer.setTime((long) (mediaPlayer.getLength() * percentage));
            }
        });

        handler.post(updateTimeTask);
    }

    private Runnable updateTimeTask = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && !isTracking) {
                long currentTime = mediaPlayer.getTime();
                long totalTime = mediaPlayer.getLength();

                tvCurrent.setText(formatTime(currentTime));
                tvTotal.setText(formatTime(totalTime));

                if (totalTime > 0) {
                    seekBar.setMax(1000);
                    seekBar.setProgress((int) ((currentTime * 1000f) / totalTime));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void setupGestures() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e1.getY() - e2.getY();
                int viewHeight = Math.max(1, videoLayout.getHeight());
                float delta = (diffY / viewHeight) * 1.5f;

                if (isLeftHalf) {
                    float brightness = initialBrightness + delta;
                    brightness = Math.max(0f, Math.min(1f, brightness));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = brightness;
                    getWindow().setAttributes(lp);
                } else {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int volumeChange = (int) (delta * maxVol);
                    int newVolume = Math.max(0, Math.min(maxVol, initialVolume + volumeChange));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mediaPlayer == null) return true;
                boolean isLeft = e.getX() < (videoLayout.getWidth() / 2f);
                long currentPos = mediaPlayer.getTime();
                long duration = mediaPlayer.getLength();
                if (duration <= 0) return true; // prevent seek on live
                
                if (isLeft) {
                    mediaPlayer.setTime(Math.max(0, currentPos - 10000));
                } else {
                    mediaPlayer.setTime(Math.min(duration, currentPos + 10000));
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    layoutControls.setVisibility(View.GONE);
                } else {
                    layoutControls.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });

        videoLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                isLeftHalf = event.getX() < (v.getWidth() / 2f);
                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                initialBrightness = getWindow().getAttributes().screenBrightness;
                if (initialBrightness < 0) {
                    try {
                        initialBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
                    } catch (Exception eFallback) {
                        initialBrightness = 0.5f;
                    }
                }
            }
            gestureDetector.onTouchEvent(event);
            return true; 
        });
    }

    private String formatTime(long millis) {
        long sec = (millis / 1000) % 60;
        long min = (millis / (1000 * 60)) % 60;
        long hr = millis / (1000 * 60 * 60);
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimeTask);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            libVLC.release();
        }
        if (pfd != null) {
            try { pfd.close(); } catch (Exception ignored) {}
        }
    }
}
