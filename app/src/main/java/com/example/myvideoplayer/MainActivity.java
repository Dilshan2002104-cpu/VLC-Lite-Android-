package com.example.myvideoplayer;

import android.content.Context;
import android.content.Intent;
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
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private VLCVideoLayout videoLayout;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private Uri currentVideoUri;
    
    // Custom UI Controls
    private LinearLayout layoutControls, layoutTopControls;
    private ImageView btnPrev, btnPlayPause, btnNext, btnRotate, btnSubtitle;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvOsd;
    private Handler handler = new Handler();
    private boolean isTracking = false;
    private boolean isLandscape = false;
    private Runnable hideOsdTask = () -> { if (tvOsd != null) tvOsd.setVisibility(View.GONE); };

    // Subtitle Customization State
    private String subtitleColor = "16777215"; // White
    private String subtitleSize = "16"; // Normal
    private String activeSubtitlePath = null;

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
        layoutTopControls = findViewById(R.id.layout_top_controls);
        btnPrev = findViewById(R.id.btn_prev);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnRotate = findViewById(R.id.btn_rotate);
        btnSubtitle = findViewById(R.id.btn_subtitle);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_time_current);
        tvTotal = findViewById(R.id.tv_time_total);
        tvOsd = findViewById(R.id.tv_osd);

        // Get URI
        if (!FolderVideosActivity.activePlaylist.isEmpty() && FolderVideosActivity.activeVideoIndex != -1) {
            currentVideoUri = FolderVideosActivity.activePlaylist.get(FolderVideosActivity.activeVideoIndex);
        } else {
            currentVideoUri = getIntent().getData() != null ? getIntent().getData() : Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
        }

        initOrUpdateVLC(0);

        setupControls();
        setupGestures();
    }

    private void initOrUpdateVLC(long seekTime) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            libVLC.release();
        }

        ArrayList<String> options = new ArrayList<>();
        options.add("--aout=opensles");
        options.add("--audio-time-stretch"); 
        options.add("-vvv");
        options.add("--freetype-color=" + subtitleColor);
        options.add("--freetype-rel-fontsize=" + subtitleSize);
        
        try {
            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            
            loadMedia(currentVideoUri);
            
            if (activeSubtitlePath != null) {
                mediaPlayer.addSlave(Media.Slave.Type.Subtitle, Uri.parse("file://" + activeSubtitlePath), true);
            }
            if (seekTime > 0) {
                handler.postDelayed(() -> {
                    if (mediaPlayer != null) mediaPlayer.setTime(seekTime);
                }, 400);
            }
        } catch (Exception e) {
            Toast.makeText(this, "VLC Engine Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMedia(Uri uri) {
        if (mediaPlayer == null) return;
        
        // Clean up previous file descriptor if navigating files
        if (pfd != null) {
            try { pfd.close(); } catch (Exception ignored) {}
        }

        Media media;
        if ("content".equals(uri.getScheme())) {
            try {
                pfd = getContentResolver().openFileDescriptor(uri, "r");
                media = new Media(libVLC, pfd.getFileDescriptor());
            } catch (Exception e) {
                return;
            }
        } else if ("file".equals(uri.getScheme())) {
            media = new Media(libVLC, uri.getPath());
        } else {
            media = new Media(libVLC, uri);
        }
        
        media.setHWDecoderEnabled(true, false);
        mediaPlayer.setMedia(media);
        media.release();
        
        mediaPlayer.play();
        if (btnPlayPause != null) btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
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

        btnNext.setOnClickListener(v -> {
            if (!FolderVideosActivity.activePlaylist.isEmpty() && FolderVideosActivity.activeVideoIndex < FolderVideosActivity.activePlaylist.size() - 1) {
                FolderVideosActivity.activeVideoIndex++;
                currentVideoUri = FolderVideosActivity.activePlaylist.get(FolderVideosActivity.activeVideoIndex);
                activeSubtitlePath = null;
                loadMedia(currentVideoUri);
                showOsd("Next Video");
            } else {
                Toast.makeText(this, "Last video in folder", Toast.LENGTH_SHORT).show();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (!FolderVideosActivity.activePlaylist.isEmpty() && FolderVideosActivity.activeVideoIndex > 0) {
                FolderVideosActivity.activeVideoIndex--;
                currentVideoUri = FolderVideosActivity.activePlaylist.get(FolderVideosActivity.activeVideoIndex);
                activeSubtitlePath = null;
                loadMedia(currentVideoUri);
                showOsd("Previous Video");
            } else {
                Toast.makeText(this, "First video in folder", Toast.LENGTH_SHORT).show();
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

        btnSubtitle.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            long currentTime = mediaPlayer.getTime();
            PopupMenu popup = new PopupMenu(MainActivity.this, btnSubtitle);
            popup.getMenu().add("Load Subtitle (.srt)");
            popup.getMenu().add("Color: Yellow");
            popup.getMenu().add("Color: White");
            popup.getMenu().add("Size: Large");
            popup.getMenu().add("Size: Normal");

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.contains("Load")) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        if (btnPlayPause != null) btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    }
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    String[] mimetypes = {"application/x-subrip", "text/vtt", "text/plain", "application/octet-stream"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    subtitlePickerLauncher.launch(intent);
                } else if (title.contains("Yellow")) {
                    subtitleColor = "16776960";
                    initOrUpdateVLC(currentTime);
                } else if (title.contains("White")) {
                    subtitleColor = "16777215";
                    initOrUpdateVLC(currentTime);
                } else if (title.contains("Large")) {
                    subtitleSize = "22";
                    initOrUpdateVLC(currentTime);
                } else if (title.contains("Normal")) {
                    subtitleSize = "16";
                    initOrUpdateVLC(currentTime);
                }
                return true;
            });
            popup.show();
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
                    showOsd("Brightness: " + (int) (brightness * 100) + "%");
                } else {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int volumeChange = (int) (delta * maxVol);
                    int newVolume = Math.max(0, Math.min(maxVol, initialVolume + volumeChange));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                    showOsd("Volume: " + (int) ((newVolume / (float) maxVol) * 100) + "%");
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
                    showOsd("Rewind -10s");
                } else {
                    mediaPlayer.setTime(Math.min(duration, currentPos + 10000));
                    showOsd("Forward +10s");
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (layoutControls.getVisibility() == View.VISIBLE) {
                    layoutControls.setVisibility(View.GONE);
                    layoutTopControls.setVisibility(View.GONE);
                } else {
                    layoutControls.setVisibility(View.VISIBLE);
                    layoutTopControls.setVisibility(View.VISIBLE);
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

    private void showOsd(String text) {
        if (tvOsd == null) return;
        tvOsd.setText(text);
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdTask);
        handler.postDelayed(hideOsdTask, 1000);
    }

    private String formatTime(long millis) {
        long sec = (millis / 1000) % 60;
        long min = (millis / (1000 * 60)) % 60;
        long hr = millis / (1000 * 60 * 60);
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        return String.format("%02d:%02d", min, sec);
    }

    // Scoped Storage safely fetches the raw text out of content:// schema into C++ readable file boundary
    private String getPathFromContentUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            File tempFile = new File(getCacheDir(), "sub_cache.srt");
            try (FileOutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // Handles the returned Subtitle file perfectly integrating to the video track asynchronously 
    private final ActivityResultLauncher<Intent> subtitlePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri subtitleUri = result.getData().getData();
                    if (subtitleUri != null && mediaPlayer != null) {
                        String localPath = getPathFromContentUri(subtitleUri);
                        if (localPath != null) {
                            activeSubtitlePath = localPath;
                            mediaPlayer.addSlave(Media.Slave.Type.Subtitle, Uri.parse("file://" + localPath), true);
                            Toast.makeText(this, "Subtitle Attached!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                
                // Resume seamlessly and force an I-frame refresh safely AFTER Surface is re-created
                handler.postDelayed(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setTime(mediaPlayer.getTime()); 
                        mediaPlayer.play();
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    }
                }, 400);
            });

    @Override
    protected void onStart() {
        super.onStart();
        if (mediaPlayer != null && videoLayout != null) {
            // Securely re-attach the video surface after returning from background
            if (!mediaPlayer.getVLCVout().areViewsAttached()) {
                mediaPlayer.attachViews(videoLayout, null, false, false);
            }
            
            // Force a decoder pipeline flush to drop smeared P-frames AFTER Surface is ready!
            if (mediaPlayer.getTime() > 0) {
                handler.postDelayed(() -> {
                    if (mediaPlayer != null) mediaPlayer.setTime(mediaPlayer.getTime());
                }, 400);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.pause(); // Standard background logic
            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
            // Safely detach the destroyed surface
            mediaPlayer.detachViews();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null); // Purge absolutely all pending Runnables to decisively stop Activity memory leaks
        if (mediaPlayer != null) {
            mediaPlayer.release();
            libVLC.release();
        }
        if (pfd != null) {
            try { pfd.close(); } catch (Exception ignored) {}
        }
    }
}
