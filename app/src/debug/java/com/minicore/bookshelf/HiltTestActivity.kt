package com.minicore.bookshelf

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty, Hilt-enabled [ComponentActivity] for instrumented tests that need `hiltViewModel()`
 * to resolve without launching the real [MainActivity] (plan R13). Debug-only: never ships in a
 * release build.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
