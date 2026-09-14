package com.nextpage

import androidx.test.core.app.ApplicationProvider
import io.sentry.Sentry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIX 4 regression: a Robolectric run instantiates the manifest Application,
 * whose `onCreate` used to call `SentryAndroid.init` against the developer's
 * real DSN and egress synthetic test failures to the production project
 * (NEXTPAGE-ANDROID-3, `device=robolectric`).
 *
 * The manifest disables Sentry's auto-init provider (`io.sentry.auto-init=false`),
 * so `Sentry.isEnabled()` can only become true through `NextPageApplication`.
 * This test pins that it stays disabled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = NextPageApplication::class, sdk = [34])
class NextPageApplicationSentryGuardTest {

    @Test
    fun `creating the Application does not initialize the real Sentry SDK`() {
        // Triggers NextPageApplication.onCreate().
        ApplicationProvider.getApplicationContext<NextPageApplication>()

        assertFalse(
            "Sentry must stay disabled while running unit tests",
            Sentry.isEnabled()
        )
    }
}
