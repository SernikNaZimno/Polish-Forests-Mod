package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.tree.TreeForm;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeSize;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Step OP1 of docs/04-pionowy-wycinek.md (generation optimized without a change of the result): the memos of the step
 * give the plans computed before it.
 *
 * <ul>
 * <li>The procedural trees of stands planned with the world's stand map (mixed age classes, managed and natural stands,
 * canopy gaps), on rolling ground, in both scales: the hash of every planned voxel (wood with radius, parent and bark,
 * leaves with their distances) equals the hash recorded on the base commit of the step (121dbbf), and planning the same
 * trees in four threads in shuffled order gives the same plans (the split surfaces shared by the threads, the hollow core
 * of the crowns).</li>
 * <li>The wide blend's lattice of a whole loaded area (as the load of the performance game test: an area of 44 × 44
 * chunks around three points) is sampled about once per lattice point, so the memo keeps the lattice of an area.</li>
 * </ul>
 */
class GenerationMemoTest {
	private static final long SEED = 20260927L;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();
	private static final float[] RAMP = {1, 1, 1, 1};
	/** Hashes of {@link #plans} for k = 1 and k = 0.5 on the base commit of step OP1 (121dbbf). */
	private static final long[] BEFORE_OP1 = {-7238326904233756416L, -3410524577272307493L};

	/** A planned tree: its request (for a second plan) and the hash of its voxels. */
	private record Planned(TreeRequest request, long hash) {
	}

	private static long hash(TreeVoxels v) {
		long h = 1125899906842597L;
		h = 31 * h + v.x();
		h = 31 * h + v.y();
		h = 31 * h + v.z();
		h = 31 * h + Float.floatToIntBits(v.height());
		for (int i = 0; i < v.woodCount(); i++) {
			h = 31 * h + v.woodX(i);
			h = 31 * h + v.woodY(i);
			h = 31 * h + v.woodZ(i);
			h = 31 * h + v.radius(i);
			h = 31 * h + v.parent(i);
			h = 31 * h + v.bark(i);
		}
		for (int i = 0; i < v.leafCount(); i++) {
			h = 31 * h + v.leafX(i);
			h = 31 * h + v.leafY(i);
			h = 31 * h + v.leafZ(i);
			h = 31 * h + v.leafDistance(i);
		}
		return h;
	}

