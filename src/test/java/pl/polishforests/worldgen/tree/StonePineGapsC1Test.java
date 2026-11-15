package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Step C1 (CP-D.8, docs/04-pionowy-wycinek.md "Projekt C1"): the spruce-stone pine forest is a loose stand of clumps with
 * frequent, large gaps. The gap chances of {@link StandProfile#STONE_PINE} stay within the table (the gap process of every
 * forest draws against the maximum of the table, so a higher chance would move the gaps of all forests), the diameters
 * within one gap cell, and the planned open share of its natural stands (the gaps of the stand map before the crowns lean
 * into them) is several times that of the profile of step TT4 in every age class.
 */
class StonePineGapsC1Test {
	private static final long SEED = 20260927L;
	/** The profile of step TT4 (before C1), for the comparison. */
	private static final StandProfile TT4 = new StandProfile(1.0, StandProfile.DEFAULT.managedAges(),
			StandProfile.DEFAULT.naturalAges(), ForestStandMap.MERGE_CHANCE, new double[] {0.10, 0.36, 0.44}, 0.6, 16, 0.45, 8, 32,
			new double[] {0, 0, 0}, 0);

	@Test
	void gapChancesStayWithinTheTable() {
		double[] withStonePine = new double[3];
		double[] without = new double[3];
		for (StandProfile p : StandProfile.ALL) {
			for (int a = 0; a < 3; a++) {
				withStonePine[a] = Math.max(withStonePine[a], p.gapChance()[a]);
				if (p != StandProfile.STONE_PINE) {
					without[a] = Math.max(without[a], p.gapChance()[a]);
				}
			}
		}
		// The maximum of the table (ForestStandMap.gapChanceMax) does not move: the gap draws of the other forests stay.
		assertArrayEquals(without, withStonePine);
		assertArrayEquals(StandProfile.MONTANE_SPRUCE.gapChance(), StandProfile.STONE_PINE.gapChance());
		StandProfile p = StandProfile.STONE_PINE;
		assertTrue(p.gapMax() <= ForestStandMap.GAP_CELL && p.gapMin() > 0);
		assertTrue(p.gapMedian() > TT4.gapMedian() && p.clusterChance() > TT4.clusterChance());
		assertEquals(0, Arrays.stream(p.nestChance()).sum(), "no managed stands in the Tatra National Park");
		// The stands themselves (ages, size) are those of the slice, so the stand lattice does not change.
		assertTrue(!p.standsDiffer());
		// The scattered gaps (the clump mechanism of C1, its own salt) belong to this profile only.
		assertTrue(p.scattered() > 0 && p.scatteredMedian() > 0);
		for (StandProfile other : StandProfile.ALL) {
			if (other != StandProfile.STONE_PINE) {
				assertEquals(0, other.scattered(), "scattered gaps outside the spruce-stone pine forest");
			}
		}
	}

