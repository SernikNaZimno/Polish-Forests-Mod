package pl.polishforests.worldgen.cave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Step J2 criteria: the vanilla cave biomes under the Poland terrain ({@link CaveBiomes}) against the vanilla overworld of
 * the same seed. (1) The choice agrees with the vanilla overworld parameter list for vanilla climate targets (the rule
 * that the surface biomes tile the climate space at depths 0 and 1). (2) The share of each cave biome by depth below the
 * surface, and the size of its patches (runs along X at one level), in both scales against vanilla land (base height
 * {@code WORLD_SURFACE_WG} Y 63 and higher; its {@code OCEAN_FLOOR_WG} needs block properties that the tests lack).
 * (3) Never near the surface, never from Y {@link CaveBiomes#MAX_Y}; a pure function.
 */
@Tag("slow")
class CaveBiomesTest {
	private static final long SEED = CaveFixture.SEED;
	/** Depth bins (blocks below the top), lower bounds; the last bin ends at {@link #MAX_DEPTH}. */
	private static final int[] BINS = {16, 48, 96, 160};
	private static final int MAX_DEPTH = 256;
	private static final String[] NAMES = {"lush", "dripstone", "sulfur", "deep_dark"};

	private static HolderLookup.Provider registries;
	private static RandomState vanillaState;
	private static CaveBiomes caves;
	private static Climate.ParameterList<ResourceKey<Biome>> overworld;

	@BeforeAll
	static void setUp() {
		registries = CaveFixture.registries();
		NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
				.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
		vanillaState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), SEED, settings);
		// The Poland worlds compile the climate with a random state without a router; the noises are the same.
		RandomState poland = RandomState.create(registries.lookupOrThrow(Registries.NOISE), SEED, false,
				Blocks.STONE.defaultBlockState(), 63, NoiseRouterData.none());
		caves = CaveBiomes.create(registries, poland);
		overworld = MultiNoiseBiomeSourceParameterList.knownPresets().get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);
	}

	private static int caveIndex(ResourceKey<Biome> key) {
		return CaveBiomes.KEYS.indexOf(key);
	}

	@Test
	void choiceAgreesWithTheVanillaParameterList() {
		Climate.Sampler sampler = vanillaState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		Random random = new Random(7);
		long[] climate = new long[CaveBiomes.CLIMATE];
		long[] costs = new long[CaveBiomes.KEYS.size()];
		int n = 20_000;
		int agree = 0;
		int[] cave = new int[CaveBiomes.KEYS.size()];
		List<String> differences = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int x = random.nextInt(200_000) - 100_000;
			int z = random.nextInt(200_000) - 100_000;
			int qx = x >> 2;
			int qz = z >> 2;
			Climate.TargetPoint t = sampler.sample(qx, 0, qz);
			// The climate of the Poland worlds is the vanilla one, the continentalness mapped onto the vanilla land.
			caves.climate(qx << 2, qz << 2, climate, 0);
			assertEquals(t.humidity(), climate[1], "humidity");
			assertEquals(t.erosion(), climate[3], "erosion");
			assertEquals(t.weirdness(), climate[4], "weirdness");
			long land = Climate.quantizeCoord(CaveBiomes.landContinentalness(Climate.unquantizeCoord(t.continentalness())));
			assertTrue(Math.abs(land - climate[2]) <= 2, "continentalness " + climate[2] + ", expected " + land);
			// The choice for any depth from the surface to below the vanilla range, for the vanilla target itself.
			long d = Climate.quantizeCoord(random.nextFloat() * 2.5F);
			Climate.TargetPoint target = new Climate.TargetPoint(t.temperature(), t.humidity(), t.continentalness(),
					t.erosion(), d, t.weirdness());
			int expected = caveIndex(overworld.findValueBruteForce(target));
			long[] vanilla = {t.temperature(), t.humidity(), t.continentalness(), t.erosion(), t.weirdness()};
			CaveBiomes.costs(vanilla, 0, costs, 0);
			int actual = CaveBiomes.nearest(costs, 0, d, 0);
			if (actual == expected) {
				agree++;
			} else if (differences.size() < 10) {
				differences.add(String.format(Locale.ROOT, "%s: vanilla %d, ours %d", target, expected, actual));
			}
			if (expected >= 0) {
				cave[expected]++;
			}
		}
		System.out.printf(Locale.ROOT, "Cave biome choice: %d of %d agree with vanilla; vanilla caves %s%n", agree, n,
				java.util.Arrays.toString(cave));
		for (int k = 0; k < cave.length; k++) {
			assertTrue(cave[k] > 0, "no vanilla target of " + NAMES[k]);
		}
		assertTrue(agree >= n * 0.999, "agreement " + agree + " of " + n + ": " + differences);
	}

	@Test
	void landContinentalnessIsMonotone() {
		float last = -Float.MAX_VALUE;
		for (float c = -1.2F; c <= 1.2F; c += 0.001F) {
			float m = CaveBiomes.landContinentalness(c);
			assertTrue(m >= last, "not monotone at " + c);
			last = m;
		}
		// The median of the whole world maps to the median of the land.
		assertEquals(0.1773F, CaveBiomes.landContinentalness(0.0065F), 1e-4F);
	}

	/** Shares of the cave biomes by depth bin, and the mean length of their runs along X at one level. */
	private static final class Profile {
		final long[][] count = new long[BINS.length][CaveBiomes.KEYS.size()];
		final long[] total = new long[BINS.length];
		final long[] runCells = new long[CaveBiomes.KEYS.size()];
		final long[] runs = new long[CaveBiomes.KEYS.size()];
		final long[] lineCells = new long[CaveBiomes.KEYS.size()];
		long linesTotal;
		long columns;

		void add(int depth, int k) {
			int b = bin(depth);
			if (b < 0) {
				return;
			}
			total[b]++;
			if (k >= 0) {
				count[b][k]++;
			}
		}

		double share(int b, int k) {
			return total[b] == 0 ? 0 : (double) count[b][k] / total[b];
		}

		double meanRun(int k) {
			return runs[k] == 0 ? 0 : (double) runCells[k] / runs[k];
		}

		String describe(String name) {
			StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s (%d columns):", name, columns));
			for (int k = 0; k < NAMES.length; k++) {
				s.append(String.format(Locale.ROOT, "%n  %-10s", NAMES[k]));
				for (int b = 0; b < BINS.length; b++) {
					s.append(String.format(Locale.ROOT, " %3d+: %5.2f%%", BINS[b], 100 * share(b, k)));
				}
				s.append(String.format(Locale.ROOT, "  run %4.0f blocks (%d runs, %.1f%% of the level)", 4 * meanRun(k), runs[k],
						linesTotal == 0 ? 0 : 100.0 * lineCells[k] / linesTotal));
			}
			return s.toString();
		}
	}

	private static int bin(int depth) {
		if (depth < BINS[0] || depth >= MAX_DEPTH) {
			return -1;
		}
		int b = 0;
		while (b + 1 < BINS.length && depth >= BINS[b + 1]) {
			b++;
		}
		return b;
	}

	/** Grid of the share survey: 40 × 40 columns 320 blocks apart (12.8 km). */
	private static final int GRID = 40;
	private static final int STEP = 320;
	/**
	 * Patches: 24 lines along X of 1024 quart cells (4 km), each at one level {@link #RUN_DEPTH} blocks below the median
	 * top of its line (vanilla: of its land), the horizontal size of the patches in space.
	 */
	private static final int LINES = 24;
	private static final int LINE_QUARTS = 1024;
	private static final int RUN_DEPTH = 72;

	private static Profile vanilla() {
		NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
				.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
				new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),
				registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
		LevelHeightAccessor height = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());
		Climate.Sampler sampler = vanillaState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		Profile p = new Profile();
		for (int gx = 0; gx < GRID; gx++) {
			for (int gz = 0; gz < GRID; gz++) {
				int x = gx * STEP - GRID * STEP / 2;
				int z = gz * STEP - GRID * STEP / 2;
				int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, vanillaState) - 1;
				if (top < 63) {
					continue;
				}
				p.columns++;
				for (int depth = BINS[0]; depth < MAX_DEPTH; depth += 4) {
					int y = top - depth;
					if (y < -60) {
						break;
					}
					p.add(depth, caveIndex(overworld.findValue(sampler.sample(x >> 2, y >> 2, z >> 2))));
				}
			}
		}
		for (int line = 0; line < LINES; line++) {
			int z = line * 1_000 - LINES * 500;
			int[] tops = new int[LINE_QUARTS];
			List<Integer> land = new ArrayList<>();
			for (int i = 0; i < LINE_QUARTS; i++) {
				tops[i] = generator.getBaseHeight(i * 4 - LINE_QUARTS * 2, z, Heightmap.Types.WORLD_SURFACE_WG, height,
						vanillaState) - 1;
				if (tops[i] >= 63) {
					land.add(tops[i]);
				}
			}
			if (land.size() < LINE_QUARTS / 4) {
				continue;
			}
			int level = median(land) - RUN_DEPTH;
			int[] run = {-1, 0};
			for (int i = 0; i < LINE_QUARTS; i++) {
				int x = i * 4 - LINE_QUARTS * 2;
				int k = tops[i] < Math.max(63, level + 16) ? -2
						: caveIndex(overworld.findValue(sampler.sample(x >> 2, level >> 2, z >> 2)));
				line(p, run, k);
			}
			line(p, run, -2);
		}
		return p;
	}

	private static int median(List<Integer> values) {
		List<Integer> sorted = new ArrayList<>(values);
		sorted.sort(null);
		return sorted.get(sorted.size() / 2);
	}

	/**
	 * Counts a cell of a line: a run of one cave biome ({@code run}: biome and length so far) ends when another value
	 * comes; -2 (no land or the level too near the top) ends a run without counting it.
	 */
	private static void line(Profile p, int[] run, int k) {
		if (k != -2) {
			p.linesTotal++;
			if (k >= 0) {
				p.lineCells[k]++;
			}
		}
		if (k == run[0]) {
			run[1]++;
			return;
		}
		if (run[0] >= 0 && k != -2) {
			p.runs[run[0]]++;
			p.runCells[run[0]] += run[1];
		}
		run[0] = k;
		run[1] = 1;
	}

	private static Profile poland(PolandScale scale) {
		LandscapeModel model = new LandscapeModel(SEED, scale.landscape(), 1.0);
		VerticalScale vertical = scale.vertical();
		Map<Long, Integer> lattice = new HashMap<>();
		CaveBiomes.Tops tops = (x, z) -> lattice.computeIfAbsent((long) x << 32 | (z & 0xFFFF_FFFFL),
				k -> vertical.topBlockY(model.sample(x, z).surface()));
		long[] climate = new long[CaveBiomes.CLIMATE];
		long[] costs = new long[CaveBiomes.KEYS.size()];
		Profile p = new Profile();
		for (int gx = 0; gx < GRID; gx++) {
			for (int gz = 0; gz < GRID; gz++) {
				int x = gx * STEP - GRID * STEP / 2;
				int z = gz * STEP - GRID * STEP / 2;
				ColumnSample s = model.sample(x, z);
				if (s.hasWater() && vertical.topBlockY(s.waterLevel()) <= vertical.seaLevelY()) {
					continue;
				}
				int top = vertical.topBlockY(s.surface());
				float smooth = caves.relief().level(tops, x, z);
				p.columns++;
				caves.climate(x & ~3, z & ~3, climate, 0);
				CaveBiomes.costs(climate, 0, costs, 0);
				for (int depth = BINS[0]; depth < MAX_DEPTH; depth += 4) {
					int y = top - depth;
					if (y < vertical.minY() + 4) {
						break;
					}
					p.add(depth, CaveBiomes.chooseByCosts(costs, 0, top, smooth, y & ~3));
				}
			}
		}
		for (int line = 0; line < LINES; line++) {
			int z = line * 1_000 - LINES * 500;
			int[] columnTops = new int[LINE_QUARTS];
			List<Integer> all = new ArrayList<>();
			for (int i = 0; i < LINE_QUARTS; i++) {
				columnTops[i] = vertical.topBlockY(model.sample(i * 4 - LINE_QUARTS * 2 + 2, z + 2).surface());
				all.add(columnTops[i]);
			}
			int level = (median(all) - RUN_DEPTH) & ~3;
			int[] run = {-1, 0};
			for (int i = 0; i < LINE_QUARTS; i++) {
				int x = i * 4 - LINE_QUARTS * 2;
				caves.climate(x, z & ~3, climate, 0);
				CaveBiomes.costs(climate, 0, costs, 0);
				int k = columnTops[i] < level + 16 ? -2
						: CaveBiomes.chooseByCosts(costs, 0, columnTops[i], caves.relief().level(tops, x + 2, z + 2), level);
				line(p, run, k);
			}
			line(p, run, -2);
		}
		return p;
	}

	@Test
	void sharesAndPatchesFollowVanilla() {
		Profile vanilla = vanilla();
		System.out.println(vanilla.describe("vanilla land"));
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			Profile p = poland(scale);
			System.out.println(p.describe(scale.getSerializedName()));
			for (int k = 0; k < NAMES.length; k++) {
				for (int b = 0; b < BINS.length; b++) {
					double v = vanilla.share(b, k);
					double ours = p.share(b, k);
					// Within a factor 2 of vanilla, or within 1.5 percentage points where vanilla is rare.
					boolean ok = Math.abs(ours - v) <= 0.015 || ours >= v / 2 && ours <= v * 2;
					if (!ok) {
						failures.add(String.format(Locale.ROOT, "%s %s %d+ blocks: %.2f%%, vanilla %.2f%%", scale.getSerializedName(),
								NAMES[k], BINS[b], 100 * ours, 100 * v));
					}
				}
				double run = p.meanRun(k);
				double vRun = vanilla.meanRun(k);
				// Patches where both have enough runs at the level (the deep dark lies mostly below it: a handful of runs).
				if (vanilla.runs[k] >= 15 && p.runs[k] >= 15 && (run < vRun / 2 || run > vRun * 2)) {
					failures.add(String.format(Locale.ROOT, "%s %s patches: %.0f blocks, vanilla %.0f", scale.getSerializedName(),
							NAMES[k], 4 * run, 4 * vRun));
				}
			}
		}
		assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	@Test
	void neverNearTheSurfaceNorFromTheTopOfTheDecoration() {
		Random random = new Random(11);
		long[] climate = new long[CaveBiomes.CLIMATE];
		long[] costs = new long[CaveBiomes.KEYS.size()];
		int caveCells = 0;
		for (int i = 0; i < 4_000; i++) {
			int x = random.nextInt(100_000) - 50_000;
			int z = random.nextInt(100_000) - 50_000;
			caves.climate(x, z, climate, 0);
			CaveBiomes.costs(climate, 0, costs, 0);
			int top = 60 + random.nextInt(1_900);
			// The smoothed top may lie far above the column (a valley floor) or below it (a ridge).
			float smooth = top + (random.nextFloat() - 0.5F) * 120;
			for (int y = -64; y <= top + 4; y += 4) {
				int k = CaveBiomes.chooseByCosts(costs, 0, top, smooth, y);
				if (k == CaveBiomes.NONE) {
					continue;
				}
				caveCells++;
				assertTrue(y < CaveBiomes.MAX_Y, "cave biome at Y " + y);
				assertTrue(top - y >= CaveBiomes.MIN_BLOCKS_BELOW_TOP, "cave biome " + k + " " + (top - y) + " blocks below the top");
				assertEquals(k, CaveBiomes.chooseByCosts(costs.clone(), 0, top, smooth, y), "not a pure function");
			}
		}
		assertTrue(caveCells > 0, "no cave biome at all");
	}

	@Test
	void smoothTopInterpolatesTheLattice() {
		CaveBiomes.Tops tops = (x, z) -> x / CaveBiomes.LATTICE * 10 + z / CaveBiomes.LATTICE;
		assertEquals(0.0F, CaveBiomes.smoothTop(tops, 0, 0), 1e-6F);
		assertEquals(10.0F, CaveBiomes.smoothTop(tops, CaveBiomes.LATTICE, 0), 1e-6F);
		assertEquals(5.5F, CaveBiomes.smoothTop(tops, CaveBiomes.LATTICE / 2, CaveBiomes.LATTICE / 2), 1e-6F);
	}

	@Test
	void deepDarkIsDryInVanillaTerms() {
		// The aquifer exclusion of vanilla: erosion below -0.225 and depth above 0.9.
		assertTrue(CaveBiomes.dryDeepDark(-0.5F, 1.2F));
		assertFalse(CaveBiomes.dryDeepDark(-0.1F, 1.2F));
		assertFalse(CaveBiomes.dryDeepDark(-0.5F, 0.8F));
		// The deep dark lies in the dry region: its erosion range ends below -0.225, and at erosion -0.9 it wins from about
		// depth 1.05 (not at 0.9, where the dry region begins).
		Climate.ParameterPoint deepDark = CaveBiomes.point(CaveBiomes.DEEP_DARK);
		assertTrue(Climate.unquantizeCoord(deepDark.erosion().max()) < -0.225F);
		long[] costs = new long[CaveBiomes.KEYS.size()];
		CaveBiomes.costs(new long[] {0, 0, 0, Climate.quantizeCoord(-0.9F), 0}, 0, costs, 0);
		assertTrue(CaveBiomes.nearest(costs, 0, Climate.quantizeCoord(0.9F), 0) != CaveBiomes.DEEP_DARK);
		assertEquals(CaveBiomes.DEEP_DARK, CaveBiomes.nearest(costs, 0, Climate.quantizeCoord(1.3F), 0));
	}
}
