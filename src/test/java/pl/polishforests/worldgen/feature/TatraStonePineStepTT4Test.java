package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.season.FoliageCurve;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.SpeciesRanges;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.norms.ForestNorms.Forest;
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
 * Step TT4 (docs/04-pionowy-wycinek.md "Projekt TT4"): the Swiss stone pine and the European larch. Their shapes (the
 * stone pine a dense column lower than the spruce, the larch a tall, loose cone), the larch's needles golden in autumn and
 * shed for the winter, the spruce-stone pine forest on the Tatra granite and gneiss (the classifier of the gameplay
 * scale), no stone pine and no larch outside the Tatras (the palettes of the Tatra associations only, which the
 * classifier gives only on the Tatra rocks), the stone pine variant of the blend and of the stand map, and the card of
 * the spruce-stone pine forest.
 */
class TatraStonePineStepTT4Test {
	private static final long SEED = 20261111L;
	static final long SEED_A = 20260927L;
	static final double BOX = 300_000;
	static final Path GENERATED = Path.of("src/main/generated/data/polishforests/worldgen/feature");

	/** Mean height of the highest wood, mean crown radius and leaves per tree of mature canopy trees in a natural stand. */
	private static double[] shape(Species species) {
		double top = 0;
		double radius = 0;
		double leaves = 0;
		int trees = 0;
		StandMap stand = StandMap.uniform(AgeClass.MATURE, true);
		for (int k = 0; k < 80; k++) {
			TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species, CrownLayer.CANOPY, AgeClass.MATURE, SEED, 37 * k - 7000, 64,
					101 * k + 13, null, null, stand, null, null, TreeSize.ONE, TreeForm.of(TreeForm.Kind.NORMAL)));
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
			leaves += v.leafCount();
			trees++;
		}
		System.out.printf(Locale.ROOT, "[tree shape] %s: top %.1f, crown radius %.2f, leaves %.0f (%d trees)%n", species.id(),
				top / trees, radius / trees, leaves / trees, trees);
		assertTrue(trees >= 60, species + ": planned trees " + trees);
		return new double[] {top / trees, radius / trees, leaves / trees};
	}

	@Test
	void shapesOfTheStonePineAndTheLarch() {
		assertTrue(SpeciesShape.supported(Species.STONE_PINE) && SpeciesShape.supported(Species.LARCH));
		assertTrue(SpeciesShape.of(Species.STONE_PINE).archetype().conifer() && SpeciesShape.of(Species.LARCH).archetype().conifer());
		double[] spruce = shape(Species.SPRUCE);
		double[] stonePine = shape(Species.STONE_PINE);
		double[] larch = shape(Species.LARCH);
		// The stone pine: 10-20 m, lower than the spruce, a long dense crown (leaves per block of height at least the
		// spruce's); the larch: as tall as the spruce or taller, a loose crown (fewer leaves per block of height).
		assertTrue(stonePine[0] >= 9 && stonePine[0] <= 20 && stonePine[0] < spruce[0], "stone pine top " + stonePine[0]);
		assertTrue(larch[0] >= 0.9 * spruce[0] && larch[0] <= 34, "larch top " + larch[0]);
		assertTrue(stonePine[2] / stonePine[0] >= 0.9 * spruce[2] / spruce[0], "stone pine density");
		assertTrue(larch[2] / larch[0] < 0.8 * spruce[2] / spruce[0], "larch looseness");
	}

	@Test
	void blocksOfTheNewSpecies() {
		assertTrue(TreeFoliage.STONE_PINE.conifer() && TreeFoliage.STONE_PINE.evergreen());
		assertTrue(TreeFoliage.LARCH.conifer() && !TreeFoliage.LARCH.evergreen());
		for (TreeFoliage f : TreeFoliage.values()) {
			assertEquals(f.conifer() && f != TreeFoliage.LARCH, f.evergreen(), f.toString());
		}
		// The species constants are appended (the registry and the saved worlds keep the order).
		assertEquals(Species.values().length - 1, Species.STONE_PINE.ordinal());
	}

	/** The larch: light green in summer, golden in mid-autumn, bare in mid-winter (the leafless window); the stone pine evergreen. */
	@Test
	void larchTurnsGoldenAndShedsItsNeedles() {
		int gray = 0x7FA05A;
		assertFalse(FoliageCurve.conifer(Species.LARCH));
		assertTrue(FoliageCurve.conifer(Species.STONE_PINE));
		int summer = FoliageCurve.summer(Species.LARCH, gray);
		int autumn = FoliageCurve.color(Species.LARCH, SeasonPhase.firstStep(SubSeason.MID_AUTUMN) + 1, 1, gray);
		int r = autumn >> 16 & 255;
		int g = autumn >> 8 & 255;
		int b = autumn & 255;
		assertTrue(r > g && g > b && r - b > 60, "golden autumn " + Integer.toHexString(autumn));
		assertTrue((summer >> 8 & 255) > (summer >> 16 & 255), "green summer " + Integer.toHexString(summer));
		for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
			assertTrue(FoliageCurve.leafless(Species.LARCH, SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1, phase, false));
			assertFalse(FoliageCurve.leafless(Species.LARCH, SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 1, phase, false));
			assertFalse(FoliageCurve.leafless(Species.STONE_PINE, SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1, phase, false));
		}
	}

	/**
	 * The palettes (trees and dead wood) with a stone pine or a larch match only the Tatra associations, so neither grows
	 * outside the Tatras; and every Tatra association has a tree palette of its own with the species.
	 */
	@Test
	void tatraSpeciesOnlyInTheTatraPalettes() throws IOException {
		int found = 0;
		for (String palette : new String[] {"tree_stand", "deadwood"}) {
			for (JsonElement e : rules(palette)) {
				JsonObject r = e.getAsJsonObject();
				String text = r.toString();
				boolean tatra = false;
				for (Species s : SpeciesRanges.TATRA_ONLY) {
					tatra |= text.contains("polishforests:tree/" + s.id()) || text.contains("polishforests:deadwood/" + s.id())
							|| text.contains("polishforests:stump/" + s.id());
				}
				if (!tatra) {
					continue;
				}
				found++;
				assertTrue(r.has("associations") && !r.getAsJsonArray("associations").isEmpty(), palette + ": " + text);
				for (JsonElement a : r.getAsJsonArray("associations")) {
					assertTrue(SpeciesRanges.TATRA_ASSOCIATIONS.contains(Association.valueOf(a.getAsString().toUpperCase(Locale.ROOT))),
							palette + ": " + text);
				}
			}
		}
		assertTrue(found >= 6, "palettes with the Tatra species: " + found);
		assertTrue(SpeciesRanges.tatraOnly(Species.STONE_PINE) && SpeciesRanges.tatraOnly(Species.LARCH)
				&& !SpeciesRanges.tatraOnly(Species.SPRUCE));
	}

	private static Iterable<JsonElement> rules(String palette) throws IOException {
		return JsonParser.parseString(Files.readString(GENERATED.resolve(palette + ".json"))).getAsJsonObject().getAsJsonArray("rules");
	}

	/**
	 * The classifier (gameplay scale, the massif nearest the origin of seed A): the spruce-stone pine forest on granite and
	 * gneiss in the upper part of the upper montane belt (most of the forest columns at 1440-1520 m on those rocks, none
	 * below 1300 m), the dwarf pine with stone pines above it, the larch slopes facing south; the associations of the
	 * series only on the Tatra granite and gneiss; none of them where the Tatra share is 0 in a box around the massif.
	 */
	@Test
	void stonePineForestOnTheTatraGranite() {
		LandscapeModel m = new LandscapeModel(SEED_A, LandscapeScale.GAMEPLAY, 1.0, true);
		HabitatClassifier k = new HabitatClassifier(SEED_A, m, HabitatClassifier.Mode.NATURAL);
		List<LandscapeModel.Tatra> ts = new ArrayList<>(m.tatras(-BOX, -BOX, BOX, BOX));
		ts.sort(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
		LandscapeModel.Tatra t = ts.get(0);
		// 0 stone pine forest (montane spruce), 1 dwarf pine with stone pines, 2 larch slopes, 3 forest columns on the
		// crystalline rocks at 1440-1520 m, 4 of them the stone pine forest, 5 the series off the crystalline rocks or low,
		// 6 the series outside the Tatras, 7 columns outside the Tatras.
		AtomicLongArray n = new AtomicLongArray(8);
		java.util.Queue<String> off = new java.util.concurrent.ConcurrentLinkedQueue<>();
		double half = 2.0 * t.halfLength();
		double step = 24;
		int cells = (int) (2 * half / step);
		IntStream.range(0, cells).parallel().forEach(j -> {
			for (int i = 0; i < cells; i++) {
				double x = t.x() - half + (i + 0.5) * step;
				double z = t.z() - half + (j + 0.5) * step;
				ColumnSample s = m.sample(x, z);
				int code = k.classify(s, x, z);
				Association a = Habitat.association(code);
				HabitatBiome biome = Habitat.biome(code);
				boolean series = a == Association.PINO_CEMBRAE_PICEETUM || a == Association.LARCH_SLOPE;
				if (s.terrain().wTatra() <= 0.3) {
					n.incrementAndGet(7);
					if (series) {
						n.incrementAndGet(6);
					}
					continue;
				}
				Substrate rock = s.terrain().rock();
				boolean crystalline = rock == Substrate.GRANITE || rock == Substrate.CRYSTALLINE;
				if (series && (!crystalline || s.surface() < 700)) {
					n.incrementAndGet(5);
					off.add(String.format(Locale.ROOT, "(%.0f, %.0f) %s %s %.0f m %s", x, z, biome.id(), a, s.surface(), rock));
				}
				if (a == Association.PINO_CEMBRAE_PICEETUM && biome == HabitatBiome.MONTANE_SPRUCE_FOREST) {
					n.incrementAndGet(0);
					if (s.surface() < 1_200) {
						n.incrementAndGet(5);
						off.add(String.format(Locale.ROOT, "(%.0f, %.0f) low %.0f m", x, z, s.surface()));
					}
				}
				if (a == Association.PINO_CEMBRAE_PICEETUM && biome == HabitatBiome.DWARF_PINE_SCRUB) {
					n.incrementAndGet(1);
				}
				if (a == Association.LARCH_SLOPE) {
					n.incrementAndGet(2);
				}
				if (crystalline && s.terrain().wTatra() >= 0.9 && s.surface() >= 1_440 && s.surface() < 1_520
						&& biome == HabitatBiome.MONTANE_SPRUCE_FOREST && Habitat.zone(code) == Zone.NONE
						&& a != Association.GULLY_TALL_HERBS) {
					n.incrementAndGet(3);
					if (a == Association.PINO_CEMBRAE_PICEETUM) {
						n.incrementAndGet(4);
					}
				}
			}
		});
		String report = String.format(Locale.ROOT, "stone pine forest %d, dwarf pine with stone pines %d, larch slopes %d, "
				+ "crystalline forest at 1440-1520 m %d (stone pine forest %d), off the rocks %d, outside the Tatras %d of %d",
				n.get(0), n.get(1), n.get(2), n.get(3), n.get(4), n.get(5), n.get(6), n.get(7))
				+ (off.isEmpty() ? "" : " " + off.stream().limit(6).toList());
		System.out.println("[tt4] " + report);
		assertTrue(n.get(0) > 0 && n.get(1) > 0 && n.get(2) > 0, report);
		assertTrue(n.get(3) >= 50 && n.get(4) >= 0.6 * n.get(3), report);
		assertEquals(0, n.get(5), report);
		assertTrue(n.get(7) > 1_000, report);
		assertEquals(0, n.get(6), report);
	}

	/** The blend: a column of the stone pine series keeps the series in its partner forest; the other columns as before. */
	@Test
	void blendKeepsTheStonePineSeries() {
		int pino = Habitat.pack(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, ForestSiteType.NONE, Association.PINO_CEMBRAE_PICEETUM,
				LandCover.FOREST, 0, Soil.forBiome(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE));
		int typical = Habitat.withAssociation(pino, Association.TYPICAL);
		assertEquals(Association.TYPICAL, Habitat.association(typical));
		assertEquals(HabitatBiome.MONTANE_SPRUCE_FOREST, Habitat.biome(typical));
		int dwarf = pl.polishforests.worldgen.feature.plan.ForestBlend.partner(pino,
				pl.polishforests.worldgen.feature.plan.ForestBlend.DWARF_PINE);
		assertEquals(HabitatBiome.DWARF_PINE_SCRUB, Habitat.biome(dwarf));
		assertEquals(Association.PINO_CEMBRAE_PICEETUM, Habitat.association(dwarf));
		int beech = pl.polishforests.worldgen.feature.plan.ForestBlend.partner(pino,
				pl.polishforests.worldgen.feature.plan.ForestBlend.MONTANE_BEECH);
		assertEquals(pl.polishforests.worldgen.feature.plan.ForestBlend.code(pino,
				pl.polishforests.worldgen.feature.plan.ForestBlend.MONTANE_BEECH), beech);
		int fromTypical = pl.polishforests.worldgen.feature.plan.ForestBlend.partner(typical,
				pl.polishforests.worldgen.feature.plan.ForestBlend.DWARF_PINE);
		assertEquals(pl.polishforests.worldgen.feature.plan.ForestBlend.code(typical,
				pl.polishforests.worldgen.feature.plan.ForestBlend.DWARF_PINE), fromTypical);
		// The stand profile of the variant: the ages and the stand size of the slice, the gaps within the table.
		StandProfile p = StandProfile.STONE_PINE;
		assertSame(p, StandProfile.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Association.PINO_CEMBRAE_PICEETUM));
		assertSame(StandProfile.MONTANE_SPRUCE, StandProfile.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Association.LARCH_SLOPE));
		assertTrue(java.util.Arrays.equals(p.managedAges(), StandProfile.DEFAULT.managedAges())
				&& java.util.Arrays.equals(p.naturalAges(), StandProfile.DEFAULT.naturalAges())
				&& p.mergeChance() == StandProfile.DEFAULT.mergeChance());
		for (int a = 0; a < 3; a++) {
			assertTrue(p.gapChance()[a] <= StandProfile.MONTANE_SPRUCE.gapChance()[a]);
		}
	}

	/** The card of the spruce-stone pine forest: a site variant of the natural montane spruce forest. */
	@Test
	void cardOfTheStonePineForest() {
		int pino = Habitat.pack(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, ForestSiteType.NONE, Association.PINO_CEMBRAE_PICEETUM,
				LandCover.FOREST, 0, Soil.forBiome(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE));
		assertEquals(ForestNorms.Site.STONE_PINE, ForestNorms.siteOf(pino));
		ForestNorms.Site site = ForestNorms.Site.STONE_PINE;
		Forest f = Forest.MONTANE_SPRUCE_NATURAL;
		ForestNorms.Norm spruce = ForestNorms.of(ForestNorms.COMPOSITION + "spruce", f, ForestNorms.Aspect.SUMMER, AgeClass.MATURE, site)
				.orElseThrow();
		assertEquals(40, spruce.min());
		assertEquals(60, spruce.max());
		ForestNorms.Norm stonePine = ForestNorms.of(ForestNorms.COMPOSITION + "stone_pine", f, ForestNorms.Aspect.SUMMER,
				AgeClass.MATURE, site).orElseThrow();
		assertEquals(20, stonePine.min());
		assertEquals(40, stonePine.max());
		assertTrue(ForestNorms.of(ForestNorms.COMPOSITION + "larch", f, ForestNorms.Aspect.SUMMER, AgeClass.MATURE, site).isPresent());
		for (String metric : new String[] {ForestNorms.TREES_PER_CHUNK, ForestNorms.CANOPY, ForestNorms.UNDERSTORY,
				ForestNorms.HERBS, ForestNorms.MOSS, ForestNorms.DEADWOOD_VOLUME}) {
			assertSame(site, ForestNorms.of(metric, f, ForestNorms.Aspect.SUMMER, AgeClass.MATURE, site).orElseThrow().site(), metric);
		}
		// The card of the typical montane spruce forest stays as it was.
		ForestNorms.Norm typical = ForestNorms.of(ForestNorms.COMPOSITION + "spruce", f, ForestNorms.Aspect.SUMMER, AgeClass.MATURE, null)
				.orElseThrow();
		assertEquals(80, typical.min());
		assertTrue(ForestNorms.of(ForestNorms.COMPOSITION + "stone_pine", f, ForestNorms.Aspect.SUMMER, AgeClass.MATURE, null).isEmpty());
	}
}
