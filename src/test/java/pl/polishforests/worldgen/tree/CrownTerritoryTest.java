package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Crown territories (step V5, decision PW-9 a, docs/04-pionowy-wycinek.md §5 {@code CrownTerritoryTest}): the territory of
 * a trunk does not depend on the chunk from which it is asked nor on the order of the questions; with open ground it asks
 * only about the chunks within radius 1 of the trunk's chunk (the habitats a chunk being decorated has); territories of
 * one layer never share a column (no crown disputes, also across chunk borders) and each holds its own trunk; the crowns
 * of the forest edge lean out over the open ground. Prints the canopy cover and the trunks per layer for step V9.
 */
class CrownTerritoryTest {
	private static final long SEED = 20260927L;
	private static final long OTHER_SEED = -7_334_120_551L;

	/** Open ground: a meandering forest edge from smooth noise (a test field; the game reads the chunk habitats). */
	private static OpenGround meadows(long seed) {
		Noise n = new Noise(seed).derive("test.crown_territory.open");
		return (x, z) -> n.fbm(x, z, 90, 2, 0.5) > 0.2;
	}

	/**
	 * Open ground that answers only for the chunks within radius 1 of (chunkX, chunkZ) and fails otherwise, like the
	 * habitats at the FEATURES stage.
	 */
	private static OpenGround radiusOne(OpenGround open, int chunkX, int chunkZ) {
		return (x, z) -> {
			if (Math.abs((x >> 4) - chunkX) > 1 || Math.abs((z >> 4) - chunkZ) > 1) {
				throw new IllegalStateException("asked about " + x + ", " + z + " outside radius 1 of chunk " + chunkX + ", "
						+ chunkZ);
			}
			return open.open(x, z);
		};
	}

	/** Trunk candidates of the chunks [x0, x0 + size) × [z0, z0 + size), as {x, z}. */
	private static List<int[]> trunks(CrownTerritories t, int x0, int z0, int size) {
		List<int[]> out = new ArrayList<>();
		CrownTerritories.Session s = t.session();
		for (int cx = x0; cx < x0 + size; cx++) {
			for (int cz = z0; cz < z0 + size; cz++) {
				for (CrownTerritory c : s.chunk(cx, cz, null)) {
					out.add(new int[] {c.x(), c.z()});
				}
			}
		}
		return out;
	}

