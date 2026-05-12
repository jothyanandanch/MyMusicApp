package com.example.mymusic.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider; // RESTORED

import com.example.mymusic.R;
import com.example.mymusic.utils.AppLog;
import com.example.mymusic.viewmodel.MusicViewModel; // RESTORED

public class MainActivity extends AppCompatActivity {
    
    private ActivityResultLauncher<String> permissionLauncher;
    private MusicViewModel musicViewModel; // RESTORED: The variable declaration
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // RESTORED: Initialize the ViewModel BEFORE the launcher tries to use it
        musicViewModel = new ViewModelProvider(this).get(MusicViewModel.class);
        
        //The Below Initializes the Popup For Asking the Permission
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            AppLog.d(AppLog.PERMISSION, "Initializing The Permission Launcher");
            if (isGranted) {
                AppLog.d(AppLog.PERMISSION, "Permission Granted for Loading the Songs");
                musicViewModel.loadLocalMusic();
            } else {
                AppLog.e(AppLog.REPO, "Permission Denied!");
            }
        });
        
        //This Checks everytime the app loads whether the Permission is Still Granted or Not
        checkStoragePermission();
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ → use READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                AppLog.d(AppLog.PERMISSION, "Permission Already Granted (Android 13+)");
                musicViewModel.loadLocalMusic(); // RESTORED: Trigger scan if already granted
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12 and below → use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                AppLog.d(AppLog.PERMISSION, "Permission Already Granted (Android 12 or lower)");
                musicViewModel.loadLocalMusic(); // RESTORED: Trigger scan if already granted
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }
}