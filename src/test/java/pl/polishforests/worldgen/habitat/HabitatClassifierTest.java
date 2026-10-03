package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Habitat classifier (docs/03-m2-biomy.md §3.4, §12.1): determinism, code packing, synthetic cases,
 * reachability of every biome and every zone, no riparian forest outside the valley floor over 10⁶ samples
 * and a classification cost ≤ 0.5 µs.
 */
class HabitatClassifierTest {
	static final long SEED = 20260927L;

	static HabitatClassifier real(HabitatClassifier.Mode t) {
		return new HabitatClassifier(SEED, LandscapeScale.REALISTIC, t);
	}

	@Test
	void packingIsReversible() {
		long h = 1;
		for (int i = 0; i < 20_000; i++) {
			h = Noise.mix(h + i);
			HabitatBiome b = HabitatBiome.values()[(int) Long.remainderUnsigned(h, HabitatBiome.values().length)];
			Zone s = Zone.values()[(int) Long.remainderUnsigned(h >>> 8, Zone.values().length)];
			ForestSiteType siteType = ForestSiteType.values()[(int) Long.remainderUnsigned(h >>> 16, ForestSiteType.values().length)];
			Association z = Association.values()[(int) Long.remainderUnsigned(h >>> 24, Association.values().length)];
			LandCover p = LandCover.values()[(int) Long.remainderUnsigned(h >>> 32, LandCover.values().length)];
			int f = (int) (h >>> 40) & 15;
			Soil g = Soil.values()[(int) Long.remainderUnsigned(h >>> 44, Soil.values().length)];
			Habitat sd = new Habitat(b, s, siteType, z, p, f, g);
			assertEquals(sd, Habitat.of(sd.code()));
		}
		assertEquals(36, HabitatBiome.values().length);
	}

	@Test
	void biomeIdsAreFrozen() {
		String expected = "dry_pine_forest fresh_pine_forest coastal_pine_forest moist_pine_forest bog_woodland mixed_pine_forest mixed_forest oak_hornbeam_forest "
				+ "lowland_beech_forest alder_carr ash_alder_forest willow_poplar_forest elm_ash_forest upland_fir_forest "
				+ "montane_beech_forest montane_spruce_forest gray_alder_forest dwarf_pine_scrub raised_bog fen reedbed "
				+ "willow_scrub heath wet_meadow hay_meadow arable_land beach white_dune gray_dune alpine_grassland sea lagoon river "
				+ "stream lake dystrophic_lake";
		StringBuilder sb = new StringBuilder();
		for (HabitatBiome b : HabitatBiome.values()) {
			sb.append(sb.isEmpty() ? "" : " ").append(b.id());
		}
		assertEquals(expected, sb.toString());
		long forestBiomes = HabitatBiome.values().length - EnumSet.allOf(HabitatBiome.class).stream().filter(b -> !b.isForest()).count();
		assertEquals(18, forestBiomes);
		assertEquals(6, EnumSet.allOf(HabitatBiome.class).stream().filter(HabitatBiome::isWater).count());
	}

