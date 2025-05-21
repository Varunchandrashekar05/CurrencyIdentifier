package com.example.currencyidentifier

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.currencyidentifier.data.CurrencyRepository
import com.example.currencyidentifier.ui.screens.CameraScreen
import com.example.currencyidentifier.ui.screens.HomeScreen
import com.example.currencyidentifier.ui.screens.ResultScreen
import com.example.currencyidentifier.ui.theme.CurrencyIdentifierTheme
import com.example.currencyidentifier.ui.viewmodel.CurrencyViewModel
import com.example.currencyidentifier.ui.viewmodel.CurrencyViewModelFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted

// REQUIRED IMPORTS FOR THE FIX
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// END REQUIRED IMPORTS

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions result if needed, though Accompanist handles much of it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions on startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }

        setContent {
            CurrencyIdentifierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    // Get an instance of CurrencyViewModel
                    val currencyViewModel: CurrencyViewModel = viewModel(
                        factory = CurrencyViewModelFactory(CurrencyRepository(applicationContext))
                    )

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onCapturePhotoClick = { navController.navigate("camera") },
                                onUploadImageClick = { bitmap ->
                                    currencyViewModel.detectCurrency(bitmap)
                                    // This navigate is typically called from a UI interaction,
                                    // which is already on the main thread, so no special handling needed here.
                                    navController.navigate("result")
                                },
                                viewModel = currencyViewModel
                            )
                        }
                        composable("camera") {
                            CameraScreen(
                                viewModel = currencyViewModel,
                                onImageCaptured = { bitmap ->
                                    // ****** THE FIX IS HERE ******
                                    // The onImageCaptured lambda is executed on a background thread
                                    // by the ImageCapture callback. Navigation and ViewModel updates
                                    // (even with postValue, for the initial call) should ideally be
                                    // initiated from the main thread or explicitly posted to it.
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        currencyViewModel.detectCurrency(bitmap)
                                        navController.navigate("result")
                                    }
                                    // ****** END FIX ******
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("result") {
                            ResultScreen(
                                viewModel = currencyViewModel,
                                onBackToHome = {
                                    currencyViewModel.clearDetectionResult()
                                    // This navigate is typically called from a UI interaction,
                                    // which is already on the main thread, so no special handling needed here.
                                    navController.popBackStack("home", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}