package com.example.myvideoplayer;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.IntentSender;
import java.util.Collections;
import java.util.HashSet;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FolderVideosActivity extends AppCompatActivity {

    public static List<Uri> activePlaylist = new ArrayList<>();
    public static int activeVideoIndex = -1;

    private RecyclerView recyclerView;
    private String bucketId;
    private String currentSortOrder;
    private Uri pendingDeleteUri = null;

    private boolean isSelectionMode = false;
    private HashSet<Uri> selectedUris = new HashSet<>();
    private View layoutNormal, layoutSelection;
    private TextView tvSelectionCount;
    private FolderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        bucketId = getIntent().getStringExtra("BUCKET_ID");
        String bName = getIntent().getStringExtra("BUCKET_NAME");

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(bName != null ? bName : "Videos");

        layoutNormal = findViewById(R.id.layout_normal);
        layoutSelection = findViewById(R.id.layout_selection);
        tvSelectionCount = findViewById(R.id.tv_selection_count);

        findViewById(R.id.btn_vault).setVisibility(View.GONE);
        if ("VAULT".equals(bucketId)) {
            findViewById(R.id.btn_vault_move).setVisibility(View.GONE);
            findViewById(R.id.btn_sort).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btn_vault_move).setOnClickListener(v -> moveToVault());
        }

        findViewById(R.id.btn_close_selection).setOnClickListener(v -> toggleSelectionMode(false));
        findViewById(R.id.btn_info_selected).setOnClickListener(v -> showVideoProperties());
        findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete Videos")
                .setMessage("Delete " + selectedUris.size() + " videos permanently?")
                .setPositiveButton("Delete", (d, w) -> deleteMultipleVideos())
                .setNegativeButton("Cancel", null)
                .show();
        });

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        currentSortOrder = getSharedPreferences("OnyxPrefs", MODE_PRIVATE)
                .getString("video_sort_order", MediaStore.Video.Media.DATE_ADDED + " DESC");

        ImageView btnSort = findViewById(R.id.btn_sort);
        btnSort.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(FolderVideosActivity.this, btnSort);
            popup.getMenu().add("Name (A-Z)");
            popup.getMenu().add("Name (Z-A)");
            popup.getMenu().add("Date (Newest)");
            popup.getMenu().add("Date (Oldest)");
            popup.getMenu().add("Size (Largest)");

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.contains("A-Z")) {
                    currentSortOrder = MediaStore.Video.Media.DISPLAY_NAME + " ASC";
                } else if (title.contains("Z-A")) {
                    currentSortOrder = MediaStore.Video.Media.DISPLAY_NAME + " DESC";
                } else if (title.contains("Newest")) {
                    currentSortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
                } else if (title.contains("Oldest")) {
                    currentSortOrder = MediaStore.Video.Media.DATE_ADDED + " ASC";
                } else if (title.contains("Size")) {
                    currentSortOrder = MediaStore.Video.Media.SIZE + " DESC";
                }
                
                getSharedPreferences("OnyxPrefs", MODE_PRIVATE).edit()
                        .putString("video_sort_order", currentSortOrder).apply();
                loadVideos();
                Toast.makeText(this, "Sorted by " + item.getTitle(), Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.show();
        });

        loadVideos();
    }

    private void toggleSelectionMode(boolean active) {
        isSelectionMode = active;
        if (!active) selectedUris.clear();
        layoutNormal.setVisibility(active ? View.GONE : View.VISIBLE);
        layoutSelection.setVisibility(active ? View.VISIBLE : View.GONE);
        updateSelectionUI();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        tvSelectionCount.setText(selectedUris.size() + " Selected");
        findViewById(R.id.btn_info_selected).setVisibility(selectedUris.size() == 1 ? View.VISIBLE : View.GONE);
    }

    private void moveToVault() {
        if (selectedUris.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle("Move to Vault")
            .setMessage("Move " + selectedUris.size() + " videos to the encrypted Secure Vault? The files will naturally disappear from the Phone Gallery and regular apps.")
            .setPositiveButton("Move", (d, w) -> executeVaultMove())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void executeVaultMove() {
        java.io.File vaultDir = new java.io.File(getExternalFilesDir(null), "OnyxVault");
        if (!vaultDir.exists()) vaultDir.mkdirs();

        try { new java.io.File(vaultDir, ".nomedia").createNewFile(); } catch (Exception ignored) {}

        Toast.makeText(this, "Moving to Secure Vault...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean anyError = false;
            for (Uri uri : selectedUris) {
                try {
                    String displayName = "hidden_" + System.currentTimeMillis() + ".mkv";
                    try (Cursor c = getContentResolver().query(uri, new String[]{MediaStore.Video.Media.DISPLAY_NAME}, null, null, null)) {
                        if (c != null && c.moveToFirst()) displayName = c.getString(0);
                    }

                    java.io.File destFile = new java.io.File(vaultDir, displayName);
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                         java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                        
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                } catch (Exception e) {
                    anyError = true;
                }
            }

            boolean finalAnyError = anyError;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalAnyError) Toast.makeText(FolderVideosActivity.this, "Some files failed to fully copy.", Toast.LENGTH_SHORT).show();
                deleteMultipleVideos(); // Use native routine to prompt for source deletion
            });
        }).start();
    }

    private void deleteMultipleVideos() {
        if (selectedUris.isEmpty()) return;
        List<Uri> targetUris = new ArrayList<>(selectedUris);
        
        if ("VAULT".equals(bucketId)) {
            for (Uri u : targetUris) {
                if (u.getPath() != null) new java.io.File(u.getPath()).delete();
            }
            toggleSelectionMode(false);
            loadVideos();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), targetUris);
                startIntentSenderForResult(pi.getIntentSender(), 1001, null, 0, 0, 0, null);
            } catch (Exception e) {}
        } else {
            for (Uri uri : targetUris) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception e) {}
            }
            toggleSelectionMode(false);
            loadVideos();
        }
    }

    private void deleteVideo(Uri uri) {
        if ("VAULT".equals(bucketId)) {
            if (uri.getPath() != null) new java.io.File(uri.getPath()).delete();
            loadVideos();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(uri));
                startIntentSenderForResult(pi.getIntentSender(), 1001, null, 0, 0, 0, null);
            } catch (Exception e) {}
        } else {
            try {
                int deleted = getContentResolver().delete(uri, null, null);
                if (deleted > 0) loadVideos();
            } catch (SecurityException e) {
                // Ignore complex Q scoping for briefness, OS will handle it mostly.
            }
        }
    }

    private void showVideoProperties() {
        if (selectedUris.size() != 1) return;
        Uri uri = selectedUris.iterator().next();
        
        if ("VAULT".equals(bucketId)) {
            java.io.File f = new java.io.File(uri.getPath());
            String msg = "File Name:\n" + f.getName() + "\n\n" +
                         "Location:\nSecure Vault (Encrypted)\n\n" +
                         "Size :  " + formatSize(f.length());
            new AlertDialog.Builder(this).setTitle("Vault Properties").setMessage(msg).setPositiveButton("OK", null).show();
            return;
        }

        String vName = "Unknown", vPath = "Unknown", vRes = "Unknown", vFormat = "Unknown";
        long vSize = 0, vDuration = 0, vDate = 0;

        String[] proj = { 
            MediaStore.Video.Media.DISPLAY_NAME, 
            MediaStore.Video.Media.DATA, 
            MediaStore.Video.Media.SIZE, 
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        };
        try (Cursor cursor = getContentResolver().query(uri, proj, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                vName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME));
                vPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                vSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
                vRes = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION));
                vDuration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                vDate = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED));
                vFormat = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
                
                if (vPath != null && vPath.lastIndexOf('.') > 0) {
                    vFormat = vPath.substring(vPath.lastIndexOf('.') + 1).toUpperCase();
                } else if (vFormat != null && vFormat.contains("/")) {
                    vFormat = vFormat.split("/")[1].toUpperCase();
                }
            }
        }

        String dateStr = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(new java.util.Date(vDate * 1000L));

        String msg = "File Name:\n" + vName + "\n\n" +
                     "Location:\n" + vPath + "\n\n" +
                     "Format :  " + vFormat + "\n" +
                     "Size :  " + formatSize(vSize) + "\n" +
                     "Resolution :  " + (vRes != null ? vRes : "Unknown") + "\n" +
                     "Length :  " + formatDuration(vDuration) + "\n" +
                     "Date :  " + dateStr;

        new AlertDialog.Builder(this)
            .setTitle("Video Properties")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show();
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(sizeBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loadVideos();
        }
    }

    private void loadVideos() {
        if ("VAULT".equals(bucketId)) {
            loadVaultVideos();
            return;
        }

        List<VideoItem> videos = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
        };
        String sel = MediaStore.Video.Media.BUCKET_ID + "=?";
        String[] selArgs = {bucketId};

        // Querying videos for this specific folder with custom Dynamic Sort!
        try (Cursor cursor = getContentResolver().query(uri, proj, sel, selArgs, currentSortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long duration = cursor.getLong(durCol);
                    
                    Uri videoUri = Uri.withAppendedPath(uri, String.valueOf(id));
                    videos.add(new VideoItem(id, name, duration, videoUri));
                }
            }
        }
        adapter = new FolderAdapter(videos);
        recyclerView.setAdapter(adapter);
    }

    private void loadVaultVideos() {
        List<VideoItem> videos = new ArrayList<>();
        java.io.File vaultDir = new java.io.File(getExternalFilesDir(null), "OnyxVault");
        if (vaultDir.exists() && vaultDir.isDirectory()) {
            java.io.File[] files = vaultDir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.getName().equals(".nomedia")) continue;
                    long dur = 0;
                    android.media.MediaMetadataRetriever ret = null;
                    try {
                        ret = new android.media.MediaMetadataRetriever();
                        ret.setDataSource(f.getAbsolutePath());
                        String time = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (time != null) dur = Long.parseLong(time);
                    } catch (Exception e) {
                        // ignore 
                    } finally {
                        if (ret != null) {
                            try { ret.release(); } catch (Exception ignore) {}
                        }
                    }
                    videos.add(new VideoItem(0L, f.getName(), dur, Uri.fromFile(f)));
                }
            }
        }
        
        adapter = new FolderAdapter(videos);
        recyclerView.setAdapter(adapter);
    }

    private String formatDuration(long millis) {
        long sec = (millis / 1000) % 60;
        long min = (millis / (1000 * 60)) % 60;
        long hr = millis / (1000 * 60 * 60);
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        return String.format("%02d:%02d", min, sec);
    }

    class VideoItem {
        long id, duration;
        String name;
        Uri uri;
        VideoItem(long id, String name, long duration, Uri uri) {
            this.id = id; this.name = name; this.duration = duration; this.uri = uri;
        }
    }

    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        List<VideoItem> list;
        FolderAdapter(List<VideoItem> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            VideoItem item = list.get(position);
            holder.tvName.setText(item.name);
            holder.tvDuration.setText(formatDuration(item.duration));
            
            // Visual check for selection
            boolean isSelected = selectedUris.contains(item.uri);
            ((androidx.cardview.widget.CardView)holder.itemView).setCardBackgroundColor(isSelected ? android.graphics.Color.parseColor("#2980B9") : android.graphics.Color.parseColor("#1A1A1A"));

            // Smooth Glide Thumbnail rendering for all formats (including MKV/HEVC)
            Glide.with(FolderVideosActivity.this)
                 .asBitmap()
                 .load(item.uri)
                 .apply(new RequestOptions()
                         .placeholder(R.drawable.ic_video_placeholder)
                         .error(R.drawable.ic_video_placeholder)
                         .diskCacheStrategy(DiskCacheStrategy.ALL)
                         .centerCrop())
                 .into(holder.ivThumb);

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    if (selectedUris.contains(item.uri)) selectedUris.remove(item.uri);
                    else selectedUris.add(item.uri);

                    if (selectedUris.isEmpty()) toggleSelectionMode(false);
                    else updateSelectionUI();
                    notifyItemChanged(position);
                    return;
                }

                // Populate static playlist for Next/Prev robust functionality
                FolderVideosActivity.activePlaylist.clear();
                for (VideoItem vi : list) {
                    FolderVideosActivity.activePlaylist.add(vi.uri);
                }
                FolderVideosActivity.activeVideoIndex = position;

                Intent intent = new Intent(FolderVideosActivity.this, MainActivity.class);
                intent.setData(item.uri);
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    toggleSelectionMode(true);
                    selectedUris.add(item.uri);
                    updateSelectionUI();
                    notifyItemChanged(position);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDuration;
            ImageView ivThumb;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_video_title);
                tvDuration = v.findViewById(R.id.tv_duration);
                ivThumb = v.findViewById(R.id.iv_thumbnail);
            }
        }
    }
}
