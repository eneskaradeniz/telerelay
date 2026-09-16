package com.telerelay

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.telerelay.ui.SettingsScreen
import com.telerelay.ui.TeleRelayTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity of the app. Extends [AppCompatActivity] so the per-app
 * language backport (AppCompatDelegate) works down to API 26.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TeleRelayTheme {
                SettingsScreen()
            }
        }
    }
}
