package pl.polishforests.worldgen.cave;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * Step J1 criteria: the caves of the Poland worlds against a vanilla overworld of the same seed. The share of cave
 * blocks (air, water, lava) by depth below the surface without caves, and the cave entrances (connected groups of
 * land columns whose top block the caves opened) per km², on four blocks of 6 × 6 chunks in each world, with the noise
 * caves, aquifers and the three vanilla carvers. Vanilla: the overworld router, its aquifers and carvers, the surface
 * without caves from its sloped cheese (the terrain density that vanilla takes the caves out of); the columns of plains,
 * with the top at Y 63–100. Poland: the plans and caves of {@link CaveFixture}, the dry columns.
 */
@Tag("slow")
class CaveProfileTest {
	/** Depth bins (blocks below the top block): lower bounds, the last bin open to the deepest counted block. */
	private static final int[] BINS = {0, 5, 10, 20, 40, 80, 160};
	/** Deepest Y counted (above the bedrock and the vanilla bottom slide). */
	private static final int FLOOR_Y = -40;
	private static final int SIDE = 6;
	/** Absolute Y bands of the report (lower bounds). */
	private static final int[] BANDS = {-40, 0, 32, 63, 100, 150, 250};
	private static final int[][] AREAS = {{200, -300}, {-1_890, 1_250}, {40, 40}, {-600, 900}};

	/** Counts of one world. */
	private static final class Profile {
		final long[] cave = new long[BINS.length];
		final long[] all = new long[BINS.length];
		final long[] caveY = new long[BANDS.length];
		final long[] allY = new long[BANDS.length];
		long columns;
		long openedColumns;
		long entrances;
		double km2;

		void add(int depth, int y, boolean isCave) {
			int band = BANDS.length - 1;
			while (band > 0 && y < BANDS[band]) {
				band--;
			}
			allY[band]++;
			if (isCave) {
				caveY[band]++;
			}
			int b = BINS.length - 1;
			while (b > 0 && depth < BINS[b]) {
				b--;
			}
			all[b]++;
			if (isCave) {
				cave[b]++;
			}
		}

		double share(int b) {
			return all[b] == 0 ? Double.NaN : (double) cave[b] / all[b];
		}

		double entrancesPerKm2() {
			return entrances / km2;
		}

		String describe(String name) {
			StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-10s", name));
			for (int b = 0; b < BINS.length; b++) {
				sb.append(String.format(Locale.ROOT, " %3d+: %5.2f%% (%d)", BINS[b], 100 * share(b), all[b]));
			}
			sb.append(" | by Y:");
			for (int b = 0; b < BANDS.length; b++) {
				sb.append(String.format(Locale.ROOT, " %d+: %.2f%%", BANDS[b], allY[b] == 0 ? Double.NaN : 100.0 * caveY[b] / allY[b]));
			}
			sb.append(String.format(Locale.ROOT, " | opened tops %.3f%%, entrances %.1f per km2", 100.0 * openedColumns / columns,
					entrancesPerKm2()));
			return sb.toString();
		}
	}

