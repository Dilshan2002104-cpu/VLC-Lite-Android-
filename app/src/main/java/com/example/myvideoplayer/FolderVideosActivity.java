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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        bucketId = getIntent().getStringExtra("BUCKET_ID");
        String bName = getIntent().getStringExtra("BUCKET_NAME");

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(bName != null ? bName : "Videos");

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

    private void loadVideos() {
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
        recyclerView.setAdapter(new VideoAdapter(videos));
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

    class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {
        List<VideoItem> list;
        VideoAdapter(List<VideoItem> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            VideoItem item = list.get(position);
            holder.tvName.setText(item.name);
            holder.tvDuration.setText(formatDuration(item.duration));
            
            // Rapid Native Thumbnail reading (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Bitmap thumb = getContentResolver().loadThumbnail(item.uri, new Size(300, 300), null);
                    holder.ivThumb.setImageBitmap(thumb);
                } catch (Exception e) {
                    holder.ivThumb.setImageResource(android.R.drawable.ic_media_play);
                }
            }

            holder.itemView.setOnClickListener(v -> {
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
