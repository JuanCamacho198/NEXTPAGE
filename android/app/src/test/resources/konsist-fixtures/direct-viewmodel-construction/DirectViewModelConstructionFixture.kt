// Committed negative fixture for DirectViewModelConstructionTest (S14 / Engram #2781 S1).
//
// This file is NOT compiled: it lives in test resources and is parsed by Konsist
// only (spotless excludes src/test/resources). It must keep exactly ONE direct
// `*ViewModel(...)` construction inside a non-Preview @Composable body — that is
// what makes the provider-confinement rule's bite reproducible from the repo.
// The other composables cover the rule's exemptions: *Factory(...) construction,
// a `remember*ViewModel(...)` provider call, and a @Preview composable.
package konsist.fixtures

@Composable
fun DirectViewModelConstructionFixture(repository: Repository) {
    val viewModel = remember { DictionaryViewModel(repository) }
    FixtureHost(viewModel)
}

@Composable
fun ExemptViewModelFactoryFixture(repository: Repository) {
    val factory = DictionaryViewModelFactory(repository)
    FixtureHost(rememberViewModel(factory))
}

@Composable
fun ExemptProviderCallFixture(repository: Repository) {
    FixtureHost(rememberDictionaryViewModel(repository))
}

@Preview
@Composable
private fun ExemptPreviewFixture() {
    FixtureHost(remember { PreviewOnlyViewModel() })
}

@Composable
private fun FixtureHost(viewModel: Any) = Unit
