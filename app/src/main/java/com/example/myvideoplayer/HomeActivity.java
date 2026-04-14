package com.example.myvideoplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private static final int PERMISSION_REQ = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        recyclerView = findViewById(R.id.recycler_view);
        tvEmpty = findViewById(R.id.tv_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        checkPermissions();
    }

    private void checkPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, PERMISSION_REQ);
        } else {
            loadFolders();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadFolders();
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void loadFolders() {
        HashMap<String, Integer> folderMap = new HashMap<>();
        HashMap<String, String> folderIdMap = new HashMap<>();

        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.BUCKET_ID};

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);

                while (cursor.moveToNext()) {
                    String folderName = cursor.getString(nameCol);
                    String folderId = cursor.getString(idCol);

                    if (folderName != null) {
                        folderMap.put(folderName, folderMap.getOrDefault(folderName, 0) + 1);
                        folderIdMap.put(folderName, folderId);
                    }
                }
            }
        }

        if (folderMap.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            List<FolderItem> folders = new ArrayList<>();
            for (String key : folderMap.keySet()) {
                folders.add(new FolderItem(key, folderIdMap.get(key), folderMap.get(key)));
            }
            recyclerView.setAdapter(new FolderAdapter(folders));
        }
    }

    class FolderItem {
        String name, id;
        int count;
        FolderItem(String name, String id, int count) {
            this.name = name; this.id = id; this.count = count;
        }
    }

    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        List<FolderItem> list;
        FolderAdapter(List<FolderItem> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FolderItem item = list.get(position);
            holder.tvName.setText(item.name);
            holder.tvCount.setText(item.count + " Videos");
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, FolderVideosActivity.class);
                intent.putExtra("BUCKET_ID", item.id);
                intent.putExtra("BUCKET_NAME", item.name);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_folder_name);
                tvCount = v.findViewById(R.id.tv_video_count);
            }
        }
    }
}
