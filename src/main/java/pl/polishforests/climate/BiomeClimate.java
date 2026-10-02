package pl.polishforests.climate;

import java.util.List;
import net.minecraft.core.BlockPos;
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

	/**
	 * Placeholder biomes from the world presets (data/polishforests/worldgen/world_preset), covered by
	 * the {@code #polishforests:polish_climate} tag until the mod's own biomes arrive (step S5). The
	 * preset has 12 slots, but "forest" and "river" appear in them twice.
	 */
	public static final List<String> PLACEHOLDERS = List.of(
			"minecraft:old_growth_pine_taiga",
			"minecraft:forest",
			"minecraft:plains",
			"minecraft:meadow",
			"minecraft:river",
			"minecraft:swamp",
			"minecraft:taiga",
			"minecraft:old_growth_spruce_taiga",
			"minecraft:ocean",
			"minecraft:beach");

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
	 * Profile of a biome from the {@code #polishforests:polish_climate} tag by identifier. In steps
	 * S1–S4 these are placeholder biomes: the sea has 0.72 and does not freeze, the river freezes like
	 * a river, the rest have 0.70. A biome added to the tag by a data pack gets the lowland profile.
	 */
	public static BiomeClimate placeholder(String id, VerticalScale scale) {
		return switch (id) {
			case "minecraft:ocean" -> new BiomeClimate(scale, T_SEA, FreezeMode.NEVER);
			case "minecraft:river" -> new BiomeClimate(scale, T_LOWLAND, FreezeMode.RIVER);
			default -> new BiomeClimate(scale, T_LOWLAND, FreezeMode.VANILLA);
		};
	}
}
