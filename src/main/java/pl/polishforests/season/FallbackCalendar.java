package pl.polishforests.season;

import net.minecraft.world.level.Level;

/**
 * Własny kalendarz używany bez Serene Seasons. Rok liczony z czasu dnia świata,
 * domyślnie 12 dni gry na podsezon (rok 144 dni, decyzja B2).
 */
public final class FallbackCalendar implements SeasonProvider {
	public static final long TICKS_PER_DAY = 24000L;

	private final int daysPerSubSeason;

	public FallbackCalendar(int daysPerSubSeason) {
		if (daysPerSubSeason < 1) {
			throw new IllegalArgumentException("daysPerSubSeason musi być >= 1");
		}
		this.daysPerSubSeason = daysPerSubSeason;
	}

	@Override
	public String name() {
		return "własny (" + daysPerSubSeason + " dni/podsezon)";
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
		long yearTicks = TICKS_PER_DAY * daysPerSubSeason * 12L;
		return (double) Math.floorMod(dayTime, yearTicks) / yearTicks;
	}
}
