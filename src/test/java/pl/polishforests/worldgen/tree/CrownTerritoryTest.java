package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Crown territories (step V5, decision PW-9 a, docs/04-pionowy-wycinek.md §5 {@code CrownTerritoryTest}): the territory of
 * a trunk does not depend on the chunk from which it is asked nor on the order of the questions; with vacancies it asks
 * only about the chunks within radius 1 of the trunk's chunk (the habitats a chunk being decorated has); territories of
 * one layer never share a column (no crown disputes, also across chunk borders) and each holds its own trunk; the crowns
 * of the forest edge lean out over the open ground; the neighbors of vacant candidates close the canopy. Prints the
 * canopy cover, the closure ceiling with and without vacancies and the trunks per layer for step V9.
 */
class CrownTerritoryTest {
	private static final long SEED = 20260927L;
	private static final long OTHER_SEED = -7_334_120_551L;

	/** Open ground: a meandering forest edge from smooth noise (a test field; the game reads the chunk habitats). */
	private static Vacancies meadows(long seed) {
		Noise n = new Noise(seed).derive("test.crown_territory.open");
		return (x, z) -> n.fbm(x, z, 90, 2, 0.5) > 0.2;
	}

	/**
	 * Vacancies of a palette that gives a tree to a hash-random share p of the forest candidates (a test field standing in
	 * for the rule chance of the game), plus the open ground, if any.
	 */
	private static Vacancies palette(long seed, double p, @Nullable Vacancies open) {
		Noise n = new Noise(seed).derive("test.crown_territory.vacant");
		return (x, z) -> (open != null && open.vacant(x, z)) || n.unit(x, z, 0) >= p;
	}

	/**
	 * Vacancies that answer only for the chunks within radius 1 of (chunkX, chunkZ) and fail otherwise, like the
	 * habitats at the FEATURES stage.
	 */
	private static Vacancies radiusOne(Vacancies vacancies, int chunkX, int chunkZ) {
		return (x, z) -> {
			if (Math.abs((x >> 4) - chunkX) > 1 || Math.abs((z >> 4) - chunkZ) > 1) {
				throw new IllegalStateException("asked about " + x + ", " + z + " outside radius 1 of chunk " + chunkX + ", "
						+ chunkZ);
			}
			return vacancies.vacant(x, z);
		};
	}

