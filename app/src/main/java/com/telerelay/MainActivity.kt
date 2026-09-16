package com.telerelay

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.telerelay.ui.SettingsScreen
import com.telerelay.ui.TeleRelayTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity of the app. Extends [AppCompatActivity] for the AppCompat
 * theme; the app language follows the device (localeConfig declares the
 * supported locales so the system can offer a per-app override).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // The very first per-app language switch can recreate the activity even
        // with locale configChanges declared; a zero-duration transition masks
        // that so the switch never shows a teardown flash. Positional arguments —
        // framework methods carry no parameter names.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        super.onCreate(savedInstanceState)
        setContent {
            TeleRelayTheme {
                SettingsScreen()
            }
        }
    }
}
