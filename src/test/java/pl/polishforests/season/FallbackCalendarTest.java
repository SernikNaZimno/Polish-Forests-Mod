package pl.polishforests.season;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Month;
import org.junit.jupiter.api.Test;

class FallbackCalendarTest {
	private static final long DAY = FallbackCalendar.TICKS_PER_DAY;

	@Test
	void yearStartsInEarlySpring() {
		FallbackCalendar c = new FallbackCalendar(12);
		assertEquals(SubSeason.EARLY_SPRING, c.subSeasonAt(0));
		assertEquals(Month.MARCH, c.subSeasonAt(0).month());
	}

	@Test
	void subSeasonAdvancesEveryTwelveDays() {
		FallbackCalendar c = new FallbackCalendar(12);
		assertEquals(SubSeason.EARLY_SPRING, c.subSeasonAt(12 * DAY - 1));
		assertEquals(SubSeason.MID_SPRING, c.subSeasonAt(12 * DAY));
		assertEquals(SubSeason.LATE_WINTER, c.subSeasonAt(144 * DAY - 1));
		assertEquals(SubSeason.EARLY_SPRING, c.subSeasonAt(144 * DAY));
	}

	@Test
	void monthMappingRoundTrips() {
		for (Month m : Month.values()) {
			assertEquals(m, SubSeason.of(m).month());
		}
	}
}