	/** Trunks (candidates that are not vacant) of the chunks [x0, x0 + size) × [z0, z0 + size), as {x, z}. */
	private static List<int[]> trunks(CrownTerritories t, int x0, int z0, int size, @Nullable Vacancies vacancies) {
		List<int[]> out = new ArrayList<>();
		CrownTerritories.Session s = t.session();
		for (int cx = x0; cx < x0 + size; cx++) {
			for (int cz = z0; cz < z0 + size; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, null)) {
					if (vacancies == null || !vacancies.vacant(c.x(), c.z())) {
						out.add(new int[] {c.x(), c.z()});
					}
				}
			}
		}
		return out;
	}

	@Test
	void independentOfTheAskingChunkAndOrder() {
		for (long seed : new long[] {SEED, OTHER_SEED}) {
			CrownTerritories t = new CrownTerritories(seed, new PatchyStandMap(seed, 0.5));
			List<int[]> trunks = trunks(t, -2, 3, 4, null);
			Map<Long, CrownTerritory> reference = new HashMap<>();
			for (int[] p : trunks) {
				// Reference: each trunk in a session of its own.
				reference.put(key(p[0], p[1]), t.session().territory(p[0], p[1], null));
			}
			Random random = new Random(seed);
			int checked = 0;
			// Ask from every chunk around the area (radius 2 beyond it): a session that first decorated its own chunk, then
			// every trunk of the area in a random order.
			for (int cx = -4; cx < 4; cx++) {
				for (int cz = 1; cz < 9; cz++) {
					CrownTerritories.Session s = t.session();
					s.chunk(cx, cz, null);
					List<int[]> order = new ArrayList<>(trunks);
					Collections.shuffle(order, random);
					for (int[] p : order) {
						assertEquals(reference.get(key(p[0], p[1])), s.territory(p[0], p[1], null),
								"trunk " + p[0] + ", " + p[1] + " asked from chunk " + cx + ", " + cz);
						checked++;
					}
				}
			}
			System.out.printf(Locale.ROOT, "[crowns] seed %d: %d trunks, %d territories compared from 64 chunks%n", seed,
					trunks.size(), checked);
		}
	}

	@Test
	void vacanciesNeedOnlyRadiusOneAndKeepOrder() {
		CrownTerritories t = new CrownTerritories(SEED, new PatchyStandMap(SEED, 0.5));
		Vacancies vacancies = palette(SEED, 0.7, meadows(SEED));
		List<int[]> trunks = trunks(t, 10, -6, 5, vacancies);
		Map<Long, CrownTerritory> reference = new HashMap<>();
		int edges = 0;
		for (int[] p : trunks) {
			// The habitats of radius 1 only: a question beyond it fails the test.
			CrownTerritory c = t.session().territory(p[0], p[1], radiusOne(vacancies, p[0] >> 4, p[1] >> 4));
			reference.put(key(p[0], p[1]), c);
			edges += c.edge() ? 1 : 0;
		}
		assertTrue(edges > trunks.size() / 20, "too few edge trunks to test the vacancies: " + edges);
		// From another chunk the habitats of the trunk's radius 1 are not all there: the question fails instead of giving
		// a territory that depends on the asking chunk.
		int[] first = trunks.get(0);
		assertThrows(IllegalStateException.class, () -> t.session().territory(first[0], first[1],
				radiusOne(vacancies, (first[0] >> 4) + 1, first[1] >> 4)));
		// One shared session asked in random orders, interleaved with whole chunks: the same territories.
		Random random = new Random(1);
		CrownTerritories.Session shared = t.session();
		for (int round = 0; round < 3; round++) {
			List<int[]> order = new ArrayList<>(trunks);
			Collections.shuffle(order, random);
			for (int[] p : order) {
				if (random.nextInt(8) == 0) {
					int cx = 10 + random.nextInt(5);
					int cz = -6 + random.nextInt(5);
					shared.chunk(cx, cz, radiusOne(vacancies, cx, cz));
				}
				assertEquals(reference.get(key(p[0], p[1])),
						shared.territory(p[0], p[1], radiusOne(vacancies, p[0] >> 4, p[1] >> 4)));
			}
		}
		System.out.printf(Locale.ROOT, "[crowns] vacancies: %d trunks, %d next to a marker%n", trunks.size(), edges);
	}

	@Test
	void vacantCandidatesHaveNoTerritory() {
		CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, false));
		Vacancies vacancies = palette(SEED, 0.6, null);
		CrownTerritories.Session s = t.session();
		int vacant = 0;
		for (CrownTerritory c : s.chunk(0, 0, null)) {
			if (vacancies.vacant(c.x(), c.z())) {
				vacant++;
				assertThrows(IllegalArgumentException.class, () -> s.territory(c.x(), c.z(), vacancies));
			}
		}
		assertTrue(vacant > 0);
		for (CrownTerritory c : s.chunk(0, 0, vacancies)) {
			assertTrue(!vacancies.vacant(c.x(), c.z()), "territory of a vacant candidate: " + c);
		}
	}

	@Test
	void noDisputesAndEachTrunkUnderItsCrown() {
		StandMap[] maps = {new PatchyStandMap(SEED, 0.5), StandMap.uniform(AgeClass.MATURE, false),
				StandMap.uniform(AgeClass.OLD_GROWTH, true)};
		Vacancies[] fields = {null, meadows(SEED), palette(SEED, 0.6, meadows(SEED))};
		for (StandMap map : maps) {
			for (Vacancies field : fields) {
				CrownTerritories t = new CrownTerritories(SEED, map);
				CrownTerritories.Session s = t.session();
				Map<CrownLayer, Map<Long, Long>> owners = new EnumMap<>(CrownLayer.class);
				int count = 0;
				for (int cx = -3; cx < 3; cx++) {
					for (int cz = -3; cz < 3; cz++) {
						// Each chunk with the habitats of its own radius 1, as in the game.
						for (CrownTerritory c : s.chunk(cx, cz, field == null ? null : radiusOne(field, cx, cz))) {
							count++;
							assertTrue(c.contains(c.x(), c.z()), "trunk outside its territory: " + c);
							assertTrue(c.reach() <= CrownTerritories.MAX_REACH);
							Map<Long, Long> layer = owners.computeIfAbsent(c.layer(), l -> new HashMap<>());
							for (int dz = -CrownTerritories.MAX_REACH; dz <= CrownTerritories.MAX_REACH; dz++) {
								for (int dx = -CrownTerritories.MAX_REACH; dx <= CrownTerritories.MAX_REACH; dx++) {
									if (c.contains(c.x() + dx, c.z() + dz)) {
										Long before = layer.put(key(c.x() + dx, c.z() + dz), key(c.x(), c.z()));
										assertTrue(before == null, "column " + (c.x() + dx) + ", " + (c.z() + dz)
												+ " claimed twice in " + c.layer() + ": " + c);
									}
								}
							}
						}
					}
				}
				assertTrue(count > 36 * 4, "too few trunks: " + count);
			}
		}
	}

	@Test
	void edgeCrownsLeanOverOpenGround() {
		// A managed mature stand (nearly all canopy) with open ground east of x = 64: the crowns within 4 blocks of the
		// edge lean east and reach over it, the crowns deep in the forest do not lean.
		CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, false));
		Vacancies open = (x, z) -> x >= 64;
		CrownTerritories.Session s = t.session();
		double edgeLean = 0;
		double innerLean = 0;
		int edgeN = 0;
		int innerN = 0;
		int over = 0;
		for (int cx = 0; cx < 4; cx++) {
			for (int cz = 0; cz < 8; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, radiusOne(open, cx, cz))) {
					if (c.layer() != CrownLayer.CANOPY) {
						continue;
					}
					double[] lean = c.centroidOffset();
					if (c.x() >= 60) {
						edgeLean += lean[0];
						edgeN++;
						for (int dz = -16; dz <= 16; dz++) {
							for (int dx = 0; dx <= 16; dx++) {
								over += c.x() + dx >= 64 && c.contains(c.x() + dx, c.z() + dz) ? 1 : 0;
							}
						}
					} else if (c.x() < 32) {
						innerLean += lean[0];
						innerN++;
					}
				}
			}
		}
		edgeLean /= edgeN;
		innerLean /= innerN;
		System.out.printf(Locale.ROOT, "[crowns] edge: %d canopy trunks within 4 blocks lean %+.2f blocks east, %d columns "
				+ "over the open ground; %d inner trunks lean %+.2f%n", edgeN, edgeLean, over, innerN, innerLean);
		assertTrue(edgeLean > 0.8, "edge crowns lean " + edgeLean);
		assertTrue(Math.abs(innerLean) < 0.3, "inner crowns lean " + innerLean);
		assertTrue(over > edgeN * 8, "crowns over the open ground: " + over);
	}

	@Test
	void onlyCandidatesHaveTerritories() {
		CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, true));
		CrownTerritories.Session s = t.session();
		int asked = 0;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (!s.candidate(x, z)) {
					int fx = x;
					int fz = z;
					assertThrows(IllegalArgumentException.class, () -> s.territory(fx, fz, null));
					asked++;
				}
			}
		}
		assertTrue(asked > 200);
	}

	/** Canopy cover and trunks per layer by stand, every candidate a tree (for the calibration in step V9). */
	@Test
	void reportCoverByStand() {
		for (boolean natural : new boolean[] {false, true}) {
			for (AgeClass age : AgeClass.values()) {
				Cover c = cover(new CrownTerritories(SEED, StandMap.uniform(age, natural)), null, null);
				System.out.printf(Locale.ROOT, "[crowns] %s %s: trunks per chunk canopy %.2f, subcanopy %.2f, regeneration %.2f; "
						+ "mean territory %.1f / %.1f / %.1f columns; canopy cover %.0f%%, any layer %.0f%%%n",
						natural ? "natural" : "managed", age, c.trunks[0], c.trunks[1], c.trunks[2], c.area[0], c.area[1],
						c.area[2], 100 * c.canopy, 100 * c.any);
				assertTrue(c.any > 0.5, "cover of all layers");
			}
		}
	}

	/**
	 * Closure ceiling when only a share p of the candidates becomes trees (review of V5, round 1): with the vacant
	 * candidates as markers their neighbors take over their cells; counted as trees (no {@link Vacancies}) they leave
	 * holes and the cover of the trees' territories falls to about p times the cover at p = 1. The cover is an upper
	 * bound of the canopy closure N4 (crowns that fill their territories).
	 */
	@Test
	void vacantCandidatesLeaveNoHoles() {
		double[] shares = {1.0, 0.9, 0.7, 0.55};
		for (boolean natural : new boolean[] {false, true}) {
			for (AgeClass age : AgeClass.values()) {
				CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(age, natural));
				StringBuilder line = new StringBuilder();
				for (double p : shares) {
					Vacancies v = palette(SEED, p, null);
					Cover holes = cover(t, null, v);
					Cover markers = cover(t, v, v);
					line.append(String.format(Locale.ROOT, "%n  p %.2f (%.1f trees/chunk): cover %.0f%% -> %.0f%%, canopy %.0f%% -> "
							+ "%.0f%%, mean canopy territory %.0f -> %.0f columns", p, markers.trunks[0] + markers.trunks[1]
							+ markers.trunks[2], 100 * holes.any, 100 * markers.any, 100 * holes.canopy, 100 * markers.canopy,
							holes.area[0], markers.area[0]));
					if (p < 1) {
						assertTrue(markers.any >= holes.any, "vacancies close the canopy: " + markers.any + " vs " + holes.any
								+ " at p " + p);
						assertTrue(markers.any > 0.9, "cover of all layers " + markers.any + " at p " + p);
						if (!natural) {
							// A single-storied stand: without markers the holes of the vacant candidates stay open.
							assertTrue(markers.canopy > holes.canopy + 0.5 * (1 - p), "canopy cover " + markers.canopy + " vs "
									+ holes.canopy + " at p " + p);
						}
					}
				}
				System.out.printf(Locale.ROOT, "[crowns] closure %s %s (vacancies as trees -> as markers):%s%n",
						natural ? "natural" : "managed", age, line);
			}
		}
	}

	/**
	 * What the radius-1 rule of the vacancies changes (review of V5, round 1): territories with vacancies known only
	 * within radius 1 of the pair against vacancies known within radius 2 (all partners), over 16 × 16 chunks.
	 */
	@Test
	void reportRadiusOneVacancyRule() {
		StandMap[] maps = {new PatchyStandMap(SEED, 0.5), StandMap.uniform(AgeClass.MATURE, false),
				StandMap.uniform(AgeClass.OLD_GROWTH, true)};
		String[] names = {"patchy", "managed mature", "natural old growth"};
		Vacancies[] fields = {meadows(SEED), palette(SEED, 0.6, meadows(SEED))};
		String[] fieldNames = {"meadows", "meadows and p 0.6"};
		for (int m = 0; m < maps.length; m++) {
			for (int f = 0; f < fields.length; f++) {
				CrownTerritories t = new CrownTerritories(SEED, maps[m]);
				CrownTerritories.Session one = t.session();
				CrownTerritories.Session two = t.session(2);
				int trunks = 0;
				int differ = 0;
				int worst = 0;
				for (int cx = 0; cx < 16; cx++) {
					for (int cz = 0; cz < 16; cz++) {
						List<CrownTerritory> a = one.chunk(cx, cz, fields[f]);
						List<CrownTerritory> b = two.chunk(cx, cz, fields[f]);
						assertEquals(a.size(), b.size());
						for (int i = 0; i < a.size(); i++) {
							trunks++;
							if (!a.get(i).equals(b.get(i))) {
								differ++;
								worst = Math.max(worst, symmetricDifference(a.get(i), b.get(i)));
							}
						}
					}
				}
				System.out.printf(Locale.ROOT, "[crowns] radius-1 vacancies, %s, %s: %d of %d territories differ from radius 2,"
						+ " at most by %d columns%n", names[m], fieldNames[f], differ, trunks, worst);
				assertTrue(differ <= trunks / 50, "radius-1 rule changes " + differ + " of " + trunks);
			}
		}
	}

	private static int symmetricDifference(CrownTerritory a, CrownTerritory b) {
		int n = 0;
		for (int dz = -CrownTerritories.MAX_REACH; dz <= CrownTerritories.MAX_REACH; dz++) {
			for (int dx = -CrownTerritories.MAX_REACH; dx <= CrownTerritories.MAX_REACH; dx++) {
				n += a.contains(a.x() + dx, a.z() + dz) != b.contains(a.x() + dx, a.z() + dz) ? 1 : 0;
			}
		}
		return n;
	}

	/** Cover of an area, per chunk: trunks and mean territory area per layer, canopy cover and cover of any layer. */
	private record Cover(double[] trunks, double[] area, double canopy, double any) {
	}

	/**
	 * Cover of the territories of the trees (candidates that {@code trees} does not call vacant; all without it) in 8 × 8
	 * chunks, the territories drawn with {@code vacancies}; only the inner 6 × 6 chunks count, so the cover is not cut at
	 * the border of the area.
	 */
	private static Cover cover(CrownTerritories t, @Nullable Vacancies vacancies, @Nullable Vacancies trees) {
		CrownTerritories.Session s = t.session();
		int size = 8;
		int layers = CrownLayer.values().length;
		double[] trunks = new double[layers];
		double[] area = new double[layers];
		Set<Long> canopy = new HashSet<>();
		Set<Long> any = new HashSet<>();
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, vacancies)) {
					if (trees != null && trees.vacant(c.x(), c.z())) {
						continue;
					}
					trunks[c.layer().ordinal()]++;
					area[c.layer().ordinal()] += c.area();
					for (int dz = -16; dz <= 16; dz++) {
						for (int dx = -16; dx <= 16; dx++) {
							int x = c.x() + dx;
							int z = c.z() + dz;
							if (x >= 16 && z >= 16 && x < 16 * (size - 1) && z < 16 * (size - 1) && c.contains(x, z)) {
								any.add(key(x, z));
								if (c.layer() == CrownLayer.CANOPY) {
									canopy.add(key(x, z));
								}
							}
						}
					}
				}
			}
		}
		double inner = Math.pow(16.0 * (size - 2), 2);
		for (int l = 0; l < layers; l++) {
			area[l] /= Math.max(1, trunks[l]);
			trunks[l] /= size * size;
		}
		return new Cover(trunks, area, canopy.size() / inner, any.size() / inner);
	}

	private static long key(int x, int z) {
		return (long) x << 32 | (z & 0xFFFF_FFFFL);
	}
}
