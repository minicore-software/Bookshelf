package com.minicore.bookshelf

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.features.splash.ui.BookshelfSplashScreen
import com.minicore.bookshelf.navigation.BookshelfNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Bookshelf is light-only (paper background): bar icons must stay dark regardless of
        // the system's dark/light mode, unlike SystemBarStyle.auto which follows it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
        )
        setContent {
            BookshelfTheme {
                var splashCompleted by rememberSaveable { mutableStateOf(false) }

                if (!splashCompleted) {
                    BookshelfSplashScreen(onComplete = { splashCompleted = true })
                } else {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BookshelfNavGraph(rememberNavController())
                    }
                }
            }
        }
    }
}
