package pl.polishforests.season.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import pl.polishforests.PolishForests;
import pl.polishforests.season.SeasonProvider;
import pl.polishforests.season.SubSeason;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHooks;

/**
 * Bridge to Serene Seasons. Loaded reflectively by {@link pl.polishforests.season.Seasons}
 * only when the "sereneseasons" mod is present.
 */
public final class SereneSeasonsProvider implements SeasonProvider {
	/** Turned off when the internal SS hook disappears in another version of that mod. */
	private volatile boolean temperatureHook = true;
	/** Turned off when the SS configuration class is missing in another version of that mod. */
	private volatile boolean configHook = true;

	@Override
	public String name() {
		return "Serene Seasons";
	}

	@Override
	public SubSeason subSeason(Level level) {
		return SubSeason.byOrdinal(SeasonHelper.getSeasonState(level).getSubSeason().ordinal());
	}

	@Override
	public double yearProgress(Level level) {
		ISeasonState state = SeasonHelper.getSeasonState(level);
		double sub = state.getSubSeason().ordinal() + Math.clamp(state.getSubSeasonProgress(), 0.0f, 1.0f);
		return Math.min(sub / 12.0, Math.nextDown(1.0));
	}

	/**
	 * Seasonal temperature from the SS hook ({@code SeasonHooks.getBiomeTemperature}), the same one SS
	 * uses for precipitation and freezing: the 0.8 gate, the blacklist, the dimension whitelist and the
	 * sub-season adjustment. The hook is not part of the SS API, so on a linkage error we fall back to the
	 * temperature without the season.
	 */
	@Override
	public float temperatureInSeason(LevelReader level, Biome biome, BlockPos pos, float temperature) {
		if (!temperatureHook) {
			return temperature;
		}
		try {
			Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).wrapAsHolder(biome);
			return SeasonHooks.getBiomeTemperature(level, holder, pos, level.getSeaLevel());
		} catch (LinkageError e) {
			temperatureHook = false;
			PolishForests.LOG.warn("Serene Seasons: temperature hook missing, river freezing ignores the season", e);
			return temperature;
		}
	}

	/**
	 * The {@code changeFoliageColor} switch of the SS seasons configuration (step V7). The configuration class is not part
	 * of the SS API, so on a linkage error (or before SS read its configuration) the foliage changes.
	 */
	@Override
	public boolean changesFoliageColor() {
		if (!configHook) {
			return true;
		}
		try {
			return ModConfig.seasons == null || ModConfig.seasons.changeFoliageColor;
		} catch (LinkageError e) {
			configHook = false;
			PolishForests.LOG.warn("Serene Seasons: configuration missing, the foliage colors follow the season", e);
			return true;
		}
	}
}
