package com.example.mymusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.mymusic.utils.AppLog
import com.example.mymusic.viewmodel.MusicViewModel
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import com.example.mymusic.ui.screens.library.MusicLibraryScreen

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var musicViewModel: MusicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //It makes the app display to appear fully like Full Screen Display
        enableEdgeToEdge()
        //It Tells the Android to draw what is inside these braces using Compose

        musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        setContent {
            MaterialTheme{
                MusicLibraryScreen(viewModel = musicViewModel)
            }
        }

        // Initializes the Popup For Asking the Permission
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            AppLog.d(AppLog.PERMISSION, "Initializing The Permission Launcher")
            if (isGranted) {
                AppLog.d(AppLog.PERMISSION, "Permission Granted for Loading the Songs")
                musicViewModel.loadLocalMusic()
            } else {
                AppLog.e(AppLog.REPO, "Permission Denied!")
            }
        }

        // Checks every time the app loads whether the Permission is Still Granted or Not
        checkStoragePermission()

    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ → use READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                AppLog.d(AppLog.PERMISSION, "Permission Already Granted (Android 13+)")
                musicViewModel.loadLocalMusic()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 and below → use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                AppLog.d(AppLog.PERMISSION, "Permission Already Granted (Android 12 or lower)")
                musicViewModel.loadLocalMusic()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