	@Test
	void independentOfTheAskingChunkAndOrder() {
		for (long seed : new long[] {SEED, OTHER_SEED}) {
			CrownTerritories t = new CrownTerritories(seed, new PatchyStandMap(seed, 0.5));
			List<int[]> trunks = trunks(t, -2, 3, 4);
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
	void edgeTerritoryNeedsOnlyRadiusOneAndKeepsOrder() {
		CrownTerritories t = new CrownTerritories(SEED, new PatchyStandMap(SEED, 0.5));
		OpenGround open = meadows(SEED);
		List<int[]> trunks = trunks(t, 10, -6, 5);
		Map<Long, CrownTerritory> reference = new HashMap<>();
		int edges = 0;
		for (int[] p : trunks) {
			// The habitats of radius 1 only: a question beyond it fails the test.
			CrownTerritory c = t.session().territory(p[0], p[1], radiusOne(open, p[0] >> 4, p[1] >> 4));
			reference.put(key(p[0], p[1]), c);
			edges += c.edge() ? 1 : 0;
		}
		assertTrue(edges > trunks.size() / 20, "too few edge trunks to test the open ground: " + edges);
		// From another chunk the habitats of the trunk's radius 1 are not all there: the question fails instead of giving
		// a territory that depends on the asking chunk.
		int[] first = trunks.get(0);
		assertThrows(IllegalStateException.class, () -> t.session().territory(first[0], first[1],
				radiusOne(open, (first[0] >> 4) + 1, first[1] >> 4)));
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
					shared.chunk(cx, cz, radiusOne(open, cx, cz));
				}
				assertEquals(reference.get(key(p[0], p[1])), shared.territory(p[0], p[1], radiusOne(open, p[0] >> 4, p[1] >> 4)));
			}
		}
		System.out.printf(Locale.ROOT, "[crowns] open ground: %d trunks, %d at an edge%n", trunks.size(), edges);
	}

	@Test
	void noDisputesAndEachTrunkUnderItsCrown() {
		StandMap[] maps = {new PatchyStandMap(SEED, 0.5), StandMap.uniform(AgeClass.MATURE, false),
				StandMap.uniform(AgeClass.OLD_GROWTH, true)};
		OpenGround open = meadows(SEED);
		for (StandMap map : maps) {
			for (boolean withOpen : new boolean[] {false, true}) {
				CrownTerritories t = new CrownTerritories(SEED, map);
				CrownTerritories.Session s = t.session();
				Map<CrownLayer, Map<Long, Long>> owners = new EnumMap<>(CrownLayer.class);
				int count = 0;
				for (int cx = -3; cx < 3; cx++) {
					for (int cz = -3; cz < 3; cz++) {
						// Each chunk with the habitats of its own radius 1, as in the game.
						for (CrownTerritory c : s.chunk(cx, cz, withOpen ? radiusOne(open, cx, cz) : null)) {
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
				assertTrue(count > 36 * 15, "too few trunks: " + count);
			}
		}
	}

	@Test
	void edgeCrownsLeanOverOpenGround() {
		// A managed mature stand (nearly all canopy) with open ground east of x = 64: the crowns within 4 blocks of the
		// edge lean east and reach over it, the crowns deep in the forest do not lean.
		CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(AgeClass.MATURE, false));
		OpenGround open = (x, z) -> x >= 64;
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

	/** Canopy cover and trunks per layer by stand (for the calibration in step V9; no thresholds beyond sanity). */
	@Test
	void reportCoverByStand() {
		for (boolean natural : new boolean[] {false, true}) {
			for (AgeClass age : AgeClass.values()) {
				CrownTerritories t = new CrownTerritories(SEED, StandMap.uniform(age, natural));
				CrownTerritories.Session s = t.session();
				int size = 8;
				int[] trunks = new int[CrownLayer.values().length];
				long[] area = new long[CrownLayer.values().length];
				java.util.Set<Long> canopy = new java.util.HashSet<>();
				java.util.Set<Long> any = new java.util.HashSet<>();
				for (int cx = 0; cx < size; cx++) {
					for (int cz = 0; cz < size; cz++) {
						for (CrownTerritory c : s.chunk(cx, cz, null)) {
							trunks[c.layer().ordinal()]++;
							area[c.layer().ordinal()] += c.area();
							for (int dz = -16; dz <= 16; dz++) {
								for (int dx = -16; dx <= 16; dx++) {
									int x = c.x() + dx;
									int z = c.z() + dz;
									// Inner chunks only, so the cover is not cut at the border of the area.
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
				double chunks = size * size;
				System.out.printf(Locale.ROOT, "[crowns] %s %s: trunks per chunk canopy %.2f, subcanopy %.2f, regeneration %.2f; "
						+ "mean territory %.1f / %.1f / %.1f columns; canopy cover %.0f%%, any layer %.0f%%%n",
						natural ? "natural" : "managed", age, trunks[0] / chunks, trunks[1] / chunks, trunks[2] / chunks,
						area[0] / (double) Math.max(1, trunks[0]), area[1] / (double) Math.max(1, trunks[1]),
						area[2] / (double) Math.max(1, trunks[2]), 100 * canopy.size() / inner, 100 * any.size() / inner);
				assertTrue(any.size() / inner > 0.5, "cover of all layers");
			}
		}
	}

	private static long key(int x, int z) {
		return (long) x << 32 | (z & 0xFFFF_FFFFL);
	}
}
