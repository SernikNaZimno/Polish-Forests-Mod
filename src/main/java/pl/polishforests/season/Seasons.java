package pl.polishforests.season;

import net.fabricmc.loader.api.FabricLoader;
import pl.polishforests.PolishForests;

/**
 * Picks the season provider at startup. The Serene Seasons bridge class is loaded only
 * when that mod is present, so a missing SS does not cause a NoClassDefFoundError.
 */
public final class Seasons {
	public static final int DEFAULT_DAYS_PER_SUB_SEASON = 12;

	private static SeasonProvider provider = new FallbackCalendar(DEFAULT_DAYS_PER_SUB_SEASON);

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
