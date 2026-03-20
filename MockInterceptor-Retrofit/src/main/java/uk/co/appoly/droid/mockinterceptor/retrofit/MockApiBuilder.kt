package uk.co.appoly.droid.mockinterceptor.retrofit

import uk.co.appoly.droid.mockinterceptor.MockRequestContext
import uk.co.appoly.droid.mockinterceptor.MockResponseBuilder
import uk.co.appoly.droid.mockinterceptor.MockRouteBuilder
import kotlin.reflect.KFunction

/**
 * Builder for defining mock handlers mapped to Retrofit API interface methods.
 *
 * Use [KFunction] references (e.g., `MyApi::getItems`) for compile-time safety —
 * if a Retrofit method is renamed, the reference will produce a compile error.
 *
 * @param T The Retrofit API interface type.
 */
class MockApiBuilder<T : Any> internal constructor() {
	internal val handlers = mutableMapOf<String, MockResponseBuilder.(MockRequestContext) -> Unit>()

	/**
	 * Register a mock handler for a Retrofit API method.
	 *
	 * ```kotlin
	 * mockApi(PhotoLabAPI::class) {
	 *     mock(PhotoLabAPI::getCategories) {
	 *         successBody(listOf(...))
	 *     }
	 *     mock(PhotoLabAPI::getProduct) { request ->
	 *         val id = request.pathParamInt("id")
	 *         successBody(products[id])
	 *     }
	 * }
	 * ```
	 *
	 * @param method A [KFunction] reference to the Retrofit interface method.
	 * @param handler The mock response handler, identical to the handlers used in
	 *   [MockRouteBuilder.get]/[MockRouteBuilder.post]/etc.
	 */
	fun mock(
		method: KFunction<*>,
		handler: MockResponseBuilder.(MockRequestContext) -> Unit,
	) {
		val name = method.name
		handlers[name] = handler
	}
}
