package pl.polskielasy.season;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * Jedno źródło prawdy o porze roku dla całego moda. Implementacja z Serene Seasons
 * jest używana, gdy mod jest zainstalowany; w przeciwnym razie działa własny kalendarz.
 */
public interface SeasonProvider {
	String name();

	/** Bieżący podsezon w danym świecie. */
	SubSeason subSeason(Level level);

	/**
	 * Dzień roku w zakresie [0, 1): 0 = początek marca (EARLY_SPRING), 1 = koniec lutego.
	 * Zdarzenia fenologiczne wyrażamy w tym ułamku, aby działały przy dowolnej długości roku.
	 */
	double yearProgress(Level level);

	/**
	 * Temperatura biomu z korektą pory roku, taką jak przy opadzie i zamarzaniu. Własny kalendarz
	 * nie zmienia temperatury (do klimatu sezonowego S2 w M4), więc domyślnie zwraca {@code temperature}.
	 */
	default float temperatureInSeason(LevelReader level, Biome biome, BlockPos pos, float temperature) {
		return temperature;
	}
}
