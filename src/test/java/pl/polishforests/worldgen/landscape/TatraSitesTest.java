package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.feature.tatra.TatraSites;

/**
 * Step TT6: the anchors of the Tatra details ({@link TatraSites}) on the Tatra massifs of the test seeds, in both scales.
 */
class TatraSitesTest {
	/** The anchors of a kind over the cells of the reach of the massif. */
	private static List<TatraSites.Site> sites(LandscapeModel m, LandscapeModel.Tatra t, TatraSites.Kind kind, boolean presentDay) {
		int cell = TatraSites.cellChunks(m, kind) * 16;
		int cx = Math.floorDiv((int) Math.round(t.x()), cell);
		int cz = Math.floorDiv((int) Math.round(t.z()), cell);
		int n = (int) Math.ceil(t.reach() / cell);
		List<TatraSites.Site> out = new ArrayList<>();
		for (int x = cx - n; x <= cx + n; x++) {
			for (int z = cz - n; z <= cz + n; z++) {
				TatraSites.Site s = TatraSites.site(m, kind, x, z, presentDay, null);
				if (s != null && Math.hypot(s.x() - t.x(), s.z() - t.z()) <= t.reach()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void everyKindHasAnchorsOnTheMassifs(LandscapeScale scale) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(scale, seed);
			LandscapeModel.Tatra t = TatraSurvey.nearest(scale, seed, 1).getFirst();
			Map<TatraSites.Kind, Integer> counts = new EnumMap<>(TatraSites.Kind.class);
			for (TatraSites.Kind kind : TatraSites.Kind.values()) {
				counts.put(kind, sites(m, t, kind, true).size());
			}
			System.out.println("CENSUS " + TatraSurvey.label(scale, seed, t) + " " + counts);
		}
	}

	@Test
	void anchorsAreDeterministicAndSuitTheirKind() {
		LandscapeScale scale = LandscapeScale.GAMEPLAY;
		long seed = TatraSurvey.SEEDS[0];
		LandscapeModel m = TatraSurvey.with(scale, seed);
		LandscapeModel.Tatra t = TatraSurvey.nearest(scale, seed, 1).getFirst();
		for (TatraSites.Kind kind : TatraSites.Kind.values()) {
			List<TatraSites.Site> first = sites(m, t, kind, true);
			assertEquals(first, sites(m, t, kind, true), kind + " is a pure function of the seed");
			for (TatraSites.Site s : first) {
				assertTrue(m.tatraRock(s.x(), s.z()), kind + " on the rock of the massif");
				assertNotNull(s.rock());
				assertFalse(m.sample(s.x(), s.z()).hasWater(), kind + " not in water");
				boolean carbonate = s.rock() == Substrate.LIMESTONE || s.rock() == Substrate.DOLOMITE;
				if (kind == TatraSites.Kind.SPRING || kind == TatraSites.Kind.CAVE_MOUTH) {
					assertTrue(carbonate, kind + " on the limestone or the dolomite");
				}
				// The anchor lies in the chunk it is listed for, at least MARGIN blocks from its edges.
				List<TatraSites.Site> inChunk = TatraSites.inChunk(m, s.x() >> 4, s.z() >> 4, true, null);
				assertTrue(inChunk.contains(s), kind + " listed in its chunk");
				assertTrue((s.x() & 15) >= TatraSites.MARGIN && (s.x() & 15) <= 15 - TatraSites.MARGIN + 1, "x " + s.x());
				assertTrue((s.z() & 15) >= TatraSites.MARGIN && (s.z() & 15) <= 15 - TatraSites.MARGIN + 1, "z " + s.z());
			}
		}
	}

	@Test
	void huntsStandOnlyInThePresentDayMode() {
		long seed = TatraSurvey.SEEDS[0];
		LandscapeModel m = TatraSurvey.with(LandscapeScale.GAMEPLAY, seed);
		LandscapeModel.Tatra t = TatraSurvey.nearest(LandscapeScale.GAMEPLAY, seed, 1).getFirst();
		assertTrue(sites(m, t, TatraSites.Kind.SHEPHERD_HUT, false).isEmpty(), "none in the natural mode");
		assertFalse(sites(m, t, TatraSites.Kind.SHEPHERD_HUT, true).isEmpty());
		assertFalse(TatraSites.exists(TatraSites.Kind.SHEPHERD_HUT, false));
		assertTrue(TatraSites.exists(TatraSites.Kind.WATERFALL, false));
		assertNull(TatraSites.find(m, TatraSites.Kind.SHEPHERD_HUT, 0, 0, 300_000, false, null, () -> false));
	}

	@Test
	void withoutTheTatrasThereAreNoAnchors() {
		long seed = TatraSurvey.SEEDS[0];
		LandscapeModel plain = TatraSurvey.without(LandscapeScale.GAMEPLAY, seed);
		for (TatraSites.Kind kind : TatraSites.Kind.values()) {
			for (int x = -20; x <= 20; x++) {
				for (int z = -20; z <= 20; z++) {
					assertNull(TatraSites.site(plain, kind, x, z, true, null));
				}
			}
		}
		assertTrue(TatraSites.inChunk(plain, 3, 4, true, null).isEmpty());
		assertNull(TatraSites.find(plain, TatraSites.Kind.WATERFALL, 0, 0, 300_000, true, null, () -> false));
	}
}
