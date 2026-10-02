package pl.polishforests.season;

import java.time.Month;

/**
 * Dwanaście podsezonów roku. Kolejność i liczba odpowiadają Serene Seasons,
 * a każdy podsezon ma przypisany miesiąc kalendarza polskiego (decyzja B3).
 */
public enum SubSeason {
	EARLY_SPRING(Month.MARCH),
	MID_SPRING(Month.APRIL),
	LATE_SPRING(Month.MAY),
	EARLY_SUMMER(Month.JUNE),
	MID_SUMMER(Month.JULY),
	LATE_SUMMER(Month.AUGUST),
	EARLY_AUTUMN(Month.SEPTEMBER),
	MID_AUTUMN(Month.OCTOBER),
	LATE_AUTUMN(Month.NOVEMBER),
	EARLY_WINTER(Month.DECEMBER),
	MID_WINTER(Month.JANUARY),
	LATE_WINTER(Month.FEBRUARY);

	private static final SubSeason[] VALUES = values();

	private final Month month;

	SubSeason(Month month) {
		this.month = month;
	}

	public Month month() {
		return month;
	}

	public static SubSeason byOrdinal(int ordinal) {
		return VALUES[Math.floorMod(ordinal, VALUES.length)];
	}

	public static SubSeason of(Month month) {
		return byOrdinal(month.ordinal() - Month.MARCH.ordinal());
	}
}
