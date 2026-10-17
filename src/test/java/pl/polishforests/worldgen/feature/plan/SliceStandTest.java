package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * The tree stand of the vertical slice (step V9 of docs/04-pionowy-wycinek.md) with the generated palette: the M2 rules
 * give the same trees with the stand map as without it (the other biomes keep their decoration); the decision of one
 * candidate ({@link TreeStandPlan.Candidates#decide}) is the plan's; the layered rules follow the chances of their
 * stands, the species lists of the layers and the gaps (the pine regenerates only in gaps, no oak in the regeneration of
 * the oak-hornbeam forest); the vacancies the decoration uses ({@link StandVacancies}) never name a candidate that grows
 * a tree, and the canopy territories have no band of weaker cover along the chunk borders (V5-i) and no disputes.
 */
@Tag("slow")
class SliceStandTest {
	private static final long SEED = 20260927L;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();
	private static final float[] RAMP = {1, 1, 1, 1};

	private static TreeStandPlan.Stand stand(double k, boolean withMap) {
		Noise gaps = new Noise(SEED).derive("feature.tree_stand.gaps");
		if (!withMap) {
			return new TreeStandPlan.Stand(RAMP, gaps, k);
		}
		ForestStandMap map = new ForestStandMap(SEED, k, 0.85);
		return new TreeStandPlan.Stand(RAMP, gaps, k, map, new CrownTerritories(SEED, map));
	}

	/** A world of the slice forests, other forests, meadows, herb fringes and lakes. */
	private static IntBinaryOperator world(double k, boolean slice) {
		Noise n = new Noise(SEED).derive("test.slice_stand");
		int[] forests = slice
				? new int[] {code(HabitatBiome.OAK_HORNBEAM_FOREST), code(HabitatBiome.MIXED_FOREST), code(HabitatBiome.MIXED_PINE_FOREST),
						code(HabitatBiome.FRESH_PINE_FOREST)}
				: new int[] {code(HabitatBiome.ELM_ASH_FOREST), code(HabitatBiome.LOWLAND_BEECH_FOREST), code(HabitatBiome.DRY_PINE_FOREST),
						code(HabitatBiome.MOIST_PINE_FOREST)};
		int meadow = code(HabitatBiome.HAY_MEADOW);
		int fringe = EcotoneTest.code(HabitatBiome.HAY_MEADOW, Zone.HERB_FRINGE);
		int lake = code(HabitatBiome.LAKE);
		return (x, z) -> {
			double c = n.at(x - 9_000, z + 1_000, 60 * k);
			if (c > 0.6) {
				return lake;
			}
			if (c > 0.52) {
				return fringe;
			}
			if (n.at(x + 5_000, z - 3_000, 80 * k) > 0.45) {
				return meadow;
			}
			double a = n.at(x, z, 120 * k);
			return forests[Math.clamp((int) Math.floor((a + 0.6) / 1.2 * 4), 0, 3)];
		};
	}

	private static int code(HabitatBiome b) {
		return EcotoneTest.code(b, Zone.NONE);
	}

	/** Effective tree codes of a chunk (the ecotones of the game). */
	private static int[] codes(IntBinaryOperator world, int cx, int cz, double k) {
		return Ecotone.effective(EcotoneTest.region(cx, cz, world, k), Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
	}

	@Test
	void m2RulesGiveTheSameTreesWithTheStandMap() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator world = world(k, false);
			int trees = 0;
			for (int cx = -10; cx < 10; cx++) {
				for (int cz = -10; cz < 10; cz++) {
					int[] codes = codes(world, cx, cz, k);
					int[] without = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, cx, cz, stand(k, false));
					int[] with = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, cx, cz, stand(k, true));
					assertEquals(without.length, with.length);
					for (int i = 0; i < with.length; i++) {
						assertEquals(TreeStandPlan.column(without[i]), TreeStandPlan.column(with[i]));
						assertEquals(TreeStandPlan.tree(without[i]), TreeStandPlan.tree(with[i]));
						assertTrue(!GENERATED.procedural(TreeStandPlan.tree(with[i])), "a placeholder tree outside the slice");
					}
					trees += with.length;
				}
			}
			assertTrue(trees > 1000, "trees planted: " + trees);
		}
	}

	@Test
	void decisionOfACandidateIsThePlans() {
		double k = 1;
		IntBinaryOperator world = world(k, true);
		TreeStandPlan.Stand stand = stand(k, true);
		TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
		for (int cx = -6; cx < 6; cx++) {
			for (int cz = -6; cz < 6; cz++) {
				int[] codes = codes(world, cx, cz, k);
				int[] plan = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, cx, cz, stand);
				Map<Integer, Integer> planned = new HashMap<>();
				for (int p : plan) {
					planned.put(TreeStandPlan.column(p), p & 0xFFFF);
				}
				int[] kept = TreeStandPlan.keptCandidates(SEED, cx, cz);
				for (int c : kept) {
					int x = (cx << 4) + (c >> 4);
					int z = (cz << 4) + (c & 15);
					int d = candidates.decide(x, z, codes[c], 0, GENERATED.palette, stand);
					assertEquals(planned.getOrDefault(c, -1).intValue(), d, "candidate " + x + ", " + z);
				}
			}
		}
	}

	@Test
	void layeredStandsFollowTheirChancesLayersAndGaps() {
		double k = 1;
		ForestStandMap map = new ForestStandMap(SEED, k, 0.85);
		CrownTerritories crowns = new CrownTerritories(SEED, map);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(RAMP, new Noise(SEED).derive("feature.tree_stand.gaps"), k, map, crowns);
		for (HabitatBiome biome : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
			int code = code(biome);
			int r = GENERATED.palette.ruleFor(code);
			TreeStandPlan.Rule rule = GENERATED.palette.rules().get(r);
			TreeStandPlan.Layers layers = rule.layers();
			assertTrue(layers != null, biome + " has a layered stand");
			int[] codes = new int[256];
			java.util.Arrays.fill(codes, code);
			// Candidates and trees by stand (natural, age class, gap).
			long[][] candidates = new long[2][4];
			long[][] grown = new long[2][4];
			Map<CrownLayer, Set<Species>> species = new HashMap<>();
			int pinesOutsideGaps = 0;
			int regenerationOaks = 0;
			for (int cx = -40; cx < 40; cx++) {
				for (int cz = -40; cz < 40; cz++) {
					Set<Integer> trees = new HashSet<>();
					for (int p : TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, cx, cz, stand)) {
						int c = TreeStandPlan.column(p);
						trees.add(c);
						int x = (cx << 4) + (c >> 4);
						int z = (cz << 4) + (c & 15);
						CrownLayer layer = TreeStandPlan.layer(p);
						Species s = GENERATED.species.get(TreeStandPlan.tree(p));
						species.computeIfAbsent(layer, l -> new HashSet<>()).add(s);
						if (s == Species.SCOTS_PINE && layer != CrownLayer.CANOPY && map.gapDepth(x, z) < TreeStandPlan.GAP_INTERIOR) {
							pinesOutsideGaps++;
						}
						if (layer == CrownLayer.REGENERATION && s == Species.OAK && biome == HabitatBiome.OAK_HORNBEAM_FOREST) {
							regenerationOaks++;
						}
					}
					for (int c : TreeStandPlan.keptCandidates(SEED, cx, cz)) {
						int x = (cx << 4) + (c >> 4);
						int z = (cz << 4) + (c & 15);
						CrownTerritories.Site site = crowns.site(x, z);
						int n = site.natural() ? 1 : 0;
						int kind = site.gap() ? 3 : site.age().ordinal();
						candidates[n][kind]++;
						grown[n][kind] += trees.contains(c) ? 1 : 0;
					}
				}
			}
			StringBuilder report = new StringBuilder();
			for (int n = 0; n < 2; n++) {
				for (int kind = 0; kind < 4; kind++) {
					if (candidates[n][kind] < 2000) {
						continue;
					}
					double want = kind == 3 ? layers.gap() : (n == 1 ? layers.natural() : layers.managed())[kind];
					double got = (double) grown[n][kind] / candidates[n][kind];
					report.append(String.format(Locale.ROOT, " %s %s: %.3f (chance %.2f, %d candidates);", n == 1 ? "natural" : "managed",
							kind == 3 ? "gap" : AgeClass.values()[kind], got, want, candidates[n][kind]));
					assertEquals(want, got, 0.02, biome + (n == 1 ? " natural" : " managed") + " kind " + kind);
				}
			}
			System.out.printf(Locale.ROOT, "[slice stand] %s: trees per candidate:%s species by layer %s%n", biome.id(), report, species);
			assertEquals(0, pinesOutsideGaps, "pine regenerates only in the gaps");
			assertEquals(0, regenerationOaks, "no oak in the regeneration of the oak-hornbeam forest");
			assertTrue(species.get(CrownLayer.SUBCANOPY) != null && !species.get(CrownLayer.SUBCANOPY).isEmpty(), "subcanopy trees");
		}
	}

	@Test
	void vacanciesNeverNameATreeAndTheCanopyHasNoChunkBorderBand() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator world = world(k, true);
			TreeStandPlan.Stand stand = stand(k, true);
			CrownTerritories crowns = stand.crowns();
			int size = 8;
			int[][][] plans = new int[size][size][];
			int[][][] codes = new int[size][size][];
			for (int a = 0; a < size; a++) {
				for (int b = 0; b < size; b++) {
					codes[a][b] = codes(world, a, b, k);
					plans[a][b] = TreeStandPlan.of(codes[a][b], new int[256], GENERATED.palette, SEED, a, b, stand);
				}
			}
			long sure = 0;
			long vacant = 0;
			long[] sureBy = new long[16];
			long[] vacantBy = new long[16];
			// Canopy territories of the procedural trees of the inner chunks, each from the decoration of its own chunk.
			Map<Long, Integer> owners = new HashMap<>();
			int disputes = 0;
			for (int a = 1; a < size - 1; a++) {
				for (int b = 1; b < size - 1; b++) {
					StandVacancies v = vacancies(world, plans, a, b, k, stand);
					// Sure vacancies of the neighbors' candidates against their own plans.
					for (int da = -1; da <= 1; da++) {
						for (int db = -1; db <= 1; db++) {
							if (da == 0 && db == 0) {
								continue;
							}
							Set<Integer> theirs = new HashSet<>();
							for (int p : plans[a + da][b + db]) {
								theirs.add(TreeStandPlan.column(p));
							}
							for (int c : TreeStandPlan.keptCandidates(SEED, a + da, b + db)) {
								int x = ((a + da) << 4) + (c >> 4);
								int z = ((b + db) << 4) + (c & 15);
								boolean isVacant = !theirs.contains(c);
								boolean isSure = v.surelyVacant(x, z);
								assertTrue(!isSure || isVacant, "a sure vacancy grows a tree at " + x + ", " + z);
								int lx = x - (a << 4);
								int lz = z - (b << 4);
								int d = Math.max(Math.max(-lx - 1, lx - 16), Math.max(-lz - 1, lz - 16));
								if (isVacant) {
									vacant++;
									vacantBy[d]++;
									sure += isSure ? 1 : 0;
									sureBy[d] += isSure ? 1 : 0;
								}
							}
						}
					}
					CrownTerritories.Session session = crowns.session();
					for (int p : plans[a][b]) {
						if (!GENERATED.procedural(TreeStandPlan.tree(p)) || TreeStandPlan.layer(p) != CrownLayer.CANOPY) {
							continue;
						}
						int c = TreeStandPlan.column(p);
						int x = (a << 4) + (c >> 4);
						int z = (b << 4) + (c & 15);
						CrownTerritory t = session.territory(x, z, v);
						for (int dx = -16; dx <= 16; dx++) {
							for (int dz = -16; dz <= 16; dz++) {
								if (t.contains(x + dx, z + dz)) {
									long key = (long) (x + dx) << 32 | (z + dz) & 0xFFFF_FFFFL;
									Integer before = owners.put(key, x * 31 + z);
									disputes += before != null ? 1 : 0;
								}
							}
						}
					}
				}
			}
			assertEquals(0, disputes, "columns claimed by two canopy territories");
			// Cover of the forest columns of the inner chunks (2 chunks in from the area's edge) by distance from a chunk border.
			long[] covered = new long[8];
			long[] forest = new long[8];
			for (int x = 32; x < (size - 2) * 16; x++) {
				for (int z = 32; z < (size - 2) * 16; z++) {
					int code = world.applyAsInt(x, z);
					int r = GENERATED.palette.ruleFor(code);
					if (r < 0 || GENERATED.palette.rules().get(r).layers() == null || stand.map().canopyGap(x, z)) {
						continue;
					}
					int d = Math.min(Math.min(x & 15, 15 - (x & 15)), Math.min(z & 15, 15 - (z & 15)));
					forest[d]++;
					covered[d] += owners.containsKey((long) x << 32 | z & 0xFFFF_FFFFL) ? 1 : 0;
				}
			}
			StringBuilder by = new StringBuilder();
			for (int d = 0; d < 8; d++) {
				by.append(String.format(Locale.ROOT, " %d: %.1f%%", d, 100.0 * covered[d] / forest[d]));
			}
			StringBuilder sureReport = new StringBuilder();
			for (int d = 0; d < 8; d++) {
				sureReport.append(String.format(Locale.ROOT, " %d: %.1f%%", d, 100.0 * sureBy[d] / Math.max(1, vacantBy[d])));
			}
			System.out.printf(Locale.ROOT, "[slice stand] k %.1f: vacancies of the neighbors known %.1f%% (by distance from the border:%s);"
					+ " canopy territory cover of the slice forest columns by distance from a chunk border:%s%n", k, 100.0 * sure / vacant,
					sureReport, by);
			double border = (double) covered[0] / forest[0];
			double inner = (double) (covered[6] + covered[7]) / (forest[6] + forest[7]);
			assertTrue(border >= inner - 0.04, String.format(Locale.ROOT, "band along the chunk borders: %.3f vs %.3f", border, inner));
		}
	}

	/** The vacancies of the decoration of chunk (a, b) of the area (water 0, ramp 1 everywhere). */
	private static StandVacancies vacancies(IntBinaryOperator world, int[][][] plans, int a, int b, double k, TreeStandPlan.Stand stand) {
		return new StandVacancies(a, b, plans[a][b], EcotoneTest.region(a, b, world, k), GENERATED.palette, stand,
				new StandVacancies.Neighbors() {
					@Override
					public int waterDepth(int x, int z) {
						return 0;
					}

					@Override
					public float[] ramp(int chunkX, int chunkZ) {
						return RAMP;
					}
				}, new TreeStandPlan.Candidates(SEED), SEED);
	}
}
