package uk.co.appoly.droid.data.repo

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI tests (Robolectric) for the APIFlowState caching composables
 * ([rememberSuccessDataAsState], [rememberSuccessData], [rememberSuccessListAsState],
 * [rememberSuccessList] and their saveable variants). The defining behaviour is that the
 * last success value is retained across subsequent Loading/Error states.
 */
@RunWith(AndroidJUnit4::class)
class APIFlowStateComposablesTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `rememberSuccessDataAsState caches the last success across loading`() {
		var state by mutableStateOf<APIFlowState<String>>(APIFlowState.Loading)
		lateinit var cached: State<String?>
		composeRule.setContent { cached = state.rememberSuccessDataAsState() }

		composeRule.runOnIdle { assertNull(cached.value) }

		state = APIFlowState.Success("hello")
		composeRule.runOnIdle { assertEquals("hello", cached.value) }

		// A subsequent Loading keeps the cached value (no flicker).
		state = APIFlowState.Loading
		composeRule.runOnIdle { assertEquals("hello", cached.value) }

		// An Error also keeps the cached value.
		state = APIFlowState.Error(500, "boom")
		composeRule.runOnIdle { assertEquals("hello", cached.value) }
	}

	@Test
	fun `rememberSaveableSuccessDataAsState caches success`() {
		var state by mutableStateOf<APIFlowState<String>>(APIFlowState.Loading)
		lateinit var cached: State<String?>
		composeRule.setContent { cached = state.rememberSaveableSuccessDataAsState() }

		state = APIFlowState.Success("saved")
		composeRule.runOnIdle { assertEquals("saved", cached.value) }
	}

	@Test
	fun `rememberSuccessData returns the cached value`() {
		var state by mutableStateOf<APIFlowState<Int>>(APIFlowState.Loading)
		var value: Int? = -1
		composeRule.setContent { value = state.rememberSuccessData() }

		composeRule.runOnIdle { assertNull(value) }
		state = APIFlowState.Success(42)
		composeRule.runOnIdle { assertEquals(42, value) }
		state = APIFlowState.Loading
		composeRule.runOnIdle { assertEquals(42, value) }
	}

	@Test
	fun `rememberSuccessListAsState caches list success and defaults to empty`() {
		var state by mutableStateOf<APIFlowState<List<String>>>(APIFlowState.Loading)
		lateinit var cached: State<List<String>>
		composeRule.setContent { cached = state.rememberSuccessListAsState() }

		composeRule.runOnIdle { assertEquals(emptyList<String>(), cached.value) }
		state = APIFlowState.Success(listOf("a", "b"))
		composeRule.runOnIdle { assertEquals(listOf("a", "b"), cached.value) }
		state = APIFlowState.Loading
		composeRule.runOnIdle { assertEquals(listOf("a", "b"), cached.value) }
	}

	@Test
	fun `rememberSuccessList returns the cached list`() {
		var state by mutableStateOf<APIFlowState<List<Int>>>(APIFlowState.Success(listOf(1, 2)))
		var value: List<Int> = emptyList()
		composeRule.setContent { value = state.rememberSuccessList() }
		composeRule.runOnIdle { assertEquals(listOf(1, 2), value) }
	}
}