	@Test
	void cavesFollowTheVanillaDepthProfile() {
		Profile vanilla = vanilla();
		List<Profile> poland = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			poland.add(poland(new CaveFixture(scale)));
		}
		System.out.println(vanilla.describe("vanilla"));
		System.out.println(poland.get(0).describe("REALISTIC"));
		System.out.println(poland.get(1).describe("GAMEPLAY"));
		for (int k = 0; k < poland.size(); k++) {
			Profile p = poland.get(k);
			String scale = PolandScale.values()[k].name();
			// Within 20 blocks of the surface the cave kinds follow the depth below the Poland surface, as in vanilla: within a
			// factor 2. Deeper, within a factor 3: under a vanilla plain (Y 63-100) the blocks 20-40 deep lie at Y 30-50,
			// where the barriers of the aquifers flooded to the sea level (Y 63) wall off part of the caves (vanilla 3.6%
			// there, 6.5-6.8% above and below); under the Poland lowlands (Y 130-250) the same depth is far above the sea.
			for (int b = 0; b < BINS.length - 1; b++) {
				double ratio = p.share(b) / vanilla.share(b);
				double limit = BINS[b] < 20 ? 2.0 : 3.0;
				assertTrue(ratio > 1 / limit && ratio < limit, String.format(Locale.ROOT, "%s: cave share %.2f%% at %d+ blocks "
						+ "deep, vanilla %.2f%%", scale, 100 * p.share(b), BINS[b], 100 * vanilla.share(b)));
			}
			double entrances = p.entrancesPerKm2() / vanilla.entrancesPerKm2();
			assertTrue(entrances > 0.5 && entrances < 2.0, String.format(Locale.ROOT, "%s: %.1f entrances per km2, vanilla %.1f",
					scale, p.entrancesPerKm2(), vanilla.entrancesPerKm2()));
		}
	}

	private static Profile poland(CaveFixture f) {
		Profile p = new Profile();
		for (int[] area : AREAS) {
			boolean[][] opened = new boolean[SIDE * 16][SIDE * 16];
			boolean[][] land = new boolean[SIDE * 16][SIDE * 16];
			for (int dx = 0; dx < SIDE; dx++) {
				for (int dz = 0; dz < SIDE; dz++) {
					CaveFixture.Chunk c = f.chunk(area[0] + dx, area[1] + dz, true);
					for (int i = 0; i < 256; i++) {
						if (c.surface().wet(i)) {
							continue;
						}
						int top = c.surface().top(i);
						land[dx * 16 + (i >> 4)][dz * 16 + (i & 15)] = true;
						p.columns++;
						if (c.caves().carved(i, top)) {
							opened[dx * 16 + (i >> 4)][dz * 16 + (i & 15)] = true;
							p.openedColumns++;
						}
						for (int y = top; y >= FLOOR_Y; y--) {
							p.add(top - y, y, c.caves().carved(i, y));
						}
					}
				}
			}
			p.entrances += groups(opened);
			p.km2 += SIDE * 16 * SIDE * 16 / 1e6;
		}
		return p;
	}

	private static Profile vanilla() {
		HolderLookup.Provider r = CaveFixture.registries();
		HolderGetter<DensityFunction> functions = r.lookupOrThrow(Registries.DENSITY_FUNCTION);
		Holder<NoiseGeneratorSettings> holder = r.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		NoiseGeneratorSettings settings = holder.value();
		RandomState random = RandomState.create(r.lookupOrThrow(Registries.NOISE), CaveFixture.SEED, settings);
		DensityFunction terrain = NoiseRouterData.postProcess(NoiseRouterData.slideOverworld(false,
				NoiseRouterData.getFunction(functions, NoiseRouterData.OVERWORLD_FUNCTIONS.slopedCheese())), 4, 8);
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
				new FixedBiomeSource(r.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)), holder);
		WorldGenerationContext context = new WorldGenerationContext(generator, LevelHeightAccessor.create(-64, 384));
		HolderGetter<WorldCarver> carverLookup = r.lookupOrThrow(Registries.CARVER);
		List<WorldCarver> carvers = List.of(carverLookup.getOrThrow(Carvers.CAVE).value(),
				carverLookup.getOrThrow(Carvers.CAVE_EXTRA_UNDERGROUND).value(), carverLookup.getOrThrow(Carvers.CANYON).value());
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
		Aquifer.FluidStatus sea = new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
		Aquifer.FluidPicker fluids = (x, y, z) -> y < -54 ? lava : sea;
		Profile p = new Profile();
		for (int[] area : AREAS) {
			boolean[][] opened = new boolean[SIDE * 16][SIDE * 16];
			for (int dx = 0; dx < SIDE; dx++) {
				for (int dz = 0; dz < SIDE; dz++) {
					ChunkPos pos = new ChunkPos(area[0] + dx, area[1] + dz);
					DensityVolume volume = new DensityVolume(16, 384, 16, pos.getMinBlockX(), -64, pos.getMinBlockZ());
					boolean[] solid = new boolean[16 * 16 * 384];
					int[] tops = new int[256];
					try (NoiseChunk chunk = new NoiseChunk(random, null, settings, fluids, Blender.empty(), volume)) {
						Aquifer aquifer = chunk.aquifer();
						DensitySampler.Bound full = chunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
						DensitySampler.Bound bare = chunk.cachingSamplers().get(terrain);
						try (ScopedDensityBuffer d = full.sampleVolume(volume); ScopedDensityBuffer t = bare.sampleVolume(volume)) {
							for (int z = 0; z < 16; z++) {
								for (int x = 0; x < 16; x++) {
									tops[x << 4 | z] = Integer.MIN_VALUE;
									for (int y = 383; y >= 0; y--) {
										int k = volume.indexUnchecked(x, y, z);
										if (tops[x << 4 | z] == Integer.MIN_VALUE && t.get(k) > 0) {
											tops[x << 4 | z] = y - 64;
										}
										BlockState s = aquifer.computeSubstance(pos.getMinBlockX() + x, y - 64, pos.getMinBlockZ() + z, d.get(k));
										solid[(x << 4 | z) * 384 + y] = s == null;
									}
								}
							}
						}
						CarvingMask mask = new CarvingMask(context.getMinGenY() + 1, context.getMinGenY() + context.getGenDepth() - 8);
						WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
						for (int sx = -8; sx <= 8; sx++) {
							for (int sz = -8; sz <= 8; sz++) {
								ChunkPos source = new ChunkPos(pos.x() + sx, pos.z() + sz);
								for (int index = 0; index < carvers.size(); index++) {
									rnd.setLargeFeatureSeed(random.seed() + index, source.x(), source.z());
									if (carvers.get(index).isStartChunk(rnd)) {
										carvers.get(index).carve(context, rnd, pos, source, mask);
									}
								}
							}
						}
						mask.visit((x, z, bottomY, topY) -> {
							for (int y = topY; y >= bottomY; y--) {
								int k = (x << 4 | z) * 384 + y + 64;
								if (solid[k] && aquifer.computeSubstance(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z, 0.0) != null) {
									solid[k] = false;
								}
							}
						});
					}
					for (int i = 0; i < 256; i++) {
						int top = tops[i];
						if (top < 63 || top > 100) {
							continue;
						}
						p.columns++;
						if (!solid[i * 384 + top + 64]) {
							opened[dx * 16 + (i >> 4)][dz * 16 + (i & 15)] = true;
							p.openedColumns++;
						}
						for (int y = top; y >= FLOOR_Y; y--) {
							p.add(top - y, y, !solid[i * 384 + y + 64]);
						}
					}
				}
			}
			p.entrances += groups(opened);
			p.km2 += SIDE * 16 * SIDE * 16 / 1e6;
		}
		return p;
	}

	/** Number of 8-connected groups of opened columns. */
	private static int groups(boolean[][] opened) {
		int n = opened.length;
		boolean[][] seen = new boolean[n][n];
		int groups = 0;
		int[] stack = new int[n * n];
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				if (!opened[x][z] || seen[x][z]) {
					continue;
				}
				groups++;
				int sp = 0;
				stack[sp++] = x * n + z;
				seen[x][z] = true;
				while (sp > 0) {
					int c = stack[--sp];
					int cx = c / n;
					int cz = c % n;
					for (int ax = Math.max(0, cx - 1); ax <= Math.min(n - 1, cx + 1); ax++) {
						for (int az = Math.max(0, cz - 1); az <= Math.min(n - 1, cz + 1); az++) {
							if (opened[ax][az] && !seen[ax][az]) {
								seen[ax][az] = true;
								stack[sp++] = ax * n + az;
							}
						}
					}
				}
			}
		}
		return groups;
	}
}
