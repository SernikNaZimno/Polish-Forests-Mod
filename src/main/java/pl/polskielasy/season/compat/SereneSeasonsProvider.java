package pl.polskielasy.season.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.season.SeasonProvider;
import pl.polskielasy.season.SubSeason;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHooks;

/**
 * Most do Serene Seasons. Ładowany refleksyjnie przez {@link pl.polskielasy.season.Seasons}
 * wyłącznie wtedy, gdy mod "sereneseasons" jest obecny.
 */
public final class SereneSeasonsProvider implements SeasonProvider {
	/** Wyłączane, gdy wewnętrzny hak SS zniknie w innej wersji moda. */
	private volatile boolean temperatureHook = true;

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
	 * Temperatura sezonowa z haka SS ({@code SeasonHooks.getBiomeTemperature}), tego samego, którego SS
	 * używa w opadzie i zamarzaniu: bramka 0,8, czarna lista, biała lista wymiarów i korekta podsezonu.
	 * Hak nie należy do API SS, więc przy błędzie łączenia wracamy do temperatury bez pory roku.
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
			PolskieLasy.LOG.warn("Serene Seasons: brak haka temperatury, zamarzanie rzek bez pory roku", e);
			return temperature;
		}
	}
}
