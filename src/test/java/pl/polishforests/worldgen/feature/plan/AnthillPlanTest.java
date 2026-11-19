package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Step FA8: the plan of the anthills ({@link AnthillPlan}) is a pure function of the seed and the site: the same mounds for
 * the same seed, none closer than the spacing whatever their tries, every mound inside its home chunk, only in the forests of
 * the ant, and there only on an edge, in a canopy gap or (the pine forests, a third of the candidates) in a light stand;
 * {@code nearest} finds the mound the chunk lists hold.
 */
class AnthillPlanTest {
	private static final long SEED = 20260927L;

	private static int code(HabitatBiome b) {
		return Habitat.pack(b, Zone.NONE, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(b), 0, Soil.forBiome(b, Zone.NONE));
	}

	private static final int PINE = code(HabitatBiome.FRESH_PINE_FOREST);
	private static final int MEADOW = code(HabitatBiome.HAY_MEADOW);

	/** Every column the given code, no gaps (or gaps of the given depth everywhere), the dense pole stage. */
	private static AnthillPlan.Site uniform(int code, double gap) {
		return uniform(code, gap, false);
	}

	/** Every column the given code, gaps of the given depth everywhere, light stands or not. */
	private static AnthillPlan.Site uniform(int code, double gap, boolean light) {
		return new AnthillPlan.Site() {
			@Override
			public boolean lightStand(int x, int z) {
				return light;
			}

			@Override
			public int code(int x, int z) {
				return code;
			}

			@Override
			public double gapDepth(int x, int z) {
				return gap;
			}
		};
	}

	/** A pine forest for x at least 0, a meadow west of it, no gaps: the forest edge runs along x = 0. */
	private static final AnthillPlan.Site EDGE = new AnthillPlan.Site() {
		@Override
		public int code(int x, int z) {
			return x >= 0 ? PINE : MEADOW;
		}

		@Override
		public double gapDepth(int x, int z) {
			return 0;
		}

		@Override
		public boolean lightStand(int x, int z) {
			return false;
		}
	};

