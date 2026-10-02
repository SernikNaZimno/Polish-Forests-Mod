package pl.polishforests.season;

import net.fabricmc.loader.api.FabricLoader;
import pl.polishforests.PolishForests;

/**
 * Wybiera dostawcę pory roku przy starcie. Klasa mostu do Serene Seasons jest ładowana
 * wyłącznie, gdy mod jest obecny, dzięki czemu brak SS nie powoduje NoClassDefFoundError.
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
				PolishForests.LOG.error("Nie udało się podłączyć Serene Seasons, używam własnego kalendarza", e);
			}
		}
	}

	public static SeasonProvider provider() {
		return provider;
	}
}
