package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AppViewModel
import com.example.ui.AppViewModelFactory
import com.example.ui.MainScreen
import com.example.ui.ReaderScreen
import com.example.ui.ScannerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle initial intent
        viewModel.handleIntent(intent)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                
                // Monitor for external document opens
                val currentBook by viewModel.currentBookRecord.collectAsState()
                val currentEngine by viewModel.currentEngine.collectAsState()
                
                androidx.compose.runtime.LaunchedEffect(currentEngine) {
                    if (currentEngine != null && navController.currentDestination?.route != "reader") {
                        navController.navigate("reader") {
                            // Ensure we don't stack reader screens if multiple intents come in
                            popUpTo("main") { inclusive = false }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenBook = { file ->
                                viewModel.openDocument(file)
                                navController.navigate("reader")
                            },
                            onNavigateToScanner = {
                                navController.navigate("scanner")
                            }
                        )
                    }
                    composable("reader") {
                        ReaderScreen(
                            viewModel = viewModel,
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("scanner") {
                        ScannerScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onOpenBook = { file ->
                                viewModel.openDocument(file)
                                navController.navigate("reader") {
                                    // Pop scanner screen so back from reader returns to home shelf
                                    popUpTo("main") { inclusive = false }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }
}