	@Test
	void resultIsDeterministic() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			HabitatClassifier a = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
			HabitatClassifier b = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
			double step = sc == LandscapeScale.REALISTIC ? 397 : 23;
			int n = 160;
			int[] serialCodes = new int[n * n];
			for (int i = 0; i < n * n; i++) {
				double x = (i % n) * step - 3_000;
				double z = (i / n) * step + 1_000;
				serialCodes[i] = a.classify(m.sample(x, z), x, z);
			}
			int[] parallelCodes = new int[n * n];
			LandscapeModel m2 = new LandscapeModel(SEED, sc, 1.0);
			IntStream.range(0, n * n).parallel().forEach(i -> {
				double x = (i % n) * step - 3_000;
				double z = (i / n) * step + 1_000;
				parallelCodes[i] = b.classify(m2.sample(x, z), x, z);
			});
			for (int i = 0; i < n * n; i++) {
				assertEquals(serialCodes[i], parallelCodes[i], "column " + i + " at scale " + sc.id());
			}
		}
	}

	@Test
	void gameplaySlopeMatchesVerticalMapping() {
		for (double m : new double[] {5, 50, 130, 400, 1_000, 1_600}) {
			double d = (VerticalScale.GAMEPLAY.blocksForMeters(m + 0.01) - VerticalScale.GAMEPLAY.blocksForMeters(m - 0.01)) / 0.02;
			assertEquals(d, Calibration.gameplayBlocksPerMeter(m), 1e-4 * d, "m = " + m);
		}
	}

	// ------------------------------------------------------------------ synthetic cases

	/**
	 * Case: a sample with the expected biome (null: any) and zone (null: any). Cases that depend on noise (patches,
	 * variants) are moved over points until the result appears; the others must come out at the first point.
	 */
	record Case(String description, ColumnSample sample, HabitatClassifier.Mode mode, HabitatBiome biome, Zone zone, boolean search) {
	}

	static List<Case> cases() {
		List<Case> l = new ArrayList<>();
		HabitatClassifier.Mode n = HabitatClassifier.Mode.NATURAL;
		HabitatClassifier.Mode d = HabitatClassifier.Mode.PRESENT_DAY;
		// Waters.
		l.add(new Case("sea", SyntheticSample.coast(-500, 0, Substrate.SAND).water(WaterKind.SEA, 0, -12).build(), n,
				HabitatBiome.SEA, Zone.NONE, false));
		SyntheticSample lagoon = SyntheticSample.coast(800, 0, Substrate.LAKE_MUD).water(WaterKind.SEA, 0, -3);
		l.add(new Case("lagoon", lagoon.build(), n, HabitatBiome.LAGOON, Zone.SUBMERGED_PLANTS, false));
		l.add(new Case("lagoon reedbed", SyntheticSample.coast(800, 0, Substrate.LAKE_MUD).water(WaterKind.SEA, 0, -1).build(),
				n, HabitatBiome.REEDBED, Zone.REEDBED, false));
		l.add(new Case("river", SyntheticSample.morainePlateau().h(95).stream(3, 150, -20, 0.3).water(WaterKind.RIVER, 98, 95).build(),
				n, HabitatBiome.RIVER, Zone.CHANNEL, false));
		l.add(new Case("stream", SyntheticSample.beskids(600).stream(1, 6, -1, 20).water(WaterKind.RIVER, 600, 599.5).build(), n,
				HabitatBiome.STREAM, Zone.CHANNEL, false));
		l.add(new Case("lake (deep water)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -80,
				110, 400, 5).water(WaterKind.LAKE, 110, 100).build(), n, HabitatBiome.LAKE, Zone.NONE, false));
		l.add(new Case("submerged plants (elodeids)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -40, 110, 400,
				5).water(WaterKind.LAKE, 110, 106).build(), n, HabitatBiome.LAKE, Zone.SUBMERGED_PLANTS, false));
		l.add(new Case("oxbow lake nymphaeids", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.OXBOW_LAKE, -30,
				110, 60, 5).water(WaterKind.OXBOW, 110, 108).build(), n, HabitatBiome.LAKE, Zone.FLOATING_LEAVED_PLANTS, true));
		l.add(new Case("lake reedbed (biome)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE,
				-20, 110, 400, 5).water(WaterKind.LAKE, 110, 109).build(), n, HabitatBiome.REEDBED, Zone.REEDBED, false));
		l.add(new Case("kettle pond reedbed (zone)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.KETTLE_POND, -3, 110,
				25, 5).water(WaterKind.KETTLE, 110, 109.5).build(), n, HabitatBiome.LAKE, Zone.REEDBED, false));
		for (long id = 1; id < 200; id++) {
			// First water body with a dystrophic hash on the outwash plain: floating mat at the shore.
			ColumnSample c = SyntheticSample.outwashPlain().standingWater(ColumnSample.StandingWaterKind.KETTLE_POND, -2, 140, 60, id)
					.water(WaterKind.KETTLE, 140, 139).build();
			if (Habitat.biome(real(n).classify(c, 0, 0)) == HabitatBiome.DYSTROPHIC_LAKE) {
				l.add(new Case("dystrophic lake, floating mat", c, n, HabitatBiome.DYSTROPHIC_LAKE, Zone.FLOATING_MAT, false));
				break;
			}
		}
		for (long id = 1; id < 400; id++) {
			// Lobelia lake: belt of alders at the shore.
			ColumnSample water = SyntheticSample.outwashPlain().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -30, 140, 300, id)
					.water(WaterKind.LAKE, 140, 134).build();
			if (Habitat.association(real(n).classify(water, 0, 0)) == Association.LOBELIA_LAKE) {
				ColumnSample land = SyntheticSample.outwashPlain().h(141.5).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 2, 140, 300, id)
						.build();
				l.add(new Case("lobelia lake, alders at the shore", land, n, null, Zone.SHORE_ALDERS, false));
				break;
			}
		}
		// Coast.
		l.add(new Case("wet beach", SyntheticSample.coast(10, 1, Substrate.BEACH_SAND).build(), n, HabitatBiome.BEACH, Zone.NONE,
				false));
		l.add(new Case("strandline", SyntheticSample.coast(40, 1.5, Substrate.BEACH_SAND).build(), n, HabitatBiome.BEACH, Zone.STRANDLINE,
				false));
		l.add(new Case("embryo dune", SyntheticSample.coast(70, 5, Substrate.BEACH_SAND).build(), n, HabitatBiome.WHITE_DUNE,
				Zone.EMBRYO_DUNE, false));
		l.add(new Case("gray dune", SyntheticSample.coast(320, 6, Substrate.SAND).build(), n, HabitatBiome.GRAY_DUNE, Zone.NONE,
				false));
		l.add(new Case("coastal crowberry pine forest", SyntheticSample.coast(1_000, 10, Substrate.SAND).build(), n, HabitatBiome.COASTAL_PINE_FOREST,
				Zone.NONE, false));
		l.add(new Case("cliff face", SyntheticSample.coast(75, 20, Substrate.GLACIAL_TILL).landform(Landform.CLIFF).build(), n,
				null, Zone.CLIFF_FACE, false));
		l.add(new Case("cliff top", SyntheticSample.coast(100, 20, Substrate.GLACIAL_TILL).build(), n, null,
				Zone.CLIFF_TOP, false));
		SyntheticSample hinterland = SyntheticSample.coast(400, 1.2, Substrate.SAND);
		hinterland.lowShore = 1;
		l.add(new Case("fen behind the spit", hinterland.build(), n, HabitatBiome.FEN, Zone.NONE, false));
		// Zonal habitats.
		SyntheticSample dryPine = SyntheticSample.outwashPlain().landform(Landform.INLAND_DUNES);
		dryPine.sandiness = 0.95;
		dryPine.sBar = 133;
		l.add(new Case("dry pine forest on a dune", dryPine.build(), n, HabitatBiome.DRY_PINE_FOREST, Zone.NONE, true));
		l.add(new Case("heath clearing on a dune", dryPine.build(), n, HabitatBiome.HEATH, Zone.NONE, true));
		SyntheticSample freshPine = SyntheticSample.outwashPlain();
		freshPine.sandiness = 0.95;
		l.add(new Case("fresh pine forest", freshPine.build(), n, HabitatBiome.FRESH_PINE_FOREST, Zone.NONE, false));
		SyntheticSample moistPine = SyntheticSample.outwashPlain();
		moistPine.sandiness = 0.95;
		moistPine.sBar = 141.0;
		l.add(new Case("moist pine forest", moistPine.build(), n, HabitatBiome.MOIST_PINE_FOREST, Zone.NONE, false));
		SyntheticSample bogWoodland = SyntheticSample.outwashPlain();
		bogWoodland.sandiness = 0.95;
		bogWoodland.sBar = 142.3;
		l.add(new Case("bog woodland", bogWoodland.build(), n, HabitatBiome.BOG_WOODLAND, Zone.NONE, false));
		SyntheticSample mixedPine = SyntheticSample.outwashPlain();
		mixedPine.sandiness = 0.2;
		l.add(new Case("mixed pine forest", mixedPine.build(), n, HabitatBiome.MIXED_PINE_FOREST, Zone.NONE, false));
		SyntheticSample mixedForest = SyntheticSample.morainePlateau();
		mixedForest.sandiness = 0.75;
		mixedForest.o = 0.35;
		l.add(new Case("mixed forest", mixedForest.build(), n, HabitatBiome.MIXED_FOREST, Zone.NONE, false));
		SyntheticSample oakHornbeam = SyntheticSample.morainePlateau();
		oakHornbeam.sandiness = 0.1;
		oakHornbeam.o = 0.35;
		l.add(new Case("oak-hornbeam forest", oakHornbeam.build(), n, HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE, false));
		SyntheticSample lowlandBeech = SyntheticSample.morainePlateau();
		lowlandBeech.sandiness = 0.1;
		lowlandBeech.o = 0.95;
		lowlandBeech.convexity = 1;
		l.add(new Case("lowland beech forest", lowlandBeech.build(), n, HabitatBiome.LOWLAND_BEECH_FOREST, Zone.NONE, true));
		SyntheticSample alderCarr = SyntheticSample.morainePlateau();
		alderCarr.sandiness = 0.1;
		alderCarr.sBar = 124.2;
		l.add(new Case("zonal alder carr", alderCarr.build(), n, HabitatBiome.ALDER_CARR, Zone.NONE, false));
		SyntheticSample firForest = SyntheticSample.morainePlateau().h(400);
		firForest.sandiness = 0.95;
		firForest.p = 0.7;
		l.add(new Case("upland fir forest", firForest.build(), n, HabitatBiome.UPLAND_FIR_FOREST, Zone.NONE, false));
		// Watercourses (§4).
		SyntheticSample willowScrub = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 20, 0.3).onValleyFloor(0, 900);
		l.add(new Case("willow scrub at a large river", willowScrub.build(), n, HabitatBiome.WILLOW_SCRUB, Zone.WILLOW_SCRUB, false));
		SyntheticSample pointBar = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 30, 0.3).onValleyFloor(0, 900);
		pointBar.convexBank = true;
		l.add(new Case("point bar", pointBar.build(), n, HabitatBiome.WILLOW_SCRUB, Zone.POINT_BAR, true));
		SyntheticSample fringe = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 50, 0.3).onValleyFloor(0, 900);
		l.add(new Case("herb fringe behind the willow scrub", fringe.build(), n, HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE, true));
		SyntheticSample whiteWillow = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 150, 0.3).onValleyFloor(0.1, 900);
		l.add(new Case("white willow forest", whiteWillow.build(), n, HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE, true));
		SyntheticSample elmAsh = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 800, 0.3).onValleyFloor(0.5, 900);
		elmAsh.convexity = 0.5;
		l.add(new Case("elm-ash forest", elmAsh.build(), n, HabitatBiome.ELM_ASH_FOREST, Zone.NONE, true));
		SyntheticSample backswamp = SyntheticSample.morainePlateau().h(91.7);
		backswamp.channelLevel = 90.4;
		backswamp.stream(3, 150, 800, 0.2).onValleyFloor(0.85, 900);
		backswamp.convexity = -1;
		l.add(new Case("backswamp: fen", backswamp.build(), n, HabitatBiome.FEN, Zone.NONE, true));
		l.add(new Case("backswamp: alder carr", backswamp.build(), n, HabitatBiome.ALDER_CARR, Zone.NONE, true));
		SyntheticSample ashAlder = SyntheticSample.morainePlateau().stream(1, 5, 8, 1).onValleyFloor(0.1, 70);
		l.add(new Case("ash-alder forest", ashAlder.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.NONE, true));
		SyntheticSample herbs = SyntheticSample.morainePlateau().stream(1, 5, 1, 1).onValleyFloor(0, 70);
		l.add(new Case("tall herbs of a small river", herbs.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.TALL_HERBS, false));
		SyntheticSample willows = SyntheticSample.outwashPlain().stream(2, 20, 15, 1).onValleyFloor(0, 100);
		willows.sandiness = 0.95;
		l.add(new Case("willows of a small river on sand", willows.build(), n, null, Zone.RIVERSIDE_WILLOWS, true));
		SyntheticSample headwaters = SyntheticSample.morainePlateau().stream(1, 3, 10, 1).onValleyFloor(0, 40).landform(Landform.HEADWATERS);
		l.add(new Case("spring area", headwaters.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.SPRING_AREA, true));
		SyntheticSample treeRow = SyntheticSample.morainePlateau().h(126).stream(1, 5, 6, 1);
		treeRow.rawSurface = 126;
		l.add(new Case("alder tree row (PRESENT_DAY mode)", treeRow.build(), d, null, Zone.TREE_ROW, true));
		SyntheticSample gravelBar = SyntheticSample.beskids(500).stream(2, 8, 4, 8).onValleyFloor(0, 40);
		gravelBar.convexBank = true;
		l.add(new Case("gravel bar", gravelBar.build(), n, null, Zone.GRAVEL_BAR, false));
		SyntheticSample mountainWillowScrub = SyntheticSample.beskids(500).stream(2, 8, 5, 8).onValleyFloor(0.1, 40);
		l.add(new Case("mountain willow scrub", mountainWillowScrub.build(), n, null, Zone.WILLOW_SCRUB, false));
		SyntheticSample grayAlderBand = SyntheticSample.beskids(600).stream(2, 8, 12, 8).onValleyFloor(0.2, 40);
		l.add(new Case("gray alder forest", grayAlderBand.build(), n, HabitatBiome.GRAY_ALDER_FOREST, Zone.NONE, true));
		SyntheticSample mountainHerbs = SyntheticSample.beskids(1_050).stream(1, 3, 0.5, 30).onValleyFloor(0, 20);
		l.add(new Case("streamside tall herbs", mountainHerbs.build(), n, null, Zone.MONTANE_TALL_HERBS, false));
		// Standing water on land.
		SyntheticSample willowCarr = SyntheticSample.morainePlateau().h(111.5).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 20, 110, 400, 5);
		willowCarr.slope = 1;
		l.add(new Case("willow carr by a lake", willowCarr.build(), n, HabitatBiome.ALDER_CARR, Zone.WILLOW_CARR, true));
		SyntheticSample shoreReedbed = SyntheticSample.morainePlateau().h(111.2).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 3, 110, 400, 5);
		l.add(new Case("shore reedbed", shoreReedbed.build(), n, null, Zone.SHORE_REEDBED, false));
		SyntheticSample raisedBog = SyntheticSample.outwashPlain().h(139.5).standingWater(ColumnSample.StandingWaterKind.KETTLE_BOG, -30, 140, 120, 9);
		raisedBog.substrate = Substrate.PEAT;
		raisedBog.ombrotrophicPeat = true;
		l.add(new Case("raised bog", raisedBog.build(), n, HabitatBiome.RAISED_BOG, Zone.NONE, false));
		// Mountains (§5.1).
		SyntheticSample beech = SyntheticSample.beskids(800);
		beech.sandiness = 0.1;
		beech.slope = 20;
		beech.aspect = 180;
		l.add(new Case("Carpathian beech forest", beech.build(), n, HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, false));
		l.add(new Case("montane spruce forest", SyntheticSample.beskids(1_260).build(), n, HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, false));
		SyntheticSample dwarfPine = SyntheticSample.beskids(1_500);
		dwarfPine.summit = 1_700;
		l.add(new Case("dwarf pine scrub", dwarfPine.build(), n, HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE, false));
		SyntheticSample alpine = SyntheticSample.beskids(1_720);
		alpine.summit = 1_720;
		l.add(new Case("alpine grassland", alpine.build(), n, HabitatBiome.ALPINE_GRASSLAND, Zone.NONE, false));
		SyntheticSample timberline = SyntheticSample.beskids(1_355);
		timberline.summit = 1_700;
		l.add(new Case("timberline", timberline.build(), n, HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.TIMBERLINE, true));
		// PRESENT_DAY mode: non-forest biomes from the forest mask.
		SyntheticSample arable = SyntheticSample.morainePlateau();
		arable.sandiness = 0.1;
		arable.o = 0.35;
		l.add(new Case("arable land (PRESENT_DAY mode)", arable.build(), d, HabitatBiome.ARABLE_LAND, Zone.NONE, true));
		l.add(new Case("hay meadow (PRESENT_DAY mode)", arable.build(), d, HabitatBiome.HAY_MEADOW, Zone.NONE, true));
		SyntheticSample meadow = SyntheticSample.morainePlateau().stream(1, 5, 8, 1).onValleyFloor(0.1, 70);
		l.add(new Case("wet meadow (PRESENT_DAY mode)", meadow.build(), d, HabitatBiome.WET_MEADOW, null, true));
		return l;
	}

	/** First point (out of 4000 on a spiral) where the case gives the expected result; null if there is none. */
	static double[] findPoint(Case p) {
		HabitatClassifier k = real(p.mode());
		int attempts = p.search() ? 4_000 : 1;
		for (int i = 0; i < attempts; i++) {
			double x = i * 37.3;
			double z = i * 53.9 - 7_000;
			int code = k.classify(p.sample(), x, z);
			if ((p.biome() == null || Habitat.biome(code) == p.biome()) && (p.zone() == null || Habitat.zone(code) == p.zone())) {
				return new double[] {x, z};
			}
		}
		return null;
	}

	@Test
	void syntheticCases() {
		List<String> errors = new ArrayList<>();
		HabitatClassifier k = real(HabitatClassifier.Mode.NATURAL);
		for (Case p : cases()) {
			if (findPoint(p) == null) {
				int code = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, p.mode()).classify(p.sample(), 0, -7_000);
				errors.add(p.description() + ": expected " + p.biome() + "/" + p.zone() + ", got " + Habitat.of(code));
			}
		}
		assertTrue(errors.isEmpty(), String.join("\n", errors));
		assertTrue(k.mode() == HabitatClassifier.Mode.NATURAL);
	}

	/**
	 * F2 (docs/m2/poprawka-geometrii.md, step K1): on the floor of a large river the zones follow the channel of the
	 * dominant valley, and a smaller, closer watercourse keeps only its own belt of ash-alder riparian forest. A column
	 * 60 m from a tributary 8 m wide (beyond its belt of at most 1.2 · max(15 m, 4 W) = 38.4 m) and 150 m from the bank
	 * of a river 150 m wide lies in the white willow forest of the river (D_wb ≥ 0.8 · 255 m); before F2 its zones came
	 * from the tributary (class B). Checked at 200 points (jitter of the belts).
	 */
	@Test
	void floorZonesFollowTheChannelOfTheDominantValley() {
		HabitatClassifier k = real(HabitatClassifier.Mode.NATURAL);
		ColumnSample river = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3).onValleyFloor(0.1, 900)
				.floorChannel(150, 150, 90).build();
		ColumnSample nearestOnly = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3).onValleyFloor(0.1, 900).build();
		ColumnSample tributaryBelt = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 10, 0.3).onValleyFloor(0.1, 900)
				.floorChannel(150, 150, 90).build();
		// The floor of the river lies more than FLOOR_H above its water: not its floor, so the tributary decides.
		ColumnSample offRiverFloor = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3).onValleyFloor(0.1, 900)
				.floorChannel(150, 150, 85).build();
		// Not wider than 1.05 W of the nearest channel: the nearest channel is (like) the channel of the dominant valley.
		ColumnSample sameWidth = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3).onValleyFloor(0.1, 900)
				.floorChannel(150, 8.4, 90).build();
		// Channel of the dominant valley of class B (order 2, W < 30 m): the nearest channel decides.
		ColumnSample classB = SyntheticSample.morainePlateau().h(91.7).stream(2, 8, 60, 0.3).onValleyFloor(0.1, 900)
				.floorChannel(150, 20, 90).build();
		// Off the model's floor (inFloor false): the nearest channel decides.
		SyntheticSample offFlag = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3).floorChannel(150, 150, 90);
		offFlag.floorHalfWidth = 900;
		ColumnSample notInFloor = offFlag.build();
		for (int i = 0; i < 200; i++) {
			double x = i * 37.3;
			double z = i * 53.9 - 7_000;
			int code = k.classify(river, x, z);
			assertEquals(HabitatBiome.WILLOW_POPLAR_FOREST, Habitat.biome(code), "river zones at (" + x + ", " + z + "): " + Habitat.of(code));
			assertEquals(Association.SALICETUM_ALBAE, Habitat.association(code), "white willow forest at (" + x + ", " + z + ")");
			assertEquals(k.classify(nearestOnly, x, z), k.classify(offRiverFloor, x, z), "off the river floor");
			assertEquals(k.classify(nearestOnly, x, z), k.classify(sameWidth, x, z), "channel not wider");
			assertTrue(Habitat.biome(k.classify(nearestOnly, x, z)) != HabitatBiome.WILLOW_POPLAR_FOREST,
					"without the channel of the dominant valley the zones come from the tributary");
			assertEquals(HabitatBiome.ASH_ALDER_FOREST, Habitat.biome(k.classify(tributaryBelt, x, z)), "belt of the tributary");
			SyntheticSample b = SyntheticSample.morainePlateau().h(91.7).stream(2, 8, 60, 0.3).onValleyFloor(0.1, 900);
			assertEquals(k.classify(b.build(), x, z), k.classify(classB, x, z), "class B channel of the dominant valley");
			SyntheticSample o = SyntheticSample.morainePlateau().h(91.7).stream(3, 8, 60, 0.3);
			o.floorHalfWidth = 900;
			assertEquals(k.classify(o.build(), x, z), k.classify(notInFloor, x, z), "off the floor flag");
		}
	}

	// ------------------------------------------------------------------ world: reachability and riparian forests only on floors

	/** Clusters of 125 × 125 columns every 2 m·k at 64 points (about 10⁶ samples per scale). */
	static void scan(LandscapeScale sc, HabitatClassifier.Mode mode, Predicate<int[]> action, Observer o) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, sc, mode);
		double area = sc == LandscapeScale.REALISTIC ? 300_000 : 20_000;
		double step = 2 * sc.local();
		IntStream.range(0, 64).parallel().forEach(q -> {
			long h = Noise.mix(SEED + 977L * q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * area;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * area;
			for (int j = 0; j < 125; j++) {
				for (int i = 0; i < 125; i++) {
					double x = cx + i * step;
					double z = cz + j * step;
					ColumnSample s = m.sample(x, z);
					o.column(k, s, x, z, k.classify(s, x, z));
				}
			}
		});
	}

	interface Observer {
		void column(HabitatClassifier k, ColumnSample s, double x, double z, int code);
	}

	@Test
	void everyBiomeAndZoneIsReachable() {
		Set<HabitatBiome> biomes = java.util.concurrent.ConcurrentHashMap.newKeySet();
		Set<Zone> zones = java.util.concurrent.ConcurrentHashMap.newKeySet();
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			Set<HabitatBiome> biomesInScale = java.util.concurrent.ConcurrentHashMap.newKeySet();
			Set<Zone> zonesInScale = java.util.concurrent.ConcurrentHashMap.newKeySet();
			scan(sc, HabitatClassifier.Mode.NATURAL, null, (k, s, x, z, code) -> {
				biomesInScale.add(Habitat.biome(code));
				zonesInScale.add(Habitat.zone(code));
			});
			// Separately for each scale: biomes and zones missing from the 64 clusters of that scale.
			Set<HabitatBiome> missingBiomes = EnumSet.allOf(HabitatBiome.class);
			missingBiomes.removeAll(biomesInScale);
			Set<Zone> missingZones = EnumSet.allOf(Zone.class);
			missingZones.removeAll(zonesInScale);
			System.out.println(sc.id() + ": biomes in the world " + biomesInScale.size() + ", missing from the clusters: " + missingBiomes);
			System.out.println(sc.id() + ": zones missing from the clusters: " + missingZones);
			biomes.addAll(biomesInScale);
			zones.addAll(zonesInScale);
		}
		Set<HabitatBiome> synthBiomes = EnumSet.noneOf(HabitatBiome.class);
		Set<Zone> synthZones = EnumSet.noneOf(Zone.class);
		for (Case p : cases()) {
			if (findPoint(p) != null) {
				if (p.biome() != null) {
					synthBiomes.add(p.biome());
				}
				if (p.zone() != null) {
					synthZones.add(p.zone());
				}
			}
		}
		Set<HabitatBiome> onlySynthBiomes = EnumSet.allOf(HabitatBiome.class);
		onlySynthBiomes.removeAll(biomes);
		Set<Zone> onlySynthZones = EnumSet.allOf(Zone.class);
		onlySynthZones.removeAll(zones);
		System.out.println("Biomes only from synthetic samples (rare or PRESENT_DAY mode): " + onlySynthBiomes);
		System.out.println("Zones only from synthetic samples: " + onlySynthZones);
		Set<HabitatBiome> missingBiomes = EnumSet.copyOf(onlySynthBiomes);
		missingBiomes.removeAll(synthBiomes);
		Set<Zone> missingZones = EnumSet.copyOf(onlySynthZones);
		missingZones.removeAll(synthZones);
		assertTrue(missingBiomes.isEmpty(), "unreachable biomes: " + missingBiomes);
		assertTrue(missingZones.isEmpty(), "unreachable zones: " + missingZones);
		// The 64 clusters in the world (without synthetic samples) must contain most biomes; the rare ones are the high
		// belts, PRESENT_DAY mode and biomes of narrow belts (lagoon, gray dune, backswamps of large valley floors).
		assertTrue(biomes.size() >= 25, "biomes in the world: " + biomes.size() + " " + biomes);
	}

	/**
	 * Riparian forests only at flowing water, by geometric criteria computed directly from the sample fields (without the
	 * classifier predicates {@code onValleyFloor()} and {@code isSeep()}): a watercourse in range, ground on the model floor or at most
	 * {@link Calibration#SEEP_HL} above the water surface of the nearest channel and in the valley (ground at most
	 * {@link Calibration#FLOOR_H} above the water surface, terrain incised at least {@link Calibration#INCISION_FROM} below the terrain
	 * before the valley, or the 6-block belt at the bank). Spring areas: the HEADWATERS landform up to 40 m·k (+20% jitter) from the channel.
	 * Also reports the extent of seeps (riparian forest outside the model's floor flag).
	 */
	@Test
	void riparianOnlyOnFloorsSpringsAndSeeps() {
		AtomicLong riparian = new AtomicLong();
		AtomicLong outside = new AtomicLong();
		AtomicLong all = new AtomicLong();
		AtomicLong outsideFlag = new AtomicLong();
		AtomicLong highAboveWater = new AtomicLong();
		java.util.concurrent.atomic.DoubleAccumulator maxHl = new java.util.concurrent.atomic.DoubleAccumulator(Math::max, 0);
		java.util.concurrent.atomic.DoubleAccumulator maxBeyondFloor = new java.util.concurrent.atomic.DoubleAccumulator(Math::max, 0);
		List<String> examples = java.util.Collections.synchronizedList(new ArrayList<>());
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			double kk = sc.local();
			scan(sc, HabitatClassifier.Mode.NATURAL, null, (k, s, x, z, code) -> {
				all.incrementAndGet();
				HabitatBiome b = Habitat.biome(code);
				if (!HabitatBiome.FLOODPLAIN_FORESTS.contains(b)) {
					return;
				}
				riparian.incrementAndGet();
				ColumnSample.Waters w = s.waters();
				double hl = s.surface() - w.channelLevel();
				double halfWidth = Double.isNaN(w.floorHalfWidth()) ? 0 : w.floorHalfWidth();
				double beyondFloor = w.channelDist() - halfWidth;
				boolean isSpringArea = s.terrain().has(Landform.HEADWATERS)
						&& w.channelDist() <= Calibration.SPRING_AREA_K * kk * (1 + Calibration.WIDTH_JITTER) + 1e-9;
				boolean inValley = hl <= Calibration.FLOOR_H || s.terrain().rawSurface() - s.surface() >= Calibration.INCISION_FROM
						|| w.channelDist() <= Calibration.MIN_ASH_ALDER;
				boolean nearWater = w.streamOrder() > 0 && (w.inFloor() || hl <= Calibration.SEEP_HL && inValley);
				if (!w.inFloor()) {
					outsideFlag.incrementAndGet();
					if (hl > 3) {
						highAboveWater.incrementAndGet();
					}
					maxHl.accumulate(hl);
					maxBeyondFloor.accumulate(beyondFloor / kk);
				}
				if (!(nearWater || isSpringArea)) {
					outside.incrementAndGet();
					if (examples.size() < 5) {
						examples.add(String.format(Locale.ROOT, "%s (%.0f, %.0f) %s: hl %.1f, beyond floor %.0f, %s", sc.id(), x, z, b, hl,
								beyondFloor, s));
					}
				}
			});
		}
		System.out.printf(Locale.ROOT, "Riparian forests: %d of %d samples, outside the criteria %d; outside the model's floor flag %d (%.1f%% of riparian), "
				+ "of which more than 3 m above the water surface %d; at most %.1f m above the water surface, at most %.0f m·k beyond the floor edge%n", riparian.get(),
				all.get(), outside.get(), outsideFlag.get(), 100.0 * outsideFlag.get() / Math.max(1, riparian.get()),
				highAboveWater.get(), maxHl.get(), maxBeyondFloor.get());
		assertTrue(all.get() >= 1_900_000, "too few samples");
		assertTrue(riparian.get() > 1_000, "too few riparian forest columns: " + riparian.get());
		assertEquals(0, outside.get(), "riparian forest far from flowing water: " + examples);
	}

	@Test
	void classificationTakesAtMostHalfAMicrosecond() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
			int n = 100_000;
			ColumnSample[] s = new ColumnSample[n];
			double[] xs = new double[n];
			double[] zs = new double[n];
			// Five areas of 20k columns each (dense, as in a chunk): lowland, valley, Beskids, coast, center.
			double[][] centers = {{0, 0}, {-49_879, -3_478}, {154_834, 1_058_738}, {-357_357, -380_500}, {66_000, 21_000}};
			double scale = sc.local();
			for (int i = 0; i < n; i++) {
				double[] c = centers[i / 20_000];
				int j = i % 20_000;
				xs[i] = c[0] * (sc == LandscapeScale.REALISTIC ? 1 : 0.02) + (j % 141) * 2 * scale;
				zs[i] = c[1] * (sc == LandscapeScale.REALISTIC ? 1 : 0.02) + (j / 141) * 2 * scale;
				s[i] = m.sample(xs[i], zs[i]);
			}
			long best = Long.MAX_VALUE;
			long sum = 0;
			for (int r = 0; r < 12; r++) {
				long t0 = System.nanoTime();
				for (int i = 0; i < n; i++) {
					sum += k.classify(s[i], xs[i], zs[i]);
				}
				long t = System.nanoTime() - t0;
				if (r >= 4) {
					best = Math.min(best, t);
				}
			}
			double us = best / 1e3 / n;
			System.out.printf(Locale.ROOT, "Classification (%s): %.3f µs per column (sum %d)%n", sc.id(), us, sum);
			assertTrue(us <= 0.5, "classification " + us + " µs");
		}
	}

	@Test
	void summitWithoutLargeMassifIsForestedToTheTop() {
		SyntheticSample without = SyntheticSample.beskids(1_460);
		without.summit = 1_460;
		assertEquals(HabitatBiome.MONTANE_SPRUCE_FOREST, Habitat.biome(real(HabitatClassifier.Mode.NATURAL).classify(without.build(), 0, 0)));
		SyntheticSample windy = SyntheticSample.beskids(1_700);
		windy.summit = 1_460;
		assertEquals(HabitatBiome.MONTANE_SPRUCE_FOREST, Habitat.biome(real(HabitatClassifier.Mode.NATURAL).classify(windy.build(), 0, 0)));
	}
}
