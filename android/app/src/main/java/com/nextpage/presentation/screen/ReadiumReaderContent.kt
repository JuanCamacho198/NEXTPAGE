package com.nextpage.presentation.screen

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.presentation.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Readium-powered EPUB reader content.
 *
 * Hosts Readium's [EpubNavigatorFragment] inside a [FragmentContainerView]
 * via [AndroidView].  Creates an [EpubNavigatorFactory] from the
 * [Publication] and uses its [EpubNavigatorFactory.createFragmentFactory]
 * to set up the fragment so it can render EPUB content natively.
 *
 * Phase 1 — basic rendering only.  Selection wiring and decoration API
 * for highlights will be added in later phases.
 */
@Composable
fun ReadiumReaderContent(
    publication: Publication,
    navigatorConfig: EpubNavigatorFactory.Configuration,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as FragmentActivity
    val fragmentManager = remember { context.supportFragmentManager }
    val containerId = remember { View.generateViewId() }

    // Create the EpubNavigatorFactory — Readium 3.x pattern
    val navigatorFactory = remember(publication, navigatorConfig) {
        EpubNavigatorFactory(publication, navigatorConfig)
    }

    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var containerReady by remember { mutableStateOf(false) }

    // Commit EpubNavigatorFragment once the FragmentContainerView is laid out
    LaunchedEffect(containerReady) {
        if (!containerReady) return@LaunchedEffect
        val tag = "ReadiumNavigator"
        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            // Start at the first reading-order resource
            val initialLocator = publication.readingOrder.firstOrNull()?.let {
                publication.locatorFromLink(it)
            }
            // Set the FragmentFactory so EpubNavigatorFragment can be
            // instantiated with the correct constructor parameters.
            val factory = navigatorFactory.createFragmentFactory(
                initialLocator = initialLocator!!
            )
            fragmentManager.fragmentFactory = factory
            fragmentManager.commit {
                add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag)
            }
        }
        // Give the fragment time to initialise its navigator
        delay(200)
        navigatorFragment = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment
    }

    // Wire the navigator's currentLocator flow → ViewModel
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        try {
            @Suppress("UNCHECKED_CAST")
            val locatorFlow = frag.currentLocator as StateFlow<Locator>
            locatorFlow.collect { locator ->
                viewModel.onReadiumLocatorChanged(locator)
            }
        } catch (_: Exception) {
            // Navigator interfaces may not be ready yet — will retry on
            // next recomposition when navigatorFragment is non-null
        }
    }

    // Clean up when this composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            val frag = navigatorFragment
            if (frag != null && frag.isAdded) {
                fragmentManager.commit { remove(frag) }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).also { it.id = containerId; containerReady = true }
        },
        modifier = modifier
    )
}

/**
 * Builds a [EpubNavigatorFactory.Configuration] from [ReaderSettings].
 *
 * Phase 1 — minimal mapping.  Only [pageMargins] is set via [EpubDefaults].
 * More properties (background, font-size, line-height, scroll mode) will
 * be mapped in later phases via UserProperties.
 */
fun buildNavigatorConfig(settings: ReaderSettings): EpubNavigatorFactory.Configuration {
    return EpubNavigatorFactory.Configuration(
        defaults = EpubDefaults(
            pageMargins = 1.4
        )
    )
}
