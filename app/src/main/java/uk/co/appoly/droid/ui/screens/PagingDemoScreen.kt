package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentedControl
import uk.co.appoly.droid.ui.viewmodels.PagingDemoViewModel
import uk.co.appoly.droid.ui.viewmodels.Product
import uk.co.appoly.droid.util.paging.lazyPagingItemsStates

private const val VIEW_LIST = "List"
private const val VIEW_GRID = "Grid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagingDemoScreen(navController: NavController) {
	val viewModel: PagingDemoViewModel = viewModel()
	val products: LazyPagingItems<Product> = viewModel.productsFlow.collectAsLazyPagingItems()
	var viewMode by remember { mutableStateOf(VIEW_LIST) }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Paging Demo") },
				navigationIcon = {
					IconButton(onClick = { navController.navigateUp() }) {
						Text("←")
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
		) {
			// Info card
			Card(
				modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Paging Extensions Demo",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "The same paging flow rendered with both LazyListPagingExtensions and " +
							"LazyGridPagingExtensions — switch between them below.",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = "• Page 3 will simulate an error\n• Total of 50 items across 5 pages\n• 10 items per page",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			// List / Grid toggle, courtesy of the SegmentedControl module.
			SegmentedControl(
				segments = listOf(VIEW_LIST, VIEW_GRID),
				selectedSegment = viewMode,
				onSegmentSelected = { viewMode = it },
				modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
			)

			when (viewMode) {
				VIEW_GRID -> LazyVerticalGrid(
					columns = GridCells.Fixed(2),
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					lazyPagingItemsStates(
						lazyPagingItems = products,
						emptyText = { "No products found" },
						errorText = { error -> "Failed to load products: ${error.error.message ?: "Unknown error"}" },
						itemKey = { product -> product.id },
						itemContent = { product ->
							ProductCard(product = product, modifier = Modifier.animateItem())
						}
					)
				}

				else -> LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					lazyPagingItemsStates(
						lazyPagingItems = products,
						emptyText = { "No products found" },
						errorText = { error -> "Failed to load products: ${error.error.message ?: "Unknown error"}" },
						itemKey = { product -> product.id },
						itemContent = { product ->
							ProductCard(product = product, modifier = Modifier.animateItem())
						}
					)
				}
			}
		}
	}
}

@Composable
private fun ProductCard(product: Product, modifier: Modifier = Modifier) {
	Card(modifier = modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = product.name,
				style = MaterialTheme.typography.titleMedium
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				text = product.category,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				text = "$${String.format("%.2f", product.price)}",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.secondary
			)
		}
	}
}
