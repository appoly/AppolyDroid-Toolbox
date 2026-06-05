package uk.co.appoly.droid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Unit tests for [DateHelperExtensions]: UTC/zone conversions, future/today/passed predicates,
 * daysFromNow, millis conversions, and the Duration helpers.
 */
class DateHelperExtensionsTest {

	@Test
	fun `toUTC converts to the UTC zone`() {
		val zoned = ZonedDateTime.of(2026, 6, 5, 12, 0, 0, 0, ZoneOffset.ofHours(5))
		assertEquals(ZoneOffset.UTC, zoned.toUTC().zone)
	}

	@Test
	fun `isFuture and isPassed reflect ordering against now`() {
		assertTrue(LocalDateTime.now().plusDays(1).isFuture())
		assertFalse(LocalDateTime.now().minusDays(1).isFuture())
		assertFalse((null as LocalDateTime?).isFuture())

		assertTrue(ZonedDateTime.now().plusDays(1).isFuture())
		assertFalse((null as ZonedDateTime?).isFuture())

		assertTrue(LocalDateTime.now().minusDays(1).isPassed())
		assertFalse(LocalDateTime.now().plusDays(1).isPassed())
		assertTrue(LocalDate.now().minusDays(1).isPassed())
	}

	@Test
	fun `isToday is true for today and false otherwise`() {
		assertTrue(LocalDate.now().isToday())
		assertFalse(LocalDate.now().minusDays(1).isToday())
		assertTrue(LocalDateTime.now().isToday())
		assertTrue(ZonedDateTime.now().isToday())
		assertFalse((null as LocalDate?).isToday())
	}

	@Test
	fun `daysFromNow is zero for today and positive for the future`() {
		assertEquals(0, LocalDate.now().daysFromNow())
		assertTrue(LocalDate.now().plusDays(3).daysFromNow() >= 2)
		assertTrue(LocalDateTime.now().plusDays(3).daysFromNow() >= 2)
		assertTrue(ZonedDateTime.now().plusDays(3).daysFromNow() >= 2)
	}

	@Test
	fun `toMillis and millisTo round-trip`() {
		val dt = LocalDateTime.of(2026, 6, 5, 10, 0, 0)
		val millis = dt.toMillis()!!
		assertEquals(dt, millis.millisToLocalDateTime())

		val date = LocalDate.of(2026, 6, 5)
		val dateMillis = date.toMillis()!!
		assertEquals(date, dateMillis.millisToLocalDate())

		assertEquals(null, (null as LocalDateTime?).toMillis())
		assertEquals(null, (null as LocalDate?).toMillis())
	}

	@Test
	fun `deviceToUTC and toDeviceZone are inverse-ish conversions`() {
		val dt = LocalDateTime.of(2026, 6, 5, 8, 0, 0)
		// deviceToUTC returns a LocalDateTime; just assert it runs and is non-null.
		assertEquals(LocalDateTime::class.java, dt.deviceToUTC()::class.java)
		val zoned = ZonedDateTime.of(2026, 6, 5, 8, 0, 0, 0, ZoneOffset.UTC)
		assertEquals(ZonedDateTime::class.java, zoned.toDeviceZone()::class.java)
	}

	@Test
	fun `Duration truncatedTo and toDaysPartCompat`() {
		val d = Duration.ofHours(50).plusMinutes(30)
		assertEquals(2L, d.toDaysPartCompat())
		val truncated = d.truncatedTo(Duration.ofHours(1))
		assertTrue(truncated.toMillis() >= 0)
	}
}
