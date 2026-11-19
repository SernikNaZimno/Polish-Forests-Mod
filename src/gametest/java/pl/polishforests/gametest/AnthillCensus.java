package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import pl.polishforests.block.AnthillBlock;
import pl.polishforests.block.RootFlareBlock;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.command.HabitatTargets;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.AnthillFeature;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.plan.AnthillPlan;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * The census of the anthills (step FA8 of docs/04-pionowy-wycinek.md), part of {@code -Pgametest=habitats} in both scales:
 * <ul>
 * <li><b>plan</b>: over a square of {@value #PLAN_HALF} blocks around each census site, on the habitat classifier and the
 * stand map of the world, the area of each forest of the ant (one code per plan cell of {@value AnthillPlan#CELL} ×
 * {@value AnthillPlan#CELL} blocks), the spaced candidates in it and the sited ones; the density of the sited mounds per
 * hectare of each forest against {@link AnthillPlan#DENSITY} (G.62-g) and the measured sited share against
 * {@link AnthillPlan#SITED_SHARE} (the calibration of the plan; reported, the norm is checked on the sum of the forests);</li>
 * <li><b>world</b>: the columns of the generated chunks around the census sites (the 5 × 5 chunks of the tree census) with
 * an anthill block: by forest of the column, and the faults, which must be 0: a mound on a trunk, a root flare or a log, a
 * mound on a water column or in water, a mound outside the forests of the ant.</li>
 * </ul>
 */
final class AnthillCensus {
	/** Half the side of the square of the plan census around a site, blocks. */
	static final int PLAN_HALF = 2_048;
	/** The density of all the forests of the ant together may be this far from the norm (calibration in stage 2). */
	static final double DENSITY_TOLERANCE = 0.35;

	private AnthillCensus() {
	}

	/** The census; failures go to {@code failures}, the report is returned. */
	static String run(MinecraftServer server, List<int[]> sites, List<String> failures) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		long seed = level.getSeed();
		HabitatTargets.World world = new HabitatTargets.World(gen.model(seed), gen.classifier(seed));
		ForestStandMap stands = gen.standMap(seed);
		double k = gen.localScale();
		AnthillPlan.Site site = new AnthillPlan.Site() {
			@Override
			public int code(int x, int z) {
				return world.code(x, z);
			}

			@Override
			public double gapDepth(int x, int z) {
				return stands.gapDepth(x, z);
			}
		};
		Map<HabitatBiome, long[]> plan = new EnumMap<>(HabitatBiome.class);
		for (HabitatBiome b : AnthillPlan.FORESTS) {
			plan.put(b, new long[3]);
		}
		long start = System.nanoTime();
		for (int[] s : sites) {
			int i0 = Math.floorDiv(s[0] - PLAN_HALF, AnthillPlan.CELL);
			int j0 = Math.floorDiv(s[1] - PLAN_HALF, AnthillPlan.CELL);
			int n = 2 * PLAN_HALF / AnthillPlan.CELL;
			for (int i = i0; i < i0 + n; i++) {
				for (int j = j0; j < j0 + n; j++) {
					int code = world.code(i * AnthillPlan.CELL + AnthillPlan.CELL / 2, j * AnthillPlan.CELL + AnthillPlan.CELL / 2);
					long[] c = AnthillPlan.antForest(code) ? plan.get(Habitat.biome(code)) : null;
					if (c == null) {
						continue;
					}
					c[0]++;
					AnthillPlan.Anthill a = AnthillPlan.spaced(seed, i, j);
					if (a == null) {
						continue;
					}
					c[1]++;
					c[2] += AnthillPlan.siting(a, site, k) == null ? 1 : 0;
				}
			}
		}
		double cellHa = AnthillPlan.CELL * AnthillPlan.CELL / 10_000.0;
		List<String> parts = new ArrayList<>();
		long cells = 0;
		long candidates = 0;
		long sited = 0;
		for (Map.Entry<HabitatBiome, long[]> e : plan.entrySet()) {
			long[] c = e.getValue();
			cells += c[0];
			candidates += c[1];
			sited += c[2];
			parts.add(String.format(Locale.ROOT, "%s %.0f ha, %d candidates, %d sited (%.2f/ha, sited share %.2f)", e.getKey().id(),
					c[0] * cellHa, c[1], c[2], c[2] / Math.max(cellHa, c[0] * cellHa), (double) c[2] / Math.max(1, c[1])));
		}
		double density = sited / Math.max(cellHa, cells * cellHa);
		double share = (double) sited / Math.max(1, candidates);
		if (cells > 0 && Math.abs(density / AnthillPlan.DENSITY - 1) > DENSITY_TOLERANCE) {
			failures.add(String.format(Locale.ROOT, "anthills: plan density %.2f/ha in the forests of the ant, the norm %.2f/ha (G.62-g) "
					+ "± %.0f%%; the measured sited share %.2f against SITED_SHARE %.2f", density, AnthillPlan.DENSITY,
					100 * DENSITY_TOLERANCE, share, AnthillPlan.SITED_SHARE));
		}
		double planSeconds = (System.nanoTime() - start) / 1e9;
		// The generated chunks around the sites.
		Map<HabitatBiome, long[]> columns = new EnumMap<>(HabitatBiome.class);
		long onWood = 0;
		long onWater = 0;
		long outside = 0;
		long anthillColumns = 0;
		List<String> faults = new ArrayList<>();
		for (int[] s : sites) {
			int cx0 = (s[0] >> 4) - 2;
			int cz0 = (s[1] >> 4) - 2;
			for (int cx = cx0; cx < cx0 + 5; cx++) {
				for (int cz = cz0; cz < cz0 + 5; cz++) {
					ChunkAccess chunk = level.getChunk(cx, cz);
					ChunkHabitats h = chunk.getAttached(ModFeatures.CHUNK_HABITATS);
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							int wx = (cx << 4) + x;
							int wz = (cz << 4) + z;
							int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
							int lowest = Integer.MIN_VALUE;
							for (int y = surface; y >= surface - 4; y--) {
								if (level.getBlockState(new BlockPos(wx, y, wz)).getBlock() instanceof AnthillBlock) {
									lowest = y;
								}
							}
							if (lowest == Integer.MIN_VALUE) {
								continue;
							}
							anthillColumns++;
							BlockState below = level.getBlockState(new BlockPos(wx, lowest - 1, wz));
							int code = h == null ? -1 : h.code(x, z);
							boolean water = h != null && h.hasWater(ChunkHabitats.index(x, z))
									|| !level.getFluidState(new BlockPos(wx, lowest, wz)).isEmpty() || below.getFluidState().isSource();
							boolean wood = below.getBlock() instanceof TrunkBlock || below.getBlock() instanceof RootFlareBlock || below.is(BlockTags.LOGS);
							if (wood) {
								onWood++;
							}
							if (water) {
								onWater++;
							}
							if (code == -1 || !AnthillPlan.antForest(code)) {
								outside++;
							} else {
								columns.computeIfAbsent(Habitat.biome(code), b -> new long[1])[0]++;
							}
							if ((wood || water || code == -1 || !AnthillPlan.antForest(code)) && faults.size() < 10) {
								faults.add(String.format(Locale.ROOT, "(%d, %d, %d) on %s, %s", wx, lowest, wz, below,
										code == -1 ? "no habitat" : Habitat.biome(code).id()));
							}
						}
					}
				}
			}
		}
		if (onWood > 0 || onWater > 0 || outside > 0) {
			failures.add(String.format(Locale.ROOT, "anthills: %d columns on wood, %d on water, %d outside the forests of the ant (0 each): %s",
					onWood, onWater, outside, faults));
		}
		List<String> world2 = new ArrayList<>();
		columns.forEach((b, c) -> world2.add(b.id() + " " + c[0]));
		return String.format(Locale.ROOT, "anthills (step FA8): plan around %d sites (%.1f s): %.2f/ha in the forests of the ant (norm %.2f), "
				+ "sited share %.2f (SITED_SHARE %.2f); %s; world around the sites: %d anthill columns (%s), on wood %d, on water %d, "
				+ "outside the forests %d; %s", sites.size(), planSeconds, density, AnthillPlan.DENSITY, share, AnthillPlan.SITED_SHARE,
				String.join("; ", parts), anthillColumns, String.join(", ", world2), onWood, onWater, outside, AnthillFeature.stats());
	}
}