	@Test
	void theMeadowIsOpenLandAndThePineForestOneOfTheAnt() {
		assertTrue(Ecotone.openLand(MEADOW));
		assertFalse(Ecotone.openLand(PINE));
		assertTrue(AnthillPlan.antForest(PINE));
		for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.ALDER_CARR, HabitatBiome.LOWLAND_BEECH_FOREST,
				HabitatBiome.BOG_WOODLAND, HabitatBiome.HAY_MEADOW)) {
			assertFalse(AnthillPlan.antForest(code(b)), b.id());
		}
		assertEquals(0.65, AnthillPlan.DENSITY, 1e-9);
		assertTrue(AnthillPlan.CELL_CHANCE > 0 && AnthillPlan.CELL_CHANCE < 1);
	}

	@Test
	void theSameSeedPlansTheSameMounds() {
		int same = 0;
		int differ = 0;
		for (int i = -20; i < 20; i++) {
			for (int j = -20; j < 20; j++) {
				AnthillPlan.Anthill a = AnthillPlan.candidate(SEED, i, j);
				AnthillPlan.Anthill b = AnthillPlan.candidate(SEED, i, j);
				assertEquals(a == null, b == null);
				if (a != null) {
					assertEquals(a.x(), b.x());
					assertEquals(a.z(), b.z());
					assertEquals(a.size(), b.size());
					assertArrayEquals(a.layers(), b.layers());
					same++;
				}
				AnthillPlan.Anthill c = AnthillPlan.candidate(SEED + 1, i, j);
				if ((a == null) != (c == null) || a != null && (a.x() != c.x() || a.z() != c.z())) {
					differ++;
				}
			}
		}
		assertTrue(same > 100, "candidates: " + same);
		assertTrue(differ > 100, "another seed plans other mounds: " + differ);
	}

	@Test
	void moundsAreSpacedInsideTheirHomeChunkAndOfTheirSize() {
		List<AnthillPlan.Anthill> all = new ArrayList<>();
		List<Integer> cellOf = new ArrayList<>();
		int candidates = 0;
		int spaced = 0;
		int cells = 0;
		int light = 0;
		int[] sizes = new int[AnthillPlan.Size.values().length];
		for (int i = -30; i < 30; i++) {
			for (int j = -30; j < 30; j++) {
				cells++;
				AnthillPlan.Anthill c = AnthillPlan.candidate(SEED, i, j);
				if (c == null) {
					continue;
				}
				candidates++;
				AnthillPlan.Anthill a = AnthillPlan.spaced(SEED, i, j);
				if (a == null) {
					continue;
				}
				spaced++;
				light += a.light() ? 1 : 0;
				sizes[a.size().ordinal()]++;
				int w = a.width();
				assertTrue(w == 2 || w == 3);
				assertEquals(w * w, a.layers().length);
				for (int l : a.layers()) {
					assertTrue(l >= 1 && l <= 16, "layers " + l);
				}
				assertTrue(a.height() >= 7, "height " + a.height());
				assertEquals(i, AnthillPlan.cellOfChunk(a.chunkX()), "the home chunk lies in the cell");
				assertEquals(j, AnthillPlan.cellOfChunk(a.chunkZ()), "the home chunk lies in the cell");
				// The home chunk lists it.
				assertTrue(AnthillPlan.candidatesInChunk(SEED, a.chunkX(), a.chunkZ()).stream().anyMatch(b -> b.x() == a.x() && b.z() == a.z()),
						"the home chunk lists " + a.x() + ", " + a.z());
				for (int t = 0; t < AnthillPlan.TRIES; t++) {
					AnthillPlan.Anthill p = AnthillPlan.tried(SEED, i, j, a, t);
					assertEquals(a.chunkX(), p.x() >> 4, "inside the home chunk in x");
					assertEquals(a.chunkX(), (p.x() + w - 1) >> 4, "inside the home chunk in x");
					assertEquals(a.chunkZ(), p.z() >> 4, "inside the home chunk in z");
					assertEquals(a.chunkZ(), (p.z() + w - 1) >> 4, "inside the home chunk in z");
					assertTrue((p.x() & 15) >= AnthillPlan.WINDOW_MIN && (p.z() & 15) >= AnthillPlan.WINDOW_MIN, "the window");
					all.add(p);
					cellOf.add(i * 1000 + j);
				}
			}
		}
		// Any try of a mound and any try of another: at least the spacing apart.
		for (int p = 0; p < all.size(); p++) {
			for (int q = p + 1; q < all.size(); q++) {
				if (cellOf.get(p).equals(cellOf.get(q))) {
					continue;
				}
				AnthillPlan.Anthill a = all.get(p);
				AnthillPlan.Anthill b = all.get(q);
				assertTrue(Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ()) >= AnthillPlan.MIN_SPACING,
						"two mounds closer than the spacing: " + a + " " + b);
			}
		}
		double chance = (double) candidates / cells;
		assertEquals(AnthillPlan.CELL_CHANCE, chance, 0.03, "candidates per cell");
		assertTrue(spaced > 0.7 * candidates, "the spacing keeps " + spaced + " of " + candidates);
		assertEquals(AnthillPlan.LIGHT_STAND_CHANCE, (double) light / spaced, 0.05, "candidates that may take a light stand");
		for (AnthillPlan.Size s : AnthillPlan.Size.values()) {
			assertTrue(sizes[s.ordinal()] > 0.1 * spaced, s + ": " + sizes[s.ordinal()]);
		}
	}

	@Test
	void onlyInTheForestsOfTheAntOnEdgesAndInGaps() {
		AnthillPlan.Site oakGaps = uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST), 5);
		AnthillPlan.Site pineGaps = uniform(PINE, 5);
		AnthillPlan.Site pineClosed = uniform(PINE, 0);
		AnthillPlan.Site pineLight = uniform(PINE, 0, true);
		AnthillPlan.Site mixedLight = uniform(code(HabitatBiome.MIXED_FOREST), 0, true);
		AnthillPlan.Site oakLight = uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST), 0, true);
		int sited = 0;
		int edges = 0;
		for (int i = -30; i < 30; i++) {
			for (int j = -30; j < 30; j++) {
				AnthillPlan.Anthill a = AnthillPlan.spaced(SEED, i, j);
				if (a == null) {
					continue;
				}
				assertEquals(AnthillPlan.Reason.HABITAT, AnthillPlan.siting(a, oakGaps, 1));
				assertNull(AnthillPlan.siting(a, pineGaps, 1), "a pine forest gap holds every mound");
				assertEquals(AnthillPlan.Reason.NOT_EDGE_OR_GAP, AnthillPlan.siting(a, pineClosed, 1));
				// A light stand of a pine forest takes the candidates drawn for it; the mixed forest only edges and gaps.
				assertEquals(a.light() ? null : AnthillPlan.Reason.NOT_EDGE_OR_GAP, AnthillPlan.siting(a, pineLight, 1));
				assertEquals(AnthillPlan.Reason.NOT_EDGE_OR_GAP, AnthillPlan.siting(a, mixedLight, 1));
				assertEquals(AnthillPlan.Reason.HABITAT, AnthillPlan.siting(a, oakLight, 1));
				// The mound of the plan is the first sited try; no try is sited in the closed forest.
				AnthillPlan.Anthill first = AnthillPlan.sited(SEED, i, j, pineGaps, 1);
				assertNotNull(first);
				assertEquals(a.x(), first.x());
				assertEquals(a.z(), first.z());
				assertNull(AnthillPlan.sited(SEED, i, j, pineClosed, 1));
				assertEquals(AnthillPlan.Reason.NOT_EDGE_OR_GAP, AnthillPlan.whyNot(SEED, i, j, a, pineClosed, 1));
				assertEquals(AnthillPlan.Reason.HABITAT, AnthillPlan.whyNot(SEED, i, j, a, oakGaps, 1));
				assertEquals(AnthillPlan.TRIES, AnthillPlan.sitedTries(SEED, i, j, a, pineGaps, 1).size());
				sited++;
				// Along the edge at x = 0: the mounds within the reach of the meadow, not at the very edge.
				AnthillPlan.Reason edge = AnthillPlan.siting(a, EDGE, 1);
				double cx = a.centerX();
				if (a.x() < 0) {
					assertEquals(AnthillPlan.Reason.HABITAT, edge, "a mound on the meadow");
				} else if (cx < AnthillPlan.EDGE_MIN) {
					assertEquals(AnthillPlan.Reason.NOT_EDGE_OR_GAP, edge, "a mound at the very edge, x " + cx);
				} else if (cx >= AnthillPlan.EDGE_MIN + 1 && cx <= AnthillPlan.EDGE_REACH - 1) {
					assertNull(edge, "a mound on the edge, x " + cx);
					edges++;
				} else if (cx > AnthillPlan.EDGE_REACH + 1) {
					assertEquals(AnthillPlan.Reason.NOT_EDGE_OR_GAP, edge, "a mound deep in the forest, x " + cx);
				}
			}
		}
		assertTrue(sited > 100, "mounds: " + sited);
		assertTrue(edges > 0, "mounds on the edge: " + edges);
		// The small scale halves the reach, down to its least value.
		assertEquals(AnthillPlan.EDGE_REACH, AnthillPlan.edgeReach(1));
		assertEquals(AnthillPlan.EDGE_REACH_MIN, AnthillPlan.edgeReach(0.5));
	}

	@Test
	void nearestFindsTheClosestSitedMound() {
		AnthillPlan.Site site = uniform(PINE, 5);
		for (double[] o : new double[][] {{0, 0}, {1234, -987}, {-5000, 333}}) {
			double[] p = AnthillPlan.nearest(SEED, site, 1, o[0], o[1], 500, () -> false);
			assertNotNull(p);
			double best = Double.MAX_VALUE;
			int c0x = (int) Math.floor(o[0] / 16);
			int c0z = (int) Math.floor(o[1] / 16);
			for (int cx = c0x - 32; cx <= c0x + 32; cx++) {
				for (int cz = c0z - 32; cz <= c0z + 32; cz++) {
					for (AnthillPlan.Anthill a : AnthillPlan.inChunk(SEED, cx, cz, site, 1)) {
						best = Math.min(best, Math.hypot(a.centerX() - o[0], a.centerZ() - o[1]));
					}
				}
			}
			assertEquals(best, Math.hypot(p[0] - o[0], p[1] - o[1]), 1e-9, "the nearest mound from " + o[0] + ", " + o[1]);
		}
		assertNull(AnthillPlan.nearest(SEED, uniform(MEADOW, 5), 1, 0, 0, 300, () -> false), "no mound on a meadow");
		assertNull(AnthillPlan.nearest(SEED, site, 1, 0, 0, 300, () -> true), "a cancelled search");
	}
}
