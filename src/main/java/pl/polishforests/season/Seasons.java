package pl.polishforests.season;

import net.fabricmc.loader.api.FabricLoader;
import pl.polishforests.PolishForests;

/**
 * Picks the season provider at startup. The Serene Seasons bridge class is loaded only
 * when that mod is present, so a missing SS does not cause a NoClassDefFoundError.
 */
public final class Seasons {
	public static final int DEFAULT_DAYS_PER_SUB_SEASON = 12;
	/**
	 * Sub-season of a new world (decision CP-B.4, step Z1): late spring, the forest in full leaf. The built-in calendar
	 * starts here; with Serene Seasons the recommended {@code starting_sub_season = 3} of its configuration does the same
	 * (the modpack ships it, docs/04 "Stan po Z1").
	 */
	public static final SubSeason START_SUB_SEASON = SubSeason.LATE_SPRING;
	/** The built-in calendar (also when Serene Seasons is installed, for the tests' dates). */
	public static final FallbackCalendar BUILT_IN = new FallbackCalendar(DEFAULT_DAYS_PER_SUB_SEASON, START_SUB_SEASON);

	private static SeasonProvider provider = BUILT_IN;

	private Seasons() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("sereneseasons")) {
			try {
				provider = (SeasonProvider) Class.forName("pl.polishforests.season.compat.SereneSeasonsProvider")
						.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException | LinkageError e) {
				PolishForests.LOG.error("Could not hook into Serene Seasons, using the built-in calendar", e);
			}
		}
	}

	public static SeasonProvider provider() {
		return provider;
	}
}
