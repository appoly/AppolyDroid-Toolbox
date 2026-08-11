package uk.co.appoly.droid.nav3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [Nav3RetentionScope]: clear empties the store and runs [ViewModel.onCleared]
 * on held ViewModels; the scope stays reusable after clear; [Nav3RetentionScope.onCleared]
 * clears the inner store when the scope itself is destroyed with its parent owner.
 */
@RunWith(AndroidJUnit4::class)
class Nav3RetentionScopeTest {

	@Test
	fun `clear empties the store and runs onCleared on held ViewModels`() {
		val scope = Nav3RetentionScope()
		val first = ViewModelProvider(scope)[ProbeViewModel::class.java]
		assertFalse(first.cleared)

		scope.clear()

		assertTrue(first.cleared)
		// Store is empty: a new resolve must create a fresh instance, not return the cleared one.
		val second = ViewModelProvider(scope)[ProbeViewModel::class.java]
		assertNotSame(first, second)
		assertFalse(second.cleared)
	}

	@Test
	fun `scope survives clear and is reusable afterwards`() {
		val scope = Nav3RetentionScope()
		val firstSession = ViewModelProvider(scope)[ProbeViewModel::class.java]
		firstSession.markUsed()

		// Sign-out: wipe retained entry stores. Scope itself stays alive (Activity-scoped).
		scope.clear()
		assertTrue(firstSession.cleared)

		// Second sign-in: same scope, empty store — fresh ViewModels, not a dead store.
		val secondSession = ViewModelProvider(scope)[ProbeViewModel::class.java]
		assertNotSame(firstSession, secondSession)
		assertFalse(secondSession.cleared)
		secondSession.markUsed()
		assertTrue(secondSession.used)

		// A second clear is also fine (idempotent empty + another session).
		scope.clear()
		assertTrue(secondSession.cleared)
		val thirdSession = ViewModelProvider(scope)[ProbeViewModel::class.java]
		assertNotSame(secondSession, thirdSession)
		assertFalse(thirdSession.cleared)
	}

	@Test
	fun `onCleared on the scope itself clears the inner store`() {
		val parentStore = ViewModelStore()
		val parentOwner = object : ViewModelStoreOwner {
			override val viewModelStore: ViewModelStore = parentStore
		}
		val scope = ViewModelProvider(parentOwner)[Nav3RetentionScope::class.java]
		val held = ViewModelProvider(scope)[ProbeViewModel::class.java]
		assertFalse(held.cleared)

		// Parent owner destroyed permanently (not config change) → scope.onCleared().
		parentStore.clear()

		assertTrue(held.cleared)
	}

	/** Minimal ViewModel that records whether [onCleared] ran. */
	class ProbeViewModel : ViewModel() {
		var cleared: Boolean = false
			private set
		var used: Boolean = false
			private set

		fun markUsed() {
			used = true
		}

		override fun onCleared() {
			cleared = true
			super.onCleared()
		}
	}
}
