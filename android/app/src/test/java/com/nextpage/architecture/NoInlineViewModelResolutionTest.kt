package com.nextpage.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Konsist rule (SDD android-stack-modernization S2, R9 / SC9.1-9.2).
 *
 * ViewModel resolution in the navigation layer must be single-sourced in
 * ViewModelProviders.kt; the feature NavGraphBuilders and the host must never
 * call `viewModel()` or `hiltViewModel()` themselves — the convention stated in
 * ViewModelProviders.kt and in every `*NavGraph.kt` KDoc.
 *
 * Scope note: the enforced boundary is the navigation layer, where the
 * single-sourced host-provider pattern lives. Screen-level stateful owners
 * (PerformanceScreen, BookDetailScreen, EditBookMetadataScreen,
 * AddonManagementRoute) still resolve their own ViewModels; a project-wide rule
 * would require hoisting those four features through their NavGraphs and is
 * tracked separately (outside slice S2's boundary).
 */
class NoInlineViewModelResolutionTest {
    private val navigationLayerMarker = "/presentation/navigation/"
    private val designatedProviderFile = "ViewModelProviders.kt"

    /** Matches `viewModel(` / `viewModel<T>(` and the `hiltViewModel` variants
     * (word-boundary anchored, so the `hiltViewModel` alternative is not
     * double-counted as `viewModel`). */
    private val inlineResolution = Regex("""\b(?:viewModel|hiltViewModel)\b\s*[<(]""")

    private val blockComment = Regex("""/\*[\s\S]*?\*/""")
    private val lineComment = Regex("""//[^\n]*""")

    @Test
    fun `navigation layer resolves ViewModels only through the designated provider`() {
        val navigationFiles =
            Konsist
                .scopeFromProduction()
                .files
                .filter { it.path.normalizedPath().contains(navigationLayerMarker) }

        assertTrue(
            "Konsist found no production navigation-layer files; the production scope could not be resolved",
            navigationFiles.any { it.path.normalizedPath().endsWith("NextPageNavHost.kt") } &&
                navigationFiles.any { it.path.normalizedPath().endsWith(designatedProviderFile) },
        )

        val offenders =
            navigationFiles
                .filterNot { it.path.normalizedPath().endsWith(designatedProviderFile) }
                .filter { file -> inlineResolution.containsMatchIn(file.text.withoutComments()) }
                .map { it.path.normalizedPath() }

        if (offenders.isNotEmpty()) {
            fail(
                "ViewModel resolution must be confined to $designatedProviderFile; " +
                    "inline viewModel()/hiltViewModel() found in:\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
    }

    private fun String.normalizedPath(): String = replace('\\', '/')

    private fun String.withoutComments(): String = replace(blockComment, " ").replace(lineComment, " ")
}
