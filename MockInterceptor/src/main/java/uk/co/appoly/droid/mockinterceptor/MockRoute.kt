package uk.co.appoly.droid.mockinterceptor

internal data class MockRoute(
	val method: String,
	val pathPattern: String,
	val pathSegments: List<String>,
	val delay: Long?,
	val handler: MockResponseBuilder.(MockRequestContext) -> Unit,
)
