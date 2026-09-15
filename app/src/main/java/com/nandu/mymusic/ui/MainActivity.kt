package com.nandu.mymusic.ui

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nandu.mymusic.service.MusicPlaybackService
import com.nandu.mymusic.ui.screens.library.MusicLibraryScreen
import com.nandu.mymusic.ui.theme.AppleMusicTheme
import com.nandu.mymusic.utils.AppLog
import com.nandu.mymusic.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var musicViewModel: MusicViewModel

    // Favorites toggled from the system media notification (Android 13+ custom
    // layout) — re-sync the app's in-memory heart state with the service write.
    private val favoritesChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MusicPlaybackService.ACTION_FAVORITES_CHANGED) {
                musicViewModel.refreshFavoritesFromPrefs()
            }
        }
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                AppLog.d(AppLog.REPO, "Download complete — refreshing local music")
                musicViewModel.loadLocalMusic()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        setContent {
            AppleMusicTheme {
                MusicLibraryScreen(viewModel = musicViewModel)
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            AppLog.d(AppLog.PERMISSION, "Permission Launcher triggered")
            if (isGranted) {
                AppLog.d(AppLog.PERMISSION, "Permission Granted — loading songs")
                musicViewModel.loadLocalMusic()
            } else {
                AppLog.e(AppLog.REPO, "Permission Denied!")
            }
        }

        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(downloadCompleteReceiver, downloadFilter, RECEIVER_NOT_EXPORTED)

        val favoritesFilter = IntentFilter(MusicPlaybackService.ACTION_FAVORITES_CHANGED)
        registerReceiver(favoritesChangedReceiver, favoritesFilter, RECEIVER_NOT_EXPORTED)

        checkStoragePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadCompleteReceiver)
        unregisterReceiver(favoritesChangedReceiver)
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                AppLog.d(AppLog.PERMISSION, "Already granted (Android 13+)")
                musicViewModel.loadLocalMusic()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                AppLog.d(AppLog.PERMISSION, "Already granted (Android 12 or below)")
                musicViewModel.loadLocalMusic()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}