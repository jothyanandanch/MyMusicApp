package com.nandu.mymusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nandu.mymusic.ui.screens.library.MusicLibraryScreen
import com.nandu.mymusic.ui.theme.SpotifyUIAppTheme
import com.nandu.mymusic.utils.AppLog
import com.nandu.mymusic.ui.components.AppUpdateDialog
import com.nandu.mymusic.viewmodel.MusicViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class MainActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var musicViewModel: MusicViewModel



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        // In MainActivity.kt onCreate():
        setContent {
            SpotifyUIAppTheme {
                val currentUser = auth.currentUser

                // State for our recurring update dialog
                var showUpdateDialog by remember { mutableStateOf(false) }
                var latestAppVersion by remember { mutableStateOf("1.0") } // Fetch this from Firebase Remote Config!

                // Simulate a version check on launch
                LaunchedEffect(Unit) {
                    // Replace with actual Remote Config fetch
                    val remoteVersion = "1.1" // Example remote version
                    val currentVersion = "1.0" // Example current version (BuildConfig.VERSION_NAME)

                    if (remoteVersion > currentVersion) {
                        latestAppVersion = remoteVersion
                        showUpdateDialog = true
                    }
                }

                if (showUpdateDialog) {
                    AppUpdateDialog(
                        latestVersion = latestAppVersion,
                        onUpdateClick = {
                            showUpdateDialog = false
                            // TODO: Trigger your DownloadManager to download the APK here
                        },
                        onDismiss = {
                            // "Not Now" - just dismisses for this session.
                            // Will reappear next time app is opened!
                            showUpdateDialog = false
                        }
                    )
                }

                // Check if logged in
                if (currentUser == null) {
                    // Show a Login Screen with a "Sign in with Google" button
                    // When login succeeds, call saveUserToFirestore(auth.currentUser!!)
                    LoginScreen(onLoginSuccess = { recreate() })
                } else {
                    // User is logged in, show the main app
                    MusicLibraryScreen(viewModel = musicViewModel)
                }
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

        checkStoragePermission()


    }

    private fun saveUserToFirestore(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        val userMap = hashMapOf(
            "uid" to firebaseUser.uid,
            "name" to (firebaseUser.displayName ?: "Unknown User"),
            "email" to (firebaseUser.email ?: ""),
            "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
            "isBanned" to false,
            "lastLogin" to System.currentTimeMillis()
        )

        // Save to Firestore under "users/{uid}"
        db.collection("users").document(firebaseUser.uid)
            .set(userMap)
            .addOnSuccessListener {
                AppLog.d(AppLog.PERMISSION, "User profile saved to Firestore!")
            }
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
