package pl.polishforests.worldgen.cave;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The vanilla cave biomes under the "Poland" terrain (step J2 of docs/04-pionowy-wycinek.md; user decision CP-C.4,
 * docs/00 G.26): lush caves, dripstone caves, sulfur caves and the deep dark, chosen as the vanilla overworld biome source
 * chooses them, from the vanilla climate of the point and its depth below the surface.
 *
 * <p>Vanilla picks the biome whose parameter point ({@code OverworldBiomeBuilder}) lies nearest to the climate target of
 * the point (temperature, humidity, continentalness, erosion, depth, weirdness). Its surface biomes tile the whole
 * climate space at depth 0 and depth 1, so where a cave biome wins depends only on the depth term of the surface biomes
 * ({@code min(d², (d − 1)²)}) against the full distance of the cave biome's point; this class makes that choice with the
 * vanilla points of the four cave biomes, so the "Poland" source needs no vanilla surface biome. The climate is the
 * vanilla one: the density functions {@code overworld/temperature}, {@code vegetation}, {@code continents},
 * {@code erosion} and {@code ridges} by id, compiled by the world's {@link RandomState} (2D: they do not depend on Y),
 * with one change: the "Poland" ground is land everywhere, so the continentalness is mapped onto its distribution under
 * the vanilla land ({@link #landContinentalness}; under vanilla land it is higher than over the whole vanilla world, and
 * the dripstone caves need a high one). The depth: vanilla takes it from its terrain ({@code overworld/depth}, the blocks
 * below a smooth terrain level / 128, the peaks and valleys of the surface aside), the "Poland" worlds from the model top
 * smoothed over a lattice of {@link #LATTICE} blocks ({@link #smoothTop}), so a cave biome boundary does not copy every
 * river bank and slope of the surface far below it, plus a relief of its own ({@link Relief}: the vanilla level rises and
 * falls with its hills and valleys at the scale of a hundred blocks, which breaks the cave biomes of one level into
 * patches; the "Poland" lowlands are flat); and no cave biome comes within {@link #MIN_BLOCKS_BELOW_TOP} blocks of the
 * column's own top.
 *
 * <p>Two limits that vanilla gets from its terrain: the depth counts at most {@link #MAX_DEPTH} (the depth of the vanilla
 * world bottom under its highest terrain; the realistic mountains are many times deeper, and below that the deep dark
 * would fill everything) and cave biomes lie only below Y {@link #MAX_Y}, the top of the vanilla height range
 * ({@code RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT}) of their decoration (moss, dripstone, sculk), so no cave biome stays bare;
 * from Y {@link #FADE_FROM_Y} an extra distance fades them out, so the upper boundary follows the climate and the depth
 * instead of one level.
 */
public final class CaveBiomes {
	/** The cave biomes in the order of their indices. */
	public static final List<ResourceKey<Biome>> KEYS = List.of(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.SULFUR_CAVES,
			Biomes.DEEP_DARK);
	public static final int LUSH = 0;
	public static final int DRIPSTONE = 1;
	public static final int SULFUR = 2;
	public static final int DEEP_DARK = 3;
	/** No cave biome: the habitat of the column. */
	public static final int NONE = -1;

	/** Vanilla depth per block below the surface (the overworld depth gradient: 3 over 384 blocks). */
	public static final float DEPTH_PER_BLOCK = 1.0F / 128.0F;
	/** Largest depth counted: Y -64 under vanilla terrain at Y 256. */
	public static final float MAX_DEPTH = 2.5F;
	/** Cave biomes lie only below this Y (the top of the vanilla height range of their decoration). */
	public static final int MAX_Y = 256;
	/** From this Y an extra distance fades the cave biomes out toward {@link #MAX_Y}. */
	public static final int FADE_FROM_Y = 224;
	/** Width of the fade: at {@link #MAX_Y} the extra distance is (({@code MAX_Y − FADE_FROM_Y}) / this)². */
	private static final float FADE_WIDTH = 64.0F;
	/**
	 * Fewest blocks between a cave biome and the top of its column (vanilla: from the depth about 0.1, 13 blocks), so the
	 * biome of the surface, the grass color and the decoration of the surface stay the habitat's also in a valley whose
	 * floor lies below the smoothed top.
	 */
	public static final int MIN_BLOCKS_BELOW_TOP = 16;
	/** Spacing of the lattice of model tops whose bilinear interpolation is the smoothed top ({@link #smoothTop}). */
	public static final int LATTICE = 64;
	/**
	 * Amplitude (blocks) and wavelength (blocks) of the relief of the level of the cave biomes ({@link Relief}): with them
	 * the patches of one level are as long as under the vanilla land (runs along X of 45–85 blocks against 48–71,
	 * {@code CaveBiomesTest}; without the relief 140–300).
	 */
	public static final double RELIEF_BLOCKS = 48.0;
	public static final double RELIEF_WAVELENGTH = 48.0;

	/**
	 * Quantiles of the vanilla continentalness over the whole world ({@code ALL}) and under its land ({@code LAND}: base
	 * height Y 63 and higher), at 0.5%, 2.5%, 5% … 97.5% and 99.5%, from 16 900 columns 467 blocks apart (seed 20260927,
	 * {@code CaveBiomesTest}); the noise is the same for every seed, only shifted.
	 */
	private static final float[] CONTINENTS_ALL = {-0.8983F, -0.6943F, -0.5859F, -0.5177F, -0.4660F, -0.4182F, -0.3775F,
			-0.3409F, -0.3054F, -0.2752F, -0.2437F, -0.2157F, -0.1885F, -0.1595F, -0.1358F, -0.1106F, -0.0861F, -0.0624F,
			-0.0389F, -0.0169F, 0.0065F, 0.0285F, 0.0499F, 0.0748F, 0.0981F, 0.1226F, 0.1484F, 0.1750F, 0.2017F, 0.2286F, 0.2552F,
			0.2850F, 0.3152F, 0.3486F, 0.3859F, 0.4289F, 0.4743F, 0.5262F, 0.6035F, 0.7075F, 0.9052F};
	private static final float[] CONTINENTS_LAND = {-0.1652F, -0.1475F, -0.1263F, -0.1080F, -0.0894F, -0.0701F, -0.0540F,
			-0.0371F, -0.0221F, -0.0055F, 0.0124F, 0.0277F, 0.0425F, 0.0583F, 0.0750F, 0.0913F, 0.1086F, 0.1249F, 0.1429F,
			0.1603F, 0.1773F, 0.1954F, 0.2154F, 0.2333F, 0.2503F, 0.2694F, 0.2884F, 0.3083F, 0.3279F, 0.3513F, 0.3763F, 0.4015F,
			0.4302F, 0.4567F, 0.4872F, 0.5234F, 0.5659F, 0.6191F, 0.6837F, 0.7810F, 0.9835F};

	/** The vanilla parameter points of the cave biomes, in the order of {@link #KEYS}. */
	private static final Climate.ParameterPoint[] POINTS = points();
	private static final long DEPTH_SURFACE_LOW = Climate.quantizeCoord(0.0F);
	private static final long DEPTH_SURFACE_HIGH = Climate.quantizeCoord(1.0F);

	/** Number of climate values of a column: temperature, humidity, continentalness, erosion, weirdness. */
	public static final int CLIMATE = 5;

	private final DensitySampler temperature;
	private final DensitySampler humidity;
	private final DensitySampler continents;
	private final DensitySampler erosion;
	private final DensitySampler weirdness;

	private final Relief relief;

	private CaveBiomes(RandomState randomState, HolderGetter<DensityFunction> functions) {
		this.relief = Relief.of(randomState.seed());
		this.temperature = randomState.getSampler(NoiseRouterData.getFunction(functions,
				NoiseRouterData.OVERWORLD_FUNCTIONS.temperature()));
		this.humidity = randomState.getSampler(NoiseRouterData.getFunction(functions,
				NoiseRouterData.OVERWORLD_FUNCTIONS.vegetation()));
		this.continents = randomState.getSampler(NoiseRouterData.getFunction(functions,
				NoiseRouterData.OVERWORLD_FUNCTIONS.continents()));
		this.erosion = randomState.getSampler(NoiseRouterData.getFunction(functions,
				NoiseRouterData.OVERWORLD_FUNCTIONS.erosion()));
		this.weirdness = randomState.getSampler(NoiseRouterData.getFunction(functions, NoiseRouterData.RIDGES));
	}

	/**
	 * Whether the worlds with caves get the cave biomes (step J2): always, except with the diagnostic JVM switch
	 * {@code -Dpolishforests.caveBiomes=off} (game tests {@code -PcaveBiomes=off}), which gives the caves of step J1 alone, for a
	 * comparison of the same world with and without the cave biomes.
	 */
	public static boolean enabledFromSystem() {
		return !"off".equals(System.getProperty("polishforests.caveBiomes"));
	}

	/** The cave biomes of a world: the vanilla climate functions of {@code registries} compiled by its random state. */
	public static CaveBiomes create(HolderLookup.Provider registries, RandomState randomState) {
		return new CaveBiomes(randomState, registries.lookupOrThrow(Registries.DENSITY_FUNCTION));
	}

	/** The relief of the level of the cave biomes of this world. */
	public Relief relief() {
		return relief;
	}

	/**
	 * The relief of the level from which the cave biomes count their depth: a 2D noise of the world seed (salt
	 * {@code cave_biomes.relief}) of {@link #RELIEF_BLOCKS} blocks and wavelength {@link #RELIEF_WAVELENGTH}, added to the
	 * smoothed top. Thread-safe, a pure function of the seed and the column.
	 */
	public static final class Relief {
		private static volatile @Nullable Relief last;
		private final long seed;
		private final Noise noise;

		private Relief(long seed) {
			this.seed = seed;
			this.noise = new Noise(seed).derive("cave_biomes.relief");
		}

		/** The relief of the world with seed {@code seed} (the last one is kept). */
		public static Relief of(long seed) {
			Relief r = last;
			if (r == null || r.seed != seed) {
				r = new Relief(seed);
				last = r;
			}
			return r;
		}

		/** Blocks by which the level of column (x, z) lies above the smoothed top. */
		public float offset(int x, int z) {
			return (float) (RELIEF_BLOCKS * noise.fbm(x, z, RELIEF_WAVELENGTH, 3, 0.5));
		}

		/** The level of column (x, z): the smoothed top of {@code tops} plus the relief. */
		public float level(Tops tops, int x, int z) {
			return smoothTop(tops, x, z) + offset(x, z);
		}
	}

	private static Climate.ParameterPoint[] points() {
		Climate.ParameterList<ResourceKey<Biome>> list = MultiNoiseBiomeSourceParameterList.knownPresets()
				.get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);
		Climate.ParameterPoint[] points = new Climate.ParameterPoint[KEYS.size()];
		for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> entry : list.values()) {
			int k = KEYS.indexOf(entry.getSecond());
			if (k >= 0) {
				if (points[k] != null) {
					throw new IllegalStateException("Two vanilla parameter points of " + entry.getSecond().identifier());
				}
				points[k] = entry.getFirst();
			}
		}
		for (int k = 0; k < points.length; k++) {
			if (points[k] == null) {
				throw new IllegalStateException("No vanilla parameter point of " + KEYS.get(k).identifier());
			}
		}
		return points;
	}

	/** The vanilla parameter point of cave biome {@code k}. */
	public static Climate.ParameterPoint point(int k) {
		return POINTS[k];
	}

	/** Whether no cave biome restricts the temperature (then it is not sampled; vanilla: all four take the full range). */
	private static final boolean ANY_TEMPERATURE = anyTemperature();

	private static boolean anyTemperature() {
		Climate.Parameter full = Climate.Parameter.span(-1.0F, 1.0F);
		for (Climate.ParameterPoint p : POINTS) {
			if (p.temperature().min() > full.min() || p.temperature().max() < full.max()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The quantized vanilla climate of the column at block (x, z) into {@code out} from {@code offset} ({@link #CLIMATE}
	 * values: temperature, humidity, continentalness, erosion, weirdness), as {@code Climate.Sampler} samples it (the
	 * functions are 2D). The temperature is 0 when no cave biome restricts it.
	 */
	public void climate(int x, int z, long[] out, int offset) {
		SamplerContext c = SamplerContext.EMPTY_UNCACHED;
		out[offset] = ANY_TEMPERATURE ? 0L : Climate.quantizeCoord(temperature.sampleValue(c, x, 0, z));
		out[offset + 1] = Climate.quantizeCoord(humidity.sampleValue(c, x, 0, z));
		out[offset + 2] = Climate.quantizeCoord(landContinentalness(continents.sampleValue(c, x, 0, z)));
		out[offset + 3] = Climate.quantizeCoord(erosion.sampleValue(c, x, 0, z));
		out[offset + 4] = Climate.quantizeCoord(weirdness.sampleValue(c, x, 0, z));
	}

	/**
	 * The vanilla continentalness {@code c} mapped onto its distribution under the vanilla land: piecewise linear between
	 * the quantiles of the whole world and of the land, beyond them with the slope of the outer segments. Monotone, so the
	 * patches keep their shapes; only the share of the high continentalness (dripstone caves) and of the coast band
	 * (sulfur caves) becomes that of the vanilla land.
	 */
	public static float landContinentalness(float c) {
		float[] a = CONTINENTS_ALL;
		float[] l = CONTINENTS_LAND;
		int i = 0;
		while (i < a.length - 2 && c > a[i + 1]) {
			i++;
		}
		return l[i] + (c - a[i]) * (l[i + 1] - l[i]) / (a[i + 1] - a[i]);
	}

	/** The model tops of a world, for the smoothed top: Y of the top block of column (x, z). */
	@FunctionalInterface
	public interface Tops {
		int top(int x, int z);
	}

	/**
	 * The smoothed top of column (x, z): the bilinear interpolation of the tops at the corners of its cell of the
	 * {@link #LATTICE}-block lattice. A pure function of the tops.
	 */
	public static float smoothTop(Tops tops, int x, int z) {
		int x0 = Math.floorDiv(x, LATTICE) * LATTICE;
		int z0 = Math.floorDiv(z, LATTICE) * LATTICE;
		float fx = (x - x0) / (float) LATTICE;
		float fz = (z - z0) / (float) LATTICE;
		float t00 = tops.top(x0, z0);
		float t10 = tops.top(x0 + LATTICE, z0);
		float t01 = tops.top(x0, z0 + LATTICE);
		float t11 = tops.top(x0 + LATTICE, z0 + LATTICE);
		return t00 + fx * (t10 - t00) + fz * (t01 - t00) + fx * fz * (t00 - t10 - t01 + t11);
	}

	/**
	 * The parts of the vanilla distances of the cave biomes that do not depend on Y: for each cave biome (order of
	 * {@link #KEYS}) the squared distances of the climate {@code climate} (from {@code offset}) and of its offset, written to
	 * {@code costs} from {@code costsOffset} ({@link #KEYS}.size() values).
	 */
	public static void costs(long[] climate, int offset, long[] costs, int costsOffset) {
		for (int k = 0; k < POINTS.length; k++) {
			Climate.ParameterPoint p = POINTS[k];
			costs[costsOffset + k] = square(p.temperature().distance(climate[offset]))
					+ square(p.humidity().distance(climate[offset + 1])) + square(p.continentalness().distance(climate[offset + 2]))
					+ square(p.erosion().distance(climate[offset + 3])) + square(p.weirdness().distance(climate[offset + 4]))
					+ square(p.offset());
		}
	}

	/**
	 * The vanilla depth parameter at Y below the smoothed top {@code smoothTop}: blocks below the top face / 128, capped at
	 * {@link #MAX_DEPTH}.
	 */
	public static float depth(float smoothTop, int y) {
		return Math.min(MAX_DEPTH, (smoothTop + 1 - y) * DEPTH_PER_BLOCK);
	}

	/**
	 * The cave biome at Y in a column with the top block {@code top}, the smoothed top {@code smoothTop} and the climate
	 * {@code climate} ({@link #climate}), or {@link #NONE}. A pure function.
	 */
	public static int choose(long[] climate, int top, float smoothTop, int y) {
		long[] costs = new long[POINTS.length];
		costs(climate, 0, costs, 0);
		return chooseByCosts(costs, 0, top, smoothTop, y);
	}

	/**
	 * The cave biome at Y in a column with the top block {@code top}, the smoothed top {@code smoothTop} and the climate
	 * parts {@code costs} ({@link #costs}, from {@code offset}), or {@link #NONE}: the cave biome nearest to the target if it
	 * is nearer than every surface biome, whose distance is only the depth term. A pure function.
	 */
	public static int chooseByCosts(long[] costs, int offset, int top, float smoothTop, int y) {
		if (y >= MAX_Y || y > top - MIN_BLOCKS_BELOW_TOP) {
			return NONE;
		}
		long fade = 0;
		if (y > FADE_FROM_Y) {
			long f = Climate.quantizeCoord((y - FADE_FROM_Y) / FADE_WIDTH);
			fade = f * f;
		}
		return nearest(costs, offset, Climate.quantizeCoord(depth(smoothTop, y)), fade);
	}

	/**
	 * The cave biome nearest to the target of the quantized depth parameter {@code d}, the climate parts {@code costs}
	 * ({@link #costs}, from {@code offset}) and the extra distance {@code fade}, if it is nearer than every surface biome
	 * (depth points 0 and 1, every other parameter in range), else {@link #NONE}.
	 */
	public static int nearest(long[] costs, int offset, long d, long fade) {
		long low = d - DEPTH_SURFACE_LOW;
		long high = d - DEPTH_SURFACE_HIGH;
		long best = Math.min(low * low, high * high);
		int chosen = NONE;
		for (int k = 0; k < POINTS.length; k++) {
			long fitness = costs[offset + k] + fade + square(POINTS[k].depth().distance(d));
			if (fitness < best) {
				best = fitness;
				chosen = k;
			}
		}
		return chosen;
	}

	private static long square(long v) {
		return v * v;
	}

	/**
	 * Whether the vanilla aquifers stay dry at this depth parameter and erosion (vanilla
	 * {@code OverworldBiomeBuilder.deepDarkRegion}: erosion below -0.225 and depth above 0.9; the aquifer exclusion that
	 * keeps the deep dark dry). For tests; the generator uses the same rule as a density function ({@link CaveDensity}).
	 */
	public static boolean dryDeepDark(float erosion, float depth) {
		return Math.min(-0.225F - erosion, Math.max(depth - 0.9F, 0.0F)) > 0.0F;
	}
}
