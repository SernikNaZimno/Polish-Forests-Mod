package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownCover;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Bare wood in the crowns of the stands of the slice (review of V9, round 2): in simulated stands on rolling ground
 * ({@link SimulatedStands}, all species of the palettes), the wood of the crowns that is bare and open to the side
 * ({@link CrownCover}: at most one own leaf among its 26 neighbors) stays under {@value #MAX_PER_TREE} blocks per tree in
 * every stand and under {@value #MAX_MEAN} on average. Before round 2 of the review the old-growth oak-hornbeam forest had
 * 0.90 and the managed one 1.75 per tree (an arm of a "lamp post", a gray candelabra of limbs under the foliage, a bare
 * whorl of a pine between crowns that do not meet); after it 0.26 and 0.60, and with the leaf below each branch block
 * and the sleeve of the lower crown (round 2, after the frames) 0.08 and 0.12, the limits tightened from 0.8 / 0.4 / 1.6.
 * Since V10 also no canopy tree is a bare pole ({@link CrownCover#pole}: before, 3-7% of the canopy trees of 12 or more
 * blocks had fewer than 12 leaves, a thin trunk with a tuft seen from above the canopy); the planner leaves such trees out,
 * and likewise a broad-leaved canopy tree with a sparse crown ({@link CrownCover#sparseCrown}: a gray pole with flat arms
 * in the view from above the old-growth oak-hornbeam forest, 1-5% of its canopy trees). Review of V10, round 1: the stem of
 * a broad-leaved canopy tree is not open to one side within its crown, with the leaves of the neighbors (a trunk at the
 * edge of its territory with a one-sided crown stood as a gray pole above lower neighbors; the planner sleeves it).
 */
@Tag("slow")
class StandCrownCoverTest {
	private static final int CHUNKS = 6;
	private static final double MAX_PER_TREE = 0.5;
	private static final double MAX_MEAN = 0.25;
	private static final double MAX_LONE = 0.6;
	/** Largest share of the canopy trees of a stand left out as suppressed (step V10): the trunk counts of N1 stay in tolerance. */
	private static final double MAX_SUPPRESSED = 0.12;
	/** Run of stem blocks open to one side ({@link CrownCover#openStem}) and the largest share of broad-leaved canopy trees with one. */
	private static final int OPEN_STEM_RUN = 5;
	private static final double MAX_OPEN_STEMS = 0.01;
	private static final int RANGES = Species.FLAG_HORNBEAM | Species.FLAG_BEECH | Species.FLAG_SPRUCE;

	private record Stand(HabitatBiome biome, boolean natural, AgeClass age) {
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
	}

	private static int height(TreeVoxels v) {
		int top = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			top = Math.max(top, v.woodY(i));
		}
		return top + 1;
	}

	@Test
	void crownsHaveLittleBareWoodOpenToTheSide() {
		Stand[] stands = {new Stand(HabitatBiome.OAK_HORNBEAM_FOREST, true, AgeClass.OLD_GROWTH),
				new Stand(HabitatBiome.OAK_HORNBEAM_FOREST, false, AgeClass.MATURE),
				new Stand(HabitatBiome.FRESH_PINE_FOREST, true, AgeClass.OLD_GROWTH),
				new Stand(HabitatBiome.FRESH_PINE_FOREST, false, AgeClass.MATURE)};
		CrownCover.Totals all = new CrownCover.Totals();
		StringBuilder report = new StringBuilder();
		for (Stand s : stands) {
			CrownCover.Totals t = new CrownCover.Totals();
			int[] poles = new int[2];
			long suppressed0 = SimulatedStands.SUPPRESSED.get();
			List<TreeVoxels> broadCanopy = new ArrayList<>();
			Set<Long> leaves = new HashSet<>();
			SimulatedStands.plant(s.biome(), s.natural(), s.age(), CHUNKS, RANGES, SimulatedStands.relief(5), (v, species, layer) -> {
				CrownCover c = CrownCover.of(v);
				t.add(c);
				all.add(c);
				for (int i = 0; i < v.leafCount(); i++) {
					leaves.add(key(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i)));
				}
				if (layer == CrownLayer.CANOPY) {
					poles[0]++;
					boolean conifer = species == Species.SCOTS_PINE || species == Species.SPRUCE;
					if (CrownCover.pole(v) || !conifer && height(v) >= 12 && c.sparseCrown()) {
						poles[1]++;
					}
					if (!conifer) {
						broadCanopy.add(v);
					}
				}
			});
			// Review of V10, round 1: the stem of a broad-leaved canopy tree is not open to one side for OPEN_STEM_RUN blocks
			// or more of its crown, the leaves of the neighbors counted (before the stem sleeve: 10-35% of these trees).
			int openStems = 0;
			for (TreeVoxels v : broadCanopy) {
				openStems += CrownCover.openStem(v, (x, y, z) -> leaves.contains(key(x, y, z))) >= OPEN_STEM_RUN ? 1 : 0;
			}
			long suppressed = SimulatedStands.SUPPRESSED.get() - suppressed0;
			report.append(String.format(Locale.ROOT, "%n  %s %s %s: %d trees, %s; canopy %d, bare poles %d, suppressed %d (%.1f%%), "
					+ "open stems %d of %d", s.biome().id(), s.natural() ? "natural" : "managed", s.age(), t.trees(), t.report(CHUNKS * CHUNKS),
					poles[0], poles[1], suppressed, 100.0 * suppressed / Math.max(1, poles[0] + suppressed), openStems, broadCanopy.size()));
			assertTrue(openStems <= MAX_OPEN_STEMS * broadCanopy.size(), s + ": broad-leaved canopy trees with an open stem " + openStems
					+ " of " + broadCanopy.size());
			assertTrue(t.exposedPerTree() <= MAX_PER_TREE, s + ": " + t.report(CHUNKS * CHUNKS));
			// Carried from the review of V9, round 2: no canopy tree of the stand is a bare pole with a tuft.
			assertEquals(0, poles[1], s + ": canopy trees with a degenerate crown");
			assertTrue(suppressed <= MAX_SUPPRESSED * (poles[0] + suppressed), s + ": suppressed " + suppressed + " of " + (poles[0] + suppressed));
		}
		System.out.printf(Locale.ROOT, "[crown cover]%s%n  all: %.2f per tree%n", report, all.exposedPerTree());
		assertTrue(all.exposedPerTree() <= MAX_MEAN, "mean bare wood open to the side per tree: " + all.exposedPerTree());
	}

	/**
	 * The lone trees of every shaped species and age class (the board of {@code slice -Psites=trees}): under
	 * {@value #MAX_LONE} per tree. The wide open-grown crowns hide wood in their core farther than the side rays of
	 * {@link CrownCover} reach, so the limit is higher (round 2: 1.37).
	 */
	@Test
	void loneTreesHaveLittleBareWoodOpenToTheSide() {
		CrownCover.Totals t = new CrownCover.Totals();
		StringBuilder report = new StringBuilder();
		for (Species sp : SpeciesShape.species()) {
			for (AgeClass age : AgeClass.values()) {
				CrownCover c = CrownCover.of(TreeShapePlan.plan(new TreeRequest(sp, CrownLayer.CANOPY, age, SimulatedStands.SEED, 0, 64, 0)));
				t.add(c);
				report.append(String.format(Locale.ROOT, " %s/%s %d", sp.id(), age.ordinal(), c.exposed()));
			}
		}
		System.out.printf(Locale.ROOT, "[crown cover] lone trees: %.2f per tree:%s%n", t.exposedPerTree(), report);
		assertTrue(t.exposedPerTree() <= MAX_LONE, "lone trees: " + t.exposedPerTree());
	}
}
