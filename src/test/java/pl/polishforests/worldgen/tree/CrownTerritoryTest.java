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
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Crown territories (step V5, decision PW-9 a, docs/04-pionowy-wycinek.md §5 {@code CrownTerritoryTest}): the territory of
 * a trunk does not depend on the chunk from which it is asked nor on the order of the questions; with vacancies it asks
 * only what the decoration of the trunk's chunk knows (the exact vacancies of its own chunk, the sure ones within radius
 * 1; round 2 of the review); territories of one layer never share a column (no crown disputes, also across chunk
 * borders) and each holds its own trunk, also when the decoration guesses the vacancies of its neighbors wrong; the
 * crowns of the forest edge lean out over the open ground; the neighbors of vacant candidates close the canopy. Prints
 * the canopy cover, the closure with each vacancy rule and the trunks per layer for step V9.
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
	 * Vacancies of a palette that gives a tree to a share p of the forest candidates (real chance draws and gap test of
	 * the tree stand plan, {@link PaletteVacancies}), plus the open ground, if any.
	 */
	private static Vacancies palette(long seed, double p, @Nullable Vacancies open) {
		return new PaletteVacancies(seed, p, open);
	}

	/**
	 * The vacancies as the decoration of chunk (chunkX, chunkZ) knows them: the exact ones of its own candidates (the
	 * ecotone codes of the chunk) and the sure ones of the chunks within radius 1; any other question fails.
	 */
	private static Vacancies decorated(Vacancies vacancies, int chunkX, int chunkZ) {
		return new Vacancies() {
			@Override
			public boolean vacant(int x, int z) {
				if ((x >> 4) != chunkX || (z >> 4) != chunkZ) {
					throw new IllegalStateException("exact vacancy of " + x + ", " + z + " asked in chunk " + chunkX + ", "
							+ chunkZ);
				}
				return vacancies.vacant(x, z);
			}

			@Override
			public boolean surelyVacant(int x, int z) {
				if (Math.abs((x >> 4) - chunkX) > 1 || Math.abs((z >> 4) - chunkZ) > 1) {
					throw new IllegalStateException("sure vacancy of " + x + ", " + z + " outside radius 1 of chunk "
							+ chunkX + ", " + chunkZ);
				}
				boolean sure = vacancies.surelyVacant(x, z);
				assertTrue(!sure || vacancies.vacant(x, z), "a sure vacancy that grows a tree: " + x + ", " + z);
				return sure;
			}
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
	void vacanciesAskOnlyWhatTheDecorationKnows() {
		CrownTerritories t = new CrownTerritories(SEED, new PatchyStandMap(SEED, 0.5));
		Vacancies vacancies = palette(SEED, 0.7, meadows(SEED));
		List<int[]> trunks = trunks(t, 10, -6, 5, vacancies);
		Map<Long, CrownTerritory> reference = new HashMap<>();
		int edges = 0;
		for (int[] p : trunks) {
			// What the decoration of the trunk's chunk knows only: any other question fails the test.
			CrownTerritory c = t.session().territory(p[0], p[1], decorated(vacancies, p[0] >> 4, p[1] >> 4));
			reference.put(key(p[0], p[1]), c);
			edges += c.edge() ? 1 : 0;
		}
		assertTrue(edges > trunks.size() / 20, "too few edge trunks to test the vacancies: " + edges);
		// Another chunk does not know the exact vacancies of the trunk's chunk: the question fails instead of giving a
		// territory that depends on the asking chunk.
		int[] first = trunks.get(0);
		assertThrows(IllegalStateException.class, () -> t.session().territory(first[0], first[1],
				decorated(vacancies, (first[0] >> 4) + 1, first[1] >> 4)));
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
					shared.chunk(cx, cz, decorated(vacancies, cx, cz));
				}
				assertEquals(reference.get(key(p[0], p[1])),
						shared.territory(p[0], p[1], decorated(vacancies, p[0] >> 4, p[1] >> 4)));
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
				// Each chunk with what its decoration knows, as in the game.
				int[] result = claims(t.session(), field == null ? null : (cx, cz) -> decorated(field, cx, cz));
				assertEquals(0, result[1], "columns claimed twice");
				assertTrue(result[0] > 36 * 4, "too few trunks: " + result[0]);
			}
		}
	}

	/**
	 * Round 2 of the review: the decoration of a chunk C cannot know the exact vacancy of a candidate in a neighbor C',
	 * since its ecotone tree code needs the habitats of chunks at radius 2 of C. A decoration that guesses it (here: the
	 * truth flipped for 10% of the neighbors' candidates, differently in each asking chunk) gives crown disputes when the
	 * guess counts in the pairs (the rule of round 1, exact vacancies within radius 1), and none with the rule of the
	 * game, which asks only the exact vacancies of its own chunk and the sure ones of its neighbors.
	 */
	@Test
	void guessedNeighborVacanciesCauseNoDisputes() {
		Vacancies truth = palette(SEED, 0.6, meadows(SEED));
		Noise flip = new Noise(SEED).derive("test.crown_territory.guess");
		BiFunction<Integer, Integer, Vacancies> guess = (cx, cz) -> new Vacancies() {
			@Override
			public boolean vacant(int x, int z) {
				boolean own = (x >> 4) == cx && (z >> 4) == cz;
				boolean wrong = !own && flip.unit(x, z, Noise.mix(cx * 31L + cz)) < 0.1;
				return truth.vacant(x, z) != wrong;
			}

			@Override
			public boolean surelyVacant(int x, int z) {
				return truth.surelyVacant(x, z);
			}
		};
		for (StandMap map : new StandMap[] {StandMap.uniform(AgeClass.MATURE, false), new PatchyStandMap(SEED, 0.5)}) {
			CrownTerritories t = new CrownTerritories(SEED, map);
			int[] roundOne = claims(t.session(1, 1), guess);
			int[] game = claims(t.session(), guess);
			System.out.printf(Locale.ROOT, "[crowns] guessed neighbor vacancies: %d columns claimed twice with exact vacancies "
					+ "of radius 1 (%d trunks), %d with the rule of the game (%d trunks)%n", roundOne[1], roundOne[0], game[1],
					game[0]);
			assertTrue(roundOne[1] > 0, "the guess should give disputes with the rule of round 1");
			assertEquals(0, game[1], "columns claimed twice with the rule of the game");
		}
	}

	/**
	 * Territories of the chunks [−3, 3)², each chunk asked with its own view of the vacancies (none without
	 * {@code views}): {trunks, columns claimed twice within a layer}; checks that each trunk is in its territory.
	 */
	private static int[] claims(CrownTerritories.Session s, @Nullable BiFunction<Integer, Integer, Vacancies> views) {
		Map<CrownLayer, Map<Long, Long>> owners = new EnumMap<>(CrownLayer.class);
		int count = 0;
		int twice = 0;
		for (int cx = -3; cx < 3; cx++) {
			for (int cz = -3; cz < 3; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, views == null ? null : views.apply(cx, cz))) {
					count++;
					assertTrue(c.contains(c.x(), c.z()), "trunk outside its territory: " + c);
					assertTrue(c.reach() <= CrownTerritories.MAX_REACH);
					Map<Long, Long> layer = owners.computeIfAbsent(c.layer(), l -> new HashMap<>());
					for (int dz = -CrownTerritories.MAX_REACH; dz <= CrownTerritories.MAX_REACH; dz++) {
						for (int dx = -CrownTerritories.MAX_REACH; dx <= CrownTerritories.MAX_REACH; dx++) {
							if (c.contains(c.x() + dx, c.z() + dz)
									&& layer.put(key(c.x() + dx, c.z() + dz), key(c.x(), c.z())) != null) {
								twice++;
							}
						}
					}
				}
			}
		}
		return new int[] {count, twice};
	}

	@Test
	void edgeCrownsLeanOverOpenGround() {
		// A managed mature stand (nearly all canopy, 65% of the candidates trees) with open ground east of a line: the
		// crowns within 4 blocks of the edge lean east and reach over it, the crowns deep in the forest do not lean. Inside
		// a chunk (x = 72) the decoration knows the exact vacancies of the meadow candidates; on a chunk border (x = 64)
		// only the sure ones (round 2 of the review), so the lean is smaller there.
		double[] lean = new double[2];
		for (int b = 0; b < 2; b++) {
			int border = b == 0 ? 72 : 64;
			CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, false));
			Vacancies v = palette(SEED, 0.65, (x, z) -> x >= border);
			CrownTerritories.Session s = t.session();
			double edgeLean = 0;
			double innerLean = 0;
			int edgeN = 0;
			int innerN = 0;
			int over = 0;
			for (int cx = 0; cx < 5; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					for (CrownTerritory c : s.chunk(cx, cz, decorated(v, cx, cz))) {
						if (c.layer() != CrownLayer.CANOPY) {
							continue;
						}
						double[] offset = c.centroidOffset();
						if (c.x() >= border - 4 && c.x() < border) {
							edgeLean += offset[0];
							edgeN++;
							for (int dz = -16; dz <= 16; dz++) {
								for (int dx = 0; dx <= 16; dx++) {
									over += c.x() + dx >= border && c.contains(c.x() + dx, c.z() + dz) ? 1 : 0;
								}
							}
						} else if (c.x() < 32) {
							innerLean += offset[0];
							innerN++;
						}
					}
				}
			}
			edgeLean /= edgeN;
			innerLean /= innerN;
			lean[b] = edgeLean;
			System.out.printf(Locale.ROOT, "[crowns] edge at x = %d (%s): %d canopy trunks within 4 blocks lean %+.2f blocks "
					+ "east, %d columns over the open ground (%.1f per trunk); %d inner trunks lean %+.2f%n", border,
					b == 0 ? "inside a chunk" : "chunk border", edgeN, edgeLean, over, over / (double) edgeN, innerN,
					innerLean);
			assertTrue(Math.abs(innerLean) < 0.3, "inner crowns lean " + innerLean);
			assertTrue(edgeLean > (b == 0 ? 0.8 : 0.2), "edge crowns lean " + edgeLean);
		}
		assertTrue(lean[1] < lean[0], "the chunk border leans less");
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
				Cover c = cover(new CrownTerritories(SEED, StandMap.uniform(age, natural)).session(), null, null);
				System.out.printf(Locale.ROOT, "[crowns] %s %s: trunks per chunk canopy %.2f, subcanopy %.2f, regeneration %.2f; "
						+ "mean territory %.1f / %.1f / %.1f columns; canopy cover %.0f%%, any layer %.0f%%%n",
						natural ? "natural" : "managed", age, c.trunks[0], c.trunks[1], c.trunks[2], c.area[0], c.area[1],
						c.area[2], 100 * c.canopy, 100 * c.any);
				assertTrue(c.any > 0.5, "cover of all layers");
			}
		}
	}

	/**
	 * Closure when only a share p of the candidates becomes trees (review of V5, rounds 1 and 2). Four rules: the vacant
	 * candidates counted as trees (holes); markers only in the trunk's own chunk (radius 0); the rule of the game (exact
	 * vacancies in the own chunk, sure ones within radius 1); exact vacancies within radius 1 (round 1, not available at
	 * the FEATURES stage, see {@link Vacancies}). The cover of the trees' territories is an upper bound of the canopy
	 * closure N4 (crowns that fill their territories). The densest palette rule has 12 trees per chunk (p ≈ 0.72), so
	 * p = 0.9 is beyond the game.
	 */
	@Test
	void vacantCandidatesLeaveNoHoles() {
		double[] shares = {1.0, 0.9, 0.72, 0.55, 0.42};
		List<Runnable> checks = new ArrayList<>();
		for (boolean natural : new boolean[] {false, true}) {
			for (AgeClass age : AgeClass.values()) {
				CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(age, natural));
				StringBuilder line = new StringBuilder();
				for (double p : shares) {
					Vacancies v = palette(SEED, p, null);
					Cover holes = cover(t.session(), null, v);
					Cover own = cover(t.session(0, 0), v, v);
					Cover game = cover(t.session(), v, v);
					Cover ideal = cover(t.session(1, 1), v, v);
					line.append(String.format(Locale.ROOT, "%n  p %.2f (%.1f trees/chunk): canopy %.0f / %.0f / %.0f / %.0f%%, any "
							+ "layer %.0f / %.0f / %.0f / %.0f%%, mean canopy territory %.0f / %.0f / %.0f / %.0f columns", p,
							game.trunks[0] + game.trunks[1] + game.trunks[2], 100 * holes.canopy, 100 * own.canopy,
							100 * game.canopy, 100 * ideal.canopy, 100 * holes.any, 100 * own.any, 100 * game.any,
							100 * ideal.any, holes.area[0], own.area[0], game.area[0], ideal.area[0]));
					// More markers never shrink a territory: holes <= radius 0 <= game <= exact radius 1.
					checks.add(() -> assertTrue(holes.any <= own.any && own.any <= game.any && game.any <= ideal.any,
							"order of the rules at p " + p + ": " + holes.any + ", " + own.any + ", " + game.any + ", " + ideal.any));
					if (p < 0.95) {
						checks.add(() -> assertTrue(game.any > 0.7, "cover of all layers " + game.any + " at p " + p));
						if (!natural) {
							// A single-storied stand: without markers the holes of the vacant candidates stay open.
							checks.add(() -> assertTrue(game.canopy > holes.canopy + 0.5 * (1 - p), "canopy cover "
									+ game.canopy + " vs " + holes.canopy + " at p " + p));
						}
					}
				}
				System.out.printf(Locale.ROOT, "[crowns] closure %s %s (holes / radius 0 / game: own exact + radius-1 sure / "
						+ "exact radius 1):%s%n", natural ? "natural" : "managed", age, line);
			}
		}
		checks.forEach(Runnable::run);
	}

	/**
	 * Where the rule of the game loses cover (round 2 of the review): a candidate across the chunk border whose vacancy is
	 * not sure counts as a tree for the trunk, so the holes gather along the chunk borders. Canopy cover by the distance
	 * of the column to the nearest chunk border (0: border column, 7: chunk middle), the rule of the game against the
	 * exact vacancies of radius 1, managed mature stand, over the inner 6 × 6 of 8 × 8 chunks.
	 */
	@Test
	void reportCoverNearChunkBorders() {
		CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, false));
		for (double p : new double[] {0.66, 0.54, 0.42}) {
			Vacancies v = palette(SEED, p, null);
			double[] game = bands(t.session(), v);
			double[] ideal = bands(t.session(1, 1), v);
			StringBuilder line = new StringBuilder();
			for (int d = 0; d < 8; d++) {
				line.append(String.format(Locale.ROOT, " %.0f/%.0f", 100 * game[d], 100 * ideal[d]));
			}
			System.out.printf(Locale.ROOT, "[crowns] canopy cover by distance to the chunk border 0..7 (game/exact radius 1, %%),"
					+ " managed mature, p %.2f:%s%n", p, line);
			assertTrue(game[7] >= game[0], "the chunk middle has at least the cover of the border");
		}
	}

	/** Canopy cover of the inner 6 × 6 of 8 × 8 chunks by the distance of the column to the nearest chunk border. */
	private static double[] bands(CrownTerritories.Session s, Vacancies vacancies) {
		int size = 8;
		Set<Long> canopy = new HashSet<>();
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, vacancies)) {
					if (c.layer() != CrownLayer.CANOPY) {
						continue;
					}
					for (int dz = -16; dz <= 16; dz++) {
						for (int dx = -16; dx <= 16; dx++) {
							if (c.contains(c.x() + dx, c.z() + dz)) {
								canopy.add(key(c.x() + dx, c.z() + dz));
							}
						}
					}
				}
			}
		}
		double[] covered = new double[8];
		double[] all = new double[8];
		for (int x = 16; x < 16 * (size - 1); x++) {
			for (int z = 16; z < 16 * (size - 1); z++) {
				int lx = x & 15;
				int lz = z & 15;
				int d = Math.min(Math.min(lx, 15 - lx), Math.min(lz, 15 - lz));
				all[d]++;
				covered[d] += canopy.contains(key(x, z)) ? 1 : 0;
			}
		}
		for (int d = 0; d < 8; d++) {
			covered[d] /= all[d];
		}
		return covered;
	}

	/**
	 * What restricting the sure vacancies to radius 1 changes: the rule of the game against sure vacancies known within
	 * radius 2 (all partners; they need no habitats, but the water depth would), over 16 × 16 chunks.
	 */
	@Test
	void reportSureVacancyRadius() {
		StandMap[] maps = {new PatchyStandMap(SEED, 0.5), StandMap.uniform(AgeClass.MATURE, false),
				StandMap.uniform(AgeClass.OLD_GROWTH, true)};
		String[] names = {"patchy", "managed mature", "natural old growth"};
		Vacancies[] fields = {palette(SEED, 0.6, meadows(SEED)), palette(SEED, 0.42, null)};
		String[] fieldNames = {"meadows and p 0.6", "p 0.42"};
		for (int m = 0; m < maps.length; m++) {
			for (int f = 0; f < fields.length; f++) {
				CrownTerritories t = new CrownTerritories(SEED, maps[m]);
				CrownTerritories.Session one = t.session();
				CrownTerritories.Session two = t.session(0, 2);
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
				System.out.printf(Locale.ROOT, "[crowns] sure vacancies of radius 1, %s, %s: %d of %d territories differ from "
						+ "radius 2, at most by %d columns%n", names[m], fieldNames[f], differ, trunks, worst);
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
	 * chunks, the territories drawn by the session with {@code vacancies}; only the inner 6 × 6 chunks count, so the
	 * cover is not cut at the border of the area.
	 */
	private static Cover cover(CrownTerritories.Session s, @Nullable Vacancies vacancies, @Nullable Vacancies trees) {
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
