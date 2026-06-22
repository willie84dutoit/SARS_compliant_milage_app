package com.mileagetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mileagetracker.app.ui.common.MileageTrackerTheme
import com.mileagetracker.app.ui.navigation.MileageTrackerNavHost
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host (T-001 blueprint §1). All screens are Compose destinations in [MileageTrackerNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MileageTrackerTheme {
                MileageTrackerNavHost()
            }
        }
    }
}