	/**
	 * The procedural trees of chunks c0 … c0 + 5 (both axes) of an oak-hornbeam forest and a fresh pine forest with the
	 * world's stand map of scale k, on rolling ground, in the order of the decoration (chunk by chunk).
	 */
	private static List<Planned> plans(double k) {
		ForestStandMap map = new ForestStandMap(SEED, k, 0.85);
		CrownTerritories crowns = new CrownTerritories(SEED, map);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(RAMP, new Noise(SEED).derive("feature.tree_stand.gaps"), k, map, crowns);
		Noise relief = new Noise(SEED).derive("test.relief");
		TreeShapePlan.Ground ground = (x, z) -> 64 + (int) Math.round(3 * relief.at(x, z, 40));
		List<Planned> out = new ArrayList<>();
		HabitatBiome[] biomes = {HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST};
		int[] c0 = {-40, 300};
		for (int f = 0; f < biomes.length; f++) {
			HabitatBiome biome = biomes[f];
			int code = Habitat.pack(biome, Zone.NONE, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(biome),
					Species.FLAG_HORNBEAM, Soil.forBiome(biome, Zone.NONE));
			int[] codes = new int[256];
			Arrays.fill(codes, code);
			int[] region = new int[Ecotone.SPAN * Ecotone.SPAN];
			Arrays.fill(region, code);
			CrownTerritories.Session session = crowns.session();
			for (int a = c0[f]; a < c0[f] + 6; a++) {
				for (int b = c0[f]; b < c0[f] + 6; b++) {
					int[] plan = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, a, b, stand);
					Ecotone.Region r = new Ecotone.Region(a, b, region, k);
					StandVacancies v = new StandVacancies(a, b, plan, r, GENERATED.palette, stand, new StandVacancies.Neighbors() {
						@Override
						public int waterDepth(int x, int z) {
							return 0;
						}

						@Override
						public float[] ramp(int chunkX, int chunkZ) {
							return RAMP;
						}
					}, new TreeStandPlan.Candidates(SEED), SEED);
					for (int p : plan) {
						int t = TreeStandPlan.tree(p);
						if (!GENERATED.procedural(t)) {
							continue;
						}
						int c = TreeStandPlan.column(p);
						int x = (a << 4) + (c >> 4);
						int z = (b << 4) + (c & 15);
						CrownLayer layer = TreeStandPlan.layer(p);
						CrownTerritory territory = session.territory(x, z, v);
						AgeClass age = layer == CrownLayer.REGENERATION && map.canopyGap(x, z) ? AgeClass.POLE_STAGE : map.ageClass(x, z);
						TreeRequest request = new TreeRequest(GENERATED.species.get(t), layer, age, SEED, x, ground.top(x, z) + 1, z, territory,
								session::candidate, map, ground, null, TreeSize.ONE, TreeForm.of(GENERATED.forms.get(t)));
						out.add(new Planned(request, hash(TreeShapePlan.plan(request))));
					}
				}
			}
		}
		return out;
	}

	private static long total(List<Planned> plans) {
		long h = 17;
		for (Planned p : plans) {
			h = 31 * h + p.hash();
		}
		return h;
	}

	@Test
	void plannedTreesAsBeforeTheStepInAnyOrderAndThread() throws Exception {
		double[] scales = {1, 0.5};
		long[] hashes = new long[scales.length];
		for (int s = 0; s < scales.length; s++) {
			List<Planned> plans = plans(scales[s]);
			hashes[s] = total(plans);
			System.out.printf(Locale.ROOT, "[memo] k %.1f: %d procedural trees, hash of the plans %dL%n", scales[s], plans.size(), hashes[s]);
			// The same trees again, in four threads, in shuffled order (fresh workspaces, the shared memos warm or cold).
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < plans.size(); i++) {
				order.add(i);
			}
			Collections.shuffle(order, new Random(11 + s));
			ExecutorService pool = Executors.newFixedThreadPool(4);
			try {
				List<Future<?>> done = new ArrayList<>();
				for (int t = 0; t < 4; t++) {
					int from = t;
					done.add(pool.submit(() -> {
						for (int q = from; q < order.size(); q += 4) {
							Planned p = plans.get(order.get(q));
							assertEquals(p.hash(), hash(TreeShapePlan.plan(p.request())), "tree " + order.get(q) + " planned again");
						}
					}));
				}
				for (Future<?> f : done) {
					f.get();
				}
			} finally {
				pool.shutdown();
			}
		}
		assertEquals(BEFORE_OP1[0], hashes[0], "plans of the realistic scale differ from the base commit of step OP1");
		assertEquals(BEFORE_OP1[1], hashes[1], "plans of the gameplay scale differ from the base commit of step OP1");
	}

	@Test
	void blendLatticeOfALoadedAreaIsSampledAboutOncePerPoint() {
		// Patches of 40 blocks of the forests of the slice and other land: every node mixes several categories.
		int[] codes = {EcotoneTest.code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE), EcotoneTest.code(HabitatBiome.MIXED_PINE_FOREST, Zone.NONE),
				EcotoneTest.code(HabitatBiome.MIXED_FOREST, Zone.NONE), EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE),
				EcotoneTest.code(HabitatBiome.ALDER_CARR, Zone.NONE)};
		IntBinaryOperator world = (x, z) -> codes[(int) Math.floorMod(Noise.mix(Math.floorDiv(x, 40) * 31L + Math.floorDiv(z, 40) * 1_000_003L), 5L)];
		for (double k : new double[] {1.0, 0.5}) {
			// Every call of the sampler by lattice point (the column it samples).
			java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger> calls = new java.util.concurrent.ConcurrentHashMap<>();
			ForestBlend blend = new ForestBlend(SEED, k, (x, z) -> {
				calls.computeIfAbsent((long) x << 32 | z & 0xFFFF_FFFFL, key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
				return world.applyAsInt(x, z);
			});
			// The load of the performance game test: 44 × 44 chunks around three points 448 blocks apart, chunks in shuffled order.
			List<int[]> chunks = new ArrayList<>();
			for (int p = 0; p < 3; p++) {
				for (int cx = -22; cx < 22; cx++) {
					for (int cz = -22; cz < 22; cz++) {
						chunks.add(new int[] {cx, cz + p * 28});
					}
				}
			}
			Collections.shuffle(chunks, new Random(5));
			chunks.parallelStream().forEach(c -> {
				ForestBlend.Field f = blend.field(c[0], c[1]);
				float[] o = new float[ForestBlend.SHARES];
				for (int x = (c[0] << 4) - Ecotone.REACH; x < (c[0] << 4) + 16 + Ecotone.REACH; x += 2) {
					for (int z = (c[1] << 4) - Ecotone.REACH; z < (c[1] << 4) + 16 + Ecotone.REACH; z += 2) {
						f.at(x, z, o);
					}
				}
			});
			long samples = 0;
			for (java.util.concurrent.atomic.AtomicInteger n : calls.values()) {
				samples += n.get();
			}
			int distinct = calls.size();
			System.out.printf(Locale.ROOT, "[memo] k %.1f: lattice samples %d of %d points (%.2f per point)%n", k, samples, distinct,
					(double) samples / distinct);
			assertTrue(samples <= 1.2 * distinct, "the lattice of the area is sampled about once per point: " + samples + " of " + distinct);
		}
	}
}
