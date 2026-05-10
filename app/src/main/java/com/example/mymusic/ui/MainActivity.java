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

import com.example.mymusic.R;
import com.example.mymusic.utils.AppLog;


public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> permissionLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        //The Below Initializes the Popup For Asking the Permission
        permissionLauncher=registerForActivityResult(new ActivityResultContracts.RequestPermission(),isGranted ->{
            AppLog.d(AppLog.PERMISSION,"Initializing The Permission Launcher");
            if(isGranted){
                AppLog.d(AppLog.PERMISSION,"Permission Granted!");
            }
            else{
                AppLog.e(AppLog.REPO,"Permission Denied!");
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
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12 and below → use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                AppLog.d(AppLog.PERMISSION, "Permission Already Granted (Android 12 or lower)");
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

}