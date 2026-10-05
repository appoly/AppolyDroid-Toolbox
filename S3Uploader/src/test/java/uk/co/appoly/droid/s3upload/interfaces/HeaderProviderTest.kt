package uk.co.appoly.droid.s3upload.interfaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderProviderTest {

	private val api = "https://api.example.com/v1/upload/presign"
	private val elsewhere = "https://attacker.example.net/collect"
	private val bearer = mapOf("Authorization" to "Bearer t0k3n")

	@Test
	fun `a plain lambda provider sends the same headers for any url`() {
		val provider = HeaderProvider { bearer }

		assertEquals(bearer, provider.provideHeaders(api))
		assertEquals(bearer, provider.provideHeaders(elsewhere))
	}

	@Test
	fun `deprecated bearer still sends its token to every url`() {
		@Suppress("DEPRECATION")
		val provider = HeaderProvider.bearer { "t0k3n" }

		assertEquals(bearer, provider.provideHeaders(elsewhere))
	}

	@Test
	fun `bearerForHosts sends the token to an allowed host`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" }

		assertEquals(bearer, provider.provideHeaders(api))
	}

	@Test
	fun `bearerForHosts omits the token for any other host`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" }

		assertTrue(provider.provideHeaders(elsewhere).isEmpty())
	}

	@Test
	fun `bearerForHosts does not treat a subdomain or lookalike as allowed`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("example.com") }) { "t0k3n" }

		assertTrue(provider.provideHeaders("https://api.example.com/x").isEmpty())
		assertTrue(provider.provideHeaders("https://example.com.evil.net/x").isEmpty())
		assertTrue(provider.provideHeaders("https://evil.net/x?next=https://example.com").isEmpty())
	}

	@Test
	fun `bearerForHosts matches hosts case-insensitively`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("API.Example.com") }) { "t0k3n" }

		assertEquals(bearer, provider.provideHeaders("https://api.EXAMPLE.com/x"))
	}

	@Test
	fun `bearerForHosts treats an unparseable url as not allowed`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" }

		assertTrue(provider.provideHeaders("not a url").isEmpty())
		assertTrue(provider.provideHeaders("ftp://api.example.com/x").isEmpty())
	}

	@Test
	fun `bearerForHosts never sends the token over plain http by default`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" }

		assertTrue(provider.provideHeaders("http://api.example.com/v1/upload/presign").isEmpty())
	}

	@Test
	fun `bearerForHosts sends over http only when requireHttps is off`() {
		val provider = HeaderProvider.bearerForHosts(
			allowedHosts = { setOf("10.0.2.2") },
			requireHttps = false,
		) { "t0k3n" }

		assertEquals(bearer, provider.provideHeaders("http://10.0.2.2:8000/presign"))
		assertTrue(
			"requireHttps = false must not relax the host check",
			provider.provideHeaders("http://attacker.example.net/collect").isEmpty()
		)
	}

	@Test
	fun `restrictedToHosts rejects http for a custom header too`() {
		val provider = HeaderProvider.custom("User-Api-Token") { "key" }
			.restrictedToHosts { setOf("api.example.com") }

		assertTrue(provider.provideHeaders("http://api.example.com/x").isEmpty())
	}

	@Test
	fun `bearerForHosts sends nothing without a token`() {
		val provider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { null }

		assertTrue(provider.provideHeaders(api).isEmpty())
	}

	@Test
	fun `allowedHosts is evaluated on every request`() {
		var allowed = setOf("old.example.com")
		val provider = HeaderProvider.bearerForHosts({ allowed }) { "t0k3n" }

		assertTrue(provider.provideHeaders(api).isEmpty())
		allowed = setOf("api.example.com")
		assertEquals(bearer, provider.provideHeaders(api))
	}

	@Test
	fun `restrictedToHosts scopes a custom header`() {
		val provider = HeaderProvider.custom("User-Api-Token") { "key" }
			.restrictedToHosts { setOf("api.example.com") }

		assertEquals(mapOf("User-Api-Token" to "key"), provider.provideHeaders(api))
		assertTrue(provider.provideHeaders(elsewhere).isEmpty())
	}

	@Test
	fun `restrictedToHosts forwards the url to its delegate`() {
		val seen = mutableListOf<String>()
		val delegate = HeaderProvider { url ->
			seen += url
			bearer
		}

		val provider = delegate.restrictedToHosts { setOf("api.example.com") }

		assertEquals(bearer, provider.provideHeaders(api))
		assertEquals(listOf(api), seen)
	}
}
