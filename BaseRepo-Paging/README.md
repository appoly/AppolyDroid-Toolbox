# BaseRepo-Paging

An extension module for BaseRepo that adds Jetpack Paging 3 support for efficient data loading in RecyclerViews and Jetpack Compose.

## Features

- Seamless integration with BaseRepo and APIResult pattern
- Support for Jetpack Paging 3 library
- Standardized paging source implementation
- Thread-safe invalidation capabilities for refreshing data
- Support for jumping to specific pages
- Integration with both traditional RecyclerView and Jetpack Compose

## Installation

```gradle.kts
// Requires the base BaseRepo module
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.0.34")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:1.0.34")

// For Compose UI integration
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:1.0.34") // For LazyColumn
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.0.34") // For LazyGrid
```

## API Response Format

This module requires your paginated API responses to follow a specific nested structure as shown below:

```json
{
  "success": true,
  "message": "Data retrieved successfully",
  "data": {
    "data": [
      { "id": 1, "name": "Item 1" },
      { "id": 2, "name": "Item 2" }
    ],
    "current_page": 1,
    "last_page": 5,
    "per_page": 10,
    "from": 1,
    "to": 10,
    "total": 48
  }
}
```

## Usage

### Step 1: Create API Service Interface

First, create your API service interface that returns paginated responses:

```kotlin
interface LibraryAPI : BaseService.API {
    @POST("api/library/search")
    suspend fun searchLibrary(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
        @Body body: SearchRequestBody
    ): ApiResponse<GenericNestedPagedResponse<LibraryItem>>
}
```

### Step 2: Add Repository Method to Fetch Pages

In your repository, create a method that uses `doNestedPagedAPICall` to fetch a page:

```kotlin
class LibraryRepository : AppolyBaseRepo({ YourRetrofitClient }) {
    private val libraryService by lazyService<LibraryAPI>()

    // Function to fetch a single page
    suspend fun fetchLibraryPage(
        perPage: Int,
        page: Int,
        query: String,
        filters: Filters
    ): APIResult<PageData<LibraryItem>> = doNestedPagedAPICall("fetchLibraryPage") {
        libraryService.api.searchLibrary(
            perPage = perPage,
            page = page,
            body = SearchRequestBody(
                query = query,
                filters = filters
            )
        )
    }
}
```

### Step 3: Create a PagingSource Factory

Create a factory that will generate paging sources on demand:

```kotlin
class LibraryRepository : AppolyBaseRepo({ YourRetrofitClient }) {
    // ...existing code...

    fun getLibraryPagingSourceFactory(
        query: String,
        filters: Filters,
        pageSize: Int = 20,
        jumpingSupported: Boolean = true,
        jumpPageThreshold: Float = 2f
    ): GenericInvalidatingPagingSourceFactory<LibraryItem> =
        GenericInvalidatingPagingSourceFactory(
            pageSize = pageSize,
            jumpingSupported = jumpingSupported,
            jumpPageThreshold = jumpPageThreshold
        ) { perPage, page ->
            fetchLibraryPage(perPage, page, query, filters)
        }
}
```

### Step 4: Use in a ViewModel

```kotlin
class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private var currentQuery = ""
    private var currentFilters = Filters()

    // Create factory and pager
    private val pagingSourceFactory =
        libraryRepository.getLibraryPagingSourceFactory(currentQuery, currentFilters)

    // Create flow to collect in UI
    val items = pagingSourceFactory.getPager()
        .flow
        .cachedIn(viewModelScope)

    // Function to refresh data
    fun refresh() {
        pagingSourceFactory.invalidate()
    }

    // Function to update search parameters
    fun search(query: String, filters: Filters) {
        currentQuery = query
        currentFilters = filters
        pagingSourceFactory.invalidate()
    }
}
```

### Step 5: Collect in UI

#### In Jetpack Compose

```kotlin
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val items = viewModel.items.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = items.itemCount,
            key = { index -> items[index]?.id ?: index }
        ) { index ->
            val item = items[index]
            if (item != null) {
                LibraryItemCard(item = item)
            } else {
                // Placeholder for loading state
                LoadingItemPlaceholder()
            }
        }

        // Handle different loading states
        when (items.loadState.refresh) {
            is LoadState.Loading -> item { FullScreenLoader() }
            is LoadState.Error -> item { ErrorView(onRetry = { items.retry() }) }
            else -> Unit
        }

        // Append loading indicator
        when (items.loadState.append) {
            is LoadState.Loading -> item { LoadingIndicator() }
            is LoadState.Error -> item { RetryButton(onRetry = { items.retry() }) }
            else -> Unit
        }
    }
}
```

#### In Traditional RecyclerView (with DataBinding)

```kotlin
class LibraryFragment : Fragment() {
    private val viewModel: LibraryViewModel by viewModels()
    private val adapter = LibraryAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view)

        binding.recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
            header = LoadingStateAdapter { adapter.retry() },
            footer = LoadingStateAdapter { adapter.retry() }
        )

        // Collect paging data
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        // Monitor load state
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadState ->
                binding.swipeRefresh.isRefreshing =
                    loadState.refresh is LoadState.Loading

                // Show error if initial load fails
                if (loadState.refresh is LoadState.Error) {
                    showError((loadState.refresh as LoadState.Error).error.message)
                }
            }
        }
    }
}
```

## Converting APIFlowState to PagingData

If you have an existing Flow of `APIFlowState<List<T>>`, you can convert it to a PagingData flow:

```kotlin
class ItemsViewModel(private val repository: ItemsRepository) : ViewModel() {
    // Convert a standard flow to a paging flow
    val itemsPaging = repository.getItemsFlow()
        .mapToPagingData()
        .cachedIn(viewModelScope)
}
```

## Advanced Usage

### Controlling Jump Threshold

The `jumpPageThreshold` parameter determines when Paging will "jump" to a specific page instead of scrolling:

```kotlin
// Create a factory that allows jumping 5 pages when fast scrolling
pagingSourceFactory = GenericInvalidatingPagingSourceFactory(
    pageSize = 20,
    jumpingSupported = true,
    jumpPageThreshold = 5f
) { perPage, page ->
    fetchPage(perPage, page)
}
```

### Testing

The factory provides a `pagingSources()` method for testing purposes:

```kotlin
@Test
fun testInvalidation() {
    val factory = GenericInvalidatingPagingSourceFactory(
        pageSize = 10,
        fetchPageCall = { _, _ -> mockSuccessResult() }
    )

    // Create a paging source
    val pagingSource = factory.invoke()

    // Verify paging source is tracked
    assertEquals(1, factory.pagingSources().size)

    // Invalidate and verify tracking is reset
    factory.invalidate()
    assertEquals(0, factory.pagingSources().size)
}
```

## Key Components

### GenericNestedPagedResponse

Models the nested paged response from your API.

### PageData

A flattened, non-nullable representation of page data with computed properties like `itemsBefore` and `itemsAfter`.

### GenericPagingSource

Implements Android's `PagingSource` for seamless integration with Paging 3.

### GenericInvalidatingPagingSourceFactory

Thread-safe factory that creates and tracks paging sources, allowing for invalidation.

## Dependencies

- [BaseRepo](../BaseRepo/README.md) - Core repository pattern implementation
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) - Android paging library

