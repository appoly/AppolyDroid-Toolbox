---
type: "query"
date: "2026-06-09T09:45:25.887110+00:00"
question: "Are LazyGridPagingExtensions and LazyListPagingExtensions duplicated code that should be DRYed up?"
contributor: "graphify"
source_nodes: ["LazyGridScope.lazyPagingItemsStates", "LazyListScope.lazyPagingItemsStates", "PagingExtensions module", "PagingErrorType"]
---

# Q: Are LazyGridPagingExtensions and LazyListPagingExtensions duplicated code that should be DRYed up?

## Answer

Unavoidable mirroring, NOT lazy copy-paste — leave it. The only differences between mirror-pair files are (1) the receiver type LazyGridScope vs LazyListScope, which have NO shared supertype in Jetpack Compose so a single extension cannot target both, and (2) a grid-only 'span' parameter (grid items span columns, list items can't). The actual state-decision logic (PagingErrorType, LoadState.isLoading/isError extensions, LocalLoadingState/ErrorState/EmptyState composables) is ALREADY hoisted into the shared PagingExtensions module, which both modules depend on. What remains duplicated is only the thin DSL-adapter layer, which is irreducible without KSP codegen — not worth the build complexity for ~300 lines of stable adapter code.

## Source Nodes

- LazyGridScope.lazyPagingItemsStates
- LazyListScope.lazyPagingItemsStates
- PagingExtensions module
- PagingErrorType