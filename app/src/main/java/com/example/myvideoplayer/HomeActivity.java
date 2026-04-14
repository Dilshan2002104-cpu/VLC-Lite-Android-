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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.IntentSender;
import java.util.HashSet;

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
    private TextView tvEmpty, tvSelectionCount;
    private View layoutNormal, layoutSelection;
    private static final int PERMISSION_REQ = 100;
    
    private boolean isSelectionMode = false;
    private HashSet<String> selectedFolders = new HashSet<>();
    private FolderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViewById(R.id.btn_sort).setVisibility(View.GONE);

        layoutNormal = findViewById(R.id.layout_normal);
        layoutSelection = findViewById(R.id.layout_selection);
        tvSelectionCount = findViewById(R.id.tv_selection_count);

        findViewById(R.id.btn_close_selection).setOnClickListener(v -> toggleSelectionMode(false));
        findViewById(R.id.btn_info_selected).setOnClickListener(v -> showFolderProperties());
        findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete Folders")
                .setMessage("Delete all videos in the " + selectedFolders.size() + " selected folders?")
                .setPositiveButton("Delete All", (d, w) -> deleteMultipleFolders())
                .setNegativeButton("Cancel", null)
                .show();
        });

        recyclerView = findViewById(R.id.recycler_view);
        tvEmpty = findViewById(R.id.tv_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        checkPermissions();
    }

    private void deleteFolder(String bucketId) {
        List<Uri> uris = new ArrayList<>();
        Uri allUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = { MediaStore.Video.Media._ID };
        try (Cursor cursor = getContentResolver().query(allUri, proj, MediaStore.Video.Media.BUCKET_ID + "=?", new String[]{bucketId}, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                while (cursor.moveToNext()) {
                    uris.add(Uri.withAppendedPath(allUri, String.valueOf(cursor.getLong(idCol))));
                }
            }
        }
        
        if (uris.isEmpty()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), 1002, null, 0, 0, 0, null);
            } catch (Exception e) {}
        } else {
            for (Uri u : uris) {
                try { getContentResolver().delete(u, null, null); } catch (Exception e) {}
            }
            loadFolders();
        }
    }

    private void toggleSelectionMode(boolean active) {
        isSelectionMode = active;
        if (!active) selectedFolders.clear();
        layoutNormal.setVisibility(active ? View.GONE : View.VISIBLE);
        layoutSelection.setVisibility(active ? View.VISIBLE : View.GONE);
        updateSelectionUI();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        tvSelectionCount.setText(selectedFolders.size() + " Selected");
        findViewById(R.id.btn_info_selected).setVisibility(selectedFolders.size() == 1 ? View.VISIBLE : View.GONE);
    }

    private void deleteMultipleFolders() {
        if (selectedFolders.isEmpty()) return;
        List<Uri> uris = new ArrayList<>();
        Uri allUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = { MediaStore.Video.Media._ID, MediaStore.Video.Media.BUCKET_ID };
        try (Cursor cursor = getContentResolver().query(allUri, proj, null, null, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int bidCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);
                while (cursor.moveToNext()) {
                    if (selectedFolders.contains(cursor.getString(bidCol))) {
                        uris.add(Uri.withAppendedPath(allUri, String.valueOf(cursor.getLong(idCol))));
                    }
                }
            }
        }
        if (uris.isEmpty()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), 1002, null, 0, 0, 0, null);
            } catch (Exception e) {}
        } else {
            for (Uri u : uris) {
                try { getContentResolver().delete(u, null, null); } catch (Exception e) {}
            }
            toggleSelectionMode(false);
            loadFolders();
        }
    }

    private void showFolderProperties() {
        if (selectedFolders.size() != 1) return;
        String bId = selectedFolders.iterator().next();
        String folderName = "Unknown";
        String folderPath = "Unknown";
        int videoCount = 0;
        long totalSize = 0;

        Uri allUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = { MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE };
        try (Cursor cursor = getContentResolver().query(allUri, proj, MediaStore.Video.Media.BUCKET_ID + "=?", new String[]{bId}, null)) {
            if (cursor != null) {
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                while (cursor.moveToNext()) {
                    if (videoCount == 0) {
                        folderName = cursor.getString(nameCol);
                        String path = cursor.getString(dataCol);
                        if (path != null && path.contains("/")) {
                            folderPath = path.substring(0, path.lastIndexOf('/'));
                        }
                    }
                    totalSize += cursor.getLong(sizeCol);
                    videoCount++;
                }
            }
        }

        String msg = "Name: " + folderName + "\n\n" +
                     "Path: " + folderPath + "\n\n" +
                     "Videos: " + videoCount + "\n\n" +
                     "Total Size: " + formatSize(totalSize);

        new AlertDialog.Builder(this)
            .setTitle("Folder Properties")
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
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            loadFolders();
        }
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
            adapter = new FolderAdapter(folders);
            recyclerView.setAdapter(adapter);
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
            
            boolean isSelected = selectedFolders.contains(item.id);
            ((androidx.cardview.widget.CardView)holder.itemView).setCardBackgroundColor(isSelected ? android.graphics.Color.parseColor("#2980B9") : android.graphics.Color.parseColor("#1A1A1A"));

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    if (selectedFolders.contains(item.id)) selectedFolders.remove(item.id);
                    else selectedFolders.add(item.id);

                    if (selectedFolders.isEmpty()) toggleSelectionMode(false);
                    else updateSelectionUI();
                    notifyItemChanged(position);
                    return;
                }

                Intent intent = new Intent(HomeActivity.this, FolderVideosActivity.class);
                intent.putExtra("BUCKET_ID", item.id);
                intent.putExtra("BUCKET_NAME", item.name);
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    toggleSelectionMode(true);
                    selectedFolders.add(item.id);
                    updateSelectionUI();
                    notifyItemChanged(position);
                }
                return true;
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
