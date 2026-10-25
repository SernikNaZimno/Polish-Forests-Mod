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
	void aNewWorldOfTheModStartsInLateSpring() {
		FallbackCalendar c = Seasons.BUILT_IN;
		assertEquals(SubSeason.LATE_SPRING, Seasons.START_SUB_SEASON);
		assertEquals(SubSeason.LATE_SPRING, c.subSeasonAt(0));
		assertEquals(Month.MAY, c.subSeasonAt(0).month());
		// Year progress 2/12 at day 0 (0 = 1 March): the color curves see May.
		assertEquals(2 / 12.0, c.progressAt(0), 1e-12);
		assertEquals(SubSeason.EARLY_SUMMER, c.subSeasonAt(12 * DAY));
		assertEquals(SubSeason.MID_SPRING, c.subSeasonAt(144 * DAY - 1));
		assertEquals(SubSeason.LATE_SPRING, c.subSeasonAt(144 * DAY));
		assertEquals(SubSeason.MID_SPRING, c.subSeasonAt(-1));
	}

	@Test
	void dayTimeOfASubSeasonIsInItsFirstYear() {
		FallbackCalendar c = Seasons.BUILT_IN;
		for (SubSeason sub : SubSeason.values()) {
			for (int day : new int[] {0, 5, 11}) {
				long t = c.dayTimeOf(sub, day);
				assertEquals(sub, c.subSeasonAt(t), sub + " day " + day);
				assertEquals(sub, c.subSeasonAt(t + 23_999), sub + " day " + day + " evening");
				org.junit.jupiter.api.Assertions.assertTrue(t >= 0 && t < 144 * DAY, sub + " in the first year");
			}
		}
		assertEquals(0, c.dayTimeOf(SubSeason.LATE_SPRING, 0));
		assertEquals(11 * 12 * DAY, c.dayTimeOf(SubSeason.MID_SPRING, 0));
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
