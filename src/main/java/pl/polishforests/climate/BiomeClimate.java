package pl.polishforests.climate;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Climate profile of a biome, attached to the {@code Biome} object through {@link BiomeClimateAccess}. A biome without a
 * profile (Nether, End, vanilla worlds) computes its temperature the vanilla way.
 *
 * <p>Two kinds ({@link Kind}): in the "Poland" world the temperature drops with the meters a.s.l. of the world's vertical
 * scale ({@link PolandClimate}); in the world type "Default + Polish Forests" (step C2, decision CP-D.11) a habitat biome
 * takes the base temperature of the vanilla biome it replaced in the column, with the vanilla height adjustment
 * ({@link VanillaClimate}).
 *
 * @param scale       vertical mapping of the world (meters a.s.l. for Y); null for {@link Kind#VANILLA_COLUMNS}
 * @param baseTemperature     temperature at sea level, at most {@link PolandClimate#MAX_BASE_TEMPERATURE}; for
 *                    {@link Kind#VANILLA_COLUMNS} the biome's own base temperature (columns without a column climate)
 * @param freezeMode  behavior of water in the biome
 * @param kind        how the temperature is computed
 */
public record BiomeClimate(@Nullable VerticalScale scale, float baseTemperature, FreezeMode freezeMode, Kind kind) {
	/** Base temperature of lowlands, mountains and inland waters (about 10 °C). */
	public static final float T_LOWLAND = 0.70F;
	/** Base temperature of the sea and the coast (milder maritime climate). */
	public static final float T_SEA = 0.72F;

	/** How a profile computes the temperature (not saved anywhere; new kinds only at the end). */
	public enum Kind {
		/** The "Poland" world: meters a.s.l. of the vertical scale ({@link PolandClimate}). */
		POLAND,
		/** The world type "Default + Polish Forests": the column's vanilla biome and the vanilla height adjustment. */
		VANILLA_COLUMNS
	}

	/** A profile of the "Poland" world. */
	public BiomeClimate(VerticalScale scale, float baseTemperature, FreezeMode freezeMode) {
		this(scale, baseTemperature, freezeMode, Kind.POLAND);
	}

	/** Step C2: the profile of a habitat biome in the world type "Default + Polish Forests". */
	public static BiomeClimate vanillaColumns(float ownBaseTemperature) {
		return new BiomeClimate(null, ownBaseTemperature, FreezeMode.VANILLA, Kind.VANILLA_COLUMNS);
	}

	/** Whether this is the profile of the world type "Default + Polish Forests". */
	public boolean vanillaColumns() {
		return kind == Kind.VANILLA_COLUMNS;
	}

	/** Behavior of water in a biome with a profile. */
	public enum FreezeMode {
		/** As in vanilla: water freezes at T < 0.15. */
		VANILLA,
		/** Rivers and streams: freeze only at T < {@link PolandClimate#RIVER_FREEZE_THRESHOLD}. */
		RIVER,
		/** The sea (Baltic, decision M2-6) never freezes. */
		NEVER
	}

	/**
	 * Temperature at the given block in the "Poland" world (without the seasonal adjustment added by Serene Seasons); for
	 * a profile of the other kind see {@link #temperature(BlockPos, int)}.
	 */
	public float temperature(BlockPos pos) {
		if (scale == null) {
			throw new IllegalStateException("A profile of the kind " + kind + " needs the sea level");
		}
		return PolandClimate.temperature(baseTemperature, scale, pos.getX(), pos.getY(), pos.getZ());
	}

	/** Temperature at the given block (without the seasonal adjustment added by Serene Seasons), for either kind. */
	public float temperature(BlockPos pos, int seaLevel) {
		return kind == Kind.VANILLA_COLUMNS ? VanillaClimate.columnTemperature(pos, seaLevel, baseTemperature) : temperature(pos);
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
