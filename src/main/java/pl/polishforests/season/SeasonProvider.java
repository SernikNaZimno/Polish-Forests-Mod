package pl.polishforests.season;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * Single source of truth about the season for the whole mod. The Serene Seasons implementation
 * is used when that mod is installed; otherwise the mod's own calendar runs.
 */
public interface SeasonProvider {
	String name();

	/** Current sub-season in the given world. */
	SubSeason subSeason(Level level);

	/**
	 * Day of the year in the range [0, 1): 0 = start of March (EARLY_SPRING), 1 = end of February.
	 * Phenological events are expressed as this fraction, so they work with any year length.
	 */
	double yearProgress(Level level);

	/**
	 * Biome temperature with the seasonal adjustment, as used for precipitation and freezing. The
	 * built-in calendar does not change the temperature (until the S2 seasonal climate in M4), so by
	 * default this returns {@code temperature}.
	 */
	default float temperatureInSeason(LevelReader level, Biome biome, BlockPos pos, float temperature) {
		return temperature;
	}
}
