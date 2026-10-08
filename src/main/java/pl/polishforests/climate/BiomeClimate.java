package pl.polishforests.climate;

import net.minecraft.core.BlockPos;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Climate profile of a biome in the "Poland" world, attached to the {@code Biome} object through
 * {@link BiomeClimateAccess}. A biome without a profile (Nether, End, vanilla worlds) computes its
 * temperature the vanilla way.
 *
 * @param scale       vertical mapping of the world (meters a.s.l. for Y)
 * @param baseTemperature     temperature at sea level, at most {@link PolandClimate#MAX_BASE_TEMPERATURE}
 * @param freezeMode  behavior of water in the biome
 */
public record BiomeClimate(VerticalScale scale, float baseTemperature, FreezeMode freezeMode) {
	/** Base temperature of lowlands, mountains and inland waters (about 10 °C). */
	public static final float T_LOWLAND = 0.70F;
	/** Base temperature of the sea and the coast (milder maritime climate). */
	public static final float T_SEA = 0.72F;

	/** Behavior of water in a biome with a profile. */
	public enum FreezeMode {
		/** As in vanilla: water freezes at T < 0.15. */
		VANILLA,
		/** Rivers and streams: freeze only at T < {@link PolandClimate#RIVER_FREEZE_THRESHOLD}. */
		RIVER,
		/** The sea (Baltic, decision M2-6) never freezes. */
		NEVER
	}

	/** Temperature at the given block (without the seasonal adjustment added by Serene Seasons). */
	public float temperature(BlockPos pos) {
		return PolandClimate.temperature(baseTemperature, scale, pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Profile of a biome from the {@code #polishforests:polish_climate} tag by identifier. The mod's biomes
	 * ({@code polishforests:*}, step S5) take the base temperature from {@link HabitatBiome#temperature()}
	 * (docs/03-m2-biomy.md §6.1): the sea never freezes (decision M2-6), the river and the stream freeze like rivers,
	 * the rest as in vanilla. A biome added to the tag by a data pack gets the lowland profile.
	 */
	public static BiomeClimate profile(String id, VerticalScale scale) {
		String prefix = PolishForests.MOD_ID + ":";
		HabitatBiome biome = id.startsWith(prefix) ? HabitatBiome.byId(id.substring(prefix.length())) : null;
		if (biome == null) {
			return new BiomeClimate(scale, T_LOWLAND, FreezeMode.VANILLA);
		}
		FreezeMode mode = switch (biome) {
			case SEA -> FreezeMode.NEVER;
			case RIVER, STREAM -> FreezeMode.RIVER;
			default -> FreezeMode.VANILLA;
		};
		return new BiomeClimate(scale, biome.temperature(), mode);
	}
}
