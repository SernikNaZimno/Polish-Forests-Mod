package pl.polishforests.worldgen.norms;

import java.util.LinkedHashMap;
import java.util.Map;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The hand-built test plot of the census (step V4, "the counters agree piece by piece on a hand-built test plot"): a
 * plot of 20 × 20 columns with a margin of 6, every object placed by hand and every expected value worked out by hand
 * below ({@link #expected}). The unit test {@code ForestCensusTest} counts the grid as it is; the game test {@code slice}
 * builds the same plot from blocks in the sky of the world (vanilla logs, leaves and plants, thin trunks of the mod),
 * reads it back with its world reader and must get the same grid and the same values, which checks the reader too.
 *
 * <p>Local coordinates (lx, lz) run from 0 to 19 over the plot, the grid from −6 to 25. The ground lies at
 * {@code groundY}, one block higher on the raised part lx 10–19, lz 13–19 (a step, so heights are read per column); the
 * columns lx 0–3, lz 17–19 have a moss block as ground, (0, 0) podzol (a mushroom stands on it), the others grass.
 *
 * <pre>
 * T1  oak (recorded), oak logs at (3, 3) dy 1–10; leaves dy 8–10 in 5 × 5 without the trunk, dy 11 in 3 × 3: height 11
 * T2  hornbeam thin trunk r4 at (12, 3) dy 1–10, branch r2 lying east at (13, 3) and (14, 3) dy 7; leaves as T1: height 11
 * T3  linden (recorded), oak logs at (3, 12) dy 1–4; leaves dy 3–4 in 3 × 3 without the trunk, dy 5 in 3 × 3, dy 6
 *     on top: height 6
 * T4  Scots pine (recorded), spruce logs at (16, 9) dy 1–10; leaves dy 8–10 in 3 × 3 without the trunk, dy 11 in
 *     3 × 3, dy 12 on top: height 12
 * T5  spruce (recorded) outside the plot, spruce logs at (−2, 10) dy 1–8; leaves dy 6–8 in 5 × 5 without the trunk,
 *     dy 9 in 3 × 3: its crown reaches lx 0, lz 8–12 of the plot
 * R1  young oak (recorded), oak log at (7, 8) dy 1, leaves dy 1 in 3 × 3 around it and dy 2 on top: height 2
 * S1  hazel (recorded), oak log at (6, 17) dy 1, leaves as R1: height 2
 * ST1 stump, oak log at (9, 12) dy 1, no leaves
 * L3  log lying east, oak logs at (10–13, 12) dy 1, right next to ST1 (a separate object)
 * SN1 snag, oak thin trunk r3 at (18, 1) dy 1–5
 * L1  log lying east on the raised part, oak logs at (12–16, 16) dy 1
 * L2  log lying east at (18–22, 8) dy 1: its middle (20) is outside the plot, it does not count
 * plants on the ground (dy 1): moss carpet lx 0–9, lz 19; bush (bilberry) lx 0–9, lz 15; fern lx 0–4, lz 14; short
 *     grass lx 5–9, lz 14; tall grass (2 blocks) at (2, 0); leaf litter lx 0–9, lz 6; brown mushroom at (0, 0);
 *     cobweb at (1, 0) (unknown to the census)
 * </pre>
 */
public final class CensusTestPlot {
	private CensusTestPlot() {
	}

	/** Side of the plot, margin of the grid around it and cells above the ground. */
	public static final int SIZE = 20;
	public static final int MARGIN = 6;
	public static final int HEIGHT = 16;

	private static final String OAK_LOG = "minecraft:oak_log";
	private static final String SPRUCE_LOG = "minecraft:spruce_log";

	/** The plot with its lowest corner at (x0, z0) and its ground at {@code groundY}. */
	public static CensusGrid grid(int x0, int z0, int groundY) {
		CensusGrid g = new CensusGrid(x0 - MARGIN, z0 - MARGIN, SIZE + 2 * MARGIN, SIZE + 2 * MARGIN, HEIGHT);
		Plot p = new Plot(g, x0, z0);
		for (int lx = -MARGIN; lx < SIZE + MARGIN; lx++) {
			for (int lz = -MARGIN; lz < SIZE + MARGIN; lz++) {
				boolean raised = lx >= 10 && lx <= 19 && lz >= 13 && lz <= 19;
				String ground = lx >= 0 && lx <= 3 && lz >= 17 && lz <= 19 ? "minecraft:moss_block"
						: lx == 0 && lz == 0 ? "minecraft:podzol" : "minecraft:grass_block";
				g.setGround(x0 + lx, z0 + lz, groundY + (raised ? 1 : 0), ground);
			}
		}
		// T1: oak, vanilla logs, recorded.
		p.up(3, 3, 1, 10, CensusGrid.log(OAK_LOG)).crown(3, 3, 8, 10, 2).crown(3, 3, 11, 11, 1).record(3, 3, Species.OAK);
		// T2: hornbeam, thin trunk r4 with a branch r2 lying east at dy 7.
		CensusGrid.Wood hornbeam = CensusGrid.trunk(4, Species.HORNBEAM, "polishforests:hornbeam_trunk");
		CensusGrid.Wood branch = CensusGrid.trunk(2, Species.HORNBEAM, "polishforests:hornbeam_trunk");
		p.up(12, 3, 1, 10, hornbeam).side(13, 3, 7, branch).side(14, 3, 7, branch).crown(12, 3, 8, 10, 2).crown(12, 3, 11, 11, 1);
		// T3: linden as an oak log, recorded.
		p.up(3, 12, 1, 4, CensusGrid.log(OAK_LOG)).crown(3, 12, 3, 5, 1).leaf(3, 12, 6).record(3, 12, Species.LINDEN);
		// T4: Scots pine as a spruce log, recorded.
		p.up(16, 9, 1, 10, CensusGrid.log(SPRUCE_LOG)).crown(16, 9, 8, 11, 1).leaf(16, 9, 12).record(16, 9, Species.SCOTS_PINE);
		// T5: spruce outside the plot, its crown over lx 0.
		p.up(-2, 10, 1, 8, CensusGrid.log(SPRUCE_LOG)).crown(-2, 10, 6, 8, 2).crown(-2, 10, 9, 9, 1).record(-2, 10, Species.SPRUCE);
		// R1: young oak; S1: hazel.
		p.up(7, 8, 1, 1, CensusGrid.log(OAK_LOG)).crown(7, 8, 1, 1, 1).leaf(7, 8, 2).record(7, 8, Species.OAK);
		p.up(6, 17, 1, 1, CensusGrid.log(OAK_LOG)).crown(6, 17, 1, 1, 1).leaf(6, 17, 2).record(6, 17, Species.HAZEL);
		// ST1 with the log L3 right next to it.
		p.up(9, 12, 1, 1, CensusGrid.log(OAK_LOG));
		for (int lx = 10; lx <= 13; lx++) {
			p.side(lx, 12, 1, CensusGrid.log(OAK_LOG));
		}
		// SN1: a snag, thin oak trunk r3.
		p.up(18, 1, 1, 5, CensusGrid.trunk(3, Species.OAK, "polishforests:oak_trunk"));
		// L1 on the raised part, L2 across the edge of the plot.
		for (int lx = 12; lx <= 16; lx++) {
			p.side(lx, 16, 1, CensusGrid.log(OAK_LOG));
		}
		for (int lx = 18; lx <= 22; lx++) {
			p.side(lx, 8, 1, CensusGrid.log(OAK_LOG));
		}
		// Plants.
		for (int lx = 0; lx <= 9; lx++) {
			p.plant(lx, 19, "minecraft:moss_carpet").plant(lx, 15, "minecraft:bush").plant(lx, 6, "minecraft:leaf_litter")
					.plant(lx, 14, lx <= 4 ? "minecraft:fern" : "minecraft:short_grass");
		}
		p.plant(2, 0, "minecraft:tall_grass");
		g.set(x0 + 2, 2, z0, CensusGrid.OTHER);
		p.plant(0, 0, "minecraft:brown_mushroom").plant(1, 0, "minecraft:cobweb");
		return g;
	}

	/**
	 * The expected values over the plot, worked out by hand. Plot: 400 columns, all with ground; 1.5625 chunks, 0.04 ha.
	 *
	 * <ul>
	 * <li>Living trees with the base in the plot: T1 11, T2 11, T3 6, T4 12, R1 2 (T5 is outside, S1 a shrub). Top height:
	 * the round(100 × 0.04) = 4 tallest, (12 + 11 + 11 + 6) / 4 = 10; a1 from 6.67: T1, T2, T4; a2 from 3.33: T3; R1 is
	 * regeneration. Trees (a1 + a2) 4 / 1.5625 = 2.56 per chunk, a1 1.92 of mean height 34 / 3, a2 0.64 of height 6, a2
	 * share 25%; a1 composition oak, hornbeam and Scots pine a third each; conifers 1 of 4.</li>
	 * <li>Canopy (leaves at dy ≥ 3): T1 25 columns, T2 25, T3 9, T4 9, T5 5 (lx 0, lz 8–12); R1 and S1 have leaves only
	 * up to dy 2: 73 columns, 18.25%. The open columns (327) touch each other around the crowns; a gap holds only those a
	 * square of 5 × 5 open columns of the plot covers (step V9), which leaves out 52: the lane of 4 columns between T1 and
	 * T2 (lx 6–9, lz 1–5: 20), the row between T1, T2 and the plot's edge (lx 0–14, lz 0: 15), the column between T1, T5
	 * and the edge (lx 0, lz 1–7: 7), the columns between T5, T3 and the edge (lx 0, lz 13 and lx 1, lz 11–13: 4) and those
	 * between T4 and the edge (lx 18–19, lz 8–10: 6): one gap of 275 columns, 68.75%, diameter 2·√(275 / π).</li>
	 * <li>Regeneration R1 (oak) stands in the gap and none under the canopy: 25 per ha, ratio infinite, oaks 25 per ha,
	 * pines under the canopy 0. Understory: R1 and S1 cover 9 columns each, 18 columns, 4.5%; 1 shrub, 0.64 per chunk.</li>
	 * <li>Ground: herbs bush 10 + fern 5 + short grass 5 + tall grass 1 = 21 columns, 5.25%; bilberry 10, 2.5%; moss
	 * 12 moss blocks + 6 carpets not on them = 18, 4.5%; leaf litter 10, 2.5%; the cobweb is unknown.</li>
	 * <li>Dead wood in the plot: stump ST1, snag SN1, logs L1 and L3 (L2 counts outside): 4 objects, 2.56 per chunk; logs
	 * 1.28, stumps 0.64, snags 0.64. Volume without the stump: L1 5 + L3 4 + SN1 5·π·3² / 256 m³ over 0.04 ha; coarse
	 * (longer than 3 m, at least 0.5 m thick) L1 and L3, 50 per ha (SN1 is 0.375 m thick).</li>
	 * </ul>
	 */
	public static Map<String, Double> expected() {
		double chunks = 400 / 256.0;
		double ha = 0.04;
		Map<String, Double> m = new LinkedHashMap<>();
		m.put(ForestNorms.TREES_PER_CHUNK, 4 / chunks);
		m.put(ForestNorms.A1_PER_CHUNK, 3 / chunks);
		m.put(ForestNorms.A1_HEIGHT, 34 / 3.0);
		m.put(ForestNorms.A2_PER_CHUNK, 1 / chunks);
		m.put(ForestNorms.A2_HEIGHT, 6.0);
		m.put(ForestNorms.A2_SHARE, 25.0);
		m.put(ForestNorms.TOP_HEIGHT, 10.0);
		for (String key : ForestNorms.COMPOSITION_KEYS) {
			boolean third = key.equals("oak") || key.equals("hornbeam") || key.equals("scots_pine");
			m.put(ForestNorms.COMPOSITION + key, third ? 100 / 3.0 : 0.0);
		}
		m.put(ForestNorms.CANOPY, 18.25);
		m.put(ForestNorms.GAP_SHARE, 68.75);
		m.put(ForestNorms.GAP_DIAMETER, 2 * Math.sqrt(275 / Math.PI));
		m.put(ForestNorms.REGENERATION, 1 / ha);
		m.put(ForestNorms.REGENERATION_RATIO, Double.POSITIVE_INFINITY);
		m.put(ForestNorms.OAK_REGENERATION, 1 / ha);
		m.put(ForestNorms.PINE_REGENERATION_UNDER_CANOPY, 0.0);
		m.put(ForestNorms.UNDERSTORY, 4.5);
		m.put(ForestNorms.SHRUBS_PER_CHUNK, 1 / chunks);
		m.put(ForestNorms.HERBS, 5.25);
		m.put(ForestNorms.BILBERRY, 2.5);
		m.put(ForestNorms.LITTER, 2.5);
		m.put(ForestNorms.MOSS, 4.5);
		m.put(ForestNorms.DEADWOOD_PER_CHUNK, 4 / chunks);
		m.put(ForestNorms.LOGS_PER_CHUNK, 2 / chunks);
		m.put(ForestNorms.STUMPS_PER_CHUNK, 1 / chunks);
		m.put(ForestNorms.SNAGS_PER_CHUNK, 1 / chunks);
		m.put(ForestNorms.DEADWOOD_VOLUME, (5 + 4 + 5 * Math.PI * 9 / 256) / ha);
		m.put(ForestNorms.COARSE_PIECES, 2 / ha);
		m.put(ForestNorms.CONIFERS, 25.0);
		return m;
	}

	/** Plants the census does not know on the plot, with their columns. */
	public static Map<String, Integer> expectedUnclassified() {
		return Map.of("minecraft:cobweb", 1);
	}

	/** Writes the objects in local coordinates of the plot. */
	private static final class Plot {
		private final CensusGrid g;
		private final int x0;
		private final int z0;

		/** Directions of a parent ({@code Direction} 3D data values). */
		private static final int DOWN = 0;
		private static final int WEST = 4;

		Plot(CensusGrid g, int x0, int z0) {
			this.g = g;
			this.x0 = x0;
			this.z0 = z0;
		}

		/** Standing wood; a thin trunk with its parent below (as the world builds it, step V9). */
		Plot up(int lx, int lz, int from, int to, CensusGrid.Wood w) {
			for (int dy = from; dy <= to; dy++) {
				g.setWood(x0 + lx, dy, z0 + lz, true, w.species() == null ? w : w.withParent(DOWN));
			}
			return this;
		}

		/** Lying wood; a thin trunk with its parent to the west (the branches of the plot grow east). */
		Plot side(int lx, int lz, int dy, CensusGrid.Wood w) {
			g.setWood(x0 + lx, dy, z0 + lz, false, w.species() == null ? w : w.withParent(WEST));
			return this;
		}

		/** Leaves in a square of radius r around (lx, lz) from dy {@code from} to {@code to}, where there is no wood. */
		Plot crown(int lx, int lz, int from, int to, int r) {
			for (int dy = from; dy <= to; dy++) {
				for (int x = lx - r; x <= lx + r; x++) {
					for (int z = lz - r; z <= lz + r; z++) {
						if (g.wood(x0 + x, dy, z0 + z) == null) {
							g.set(x0 + x, dy, z0 + z, CensusGrid.LEAVES);
						}
					}
				}
			}
			return this;
		}

		Plot leaf(int lx, int lz, int dy) {
			g.set(x0 + lx, dy, z0 + lz, CensusGrid.LEAVES);
			return this;
		}

		Plot record(int lx, int lz, Species s) {
			g.setRecord(x0 + lx, z0 + lz, s);
			return this;
		}

		Plot plant(int lx, int lz, String id) {
			g.setPlant(x0 + lx, z0 + lz, id);
			return this;
		}
	}
}
