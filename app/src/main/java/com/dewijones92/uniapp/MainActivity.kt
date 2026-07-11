package com.dewijones92.uniapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.AppShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            UniAppTheme { AppShell() }
        }
    }
}
