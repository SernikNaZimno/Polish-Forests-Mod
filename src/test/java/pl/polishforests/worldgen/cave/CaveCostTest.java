package pl.polishforests.worldgen.cave;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Cost of the caves of step J1 per chunk (wall clock, no assertions; {@code ./gradlew costTest} on a quiet machine): the
 * surface plan of the chunk against the noise caves alone and the noise caves with the vanilla carvers, in the lowland
 * and in the mountains of both scales, medians of {@code -PcostRuns} rounds (default 7) over 64 chunks each.
 */
@Tag("cost")
class CaveCostTest {
	private static final int CHUNKS = 64;
	/** Keeps the counted blocks alive (no dead code in the timed loop). */
	static long sink;

	@Test
	void reportCostPerChunk() {
		int runs = Integer.getInteger("polishforests.cost.runs", 7);
		for (PolandScale scale : PolandScale.values()) {
			CaveFixture f = new CaveFixture(scale);
			int[] mountains = CaveFixture.find(0, 0, 512, 20_000, p -> {
				ColumnSample s = f.model.sample(p[0], p[1]);
				return s.type() == LandscapeType.BESKIDS && s.surface() > 800;
			});
			int[][] origins = {{200, -300}, mountains == null ? new int[] {0, 0} : new int[] {mountains[0] >> 4, mountains[1] >> 4}};
			String[] names = {"lowland", "mountains"};
			for (int o = 0; o < origins.length; o++) {
				ChunkSurface[] plans = new ChunkSurface[CHUNKS];
				double[] plan = new double[runs];
				double[] noise = new double[runs];
				double[] carvers = new double[runs];
				long carved = 0;
				for (int r = 0; r < runs; r++) {
					long t0 = System.nanoTime();
					for (int k = 0; k < CHUNKS; k++) {
						plans[k] = f.chunkPlan(origins[o][0] + (k & 7), origins[o][1] + (k >> 3));
					}
					long t1 = System.nanoTime();
					for (int k = 0; k < CHUNKS; k++) {
						f.caves.carve(new ChunkPos(origins[o][0] + (k & 7), origins[o][1] + (k >> 3)), plans[k], f.minY(), f.maxY(),
								f.randomState, f.columns(), null, null, null);
					}
					long t2 = System.nanoTime();
					carved = 0;
					for (int k = 0; k < CHUNKS; k++) {
						carved += f.caves.carve(new ChunkPos(origins[o][0] + (k & 7), origins[o][1] + (k >> 3)), plans[k], f.minY(),
								f.maxY(), f.randomState, f.columns(), null, null, f.carverContext).carvedBlocks();
					}
					long t3 = System.nanoTime();
					plan[r] = (t1 - t0) / 1e6 / CHUNKS;
					noise[r] = (t2 - t1) / 1e6 / CHUNKS;
					carvers[r] = (t3 - t2) / 1e6 / CHUNKS;
				}
				System.out.printf(Locale.ROOT, "[cave cost] %s %s: plan %.3f ms, noise caves %.3f ms, noise caves and carvers "
						+ "%.3f ms per chunk (medians of %d rounds of %d chunks; %d carved blocks per chunk)%n", scale, names[o],
						median(plan), median(noise), median(carvers), runs, CHUNKS, carved / CHUNKS);
			}
		}
	}

	/**
	 * For comparison, the caves of a vanilla overworld (decision CP-C.4a): the noise of 64 chunks with the vanilla final
	 * density and with the same density without the caves (the sloped cheese), both through the aquifer, and the three
	 * vanilla carvers; ms per chunk, medians.
	 */
	@Test
	void reportVanillaCaveCost() {
		int runs = Integer.getInteger("polishforests.cost.runs", 7);
		var r = CaveFixture.registries();
		var functions = r.lookupOrThrow(net.minecraft.core.registries.Registries.DENSITY_FUNCTION);
		var holder = r.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
				.getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
		var settings = holder.value();
		var random = net.minecraft.world.level.levelgen.RandomState.create(r.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
				CaveFixture.SEED, settings);
		var terrain = net.minecraft.world.level.levelgen.NoiseRouterData.postProcess(
				net.minecraft.world.level.levelgen.NoiseRouterData.slideOverworld(false, net.minecraft.world.level.levelgen.NoiseRouterData
						.getFunction(functions, net.minecraft.world.level.levelgen.NoiseRouterData.OVERWORLD_FUNCTIONS.slopedCheese())), 4, 8);
		var lava = new net.minecraft.world.level.levelgen.Aquifer.FluidStatus(-54,
				net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
		var sea = new net.minecraft.world.level.levelgen.Aquifer.FluidStatus(63,
				net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
		net.minecraft.world.level.levelgen.Aquifer.FluidPicker fluids = (x, y, z) -> y < -54 ? lava : sea;
		double[] full = new double[runs];
		double[] bare = new double[runs];
		for (int run = 0; run < runs; run++) {
			for (int pass = 0; pass < 2; pass++) {
				var function = pass == 0 ? settings.noiseRouter().finalDensity() : terrain;
				long t0 = System.nanoTime();
				long solid = 0;
				for (int k = 0; k < CHUNKS; k++) {
					ChunkPos pos = new ChunkPos(200 + (k & 7), -300 + (k >> 3));
					var volume = new net.minecraft.world.level.levelgen.densityfunction.DensityVolume(16, 384, 16, pos.getMinBlockX(), -64,
							pos.getMinBlockZ());
					try (var chunk = new net.minecraft.world.level.levelgen.NoiseChunk(random, null, settings, fluids,
							net.minecraft.world.level.levelgen.blending.Blender.empty(), volume);
							var d = chunk.cachingSamplers().get(function).sampleVolume(volume)) {
						var aquifer = chunk.aquifer();
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								for (int y = 0; y < 384; y++) {
									if (aquifer.computeSubstance(pos.getMinBlockX() + x, y - 64, pos.getMinBlockZ() + z,
											d.get(volume.indexUnchecked(x, y, z))) == null) {
										solid++;
									}
								}
							}
						}
					}
				}
				double ms = (System.nanoTime() - t0) / 1e6 / CHUNKS;
				sink += solid;
				if (pass == 0) {
					full[run] = ms;
				} else {
					bare[run] = ms;
				}
			}
		}
		System.out.printf(Locale.ROOT, "[cave cost] vanilla overworld: noise with caves %.3f ms, without caves %.3f ms per chunk "
				+ "(medians of %d rounds of %d chunks; the vanilla carvers are not in the noise)%n", median(full), median(bare), runs,
				CHUNKS);
	}

	private static double median(double[] v) {
		double[] s = v.clone();
		Arrays.sort(s);
		return s[s.length / 2];
	}
}