	@Test
	void stonePineGapEdges() {
		// The census of a spruce-stone pine forest plot: the timberline zone of the forest and the brooks end no gap, any
		// other non-forest column does (ForestNorms.breaksStonePineGap, docs/04 "Projekt C1").
		java.util.function.BiFunction<pl.polishforests.worldgen.habitat.HabitatBiome, pl.polishforests.worldgen.habitat.Zone, Integer> code =
				(b, z) -> pl.polishforests.worldgen.habitat.Habitat.pack(b, z, pl.polishforests.worldgen.habitat.ForestSiteType.NONE,
						pl.polishforests.worldgen.habitat.Association.TYPICAL, pl.polishforests.worldgen.habitat.LandCover.forBiome(b), 0,
						pl.polishforests.worldgen.habitat.Soil.forBiome(b, z));
		assertTrue(!pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_SPRUCE_FOREST, pl.polishforests.worldgen.habitat.Zone.NONE)));
		assertTrue(!pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_SPRUCE_FOREST, pl.polishforests.worldgen.habitat.Zone.TIMBERLINE)));
		assertTrue(!pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.STREAM, pl.polishforests.worldgen.habitat.Zone.NONE)));
		assertTrue(pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_SPRUCE_FOREST, pl.polishforests.worldgen.habitat.Zone.MONTANE_TALL_HERBS)));
		assertTrue(pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.SUBNIVAL_ROCKS, pl.polishforests.worldgen.habitat.Zone.NONE)));
		assertTrue(pl.polishforests.worldgen.norms.ForestNorms.breaksStonePineGap(code.apply(
				pl.polishforests.worldgen.habitat.HabitatBiome.ALPINE_GRASSLAND, pl.polishforests.worldgen.habitat.Zone.NONE)));
	}

	@Test
	void rockOutcropsOfAGapsSizeLeaveThePlot() {
		// Stage 2 of C1: a group of at least GAP_MIN_AREA rock columns is an outcrop (non-forest ground that ends a gap); a
		// smaller group, a boulder, or rock touching only by a corner is not.
		pl.polishforests.worldgen.norms.CensusGrid g = new pl.polishforests.worldgen.norms.CensusGrid(0, 0, 30, 30, 4);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				g.setGround(x, z, 100, "polishforests:mossy_podzol");
			}
		}
		int min = pl.polishforests.worldgen.norms.ForestCensus.GAP_MIN_AREA;
		for (int i = 0; i < min; i++) {
			g.setGround(i % 10, i / 10, 100, i % 2 == 0 ? "polishforests:tatra_granite" : "polishforests:granite_scree");
		}
		for (int i = 0; i < min - 1; i++) {
			g.setGround(15 + i % 10, 15 + i / 10, 100, "polishforests:gneiss");
		}
		g.setGround(29, 29, 100, "minecraft:stone").setGround(28, 28, 100, "minecraft:stone");
		boolean[] out = pl.polishforests.worldgen.norms.ForestCensus.rockOutcrops(g,
				pl.polishforests.worldgen.norms.ForestNorms.ROCK_GROUND, min);
		int count = 0;
		for (boolean b : out) {
			count += b ? 1 : 0;
		}
		assertEquals(min, count);
		assertTrue(out[g.column(0, 0)] && out[g.column(9, 4)]);
		assertTrue(!out[g.column(15, 15)] && !out[g.column(29, 29)]);
		assertTrue(!pl.polishforests.worldgen.norms.ForestNorms.ROCK_GROUND.contains("polishforests:rendzina"));
	}

	@Test
	void plannedOpenShareGrows() {
		ForestStandMap world = new ForestStandMap(SEED, 1, 0.85);
		int side = 1_536;
		for (AgeClass age : AgeClass.values()) {
			double now = openShare(world, age, StandProfile.STONE_PINE, side);
			double before = openShare(world, age, TT4, side);
			double spruce = openShare(world, age, StandProfile.MONTANE_SPRUCE, side);
			System.out.printf(Locale.ROOT, "[C1 gaps] %s: planned open share %.2f%% (TT4 %.2f%%, montane spruce %.2f%%)%n", age,
					100 * now, 100 * before, 100 * spruce);
			assertTrue(now >= 2.5 * before, age + ": " + now + " vs " + before);
			assertTrue(now > spruce, age + ": " + now + " vs the spruce forest " + spruce);
		}
		double old = openShare(world, AgeClass.OLD_GROWTH, StandProfile.STONE_PINE, side);
		// N5 of the spruce forest is 5-20%: the simulated census of the stands measures about 16% in the old growth of the
		// C1 profile without a non-forest mask (planned 12.6%: the census also counts the openings among the scattered
		// crowns next to a gap), 12.7-12.8% on the masks of the two Tatra plots (docs/04 "Projekt C1").
		assertTrue(old >= 0.08 && old <= 0.20, "planned open share of the old growth " + old);
	}

	/** Share of the columns of a square inside a gap of the profile's natural stands of one age class. */
	private static double openShare(ForestStandMap world, AgeClass age, StandProfile profile, int side) {
		int open = 0;
		for (int x = 0; x < side; x += 2) {
			for (int z = 0; z < side; z += 2) {
				open += world.rawGapDepth(x, z, age, true, profile) > 0 ? 1 : 0;
			}
		}
		return open / (double) (side / 2 * (side / 2));
	}
}
