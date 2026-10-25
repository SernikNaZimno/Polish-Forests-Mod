package pl.polishforests.season;

import net.minecraft.world.level.Level;

/**
 * The mod's own calendar, used without Serene Seasons. The year is computed from the world's day time,
 * by default 12 game days per sub-season (a 144-day year, decision B2). Day 0 of the world's clock is the first day of
 * the start sub-season: since step Z1 (decision CP-B.4) a new world starts in LATE_SPRING (May, the forest in full leaf);
 * worlds of older versions of the mod move forward by the same two sub-seasons once.
 */
public final class FallbackCalendar implements SeasonProvider {
	public static final long TICKS_PER_DAY = 24000L;

	private final int daysPerSubSeason;
	private final SubSeason start;

	public FallbackCalendar(int daysPerSubSeason) {
		this(daysPerSubSeason, SubSeason.EARLY_SPRING);
	}

	public FallbackCalendar(int daysPerSubSeason, SubSeason start) {
		if (daysPerSubSeason < 1) {
			throw new IllegalArgumentException("daysPerSubSeason must be >= 1");
		}
		this.daysPerSubSeason = daysPerSubSeason;
		this.start = start;
	}

	/** The sub-season of day 0 of the world's clock. */
	public SubSeason start() {
		return start;
	}

	/**
	 * The first day time (ticks) of day {@code day} of a sub-season in the first year of the clock (for {@code time set}):
	 * the sub-season's offset from the start sub-season, modulo the year.
	 */
	public long dayTimeOf(SubSeason sub, int day) {
		long offset = Math.floorMod(sub.ordinal() - start.ordinal(), 12);
		return (offset * daysPerSubSeason + day) * TICKS_PER_DAY;
	}

	@Override
	public String name() {
		return "built-in (" + daysPerSubSeason + " days/sub-season)";
	}

	@Override
	public SubSeason subSeason(Level level) {
		return subSeasonAt(level.getOverworldClockTime());
	}

	@Override
	public double yearProgress(Level level) {
		return progressAt(level.getOverworldClockTime());
	}

	SubSeason subSeasonAt(long dayTime) {
		return SubSeason.byOrdinal((int) Math.floor(progressAt(dayTime) * 12.0));
	}

	double progressAt(long dayTime) {
		long subTicks = TICKS_PER_DAY * daysPerSubSeason;
		long yearTicks = subTicks * 12L;
		return (double) Math.floorMod(dayTime + start.ordinal() * subTicks, yearTicks) / yearTicks;
	}
}
