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
 * Klasyfikator siedlisk (docs/03-m2-biomy.md §3.4, §12.1): determinizm, pakowanie kodu, przypadki
 * syntetyczne, osiągalność każdego biomu i każdej strefy, brak łęgu poza dnem doliny na 10⁶ próbkach
 * i koszt klasyfikacji ≤ 0,5 µs.
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
				assertEquals(serialCodes[i], parallelCodes[i], "kolumna " + i + " w skali " + sc.id());
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

	// ------------------------------------------------------------------ przypadki syntetyczne

	/**
	 * Przypadek: próbka i oczekiwany biom (null: dowolny) i strefa (null: dowolna). Zależne od szumu (płaty,
	 * warianty) przesuwamy po punktach, aż wynik się pojawi; pozostałe muszą wyjść w pierwszym punkcie.
	 */
	record Case(String description, ColumnSample sample, HabitatClassifier.Mode mode, HabitatBiome biome, Zone zone, boolean search) {
	}

	static List<Case> cases() {
		List<Case> l = new ArrayList<>();
		HabitatClassifier.Mode n = HabitatClassifier.Mode.NATURAL;
		HabitatClassifier.Mode d = HabitatClassifier.Mode.PRESENT_DAY;
		// Wody.
		l.add(new Case("morze", SyntheticSample.coast(-500, 0, Substrate.SAND).water(WaterKind.SEA, 0, -12).build(), n,
				HabitatBiome.SEA, Zone.NONE, false));
		SyntheticSample lagoon = SyntheticSample.coast(800, 0, Substrate.LAKE_MUD).water(WaterKind.SEA, 0, -3);
		l.add(new Case("zalew", lagoon.build(), n, HabitatBiome.LAGOON, Zone.SUBMERGED_PLANTS, false));
		l.add(new Case("szuwar zalewu", SyntheticSample.coast(800, 0, Substrate.LAKE_MUD).water(WaterKind.SEA, 0, -1).build(),
				n, HabitatBiome.REEDBED, Zone.REEDBED, false));
		l.add(new Case("rzeka", SyntheticSample.morainePlateau().h(95).stream(3, 150, -20, 0.3).water(WaterKind.RIVER, 98, 95).build(),
				n, HabitatBiome.RIVER, Zone.CHANNEL, false));
		l.add(new Case("potok", SyntheticSample.beskids(600).stream(1, 6, -1, 20).water(WaterKind.RIVER, 600, 599.5).build(), n,
				HabitatBiome.STREAM, Zone.CHANNEL, false));
		l.add(new Case("jezioro (głębia)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -80,
				110, 400, 5).water(WaterKind.LAKE, 110, 100).build(), n, HabitatBiome.LAKE, Zone.NONE, false));
		l.add(new Case("elodeidy", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -40, 110, 400,
				5).water(WaterKind.LAKE, 110, 106).build(), n, HabitatBiome.LAKE, Zone.SUBMERGED_PLANTS, false));
		l.add(new Case("nymfeidy starorzecza", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.OXBOW_LAKE, -30,
				110, 60, 5).water(WaterKind.OXBOW, 110, 108).build(), n, HabitatBiome.LAKE, Zone.FLOATING_LEAVED_PLANTS, true));
		l.add(new Case("szuwar jeziora (biom)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE,
				-20, 110, 400, 5).water(WaterKind.LAKE, 110, 109).build(), n, HabitatBiome.REEDBED, Zone.REEDBED, false));
		l.add(new Case("szuwar oczka (strefa)", SyntheticSample.morainePlateau().standingWater(ColumnSample.StandingWaterKind.KETTLE_POND, -3, 110,
				25, 5).water(WaterKind.KETTLE, 110, 109.5).build(), n, HabitatBiome.LAKE, Zone.REEDBED, false));
		for (long id = 1; id < 200; id++) {
			// Pierwszy zbiornik o skrócie dystroficznym na sandrze: pło przy brzegu.
			ColumnSample c = SyntheticSample.outwashPlain().standingWater(ColumnSample.StandingWaterKind.KETTLE_POND, -2, 140, 60, id)
					.water(WaterKind.KETTLE, 140, 139).build();
			if (Habitat.biome(real(n).classify(c, 0, 0)) == HabitatBiome.DYSTROPHIC_LAKE) {
				l.add(new Case("jezioro dystroficzne, pło", c, n, HabitatBiome.DYSTROPHIC_LAKE, Zone.FLOATING_MAT, false));
				break;
			}
		}
		for (long id = 1; id < 400; id++) {
			// Jezioro lobeliowe: pas olszy przy brzegu.
			ColumnSample water = SyntheticSample.outwashPlain().standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, -30, 140, 300, id)
					.water(WaterKind.LAKE, 140, 134).build();
			if (Habitat.association(real(n).classify(water, 0, 0)) == Association.LOBELIA_LAKE) {
				ColumnSample land = SyntheticSample.outwashPlain().h(141.5).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 2, 140, 300, id)
						.build();
				l.add(new Case("jezioro lobeliowe, olsza przy brzegu", land, n, null, Zone.SHORE_ALDERS, false));
				break;
			}
		}
		// Wybrzeże.
		l.add(new Case("plaża mokra", SyntheticSample.coast(10, 1, Substrate.BEACH_SAND).build(), n, HabitatBiome.BEACH, Zone.NONE,
				false));
		l.add(new Case("kidzina", SyntheticSample.coast(40, 1.5, Substrate.BEACH_SAND).build(), n, HabitatBiome.BEACH, Zone.STRANDLINE,
				false));
		l.add(new Case("wydma inicjalna", SyntheticSample.coast(70, 5, Substrate.BEACH_SAND).build(), n, HabitatBiome.WHITE_DUNE,
				Zone.EMBRYO_DUNE, false));
		l.add(new Case("wydma szara", SyntheticSample.coast(320, 6, Substrate.SAND).build(), n, HabitatBiome.GRAY_DUNE, Zone.NONE,
				false));
		l.add(new Case("bór bażynowy", SyntheticSample.coast(1_000, 10, Substrate.SAND).build(), n, HabitatBiome.COASTAL_PINE_FOREST,
				Zone.NONE, false));
		l.add(new Case("ściana klifu", SyntheticSample.coast(75, 20, Substrate.GLACIAL_TILL).landform(Landform.CLIFF).build(), n,
				null, Zone.CLIFF_FACE, false));
		l.add(new Case("korona klifu", SyntheticSample.coast(100, 20, Substrate.GLACIAL_TILL).build(), n, null,
				Zone.CLIFF_TOP, false));
		SyntheticSample hinterland = SyntheticSample.coast(400, 1.2, Substrate.SAND);
		hinterland.lowShore = 1;
		l.add(new Case("torfowisko niskie za mierzeją", hinterland.build(), n, HabitatBiome.FEN, Zone.NONE, false));
		// Siedliska strefowe.
		SyntheticSample dryPine = SyntheticSample.outwashPlain().landform(Landform.INLAND_DUNES);
		dryPine.sandiness = 0.95;
		dryPine.sBar = 133;
		l.add(new Case("bór suchy na wydmie", dryPine.build(), n, HabitatBiome.DRY_PINE_FOREST, Zone.NONE, true));
		l.add(new Case("prześwit wrzosowiska na wydmie", dryPine.build(), n, HabitatBiome.HEATH, Zone.NONE, true));
		SyntheticSample freshPine = SyntheticSample.outwashPlain();
		freshPine.sandiness = 0.95;
		l.add(new Case("bór świeży", freshPine.build(), n, HabitatBiome.FRESH_PINE_FOREST, Zone.NONE, false));
		SyntheticSample moistPine = SyntheticSample.outwashPlain();
		moistPine.sandiness = 0.95;
		moistPine.sBar = 141.0;
		l.add(new Case("bór wilgotny", moistPine.build(), n, HabitatBiome.MOIST_PINE_FOREST, Zone.NONE, false));
		SyntheticSample bogWoodland = SyntheticSample.outwashPlain();
		bogWoodland.sandiness = 0.95;
		bogWoodland.sBar = 142.3;
		l.add(new Case("bór bagienny", bogWoodland.build(), n, HabitatBiome.BOG_WOODLAND, Zone.NONE, false));
		SyntheticSample mixedPine = SyntheticSample.outwashPlain();
		mixedPine.sandiness = 0.2;
		l.add(new Case("bór mieszany", mixedPine.build(), n, HabitatBiome.MIXED_PINE_FOREST, Zone.NONE, false));
		SyntheticSample mixedForest = SyntheticSample.morainePlateau();
		mixedForest.sandiness = 0.75;
		mixedForest.o = 0.35;
		l.add(new Case("las mieszany", mixedForest.build(), n, HabitatBiome.MIXED_FOREST, Zone.NONE, false));
		SyntheticSample oakHornbeam = SyntheticSample.morainePlateau();
		oakHornbeam.sandiness = 0.1;
		oakHornbeam.o = 0.35;
		l.add(new Case("grąd", oakHornbeam.build(), n, HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE, false));
		SyntheticSample lowlandBeech = SyntheticSample.morainePlateau();
		lowlandBeech.sandiness = 0.1;
		lowlandBeech.o = 0.95;
		lowlandBeech.convexity = 1;
		l.add(new Case("buczyna niżowa", lowlandBeech.build(), n, HabitatBiome.LOWLAND_BEECH_FOREST, Zone.NONE, true));
		SyntheticSample alderCarr = SyntheticSample.morainePlateau();
		alderCarr.sandiness = 0.1;
		alderCarr.sBar = 124.2;
		l.add(new Case("ols strefowy", alderCarr.build(), n, HabitatBiome.ALDER_CARR, Zone.NONE, false));
		SyntheticSample firForest = SyntheticSample.morainePlateau().h(400);
		firForest.sandiness = 0.95;
		firForest.p = 0.7;
		l.add(new Case("jedlina wyżynna", firForest.build(), n, HabitatBiome.UPLAND_FIR_FOREST, Zone.NONE, false));
		// Cieki (§4).
		SyntheticSample willowScrub = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 20, 0.3).onValleyFloor(0, 900);
		l.add(new Case("wikliny przy dużej rzece", willowScrub.build(), n, HabitatBiome.WILLOW_SCRUB, Zone.WILLOW_SCRUB, false));
		SyntheticSample pointBar = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 30, 0.3).onValleyFloor(0, 900);
		pointBar.convexBank = true;
		l.add(new Case("łacha", pointBar.build(), n, HabitatBiome.WILLOW_SCRUB, Zone.POINT_BAR, true));
		SyntheticSample fringe = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 50, 0.3).onValleyFloor(0, 900);
		l.add(new Case("okrajek za wikliną", fringe.build(), n, HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE, true));
		SyntheticSample whiteWillow = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 150, 0.3).onValleyFloor(0.1, 900);
		l.add(new Case("łęg wierzbowy", whiteWillow.build(), n, HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE, true));
		SyntheticSample elmAsh = SyntheticSample.morainePlateau().h(91.7).stream(3, 150, 800, 0.3).onValleyFloor(0.5, 900);
		elmAsh.convexity = 0.5;
		l.add(new Case("łęg wiązowo-jesionowy", elmAsh.build(), n, HabitatBiome.ELM_ASH_FOREST, Zone.NONE, true));
		SyntheticSample backswamp = SyntheticSample.morainePlateau().h(91.7);
		backswamp.channelLevel = 90.4;
		backswamp.stream(3, 150, 800, 0.2).onValleyFloor(0.85, 900);
		backswamp.convexity = -1;
		l.add(new Case("zastoisko: torfowisko niskie", backswamp.build(), n, HabitatBiome.FEN, Zone.NONE, true));
		l.add(new Case("zastoisko: ols", backswamp.build(), n, HabitatBiome.ALDER_CARR, Zone.NONE, true));
		SyntheticSample ashAlder = SyntheticSample.morainePlateau().stream(1, 5, 8, 1).onValleyFloor(0.1, 70);
		l.add(new Case("łęg jesionowo-olszowy", ashAlder.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.NONE, true));
		SyntheticSample herbs = SyntheticSample.morainePlateau().stream(1, 5, 1, 1).onValleyFloor(0, 70);
		l.add(new Case("ziołorośla małej rzeki", herbs.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.TALL_HERBS, false));
		SyntheticSample willows = SyntheticSample.outwashPlain().stream(2, 20, 15, 1).onValleyFloor(0, 100);
		willows.sandiness = 0.95;
		l.add(new Case("wierzby małej rzeki na piasku", willows.build(), n, null, Zone.RIVERSIDE_WILLOWS, true));
		SyntheticSample headwaters = SyntheticSample.morainePlateau().stream(1, 3, 10, 1).onValleyFloor(0, 40).landform(Landform.HEADWATERS);
		l.add(new Case("źródlisko", headwaters.build(), n, HabitatBiome.ASH_ALDER_FOREST, Zone.SPRING_AREA, true));
		SyntheticSample treeRow = SyntheticSample.morainePlateau().h(126).stream(1, 5, 6, 1);
		treeRow.rawSurface = 126;
		l.add(new Case("szpaler olszy (tryb D)", treeRow.build(), d, null, Zone.TREE_ROW, true));
		SyntheticSample gravelBar = SyntheticSample.beskids(500).stream(2, 8, 4, 8).onValleyFloor(0, 40);
		gravelBar.convexBank = true;
		l.add(new Case("kamieniec", gravelBar.build(), n, null, Zone.GRAVEL_BAR, false));
		SyntheticSample mountainWillowScrub = SyntheticSample.beskids(500).stream(2, 8, 5, 8).onValleyFloor(0.1, 40);
		l.add(new Case("wiklina górska", mountainWillowScrub.build(), n, null, Zone.WILLOW_SCRUB, false));
		SyntheticSample grayAlderBand = SyntheticSample.beskids(600).stream(2, 8, 12, 8).onValleyFloor(0.2, 40);
		l.add(new Case("olszyna górska", grayAlderBand.build(), n, HabitatBiome.GRAY_ALDER_FOREST, Zone.NONE, true));
		SyntheticSample mountainHerbs = SyntheticSample.beskids(1_050).stream(1, 3, 0.5, 30).onValleyFloor(0, 20);
		l.add(new Case("ziołorośla nadpotokowe", mountainHerbs.build(), n, null, Zone.MONTANE_TALL_HERBS, false));
		// Wody stojące na lądzie.
		SyntheticSample willowCarr = SyntheticSample.morainePlateau().h(111.5).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 20, 110, 400, 5);
		willowCarr.slope = 1;
		l.add(new Case("łozowisko przy jeziorze", willowCarr.build(), n, HabitatBiome.ALDER_CARR, Zone.WILLOW_CARR, true));
		SyntheticSample shoreReedbed = SyntheticSample.morainePlateau().h(111.2).standingWater(ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, 3, 110, 400, 5);
		l.add(new Case("szuwar lądowy", shoreReedbed.build(), n, null, Zone.SHORE_REEDBED, false));
		SyntheticSample raisedBog = SyntheticSample.outwashPlain().h(139.5).standingWater(ColumnSample.StandingWaterKind.KETTLE_BOG, -30, 140, 120, 9);
		raisedBog.substrate = Substrate.PEAT;
		raisedBog.ombrotrophicPeat = true;
		l.add(new Case("torfowisko wysokie", raisedBog.build(), n, HabitatBiome.RAISED_BOG, Zone.NONE, false));
		// Góry (§5.1).
		SyntheticSample beech = SyntheticSample.beskids(800);
		beech.sandiness = 0.1;
		beech.slope = 20;
		beech.aspect = 180;
		l.add(new Case("buczyna karpacka", beech.build(), n, HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, false));
		l.add(new Case("świerczyna górska", SyntheticSample.beskids(1_260).build(), n, HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, false));
		SyntheticSample dwarfPine = SyntheticSample.beskids(1_500);
		dwarfPine.summit = 1_700;
		l.add(new Case("kosodrzewina", dwarfPine.build(), n, HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE, false));
		SyntheticSample alpine = SyntheticSample.beskids(1_720);
		alpine.summit = 1_720;
		l.add(new Case("hala", alpine.build(), n, HabitatBiome.ALPINE_GRASSLAND, Zone.NONE, false));
		SyntheticSample timberline = SyntheticSample.beskids(1_355);
		timberline.summit = 1_700;
		l.add(new Case("granica lasu", timberline.build(), n, HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.TIMBERLINE, true));
		// Tryb D: biomy nieleśne z maski lasu.
		SyntheticSample arable = SyntheticSample.morainePlateau();
		arable.sandiness = 0.1;
		arable.o = 0.35;
		l.add(new Case("pole (tryb D)", arable.build(), d, HabitatBiome.ARABLE_LAND, Zone.NONE, true));
		l.add(new Case("łąka świeża (tryb D)", arable.build(), d, HabitatBiome.HAY_MEADOW, Zone.NONE, true));
		SyntheticSample meadow = SyntheticSample.morainePlateau().stream(1, 5, 8, 1).onValleyFloor(0.1, 70);
		l.add(new Case("łąka wilgotna (tryb D)", meadow.build(), d, HabitatBiome.WET_MEADOW, null, true));
		return l;
	}

	/** Pierwszy punkt (z 4000 na spirali), w którym przypadek daje oczekiwany wynik; null, gdy żaden. */
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
				errors.add(p.description() + ": oczekiwano " + p.biome() + "/" + p.zone() + ", jest " + Habitat.of(code));
			}
		}
		assertTrue(errors.isEmpty(), String.join("\n", errors));
		assertTrue(k.mode() == HabitatClassifier.Mode.NATURAL);
	}

	// ------------------------------------------------------------------ świat: osiągalność i łęgi tylko w dnie

	/** Skupiska 125 × 125 kolumn co 2 m·k w 64 punktach (ok. 10⁶ próbek na skalę). */
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
			// Osobno dla każdej skali: biomy i strefy, których nie ma w 64 skupiskach tej skali.
			Set<HabitatBiome> missingBiomes = EnumSet.allOf(HabitatBiome.class);
			missingBiomes.removeAll(biomesInScale);
			Set<Zone> missingZones = EnumSet.allOf(Zone.class);
			missingZones.removeAll(zonesInScale);
			System.out.println(sc.id() + ": biomy w świecie " + biomesInScale.size() + ", brak w skupiskach: " + missingBiomes);
			System.out.println(sc.id() + ": strefy brak w skupiskach: " + missingZones);
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
		System.out.println("Biomy tylko z próbek syntetycznych (rzadkie albo tryb D): " + onlySynthBiomes);
		System.out.println("Strefy tylko z próbek syntetycznych: " + onlySynthZones);
		Set<HabitatBiome> missingBiomes = EnumSet.copyOf(onlySynthBiomes);
		missingBiomes.removeAll(synthBiomes);
		Set<Zone> missingZones = EnumSet.copyOf(onlySynthZones);
		missingZones.removeAll(synthZones);
		assertTrue(missingBiomes.isEmpty(), "nieosiągalne biomy: " + missingBiomes);
		assertTrue(missingZones.isEmpty(), "nieosiągalne strefy: " + missingZones);
		// W 64 skupiskach w świecie (bez próbek syntetycznych) musi być większość biomów; rzadkie to piętra
		// wysokie, tryb D i biomy wąskich pasów (zalew, wydma szara, zastoiska wielkich den).
		assertTrue(biomes.size() >= 25, "biomy w świecie: " + biomes.size() + " " + biomes);
	}

	/**
	 * Łęgi tylko przy wodzie płynącej, z kryteriów geometrycznych liczonych wprost z pól próbki (bez predykatów
	 * klasyfikatora {@code dno()} i {@code wysiek()}): ciek w zasięgu, grunt w dnie modelu albo najwyżej
	 * {@link Calibration#SEEP_HL} nad lustrem najbliższego koryta i w dolinie (grunt najwyżej
	 * {@link Calibration#FLOOR_H} nad lustrem, teren wcięty co najmniej {@link Calibration#INCISION_FROM} poniżej terenu
	 * przed doliną albo pas 6 bloków przy brzegu). Źródliska: forma ZRODLO do 40 m·k (+20% drgania) od koryta.
	 * Raportuje też skalę wysięków (łęg poza flagą dna modelu).
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
						examples.add(String.format(Locale.ROOT, "%s (%.0f, %.0f) %s: hl %.1f, za dnem %.0f, %s", sc.id(), x, z, b, hl,
								beyondFloor, s));
					}
				}
			});
		}
		System.out.printf(Locale.ROOT, "Łęgi: %d z %d próbek, poza kryteriami %d; poza flagą dna modelu %d (%.1f%% łęgów), "
				+ "z nich wyżej niż 3 m nad lustrem %d; najwyżej %.1f m nad lustrem, najdalej %.0f m·k za skrajem dna%n", riparian.get(),
				all.get(), outside.get(), outsideFlag.get(), 100.0 * outsideFlag.get() / Math.max(1, riparian.get()),
				highAboveWater.get(), maxHl.get(), maxBeyondFloor.get());
		assertTrue(all.get() >= 1_900_000, "za mało próbek");
		assertTrue(riparian.get() > 1_000, "za mało łęgów: " + riparian.get());
		assertEquals(0, outside.get(), "łęg z dala od wody płynącej: " + examples);
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
			// Pięć obszarów po 20 tys. kolumn (gęsto, jak w chunku): nizina, dolina, Beskidy, wybrzeże, środek.
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
			System.out.printf(Locale.ROOT, "Klasyfikacja (%s): %.3f µs na kolumnę (suma %d)%n", sc.id(), us, sum);
			assertTrue(us <= 0.5, "klasyfikacja " + us + " µs");
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
