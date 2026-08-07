package uk.co.appoly.droid.nav3

/**
 * Voyager-style "screen that can receive a result from a pushed child".
 *
 * Prefer this over the Nav3 1.2 alpha [androidx.navigation3.runtime.result.ResultEventBus]
 * until that API stabilises — event vs state variants differ on process-death behaviour.
 *
 * ### Example
 *
 * ```kotlin
 * @Serializable
 * data object ListScreen : Nav3Screen, Nav3ResultReceiver {
 *     // non-serializable receiver state must not be stored vals if you also @Serializable
 *     // the screen — hold pending results in a ViewModel instead when the key is persisted.
 *     override fun onResult(result: Any?) { /* refresh list, apply selection, … */ }
 *
 *     @Composable
 *     override fun Content() {
 *         val navigator = LocalNav3Navigator.current
 *         Button(onClick = { navigator?.push(PickerScreen) }) { Text("Pick") }
 *     }
 * }
 *
 * @Serializable
 * data object PickerScreen : Nav3Screen {
 *     @Composable
 *     override fun Content() {
 *         val navigator = LocalNav3Navigator.current
 *         Button(onClick = { navigator?.popWithResult("chosen-id") }) { Text("Done") }
 *     }
 * }
 * ```
 *
 * **Serializable screens:** if the [Nav3Screen] is `@Serializable` and rides
 * `rememberNavBackStack`, do **not** store result callbacks or mutable UI state as body
 * `val`s on the key — use a screen-scoped ViewModel (or a non-persisted side channel) as the
 * [onResult] target, same rule as `metadata`.
 */
interface Nav3ResultReceiver {
	/**
	 * Called when a child screen pops with a result via [popWithResult] / [popUntilWithResult].
	 *
	 * The payload is untyped on purpose (Voyager-style): callers cast/check inside the
	 * implementation. A method generic (`fun <T> onResult(result: T)`) would not add compile-time
	 * safety here — the type is chosen by the child at the pop site.
	 *
	 * @param result the value the child supplied; cast/check type inside the implementation.
	 */
	fun onResult(result: Any?)
}

/**
 * Pops the top screen and delivers [result] to the **previous** screen when it implements
 * [Nav3ResultReceiver] (Voyager-style `popWithResult`).
 *
 * If there is no previous screen, or it is not a [Nav3ResultReceiver], the result is dropped
 * and the pop still proceeds.
 *
 * @see Nav3ResultReceiver
 * @see popUntilWithResult
 */
fun Nav3Navigator.popWithResult(result: Any?) {
	val receiver = previousItem as? Nav3ResultReceiver
	receiver?.onResult(result)
	pop()
}

/**
 * Pops until [predicate] matches (see [Nav3Navigator.popUntil]), then delivers [result] to
 * the **new top** when it implements [Nav3ResultReceiver].
 *
 * Delivery is **gated on a successful match**: if [Nav3Navigator.popUntil] returns `false`
 * (nothing matched), the stack is left alone **and** [result] is not delivered — even when the
 * current top happens to be a [Nav3ResultReceiver].
 *
 * Useful for multi-step flows that should dismiss intermediate screens and hand a value back
 * to the screen that started the flow:
 *
 * ```kotlin
 * navigator.popUntilWithResult(result = selectedId) { it is ListScreen }
 * ```
 *
 * @param result value delivered to the new top after a successful pop.
 * @param inclusive when `true`, also removes the matching screen (result goes to whatever is
 *   under it).
 * @param predicate last matching screen is left on top (unless [inclusive]).
 * @return `true` if the predicate matched and a result was (attempted to be) delivered;
 *   `false` if nothing matched (stack and receivers unchanged).
 */
fun Nav3Navigator.popUntilWithResult(
	result: Any?,
	inclusive: Boolean = false,
	predicate: (Nav3Screen) -> Boolean,
): Boolean {
	if (!popUntil(inclusive = inclusive, predicate = predicate)) return false
	(lastItem as? Nav3ResultReceiver)?.onResult(result)
	return true
}
