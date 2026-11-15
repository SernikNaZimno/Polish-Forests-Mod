package pl.polishforests.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jspecify.annotations.Nullable;

/**
 * Step C2 (decision CP-D.11, G.58-b): the temperature of the habitat biomes in the world type "Default + Polish Forests".
 * A column takes the base temperature of the vanilla biome the forest of the mod replaced there ({@link ColumnClimate},
 * an attachment of the chunk) and the vanilla height adjustment ({@code Biome#getHeightAdjustedTemperature}): above
 * Y {@code seaLevel + 17} the temperature drops by 0.05 per 40 blocks, shifted by the vanilla temperature noise (fixed
 * seed 1234, so the client computes it too). Snow therefore lies where it lay in vanilla: everywhere in the former snowy
 * taiga, higher up in the taigas and the windswept forest; lower down it rains.
 *
 * <p>The formula is a pure function; the column climate of a chunk comes from {@link Columns}, which the mod binds to the
 * chunks of the server, of the generation region and of the client ({@code ColumnClimates}). A chunk without the
 * attachment (chunks of W1 worlds generated before step C2) keeps the biome's own base temperature, the temperature of
 * those chunks before the step.
 */
public final class VanillaClimate {
	/** Blocks above the sea level from which the vanilla temperature drops with height. */
	public static final int SNOW_LEVEL_ABOVE_SEA = 17;

	/** The vanilla temperature noise ({@code Biome.TEMPERATURE_NOISE}: the same seed and kind, so the same values). */
	private static final SimplexNoise TEMPERATURE_NOISE = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), true);

	/** Where the temperature finds the column climate of a chunk. */
	@FunctionalInterface
	public interface Columns {
		/** The column climate of chunk (chunkX, chunkZ) as the calling thread sees it, or null. */
		@Nullable ColumnClimate at(int chunkX, int chunkZ);
	}

	private static final Columns NONE = (x, z) -> null;
	private static volatile Columns columns = NONE;

	private VanillaClimate() {
	}

	/** Binds the lookup of the column climates (the mod's initializer; tests bind their own and restore null). */
	public static void bind(@Nullable Columns lookup) {
		columns = lookup == null ? NONE : lookup;
	}

	/**
	 * The vanilla temperature at block (x, y, z) of a biome with base temperature {@code base} and no temperature modifier:
	 * the expression of {@code Biome#getHeightAdjustedTemperature} with the same float arithmetic.
	 */
	public static float temperature(float base, int x, int y, int z, int seaLevel) {
		int snowLevel = seaLevel + SNOW_LEVEL_ABOVE_SEA;
		if (y > snowLevel) {
			float v = TEMPERATURE_NOISE.get(x / 8.0F, z / 8.0F) * 8.0F;
			return base - (v + y - snowLevel) * 0.05F / 40.0F;
		}
		return base;
	}

	/**
	 * Temperature of a habitat biome at a block of the world type "Default + Polish Forests": the base temperature of the
	 * column's vanilla biome ({@link ColumnClimate}) or, without the attachment, the biome's own base temperature
	 * {@code ownBase}, with the vanilla height adjustment.
	 */
	public static float columnTemperature(BlockPos pos, int seaLevel, float ownBase) {
		return temperature(columnBase(pos.getX(), pos.getZ(), ownBase), pos.getX(), pos.getY(), pos.getZ(), seaLevel);
	}

	/** Base temperature of column (x, z): the column climate of its chunk, or {@code ownBase} without one. */
	public static float columnBase(int x, int z, float ownBase) {
		ColumnClimate c = columns.at(x >> 4, z >> 4);
		return c == null ? ownBase : c.base(x, z);
	}

	/** The Y below which a biome of base temperature {@code base} in column (x, z) is warm enough to rain (vanilla 0.15). */
	public static int snowLine(float base, int x, int z, int seaLevel) {
		int snowLevel = seaLevel + SNOW_LEVEL_ABOVE_SEA;
		if (base < PolandClimate.SNOW_THRESHOLD) {
			return Integer.MIN_VALUE;
		}
		// base - (v + y - s) * 0.05 / 40 < 0.15  <=>  y > s - v + (base - 0.15) * 800
		float v = TEMPERATURE_NOISE.get(x / 8.0F, z / 8.0F) * 8.0F;
		int y = (int) Math.floor(snowLevel - v + (base - PolandClimate.SNOW_THRESHOLD) * 800.0F);
		while (y > snowLevel && temperature(base, x, y, z, seaLevel) < PolandClimate.SNOW_THRESHOLD) {
			y--;
		}
		while (temperature(base, x, y + 1, z, seaLevel) >= PolandClimate.SNOW_THRESHOLD) {
			y++;
		}
		return y;
	}
}
