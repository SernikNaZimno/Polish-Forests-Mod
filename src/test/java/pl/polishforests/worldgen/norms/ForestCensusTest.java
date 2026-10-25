package pl.polishforests.worldgen.norms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.norms.ForestCensus.Kind;
import pl.polishforests.worldgen.norms.ForestCensus.WoodObject;

/**
 * The census of the norms card (step V4): every counter agrees piece by piece with the hand-built test plot
 * ({@link CensusTestPlot}), wherever the plot lies, and the rules the plot does not reach (the least gap, the tie of two
 * crowns over a leaf, the mossy state, the transect) have their own small grids.
 */
class ForestCensusTest {
	private static final double EPS = 1e-9;

	private static void assertMetrics(Map<String, Double> expected, Map<String, Double> actual) {
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Double> e : expected.entrySet()) {
			Double a = actual.get(e.getKey());
			double x = e.getValue();
			boolean ok = a != null && (Double.isNaN(x) ? a.isNaN() : Double.isInfinite(x) ? a == x : Math.abs(a - x) < EPS);
			if (!ok) {
				wrong.add(e.getKey() + ": expected " + x + ", got " + a);
			}
		}
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	@Test
	void handBuiltPlotIsCountedPieceByPiece() {
		for (int[] at : new int[][] {{1000, 2000, 64}, {-517, -3001, -20}, {0, 0, 300}}) {
			CensusGrid g = CensusTestPlot.grid(at[0], at[1], at[2]);
			ForestCensus.Census census = ForestCensus.of(g);
			boolean[] plot = ForestCensus.rectangle(g, at[0], at[1], CensusTestPlot.SIZE, CensusTestPlot.SIZE);
			Map<String, Double> m = census.measure(plot);
			assertMetrics(CensusTestPlot.expected(), m);
			assertEquals(ForestNorms.METRICS.keySet().stream().filter(k -> !k.startsWith("N15.") || k.equals(ForestNorms.CONIFERS)).toList(),
					List.copyOf(m.keySet().stream().filter(k -> !k.startsWith("N15.") || k.equals(ForestNorms.CONIFERS)).toList()),
					"the census writes the metrics of the card in its order");
			assertEquals(CensusTestPlot.expectedUnclassified(), census.unclassified(plot));
		}
	}

	@Test
	void objectsOfTheHandBuiltPlot() {
		CensusGrid g = CensusTestPlot.grid(0, 0, 64);
		List<WoodObject> objects = ForestCensus.of(g).objects();
		Map<Kind, Integer> kinds = new EnumMap<>(Kind.class);
		objects.forEach(o -> kinds.merge(o.kind(), 1, Integer::sum));
		// T1–T5 and R1; S1; ST1; SN1; L1, L2, L3.
		assertEquals(Map.of(Kind.TREE, 6, Kind.SHRUB, 1, Kind.STUMP, 1, Kind.SNAG, 1, Kind.LOG, 3), kinds);
		WoodObject t2 = find(objects, 12, 3);
		assertEquals(Species.HORNBEAM, t2.species());
		assertEquals(11, t2.height());
		assertEquals(81, t2.leaves());
		assertEquals(0.5, t2.diameter(), EPS);
		assertEquals(10 * Math.PI * 16 / 256 + 2 * Math.PI * 4 / 256, t2.volume(), EPS);
		WoodObject t3 = find(objects, 3, 12);
		assertEquals(Species.LINDEN, t3.species(), "the species of a vanilla log comes from the record");
		assertEquals("minecraft:oak_log", t3.block());
		WoodObject stump = find(objects, 9, 12);
		assertEquals(Kind.STUMP, stump.kind());
		assertEquals(1, stump.height(), "a stump owns no leaves; its height is its wood");
		WoodObject l3 = objects.stream().filter(o -> o.kind() == Kind.LOG && o.z() == 12).findFirst().orElseThrow();
		assertEquals(12, l3.x(), "the middle of the log at x 10-13");
		assertEquals(4, l3.length(), EPS);
		WoodObject l1 = objects.stream().filter(o -> o.kind() == Kind.LOG && o.z() == 16).findFirst().orElseThrow();
		assertEquals(66, l1.y(), "the log on the raised part (ground 65) lies one block higher");
		WoodObject snag = find(objects, 18, 1);
		assertEquals(Kind.SNAG, snag.kind());
		assertEquals(5, snag.woodHeight());
	}

