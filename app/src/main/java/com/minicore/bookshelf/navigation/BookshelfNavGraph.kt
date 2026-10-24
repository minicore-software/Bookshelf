package com.minicore.bookshelf.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.minicore.bookshelf.R

@Composable
fun BookshelfNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BookshelfDestinations.Home.route
    ) {
        composable(BookshelfDestinations.Home.route) {
            PlaceholderHomeScreen()
        }
    }
}

@Composable
private fun PlaceholderHomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(R.string.app_name))
    }
}
