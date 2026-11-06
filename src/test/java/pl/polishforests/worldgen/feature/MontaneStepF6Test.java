package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.GroundPhases.Look;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.SubSeason;
import pl.polishforests.season.TreeFruit;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.norms.ForestNorms.Forest;
import pl.polishforests.worldgen.norms.ForestNorms.Norm;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.StandMap;
import pl.polishforests.worldgen.tree.StandProfile;
import pl.polishforests.worldgen.tree.TreeForm;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeSize;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Step F6 of docs/04-pionowy-wycinek.md (the montane spruce forest, the gray alder forest and the dwarf pine scrub): the
 * growth forms of the spruce (the narrow candle spruce of the mountains, the dwarf spruce of the timberline), the shapes of
 * the gray alder and the rowan, the rowan's fruit (a pure function of the position and the season), the seasons of the new
 * herbs, the stand profile of the montane spruce forest and the card of the three forests.
 */
class MontaneStepF6Test {
	private static final long SEED = 20261104L;

	/** Mean height of the highest wood and mean crown radius (the leaves' mean horizontal distance from the stem). */
	private static double[] shape(Species species, TreeForm.Kind kind) {
		return shape(species, kind, StandMap.uniform(AgeClass.MATURE, true));
	}

	/** As {@link #shape(Species, TreeForm.Kind)}, in a stand map or (null) for lone, open-grown trees. */
	private static double[] shape(Species species, TreeForm.Kind kind, StandMap stand) {
		double top = 0;
		double radius = 0;
		int trees = 0;
		for (int k = 0; k < 120; k++) {
			TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species, CrownLayer.CANOPY, AgeClass.MATURE, SEED, 37 * k - 7000, 64,
					101 * k + 13, null, null, stand, null, null, TreeSize.ONE, TreeForm.of(kind)));
			if (v.woodCount() == 0 || v.leafCount() == 0) {
				continue;
			}
			int high = 0;
			for (int i = 0; i < v.woodCount(); i++) {
				high = Math.max(high, v.woodY(i));
			}
			double r = 0;
			for (int i = 0; i < v.leafCount(); i++) {
				r += Math.hypot(v.leafX(i), v.leafZ(i));
			}
			top += high;
			radius += r / v.leafCount();
			trees++;
		}
		System.out.printf(Locale.ROOT, "[tree shape] %s %s: top %.1f, crown radius %.2f (%d trees)%n", species.id(), kind.id(),
				top / trees, radius / trees, trees);
		return new double[] {top / trees, radius / trees};
	}

	/**
	 * The candle spruce of the montane spruce forest is about as tall as the lowland spruce with a narrower crown (the
	 * short branches of the snow load); the dwarf spruce of the timberline is a third to two thirds of its height.
	 */
	@Test
	void growthFormsOfTheSpruce() {
		double[] normal = shape(Species.SPRUCE, TreeForm.Kind.NORMAL);
		double[] montane = shape(Species.SPRUCE, TreeForm.Kind.MONTANE);
		double[] dwarf = shape(Species.SPRUCE, TreeForm.Kind.DWARF);
		double height = montane[0] / normal[0];
		assertTrue(height >= 0.7 && height <= 1.0, "montane spruce height " + height);
		assertTrue(montane[1] / montane[0] < 0.85 * normal[1] / normal[0], "montane spruce slenderness " + montane[1] / montane[0]
				+ " vs " + normal[1] / normal[0]);
		double low = dwarf[0] / normal[0];
		assertTrue(low >= 0.25 && low <= 0.65, "dwarf spruce " + low);
		// The low rowan of the timberline (a lone tree of the zone).
		double rowan = shape(Species.ROWAN, TreeForm.Kind.DWARF, null)[0] / shape(Species.ROWAN, TreeForm.Kind.NORMAL, null)[0];
		assertTrue(rowan >= 0.25 && rowan <= 0.75, "dwarf rowan " + rowan);
	}

	/** The gray alder is a mid-sized tree of the stream banks (on stools now and then), the rowan a small one. */
	@Test
	void grayAlderAndRowanShapes() {
		assertTrue(SpeciesShape.supported(Species.GRAY_ALDER) && SpeciesShape.supported(Species.ROWAN));
		assertTrue(SpeciesShape.of(Species.GRAY_ALDER).height().mature() < SpeciesShape.of(Species.BLACK_ALDER).height().mature());
		assertTrue(SpeciesShape.of(Species.ROWAN).height().mature() < SpeciesShape.of(Species.GRAY_ALDER).height().mature());
		assertEquals(0.35, SpeciesShape.stoolChance(Species.GRAY_ALDER), 1e-9);
		double rowan = shape(Species.ROWAN, TreeForm.Kind.NORMAL, null)[0];
		double alder = shape(Species.GRAY_ALDER, TreeForm.Kind.NORMAL)[0];
		// In a canopy place of a stand the rowan grows as a tree of the subcanopy, with leaves (not cut by the canopy's floor).
		double[] inStand = shape(Species.ROWAN, TreeForm.Kind.NORMAL);
		assertTrue(inStand[0] >= 4 && inStand[1] > 0.8, "rowan in a stand " + inStand[0] + " " + inStand[1]);
		assertTrue(rowan >= 6 && rowan <= 15, "mature rowan " + rowan);
		assertTrue(alder >= 10 && alder <= 21, "mature gray alder " + alder);
	}

	private static int step(SubSeason sub, int k) {
		return SeasonPhase.firstStep(sub) + k;
	}

	/** The rowan carries fruit on about a fifth of its leaf blocks: orange in late August, scarlet in September. */
	@Test
	void rowanFruitIsPureAndSeasonal() {
		assertTrue(TreeFruit.fruiting(Species.ROWAN));
		for (Species s : Species.values()) {
			if (s != Species.ROWAN) {
				assertTrue(!TreeFruit.fruiting(s), s.id());
			}
		}
		int bearing = 0;
		int n = 20_000;
		for (int k = 0; k < n; k++) {
			int x = 37 * k - 9000;
			int y = k % 300 - 60;
			int z = 101 * k + 7;
			boolean b = TreeFruit.bearing(x, y, z);
			assertEquals(b, TreeFruit.bearing(x, y, z));
			bearing += b ? 1 : 0;
		}
		assertEquals(0.22, bearing / (double) n, 0.02, "bearing share");
		Map<TreeFruit.Look, Integer> september = new EnumMap<>(TreeFruit.Look.class);
		Map<TreeFruit.Look, Integer> june = new EnumMap<>(TreeFruit.Look.class);
		for (int k = 0; k < 2000; k++) {
			int x = 13 * k;
			int z = -7 * k;
			september.merge(TreeFruit.look(Species.ROWAN, step(SubSeason.EARLY_AUTUMN, 2), 1, x, 70, z), 1, Integer::sum);
			june.merge(TreeFruit.look(Species.ROWAN, step(SubSeason.EARLY_SUMMER, 0), 0, x, 70, z), 1, Integer::sum);
			assertSame(TreeFruit.Look.NONE, TreeFruit.look(Species.BIRCH, step(SubSeason.EARLY_AUTUMN, 1), 0, x, 70, z));
		}
		assertTrue(september.getOrDefault(TreeFruit.Look.RIPE, 0) > 300, "ripe in September " + september);
		assertNull(june.get(TreeFruit.Look.RIPE), "ripe in June " + june);
		assertNull(june.get(TreeFruit.Look.UNRIPE), "unripe in June " + june);
	}

	private static Map<Look, Double> shares(GroundPlant plant, int step) {
		Map<Look, Double> out = new EnumMap<>(Look.class);
		for (Look l : Look.values()) {
			out.put(l, 0.0);
		}
		int size = 40;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				out.merge(GroundPhases.look(plant, step, x * 7 - 100, z * 13 + 5), 1.0 / (size * size), Double::sum);
			}
		}
		return out;
	}

	@Test
	void herbsOfTheMountainForestsOfStepF6() {
		// The alpine coltsfoot: evergreen rosettes, purple heads in early July.
		assertEquals(1.0, shares(GroundPlant.ALPINE_COLTSFOOT, step(SubSeason.MID_SUMMER, 0)).get(Look.FULL), 1e-9, "coltsfoot VII");
		assertEquals(1.0, shares(GroundPlant.ALPINE_COLTSFOOT, step(SubSeason.MID_WINTER, 1)).get(Look.LEAVES), 1e-9, "coltsfoot I");
		// The hairy small-reed: panicles in August, leaves in June, gone in winter.
		assertEquals(1.0, shares(GroundPlant.HAIRY_SMALL_REED, step(SubSeason.LATE_SUMMER, 0)).get(Look.FULL), 1e-9, "small-reed VIII");
		assertEquals(1.0, shares(GroundPlant.HAIRY_SMALL_REED, step(SubSeason.EARLY_SUMMER, 0)).get(Look.LEAVES), 1e-9, "small-reed VI");
		assertEquals(1.0, shares(GroundPlant.HAIRY_SMALL_REED, step(SubSeason.MID_WINTER, 1)).get(Look.NONE), 1e-9, "small-reed I");
		// The alpine lady fern: green fronds in July, brown in early October, gone in winter.
		assertEquals(1.0, shares(GroundPlant.ALPINE_LADY_FERN, step(SubSeason.MID_SUMMER, 1)).get(Look.FULL), 1e-9, "lady fern VII");
		assertEquals(1.0, shares(GroundPlant.ALPINE_LADY_FERN, step(SubSeason.MID_WINTER, 1)).get(Look.NONE), 1e-9, "lady fern I");
		// The butterbur: pink spikes in April before its leaves, the large leaves in summer.
		assertEquals(1.0, shares(GroundPlant.BUTTERBUR, step(SubSeason.MID_SPRING, 0)).get(Look.FULL), 1e-9, "butterbur IV");
		assertEquals(1.0, shares(GroundPlant.BUTTERBUR, step(SubSeason.MID_SUMMER, 0)).get(Look.LEAVES), 1e-9, "butterbur VII");
		assertEquals(1.0, shares(GroundPlant.BUTTERBUR, step(SubSeason.MID_WINTER, 1)).get(Look.NONE), 1e-9, "butterbur I");
	}

	/** The montane spruce forest opens in clumps (more and clustered windthrow gaps) within the gap chances of the table. */
	@Test
	void montaneSpruceStandProfile() {
		StandProfile p = StandProfile.of(HabitatBiome.MONTANE_SPRUCE_FOREST);
		assertSame(StandProfile.MONTANE_SPRUCE, p);
		assertSame(StandProfile.ASH_ALDER, StandProfile.of(HabitatBiome.GRAY_ALDER_FOREST));
		assertSame(StandProfile.DEFAULT, StandProfile.of(HabitatBiome.DWARF_PINE_SCRUB));
		assertTrue(p.clusterChance() > StandProfile.BEECH.clusterChance());
		for (int a = 0; a < 3; a++) {
			assertTrue(p.gapChance()[a] <= StandProfile.BEECH.gapChance()[a] + 1e-9, "gap chance " + a);
		}
	}

	/** The card of the montane spruce forest, the gray alder forest and the dwarf pine scrub (docs/04 "Projekt F6"). */
	@Test
	void montaneCardOfStepF6() {
		for (Forest f : new Forest[] {Forest.MONTANE_SPRUCE_NATURAL, Forest.MONTANE_SPRUCE_MANAGED}) {
			range(80, 100, ForestNorms.COMPOSITION + "spruce", f, null);
			range(45, 80, ForestNorms.COMPOSITION + "spruce", f, ForestNorms.Site.FIR_SPRUCE);
			range(20, 50, ForestNorms.COMPOSITION + "fir", f, ForestNorms.Site.FIR_SPRUCE);
			range(20, 60, ForestNorms.BILBERRY, f, null);
			range(60, 95, ForestNorms.MOSS, f, null);
		}
		range(50, 80, ForestNorms.CANOPY, Forest.MONTANE_SPRUCE_NATURAL, null);
		for (Forest f : new Forest[] {Forest.GRAY_ALDER_NATURAL, Forest.GRAY_ALDER_MANAGED}) {
			range(60, 100, ForestNorms.COMPOSITION + "gray_alder", f, null);
			range(70, 100, ForestNorms.HERBS, f, null);
		}
		range(50, 90, ForestNorms.UNDERSTORY, Forest.DWARF_PINE_NATURAL, null);
		range(0, 3, ForestNorms.TREES_PER_CHUNK, Forest.DWARF_PINE_NATURAL, null);
		assertSame(Forest.DWARF_PINE_NATURAL, Forest.of(HabitatBiome.DWARF_PINE_SCRUB, true));
		assertNull(Forest.of(HabitatBiome.DWARF_PINE_SCRUB, false));
		assertSame(Forest.GRAY_ALDER_MANAGED, Forest.of(HabitatBiome.GRAY_ALDER_FOREST, false));
		assertTrue(ForestNorms.COMPOSITION_KEYS.contains("gray_alder") && ForestNorms.COMPOSITION_KEYS.contains("rowan"));
		assertEquals("other", ForestNorms.COMPOSITION_KEYS.get(ForestNorms.COMPOSITION_KEYS.size() - 1));
	}

	private static void range(double min, double max, String metric, Forest forest, ForestNorms.Site site) {
		Norm n = (site == null ? ForestNorms.of(metric, forest, null, null) : ForestNorms.of(metric, forest, null, null, site))
				.orElseThrow(() -> new AssertionError("no norm " + metric + " " + forest + " " + site));
		assertEquals(min, n.min(), 1e-9, metric + " " + forest);
		assertEquals(max, n.max(), 1e-9, metric + " " + forest);
	}
}
