package uk.co.appoly.droid.compose.extensions

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Robolectric tests for the [Context.getActivity] tail-recursive unwrapping.
 */
@RunWith(AndroidJUnit4::class)
class GetActivityTest {

	@Test
	fun `getActivity returns the ComponentActivity itself`() {
		val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
		assertSame(activity, activity.getActivity())
	}

	@Test
	fun `getActivity unwraps through ContextWrapper layers`() {
		val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
		val doubleWrapped = ContextWrapper(ContextWrapper(activity))
		assertSame(activity, doubleWrapped.getActivity())
	}

	@Test
	fun `getActivity returns null for a non-activity context`() {
		val appContext = ApplicationProvider.getApplicationContext<Context>()
		assertNull(appContext.getActivity())
	}
}
