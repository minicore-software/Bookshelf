package com.minicore.bookshelf

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instantiates [HiltTestApplication] instead of [BookshelfApp] for every instrumented test
 * (plan R13). Test-only (`androidTest`), wired via `testInstrumentationRunner` in
 * `app/build.gradle.kts`.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
