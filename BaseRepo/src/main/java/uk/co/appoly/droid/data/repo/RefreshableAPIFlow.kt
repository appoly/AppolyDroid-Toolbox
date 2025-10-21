package uk.co.appoly.droid.data.repo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.appoly.droid.BaseRepoLog
import uk.co.appoly.droid.data.remote.model.APIResult
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A wrapper around a [Flow] that fetches data from an API and emits [APIFlowState] of [T] for
 * loading, success or error states, and provides a method to [refresh] the api call.
 *
 * @param T The type of data returned by the API
 * @param initialValue Optional initial value to use before the API call completes
 * @param initialRefresh Whether to automatically refresh the data when created
 * @param apiCall The suspend function that fetches the data from the API
 * @param scope The [CoroutineScope] to use for API calls
 */
@OptIn(ExperimentalAtomicApi::class)
class RefreshableAPIFlow<T : Any>(
	initialValue: T?,
	initialRefresh: Boolean = initialValue == null,
	private val apiCall: suspend () -> APIResult<T>,
	private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : Flow<APIFlowState<T>> {
	/**
	 * Constructor that creates a [RefreshableAPIFlow] with an optional initial value.
	 *
	 * @param initialValue Optional initial value to use before the API call completes
	 * @param apiCall The suspend function that fetches the data from the API
	 * @param scope The [CoroutineScope] to use for API calls
	 */
	constructor(
		initialValue: T?,
		apiCall: suspend () -> APIResult<T>,
		scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
	) : this(
		initialValue = initialValue,
		initialRefresh = initialValue == null,
		apiCall = apiCall,
		scope = scope
	)

	/**
	 * Constructor that creates a [RefreshableAPIFlow] with no initial value.
	 * Will automatically refresh on creation.
	 *
	 * @param apiCall The suspend function that fetches the data from the API
	 * @param scope The [CoroutineScope] to use for API calls
	 */
	constructor(
		apiCall: suspend () -> APIResult<T>,
		scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
	) : this(
		initialValue = null,
		initialRefresh = true,
		apiCall = apiCall,
		scope = scope
	)

	private val isRefreshing = AtomicBoolean(false)

	@Volatile
	private var refreshCompletion = CompletableDeferred<Unit>().apply { complete(Unit) }
	private val internalFlow = MutableStateFlow<APIFlowState<T>>(APIFlowState.Loading)

	/**
	 * Converts this flow into a [SharedFlow] that can be shared among multiple collectors.
	 *
	 * @return A [SharedFlow] that emits the same values as this flow
	 */
	fun asSharedFlow(): SharedFlow<APIFlowState<T>> = internalFlow.asSharedFlow()

	/**
	 * Refresh the API call and emit the new state.
	 *
	 * @param onComplete Optional callback to be invoked when the refresh operation completes,
	 * will be invoked on the Main thread.
	 */
	fun refresh(
		onComplete: (() -> Unit)? = null
	) = refresh(simulatedError = null, onComplete = onComplete)

	/**
	 * Refresh the API call and emit the new state.
	 *
	 * @param simulatedError Optional simulated error to emit instead of making an API call,
	 * this is useful for testing purposes.
	 * @param onComplete Optional callback to be invoked when the refresh operation completes,
	 * will be invoked on the Main thread.
	 */
	fun refresh(
		simulatedError: APIFlowState.Error? = null,
		onComplete: (() -> Unit)? = null
	) {
		if (isRefreshing.compareAndSet(expectedValue = false, newValue = true)) {
			// Create a new CompletableDeferred for this refresh operation
			refreshCompletion = CompletableDeferred()
			scope.launch {
				try {
					internalFlow.emit(APIFlowState.Loading)
					if (simulatedError != null) {
						BaseRepoLog.v(this@RefreshableAPIFlow, "Simulated error: ${simulatedError.message}")
						delay(350) // Simulate network delay
						internalFlow.emit(simulatedError)
					} else {
						internalFlow.emit(apiCall().asApiFlowState())
					}
				} catch (e: Exception) {
					BaseRepoLog.w(this@RefreshableAPIFlow, "Exception in refresh", e)
					internalFlow.emit(APIFlowState.Error(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, e.message ?: "Unknown error"))
				} finally {
					isRefreshing.store(false)
					refreshCompletion.complete(Unit)
					onComplete?.let { onComplete ->
						withContext(Dispatchers.Main) { onComplete() }
					}
				}
			}
		} else {
			// Refresh is already running
			val currentCompletion = refreshCompletion
			if (onComplete != null) {
				scope.launch {
					currentCompletion.await() // Wait for current refresh to complete
					withContext(Dispatchers.Main) { onComplete() }
				}
			}
		}
	}

	/**
	 * Implements the [Flow.collect] method to collect values from this flow.
	 *
	 * @param collector The [FlowCollector] to collect values from this flow
	 */
	override suspend fun collect(collector: FlowCollector<APIFlowState<T>>) = internalFlow.collect(collector)

	/**
	 * Manually updates the flow with a success state containing the provided value.
	 *
	 * @param value The value to emit as a success state
	 */
	suspend fun manualUpdate(value: T) {
		internalFlow.emit(APIFlowState.Success(value))
	}

	/**
	 * Updates the current state using the provided transformation function.
	 *
	 * @param function A function that transforms the current state into a new state
	 */
	fun updateState(function: (APIFlowState<T>?) -> APIFlowState<T>) =
		internalFlow.update(function = function)

	/**
	 * Updates the current data value using the provided transformation function.
	 * The current state must be a success state, or the transformation will be
	 * applied to null.
	 *
	 * @param function A function that transforms the current data value into a new value
	 */
	fun update(function: (T?) -> T) =
		internalFlow.update {
			APIFlowState.Success(function(it.successData()))
		}

	init {
		scope.launch {
			if (initialValue != null) {
				BaseRepoLog.v(this@RefreshableAPIFlow, "Initial value provided, emitting success state")
				internalFlow.emit(APIFlowState.Success(initialValue))
			}
			if (initialRefresh) {
				BaseRepoLog.v(this@RefreshableAPIFlow, "Initial refresh requested")
				refresh()
			}
		}
	}
}

/**
 * Creates a [StateFlow] from this [RefreshableAPIFlow] that can be shared among multiple collectors.
 *
 * @param scope The [CoroutineScope] in which sharing is to be started
 * @param started The strategy that controls when sharing is started and stopped
 * @param initialValue The initial value to emit before any value from the source flow is emitted
 * @return A [StateFlow] that emits the same values as this flow
 */
fun <T : Any> RefreshableAPIFlow<T>.stateIn(
	scope: CoroutineScope,
	started: SharingStarted = SharingStarted.WhileSubscribed(5000),
	initialValue: APIFlowState<T> = APIFlowState.Loading
) = this.stateIn(scope, started, initialValue)

/**
 * Collects values from this flow and represents its latest value as [State] in a composable.
 *
 * @return A [State] object that represents the latest value from this flow
 */
@Composable
fun <T : Any> RefreshableAPIFlow<T>.collectAsState(): State<APIFlowState<T>> =
	this.collectAsState(APIFlowState.Loading)
