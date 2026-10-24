package com.minicore.bookshelf.features.splash.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.minicore.bookshelf.R
import kotlinx.coroutines.delay

internal const val SPLASH_DURATION_MS = 600L
const val SPLASH_SCREEN_TEST_TAG = "bookshelf_splash_screen"

@Composable
fun BookshelfSplashScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        currentOnComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.paper))
            .testTag(SPLASH_SCREEN_TEST_TAG)
    ) {
        Image(
            painter = painterResource(R.drawable.splash_screen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}

@Preview(name = "Splash", showSystemUi = true)
@Composable
private fun BookshelfSplashScreenPreview() {
    BookshelfSplashScreen(onComplete = {})
}
