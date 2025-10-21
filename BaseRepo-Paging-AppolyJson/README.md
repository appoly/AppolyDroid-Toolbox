# BaseRepo-Paging-AppolyJson

An extension module for BaseRepo-Paging that provides support for Appoly's nested JSON paging response structure. This module implements the specific JSON parsing and paging handling for APIs that
follow Appoly's paging format.

## Features

- Implementation of Appoly's nested JSON paging response format
- Automatic parsing of paginated API responses
- Integration with Jetpack Paging 3
- Support for RecyclerView and Jetpack Compose
- Thread-safe data invalidation and refresh

## Installation

```gradle.kts
// Requires the base modules
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.0.38_rc03")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:1.0.38_rc03")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging-AppolyJson:1.0.38_rc03")

// For Compose UI integration
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:1.0.38_rc03") // For LazyColumn
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.0.38_rc03") // For LazyGrid
```

## API Response Format

This module requires your paginated API responses to follow Appoly's specific nested structure as shown below:

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
        perPage: Int,
        query: String,
        filters: Filters
    ): InvalidatingPagingSourceFactory<Int, LibraryItem> {
        return GenericInvalidatingPagingSourceFactory { page ->
            fetchLibraryPage(perPage, page, query, filters)
        }
    }
}
```

### Step 4: Use in ViewModel

In your ViewModel, create a Pager and expose it as a Flow:

```kotlin
class LibraryViewModel : ViewModel() {
    private val repository = LibraryRepository()

    val libraryPager = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = {
            repository.getLibraryPagingSourceFactory(
                perPage = 20,
                query = _query.value,
                filters = _filters.value
            ).create()
        }
    ).flow.cachedIn(viewModelScope)
}
```

### Step 5: Display in Compose

Use the LazyPagingItems in your Compose UI:

```kotlin
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val lazyPagingItems = viewModel.libraryPager.collectAsLazyPagingItems()

    LazyColumn {
        items(lazyPagingItems) { item ->
            // Display your item
        }
    }
}
```
