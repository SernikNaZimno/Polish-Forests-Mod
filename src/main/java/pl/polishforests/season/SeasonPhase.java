package pl.polishforests.season;

import net.minecraft.world.level.Level;

/**
 * The phase of the year shared by the client and the server (step V7 of docs/04-pionowy-wycinek.md): the color step of
 * the species foliage curves ({@link FoliageCurve}), {@value #STEPS_PER_SUB_SEASON} steps per sub-season and
 * {@value #STEPS} per year, from the year progress of the season provider ({@link Seasons}: Serene Seasons when it is
 * installed, else the built-in {@link FallbackCalendar}). Step 0 starts at EARLY_SPRING (1 March); one step is about ten
 * days of the Polish calendar (a decade, as the phenology of report 08 §9 counts). Both sides compute the step from the
 * same provider, so a server-side rule (V13: the berries of the bilberry) and the client's colors agree.
 */
public final class SeasonPhase {
	public static final int STEPS_PER_SUB_SEASON = 3;
	public static final int STEPS = 12 * STEPS_PER_SUB_SEASON;

	private SeasonPhase() {
	}

	/** The color step of a level now, 0 … {@value #STEPS} − 1. */
	public static int step(Level level) {
		return stepAt(Seasons.provider().yearProgress(level));
	}

	/** The color step at a year progress in [0, 1). */
	public static int stepAt(double yearProgress) {
		return Math.clamp((int) Math.floor(yearProgress * STEPS), 0, STEPS - 1);
	}

	/** The sub-season of a color step. */
	public static SubSeason subSeason(int step) {
		return SubSeason.byOrdinal(Math.floorMod(step, STEPS) / STEPS_PER_SUB_SEASON);
	}

	/** The first color step of a sub-season. */
	public static int firstStep(SubSeason subSeason) {
		return subSeason.ordinal() * STEPS_PER_SUB_SEASON;
	}

	/**
	 * Whether the foliage changes its color with the season: the switch {@code changeFoliageColor} of Serene Seasons when
	 * that mod is installed (decision B6 (a): SS is only the source of the season and this switch), always without it.
	 */
	public static boolean foliageChanges() {
		return Seasons.provider().changesFoliageColor();
	}
}
