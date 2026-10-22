package com.rep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rep.app.ui.AppNavHost
import com.rep.app.ui.theme.RepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RepApp).container
        setContent {
            RepTheme {
                AppNavHost(container)
            }
        }
    }
}
