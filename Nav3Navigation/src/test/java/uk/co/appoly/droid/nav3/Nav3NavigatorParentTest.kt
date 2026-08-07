package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Nav3Navigator.parent] chain and [root] helper.
 */
class Nav3NavigatorParentTest {

	@Test
	fun `BackStackNav3Navigator parent defaults to null`() {
		val nav = BackStackNav3Navigator(NavBackStack(HomeScreen))
		assertNull(nav.parent)
		assertSame(nav, nav.root())
	}

	@Test
	fun `parent chain and root walk to outermost`() {
		val rootStack = NavBackStack<NavKey>(HomeScreen)
		val root = BackStackNav3Navigator(rootStack)
		val nestedStack = NavBackStack<NavKey>(ListScreen)
		val nested = BackStackNav3Navigator(nestedStack, parent = root)
		val deepStack = NavBackStack<NavKey>(DetailScreen(1))
		val deep = BackStackNav3Navigator(deepStack, parent = nested)

		assertSame(root, nested.parent)
		assertSame(nested, deep.parent)
		assertSame(root, deep.root())
		assertSame(root, nested.root())
		assertSame(root, root.root())
	}

	@Test
	fun `parent pop mutates the outer stack only`() {
		val rootStack = NavBackStack<NavKey>(HomeScreen, ListScreen)
		val root = BackStackNav3Navigator(rootStack)
		val childStack = NavBackStack<NavKey>(DetailScreen(1))
		val child = BackStackNav3Navigator(childStack, parent = root)

		child.push(SettingsScreen)
		assertEquals(2, childStack.size)

		child.parent!!.pop()

		assertEquals(listOf(HomeScreen), rootStack.toList())
		assertEquals(listOf(DetailScreen(1), SettingsScreen), childStack.toList())
	}

	@Test
	fun `TabsNav3Navigator accepts parent`() {
		val root = BackStackNav3Navigator(NavBackStack(HomeScreen))
		val tabs = TabsNav3Navigator(listOf(HomeScreen, ListScreen), parent = root)

		assertSame(root, tabs.parent)
		assertSame(root, tabs.root())
	}

	@Test
	fun `root replaceAll on parent clears outer stack`() {
		val rootStack = NavBackStack<NavKey>(HomeScreen, ListScreen, DetailScreen(1))
		val root = BackStackNav3Navigator(rootStack)
		val child = BackStackNav3Navigator(NavBackStack(SettingsScreen), parent = root)

		child.root().replaceAll(HomeScreen)

		assertEquals(listOf(HomeScreen), rootStack.toList())
		assertTrue(child.parent === root)
	}
}
