package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Step S9 (docs/03-m2-biomy.md §12.1, {@code PolishForestsCommandsTest}): every biome and every zone of
 * {@code /polishforests find biome} and {@code find zone} is found from (0, 0) of the test seed in the gameplay scale,
 * in the natural vegetation mode, or in the present-day mode for the meadows, fields and the alder tree row, which only
 * occur there; the place found has the biome or the zone. The montane tall herbs do not occur in the gameplay scale
 * ({@link HabitatTargets#occurs(Zone, LandscapeScale)}) and are only searched in the realistic scale.
 * {@link HabitatTargetsRealisticTest} does the same in the realistic scale. Also: what does not occur in a mode or a
 * scale is reported so without a search.
 */
@Tag("slow")
class HabitatTargetsTest {
	@Test
	void everyBiomeAndZoneIsFoundInGameplayScale() {
		assertEverythingFound(LandscapeScale.GAMEPLAY);
	}

	@Test
	void meadowsFieldsAndTreeRowsOnlyInPresentDayMode() {
		for (HabitatBiome b : List.of(HabitatBiome.WET_MEADOW, HabitatBiome.HAY_MEADOW, HabitatBiome.ARABLE_LAND)) {
			assertTrue(!HabitatTargets.occurs(b, HabitatClassifier.Mode.NATURAL) && HabitatTargets.occurs(b, HabitatClassifier.Mode.PRESENT_DAY));
		}
		assertTrue(!HabitatTargets.occurs(Zone.TREE_ROW, HabitatClassifier.Mode.NATURAL));
		assertTrue(!HabitatTargets.occurs(Zone.NONE, HabitatClassifier.Mode.PRESENT_DAY));
		assertTrue(!HabitatTargets.occurs(Zone.MONTANE_TALL_HERBS, LandscapeScale.GAMEPLAY)
				&& HabitatTargets.occurs(Zone.MONTANE_TALL_HERBS, LandscapeScale.REALISTIC));
		long natural = java.util.Arrays.stream(HabitatBiome.values()).filter(b -> HabitatTargets.occurs(b, HabitatClassifier.Mode.NATURAL)).count();
		assertTrue(natural == HabitatBiome.values().length - 3, "biomes of the natural mode: " + natural);
	}

	/** Finds every biome and zone in the scale, each in a mode where it occurs; fails with the list of the missing ones. */
	static void assertEverythingFound(LandscapeScale scale) {
		HabitatTargets.World natural = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.NATURAL);
		HabitatTargets.World presentDay = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.PRESENT_DAY);
		List<String> missing = new ArrayList<>();
		long total = System.nanoTime();
		for (HabitatBiome b : HabitatBiome.values()) {
			HabitatTargets.World w = HabitatTargets.occurs(b, HabitatClassifier.Mode.NATURAL) ? natural : presentDay;
			long t0 = System.nanoTime();
			double[] p = HabitatTargets.locate(w, b, 0, 0, () -> false);
			report(scale, "biome " + b.id(), w, p, t0);
			if (p == null || Habitat.biome(w.code(p[0], p[1])) != b) {
				missing.add("biome " + b.id());
			}
		}
		for (Zone z : Zone.values()) {
			if (z == Zone.NONE || !HabitatTargets.occurs(z, scale)) {
				System.out.println(scale.id() + " zone " + z.id() + ": does not occur in this scale");
				continue;
			}
			HabitatTargets.World w = HabitatTargets.occurs(z, HabitatClassifier.Mode.NATURAL) ? natural : presentDay;
			long t0 = System.nanoTime();
			double[] p = HabitatTargets.locate(w, z, 0, 0, () -> false);
			report(scale, "zone " + z.id(), w, p, t0);
			if (p == null || Habitat.zone(w.code(p[0], p[1])) != z) {
				missing.add("zone " + z.id());
			}
		}
		System.out.printf(Locale.ROOT, "%s: all biomes and zones in %.1f s%n", scale.id(), (System.nanoTime() - total) / 1e9);
		assertTrue(missing.isEmpty(), scale.id() + ": not found: " + missing);
	}

	private static void report(LandscapeScale scale, String what, HabitatTargets.World w, double[] p, long t0) {
		System.out.printf(Locale.ROOT, "%s %s (%s): %s (%.0f ms)%n", scale.id(), what,
				w.classifier().mode().name().toLowerCase(Locale.ROOT),
				p == null ? "none" : Math.round(Math.hypot(p[0], p[1])) + " m", (System.nanoTime() - t0) / 1e6);
	}
}
