package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * The census of the anthills (step FA8 of docs/04-pionowy-wycinek.md), part of {@code -Pgametest=habitats} in both scales:
 * <ul>
 * <li><b>plan</b>: over a square of {@value #PLAN_HALF} blocks around each census site, on the habitat classifier and the
 * stand map of the world, the area of each forest of the ant (one code per plan cell of {@value AnthillPlan#CELL} ×
 * {@value AnthillPlan#CELL} blocks: the code of its center), the drawn candidates of these cells and the mounds of the plan
 * ({@link AnthillPlan#sited}); the density of the mounds per hectare of each forest, and on the pine forests together
 * ({@link AnthillPlan#LIGHT_FORESTS}, G.62-g: "of the pine forests"), and their measured sited share against
 * {@link AnthillPlan#SITED_SHARE} (the calibration of the plan). The mixed forest and the montane spruce forest (mounds only
 * on edges and in gaps) are reported;</li>
 * <li><b>standing</b> (FA8 review round 1): the home chunks of the {@value #SAMPLE_PER_SITE} planned mounds of the pine
 * forests nearest each site are generated and checked for a standing mound ({@link AnthillFeature#standing}, as
 * {@code find anthill} does): the written share of the plan against {@link AnthillPlan#WRITTEN_SHARE} (within
 * {@value #WRITTEN_TOLERANCE}, round 2: the calibration of the cell chance), and the density of the standing mounds (the
 * plan density times the written share) against {@link AnthillPlan#DENSITY} (G.62-g, the mounds that stand in the world),
 * with the distance of a standing mound from the plan's position. Round 2: the sample is generated site by site
 * ({@link #sample}), and between the sites the game test lets the server unload the chunks it no longer needs
 * ({@link #release}; in round 1 the whole sample in one server task ran the heap out in the next checks);</li>
 * <li><b>world</b>: the columns of the generated chunks around the census sites (the 5 × 5 chunks of the tree census) with
 * an anthill block: by forest of the column, and the faults, which must be 0: a mound on a trunk, a root flare or a log, a
 * mound on a water column or in water, a mound outside the forests of the ant;</li>
 * <li><b>written</b> (stage 2 of FA8): every mound the feature wrote since {@link #record} (observer
 * {@link AnthillFeature#PLACEMENTS}), with the same faults per column (the census chunks alone held none
 * in both scales); a column whose mound a later tree of a neighbor chunk took is counted, not a fault. Round 2: a chunk
 * that is not loaded in full is read at the status of the features (its mound is written by then), so the census does
 * not generate the neighbors of every mound's chunk.</li>
 * </ul>
 */
final class AnthillCensus {
	/** Half the side of the square of the plan census around a site, blocks. */
	static final int PLAN_HALF = 2_048;
	/** The density of the pine forests together may be this far from the norm. */
	static final double DENSITY_TOLERANCE = 0.35;
	/** FA8 review round 1: the planned mounds of the pine forests per site whose home chunks the census generates. */
	static final int SAMPLE_PER_SITE = 8;
	/** FA8 review round 1: the least sample of the written share. */
	static final int SAMPLE_MIN = 20;
	/** FA8 review round 2: the measured written share may be this far from {@link AnthillPlan#WRITTEN_SHARE}. */
	static final double WRITTEN_TOLERANCE = 0.15;
	/** FA8 review round 2: {@link #release} waits at most this many ticks, and stops after this many without progress. */
	static final int RELEASE_MAX_TICKS = 400;
	static final int RELEASE_STALL_TICKS = 60;

	private AnthillCensus() {
	}

	/** The mounds the feature wrote since {@link #record}: the corner (ground + 1 of the lowest column) and the width. */
	private static final List<int[]> WRITTEN = java.util.Collections.synchronizedList(new ArrayList<>());

	/** Starts recording the written mounds (before the world is created; the list is cleared). */
	static void record() {
		WRITTEN.clear();
		AnthillFeature.PLACEMENTS = (corner, a) -> WRITTEN.add(new int[] {corner.getX(), corner.getY(), corner.getZ(), a.width()});
	}

	/** Stops recording. */
	static void stopRecording() {
		AnthillFeature.PLACEMENTS = null;
	}

	/**
	 * A census between its phases (FA8 review round 2): {@link #plan} on the server thread, {@link #sample} of each site on
	 * the server thread with {@link #release} on the test thread in between, {@link #finish} on the server thread.
	 */
	static final class Census {
		final List<int[]> sites;
		/** Per site the planned mounds of the pine forests: {distance, center x, center z, chunk x, chunk z}, nearest first. */
		final List<List<double[]>> nearestBySite = new ArrayList<>();
		final java.util.Set<Long> sampled = new java.util.HashSet<>();
		long cells;
		double density;
		double share;
		double planSeconds;
		List<String> parts = List.of();
		long sample;
		long standing;
		long atPlan;
		double offsetSum;
		double offsetMax;
		double sampleSeconds;
		int releaseTicks;
		int loadedPeak;
		long heapPeakMb;

		Census(List<int[]> sites) {
			this.sites = List.copyOf(sites);
		}
	}

	/** The plan census around the sites (server thread). */
	static Census plan(MinecraftServer server, List<int[]> sites) {
		ServerLevel level = server.overworld();
		Census c = new Census(sites);
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

			@Override
			public boolean lightStand(int x, int z) {
				return stands.ageClass(x, z) != AgeClass.POLE_STAGE;
			}
		};
		Map<HabitatBiome, long[]> plan = new EnumMap<>(HabitatBiome.class);
		for (HabitatBiome b : AnthillPlan.FORESTS) {
			plan.put(b, new long[3]);
		}
		long start = System.nanoTime();
		// FA8 review round 1: per site the planned mounds of the pine forests by distance (the sample of the written share).
		List<List<double[]>> nearestBySite = c.nearestBySite;
		for (int[] s : sites) {
			List<double[]> near = new ArrayList<>();
			nearestBySite.add(near);
			int i0 = Math.floorDiv(s[0] - PLAN_HALF, AnthillPlan.CELL);
			int j0 = Math.floorDiv(s[1] - PLAN_HALF, AnthillPlan.CELL);
			int n = 2 * PLAN_HALF / AnthillPlan.CELL;
			for (int i = i0; i < i0 + n; i++) {
				for (int j = j0; j < j0 + n; j++) {
					int code = world.code(i * AnthillPlan.CELL + AnthillPlan.CELL / 2, j * AnthillPlan.CELL + AnthillPlan.CELL / 2);
					long[] f = AnthillPlan.antForest(code) ? plan.get(Habitat.biome(code)) : null;
					if (f == null) {
						continue;
					}
					f[0]++;
					if (AnthillPlan.candidate(seed, i, j) == null) {
						continue;
					}
					f[1]++;
					AnthillPlan.Anthill mound = AnthillPlan.sited(seed, i, j, site, k);
					if (mound == null) {
						continue;
					}
					f[2]++;
					if (AnthillPlan.LIGHT_FORESTS.contains(Habitat.biome(code))) {
						near.add(new double[] {Math.hypot(mound.centerX() - s[0], mound.centerZ() - s[1]), mound.centerX(),
								mound.centerZ(), mound.chunkX(), mound.chunkZ()});
					}
				}
			}
		}
		double cellHa = AnthillPlan.CELL * AnthillPlan.CELL / 10_000.0;
		List<String> parts = new ArrayList<>();
		long cells = 0;
		long candidates = 0;
		long sited = 0;
		for (Map.Entry<HabitatBiome, long[]> e : plan.entrySet()) {
			long[] f = e.getValue();
			if (AnthillPlan.LIGHT_FORESTS.contains(e.getKey())) {
				cells += f[0];
				candidates += f[1];
				sited += f[2];
			}
			parts.add(String.format(Locale.ROOT, "%s %.0f ha, %d candidates, %d mounds (%.2f/ha, sited share %.2f)", e.getKey().id(),
					f[0] * cellHa, f[1], f[2], f[2] / Math.max(cellHa, f[0] * cellHa), (double) f[2] / Math.max(1, f[1])));
		}
		c.cells = cells;
		c.density = sited / Math.max(cellHa, cells * cellHa);
		c.share = (double) sited / Math.max(1, candidates);
		c.parts = parts;
		c.planSeconds = (System.nanoTime() - start) / 1e9;
		for (List<double[]> near : nearestBySite) {
			near.sort(java.util.Comparator.comparingDouble(d -> d[0]));
		}
		return c;
	}

	/**
	 * The standing mounds of the sample of one site (server thread): the home chunks of its {@value #SAMPLE_PER_SITE}
	 * nearest planned mounds of the pine forests are generated in full and checked as {@code find anthill} does.
	 */
	static void sample(MinecraftServer server, Census c, int siteIndex) {
		long start = System.nanoTime();
		ServerLevel level = server.overworld();
		int taken = 0;
		for (double[] d : c.nearestBySite.get(siteIndex)) {
			if (taken == SAMPLE_PER_SITE) {
				break;
			}
			int hx = (int) d[3];
			int hz = (int) d[4];
			if (!c.sampled.add((long) hx << 32 | hz & 0xFFFF_FFFFL)) {
				continue;
			}
			taken++;
			c.sample++;
			int[] m = AnthillFeature.standing(level.getChunk(hx, hz));
			if (m == null) {
				continue;
			}
			c.standing++;
			double offset = Math.hypot(m[0] + 0.5 - d[1], m[2] + 0.5 - d[2]);
			c.atPlan += offset <= 1.5 ? 1 : 0;
			c.offsetSum += offset;
			c.offsetMax = Math.max(c.offsetMax, offset);
		}
		c.sampleSeconds += (System.nanoTime() - start) / 1e9;
		c.loadedPeak = Math.max(c.loadedPeak, level.getChunkSource().getLoadedChunksCount());
		Runtime r = Runtime.getRuntime();
		c.heapPeakMb = Math.max(c.heapPeakMb, (r.totalMemory() - r.freeMemory()) >> 20);
	}

	/**
	 * Lets the server unload the chunks of a site's sample (test thread): waits while more than {@code baseline} chunks are
	 * loaded and their number still falls, at most {@value #RELEASE_MAX_TICKS} ticks.
	 */
	static void release(ClientGameTestContext context, TestSingleplayerContext sp, Census c, int baseline) {
		int best = loaded(sp);
		int stall = 0;
		int t = 0;
		while (t < RELEASE_MAX_TICKS && best > baseline && stall < RELEASE_STALL_TICKS) {
			context.waitTicks(10);
			t += 10;
			int now = loaded(sp);
			stall = now < best ? 0 : stall + 10;
			best = Math.min(best, now);
		}
		c.releaseTicks += t;
	}

	/** The chunks the server holds (of any status). */
	static int loaded(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getLoadedChunksCount());
	}

	/** The checks of the standing sample and of the written mounds (server thread); failures go to {@code failures}. */
	static String finish(MinecraftServer server, Census c, List<String> failures) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		HabitatTargets.World world = new HabitatTargets.World(gen.model(level.getSeed()), gen.classifier(level.getSeed()));
		List<int[]> sites = c.sites;
		double density = c.density;
		double writtenShare = (double) c.standing / Math.max(1, c.sample);
		double standingDensity = density * writtenShare;
		if (c.sample < SAMPLE_MIN) {
			failures.add(String.format(Locale.ROOT, "anthills: only %d planned mounds of the pine forests sampled (at least %d)",
					c.sample, SAMPLE_MIN));
		} else {
			if (c.cells > 0 && Math.abs(standingDensity / AnthillPlan.DENSITY - 1) > DENSITY_TOLERANCE) {
				failures.add(String.format(Locale.ROOT, "anthills: standing density %.2f/ha in the pine forests (plan %.2f/ha times "
						+ "the written share %.2f, %d of %d), the norm %.2f/ha (G.62-g) +- %.0f%%; the measured sited share %.2f "
						+ "against SITED_SHARE %.2f, WRITTEN_SHARE %.2f", standingDensity, density, writtenShare, c.standing, c.sample,
						AnthillPlan.DENSITY, 100 * DENSITY_TOLERANCE, c.share, AnthillPlan.SITED_SHARE, AnthillPlan.WRITTEN_SHARE));
			}
			if (Math.abs(writtenShare - AnthillPlan.WRITTEN_SHARE) > WRITTEN_TOLERANCE) {
				failures.add(String.format(Locale.ROOT, "anthills: written share %.2f (%d of %d sampled home chunks), WRITTEN_SHARE "
						+ "%.2f +- %.2f: recalibrate WRITTEN_SHARE and CELL_CHANCE", writtenShare, c.standing, c.sample,
						AnthillPlan.WRITTEN_SHARE, WRITTEN_TOLERANCE));
			}
		}
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
		// Every mound written in this world (its chunk is generated; a saved chunk is loaded again).
		List<int[]> written;
		synchronized (WRITTEN) {
			written = new ArrayList<>(WRITTEN);
		}
		long mounds = 0;
		long writtenColumns = 0;
		long taken = 0;
		Map<HabitatBiome, long[]> writtenByForest = new EnumMap<>(HabitatBiome.class);
		for (int[] m : written) {
			mounds++;
			// Round 2: the chunk loaded in full, or else at the status of the features (no neighbors generated for it); the
			// feature writes the whole mound into this one chunk.
			ChunkAccess loadedChunk = level.getChunkSource().getChunkNow(m[0] >> 4, m[2] >> 4);
			ChunkAccess chunk = loadedChunk != null ? loadedChunk
					: level.getChunkSource().getChunk(m[0] >> 4, m[2] >> 4, ChunkStatus.FEATURES, true);
			ChunkHabitats h = chunk.getAttached(ModFeatures.CHUNK_HABITATS);
			for (int dx = 0; dx < m[3]; dx++) {
				for (int dz = 0; dz < m[3]; dz++) {
					int wx = m[0] + dx;
					int wz = m[2] + dz;
					int lowest = Integer.MIN_VALUE;
					for (int y = m[1] + 2; y >= m[1] - 1; y--) {
						if (chunk.getBlockState(new BlockPos(wx, y, wz)).getBlock() instanceof AnthillBlock) {
							lowest = y;
						}
					}
					if (lowest == Integer.MIN_VALUE) {
						taken++;
						continue;
					}
					writtenColumns++;
					BlockState below = chunk.getBlockState(new BlockPos(wx, lowest - 1, wz));
					// A chunk saved and loaded again has no habitats attached: the classifier's code (the plan's site).
					int code = h != null ? h.code(wx & 15, wz & 15) : world.code(wx, wz);
					boolean water = h != null && h.hasWater(ChunkHabitats.index(wx & 15, wz & 15))
							|| !chunk.getFluidState(new BlockPos(wx, lowest, wz)).isEmpty() || below.getFluidState().isSource();
					boolean wood = below.getBlock() instanceof TrunkBlock || below.getBlock() instanceof RootFlareBlock || below.is(BlockTags.LOGS);
					onWood += wood ? 1 : 0;
					onWater += water ? 1 : 0;
					if (code == -1 || !AnthillPlan.antForest(code)) {
						outside++;
					} else {
						writtenByForest.computeIfAbsent(Habitat.biome(code), b -> new long[1])[0]++;
					}
					if ((wood || water || code == -1 || !AnthillPlan.antForest(code)) && faults.size() < 10) {
						faults.add(String.format(Locale.ROOT, "written (%d, %d, %d) on %s, %s", wx, lowest, wz, below,
								code == -1 ? "no habitat" : Habitat.biome(code).id()));
					}
				}
			}
		}
		if (mounds == 0) {
			failures.add("anthills: the feature wrote no mound in this world");
		}
		if (onWood > 0 || onWater > 0 || outside > 0) {
			failures.add(String.format(Locale.ROOT, "anthills: %d columns on wood, %d on water, %d outside the forests of the ant (0 each): %s",
					onWood, onWater, outside, faults));
		}
		List<String> world2 = new ArrayList<>();
		columns.forEach((b, n) -> world2.add(b.id() + " " + n[0]));
		List<String> written2 = new ArrayList<>();
		writtenByForest.forEach((b, n) -> written2.add(b.id() + " " + n[0]));
		Runtime r = Runtime.getRuntime();
		return String.format(Locale.ROOT, "anthills (step FA8): plan around %d sites (%.1f s): %.2f/ha in the pine forests, "
				+ "sited share %.2f (SITED_SHARE %.2f); standing (sample of %d home chunks, %.1f s, released in %d ticks, at most "
				+ "%d chunks loaded and %d MB heap used, now %d chunks and %d MB): %d written (share %.2f, WRITTEN_SHARE %.2f "
				+ "+- %.2f), %d at the plan's position (up to 1.5 blocks), offset mean %.1f, max %.1f blocks; standing density "
				+ "%.2f/ha in the pine forests (norm %.2f +- %.0f%%); %s; world around the sites: %d anthill columns (%s); "
				+ "written mounds: %d, %d columns (%s), %d columns taken by later trees; faults: on wood %d, on water %d, outside "
				+ "the forests %d; %s", sites.size(), c.planSeconds, density, c.share, AnthillPlan.SITED_SHARE, c.sample,
				c.sampleSeconds, c.releaseTicks, c.loadedPeak, c.heapPeakMb, level.getChunkSource().getLoadedChunksCount(),
				(r.totalMemory() - r.freeMemory()) >> 20, c.standing, writtenShare, AnthillPlan.WRITTEN_SHARE, WRITTEN_TOLERANCE,
				c.atPlan, c.offsetSum / Math.max(1, c.standing), c.offsetMax, standingDensity, AnthillPlan.DENSITY,
				100 * DENSITY_TOLERANCE, String.join("; ", c.parts), anthillColumns,
				String.join(", ", world2), mounds, writtenColumns, String.join(", ", written2), taken, onWood, onWater, outside,
				AnthillFeature.stats());
	}
}