	private static WoodObject find(List<WoodObject> objects, int x, int z) {
		return objects.stream().filter(o -> o.x() == x && o.z() == z).findFirst().orElseThrow(() -> new AssertionError("no object at " + x + " " + z));
	}

	/** A grid with flat ground at 0 and grass everywhere. */
	private static CensusGrid flat(int size, int height) {
		CensusGrid g = new CensusGrid(0, 0, size, size, height);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				g.setGround(x, z, 0, "minecraft:grass_block");
			}
		}
		return g;
	}

	@Test
	void aGapNeedsItsLeastArea() {
		// Leaves at dy 3 everywhere but two holes: 7 × 7 = 49 columns (no gap) and 8 × 8 = 64 (a gap).
		CensusGrid g = flat(40, 4);
		for (int x = 0; x < 40; x++) {
			for (int z = 0; z < 40; z++) {
				boolean small = x >= 3 && x < 10 && z >= 3 && z < 10;
				boolean large = x >= 20 && x < 28 && z >= 20 && z < 28;
				if (!small && !large) {
					g.set(x, 3, z, CensusGrid.LEAVES);
				}
			}
		}
		Map<String, Double> m = ForestCensus.plot(g, 0, 0, 40, 40);
		assertEquals(100.0 * (1600 - 49 - 64) / 1600, m.get(ForestNorms.CANOPY), EPS);
		assertEquals(100.0 * 64 / 1600, m.get(ForestNorms.GAP_SHARE), EPS);
		assertEquals(2 * Math.sqrt(64 / Math.PI), m.get(ForestNorms.GAP_DIAMETER), EPS);
		// Leaves at dy 2 do not close the canopy.
		CensusGrid low = flat(10, 4);
		low.set(5, 2, 5, CensusGrid.LEAVES);
		assertEquals(0.0, ForestCensus.plot(low, 0, 0, 10, 10).get(ForestNorms.CANOPY), EPS);
	}

	@Test
	void aLeafReachedByTwoObjectsGoesToTheLargerOne() {
		// A trunk of 4 logs at (2, 2) and a stump at (4, 2); one leaf at (3, 2) dy 1 between them.
		CensusGrid g = flat(8, 6);
		for (int dy = 1; dy <= 4; dy++) {
			g.setWood(2, dy, 2, true, CensusGrid.log("minecraft:oak_log"));
		}
		g.setWood(4, 1, 2, true, CensusGrid.log("minecraft:oak_log"));
		g.set(3, 1, 2, CensusGrid.LEAVES);
		List<WoodObject> objects = ForestCensus.of(g).objects();
		assertEquals(Kind.TREE, find(objects, 2, 2).kind());
		assertEquals(1, find(objects, 2, 2).leaves());
		assertEquals(Kind.STUMP, find(objects, 4, 2).kind(), "the stump gets no leaves, it stays dead");
	}

	@Test
	void aLeafBetweenAShrubAndAStumpGoesToTheShrub() {
		// A recorded hazel at (4, 2) and a stump at (2, 2), both one log; the leaf at (3, 2) touches both.
		CensusGrid g = flat(8, 4);
		g.setWood(2, 1, 2, true, CensusGrid.log("minecraft:oak_log"));
		g.setWood(4, 1, 2, true, CensusGrid.log("minecraft:oak_log"));
		g.setRecord(4, 2, Species.HAZEL);
		g.set(3, 1, 2, CensusGrid.LEAVES);
		List<WoodObject> objects = ForestCensus.of(g).objects();
		assertEquals(Kind.SHRUB, find(objects, 4, 2).kind());
		assertEquals(Kind.STUMP, find(objects, 2, 2).kind());
	}

	@Test
	void aStumpUnderALowCrownStaysAStump() {
		// A tree at (2, 2), logs dy 1-4, with leaves reaching down to dy 2 at (3, 2) and (4, 2); a stump at (4, 2) dy 1
		// right under a leaf, which it reaches before the tree does.
		CensusGrid g = flat(8, 6);
		for (int dy = 1; dy <= 4; dy++) {
			g.setWood(2, dy, 2, true, CensusGrid.log("minecraft:oak_log"));
		}
		g.set(3, 2, 2, CensusGrid.LEAVES);
		g.set(4, 2, 2, CensusGrid.LEAVES);
		g.setWood(4, 1, 2, true, CensusGrid.log("minecraft:pale_oak_log"));
		List<WoodObject> objects = ForestCensus.of(g).objects();
		assertEquals(Kind.STUMP, find(objects, 4, 2).kind());
		assertEquals(2, find(objects, 2, 2).leaves());
		// A recorded young tree of one log keeps its leaves.
		g.setRecord(4, 2, Species.OAK);
		assertEquals(Kind.TREE, find(ForestCensus.of(g).objects(), 4, 2).kind());
	}

	@Test
	void leavesFartherThanTheReachBelongToNoOne() {
		CensusGrid g = flat(12, 3);
		g.setWood(1, 1, 1, true, CensusGrid.log("minecraft:oak_log"));
		g.setRecord(1, 1, Species.OAK);
		for (int x = 2; x <= 9; x++) {
			g.set(x, 1, 1, CensusGrid.LEAVES);
		}
		assertEquals(ForestCensus.LEAF_REACH, find(ForestCensus.of(g).objects(), 1, 1).leaves());
	}

	@Test
	void groundClassification() {
		assertTrue(ForestCensus.moss("polishforests:test_plant" + ForestCensus.MOSSY, "minecraft:grass_block"));
		assertTrue(ForestCensus.moss(null, "polishforests:test_ground" + ForestCensus.MOSSY));
		assertTrue(ForestCensus.moss(null, "minecraft:moss_block"));
		assertFalse(ForestCensus.moss("minecraft:fern", "minecraft:grass_block"));
		assertTrue(ForestCensus.herb("minecraft:bush" + ForestCensus.MOSSY));
		assertTrue(ForestCensus.bilberry("minecraft:bush"));
		assertFalse(ForestCensus.herb("minecraft:leaf_litter"));
		assertTrue(ForestCensus.litter("minecraft:leaf_litter"));
		assertFalse(ForestCensus.unclassified("minecraft:oak_sapling"));
		assertFalse(ForestCensus.unclassified("minecraft:red_mushroom"));
		assertTrue(ForestCensus.unclassified("minecraft:cobweb"));
		for (String id : ForestCensus.BILBERRY_PLANTS) {
			assertTrue(ForestCensus.HERB_PLANTS.contains(id), "the bilberry is a part of the herb layer");
		}
	}

	@Test
	void compositionKeys() {
		assertEquals("oak", ForestCensus.compositionKey(Species.SESSILE_OAK));
		assertEquals("maple_ash", ForestCensus.compositionKey(Species.ASH));
		assertEquals("maple_ash", ForestCensus.compositionKey(Species.SYCAMORE_MAPLE));
		assertEquals("linden", ForestCensus.compositionKey(Species.LINDEN));
		assertEquals("other", ForestCensus.compositionKey(Species.ELM));
		assertEquals("other", ForestCensus.compositionKey(null));
	}

	// ------------------------------------------------------------------ transect

	/**
	 * A transect along z from 0 to 399, band 8: four forests of 100 steps. Per step a share p of the 17 band columns has
	 * moss and a share q leaf litter; trees with the given species every 10 steps.
	 */
	private static CensusGrid transectGrid(double[] moss, double[] litter, Species[] trees) {
		CensusGrid g = new CensusGrid(-10, 0, 21, 400, 4);
		for (int z = 0; z < 400; z++) {
			int mossColumns = (int) Math.round(17 * moss[z]);
			int litterColumns = (int) Math.round(17 * litter[z]);
			for (int x = -10; x <= 10; x++) {
				g.setGround(x, z, 0, "minecraft:grass_block");
				int k = x + 8;
				if (k >= 0 && k < mossColumns) {
					g.setPlant(x, z, "minecraft:moss_carpet");
				} else if (k >= 0 && k < 17 && k >= 17 - litterColumns) {
					g.setPlant(x, z, "minecraft:leaf_litter");
				}
			}
			if (z % 10 == 5) {
				for (int dy = 1; dy <= 3; dy++) {
					g.setWood(0, dy, z, true, CensusGrid.log("minecraft:oak_log"));
				}
				g.set(0, 4, z, CensusGrid.LEAVES);
				g.setRecord(0, z, trees[z / 100]);
			}
		}
		return g;
	}

	@Test
	void transectOrderAndWidths() {
		double[] moss = new double[400];
		double[] litter = new double[400];
		// Moss 0.8 → 0.5 → 0.2 → 0; litter 0 → 0.2 → 0.5 → 0.8 (per forest); the first border a step, the second a
		// logistic curve of scale 4 steps (10–90% width 4 · 2 ln 9 = 17.6), the third a step.
		double[] m = {0.8, 0.5, 0.2, 0};
		double[] l = {0, 0.2, 0.5, 0.8};
		for (int z = 0; z < 400; z++) {
			int r = z / 100;
			moss[z] = m[r];
			litter[z] = l[r];
			if (z >= 150 && z < 250) {
				double s = 1 / (1 + Math.exp(-(z - 199.5) / 4));
				moss[z] = m[1] + (m[2] - m[1]) * s;
				litter[z] = l[1] + (l[2] - l[1]) * s;
			}
		}
		Species[] pines = {Species.SCOTS_PINE, Species.SCOTS_PINE, Species.OAK, Species.OAK};
		CensusGrid g = transectGrid(moss, litter, pines);
		int[] rank = new int[400];
		for (int z = 0; z < 400; z++) {
			rank[z] = z / 100;
		}
		ForestCensus.Transect t = ForestCensus.transect(g, 0.5, 0.5, 0.5, 399.5, 399, rank, 8, 1);
		assertTrue(t.order());
		assertEquals(4, t.stretches().size());
		assertEquals(100.0, t.stretches().get(0).get(ForestNorms.CONIFERS), EPS);
		assertEquals(0.0, t.stretches().get(3).get(ForestNorms.CONIFERS), EPS);
		assertTrue(t.widths()[0] < 3, "a step: " + t.widths()[0]);
		assertEquals(4 * 2 * Math.log(9), t.widths()[1], 4, "a logistic curve of scale 4: " + t.widths()[1]);
		assertTrue(t.widths()[2] < 3, "a step: " + t.widths()[2]);
		// k divides the widths.
		ForestCensus.Transect half = ForestCensus.transect(g, 0.5, 0.5, 0.5, 399.5, 399, rank, 8, 2);
		assertEquals(t.widths()[1] / 2, half.widths()[1], EPS);
		// Conifers growing towards the oak-hornbeam forest break the order.
		ForestCensus.Transect wrong = ForestCensus.transect(transectGrid(moss, litter,
				new Species[] {Species.OAK, Species.SCOTS_PINE, Species.SCOTS_PINE, Species.OAK}), 0.5, 0.5, 0.5, 399.5, 399, rank, 8, 1);
		assertFalse(wrong.order());
	}

	@Test
	void orderBreaksOnlyBeyondTheSamplingNoise() {
		// Leaf litter 35.6% then 35.0% over 2700 columns each: the same within the noise (it should not shrink).
		assertFalse(ForestCensus.reversed(-35.6, 2700, -35.0, 2700));
		// 35.6% then 30% over 2700 columns: shrinks for sure.
		assertTrue(ForestCensus.reversed(-35.6, 2700, -30, 2700));
		// Conifers 40% then 50% of 20 trees each: within the noise of so few trees; of 2000 trees not.
		assertFalse(ForestCensus.reversed(40, 20, 50, 20));
		assertTrue(ForestCensus.reversed(40, 2000, 50, 2000));
		assertTrue(ForestCensus.reversed(Double.NaN, 20, 50, 20), "nothing counted breaks the order");
	}

	/**
	 * Noisy transects (step Z1): each band column has moss or leaf litter by its own draw, as on the ground of the game. A
	 * weak contrast (moss 40% → 30%, litter 0 → 10%) over a wide transition leaves the width unmeasured (the interval spans
	 * more than {@link ForestCensus#RESOLVED_RATIO} times); a strong one (moss 80% → 0, litter 0 → 80%) at a step gives a
	 * narrow interval and a width below 20.
	 */
	@Test
	void noisyTransectsGiveAWidthOnlyWhenTheFitIsResolved() {
		java.util.Random random = new java.util.Random(7);
		int[] rank = new int[400];
		for (int z = 0; z < 400; z++) {
			rank[z] = z / 200;
		}
		double[][] cases = {{0.4, 0.3, 0, 0.1, 30}, {0.8, 0, 0, 0.8, 0.25}};
		for (int q = 0; q < cases.length; q++) {
			double[] c = cases[q];
			CensusGrid g = new CensusGrid(-10, 0, 21, 400, 4);
			for (int z = 0; z < 400; z++) {
				double s = 1 / (1 + Math.exp(-(z - 199.5) / c[4]));
				double moss = c[0] + (c[1] - c[0]) * s;
				double litter = c[2] + (c[3] - c[2]) * s;
				for (int x = -10; x <= 10; x++) {
					g.setGround(x, z, 0, "minecraft:grass_block");
					double u = random.nextDouble();
					if (u < moss) {
						g.setPlant(x, z, "minecraft:moss_carpet");
					} else if (u < moss + litter) {
						g.setPlant(x, z, "minecraft:leaf_litter");
					}
				}
			}
			ForestCensus.Transect t = ForestCensus.transect(g, 0.5, 0.5, 0.5, 399.5, 399, rank, 8, 1);
			double lo = t.widthLow()[0];
			double hi = t.widthHigh()[0];
			if (q == 0) {
				assertTrue(Double.isNaN(t.widths()[0]), "weak contrast: not measured, interval " + lo + "-" + hi);
				assertTrue(hi > ForestCensus.RESOLVED_RATIO * lo, "a wide interval " + lo + "-" + hi);
			} else {
				assertTrue(t.widths()[0] < 20 && hi < 20, "a sharp step: " + t.widths()[0] + ", interval " + lo + "-" + hi);
			}
		}
	}

	@Test
	void transectWithoutContrastHasNoWidth() {
		double[] same = new double[400];
		java.util.Arrays.fill(same, 0.3);
		CensusGrid g = transectGrid(same, new double[400], new Species[] {Species.OAK, Species.OAK, Species.OAK, Species.OAK});
		int[] rank = new int[400];
		for (int z = 0; z < 400; z++) {
			rank[z] = z / 100;
		}
		ForestCensus.Transect t = ForestCensus.transect(g, 0.5, 0.5, 0.5, 399.5, 399, rank, 8, 1);
		for (double w : t.widths()) {
			assertTrue(Double.isNaN(w));
		}
	}
}
