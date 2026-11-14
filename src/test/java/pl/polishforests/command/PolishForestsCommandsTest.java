package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Calibration;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Every target of the {@code /polishforests find} command can be found, and the place found meets its condition.
 * Step K8b2: {@code find cliff} finds a cliff of a high shore (low shore share below {@code Calibration.LOW_SHORE}, at
 * least 12 m, steep seaward wall), not the high foredune of a low shore ({@link #cliffIsAHighShore}). Step S9: the
 * peatland target comes from the habitat (raised bog or fen); every biome and every zone of {@code find biome} and
 * {@code find zone} is found in both scales ({@link HabitatTargetsTest}).
 */
@Tag("slow")
class PolishForestsCommandsTest {
	static final long SEED = 20260927L;

	/** Step TT1: the targets of the Tatra complexes (found only in a world with the Tatras). */
	static final Set<PolishForestsCommands.Target> TATRA_TARGETS = EnumSet.of(PolishForestsCommands.Target.TATRA,
			PolishForestsCommands.Target.HIGH_TATRA, PolishForestsCommands.Target.WESTERN_TATRA, PolishForestsCommands.Target.PODHALE,
			PolishForestsCommands.Target.CIRQUE, PolishForestsCommands.Target.U_VALLEY, PolishForestsCommands.Target.TARN,
			PolishForestsCommands.Target.PINNACLE, PolishForestsCommands.Target.GULLY, PolishForestsCommands.Target.SCREE,
			PolishForestsCommands.Target.MORAINE, PolishForestsCommands.Target.SNOW_PATCH,
			// Step TT6: the details of the Tatras (the shepherd's hut only in the present-day mode, see below).
			PolishForestsCommands.Target.KARST_SPRING, PolishForestsCommands.Target.CAVE_MOUTH, PolishForestsCommands.Target.WATERFALL);

	static HabitatTargets.World world(LandscapeScale scale, HabitatClassifier.Mode mode) {
		return world(SEED, scale, mode, false);
	}

	static HabitatTargets.World world(long seed, LandscapeScale scale, HabitatClassifier.Mode mode, boolean tatra) {
		LandscapeModel m = new LandscapeModel(seed, scale, 1.0, tatra);
		return new HabitatTargets.World(m, new HabitatClassifier(seed, m, mode));
	}

	@Test
	void everyTargetIsFoundInGameplayScale() {
		// The old targets in a world without the Tatras (as before step TT1), the Tatra targets in one with them.
		assertFound(world(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL), List.of(PolishForestsCommands.Target.values())
				.stream().filter(t -> !TATRA_TARGETS.contains(t) && t != PolishForestsCommands.Target.SHEPHERD_HUT).toList());
		assertFound(world(SEED, LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL, true), List.copyOf(TATRA_TARGETS));
	}

	/**
	 * Step TT1: the Tatra targets are found for a second seed too (the one with the nearest massif among the probed
	 * seeds), and none in a world without the Tatras.
	 */
	@Test
	void tatraTargetsAreFoundOnlyWithTheTatras() {
		assertFound(world(4L, LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL, true), List.copyOf(TATRA_TARGETS));
		HabitatTargets.World without = world(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL);
		for (PolishForestsCommands.Target t : TATRA_TARGETS) {
			assertTrue(PolishForestsCommands.locate(without, t, 0, 0) == null, t.id() + " found in a world without the Tatras");
		}
	}

	/**
	 * Step TT5: in the realistic scale (the Tatras on in new worlds) the Tatra targets are found for the seeds of the
	 * criterion (20260927 and 4), within the wide radius of 2000 km ({@code tatraRadius}); the distance of
	 * {@code find tatra} from the origin and the time of each search go to the output.
	 */
	@Test
	void tatraTargetsAreFoundInRealisticScale() {
		for (long seed : new long[] {SEED, 4L}) {
			HabitatTargets.World w = world(seed, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL, true);
			assertFound(w, List.copyOf(TATRA_TARGETS));
			double[] p = PolishForestsCommands.locate(w, PolishForestsCommands.Target.TATRA, 0, 0);
			System.out.printf(java.util.Locale.ROOT, "realistic seed %d: find tatra at (%.0f, %.0f), %.0f km from the origin%n", seed,
					p[0], p[1], Math.hypot(p[0], p[1]) / 1000);
			assertTrue(Math.hypot(p[0], p[1]) < PolishForestsCommands.tatraRadius(w.model()));
		}
	}

	/**
	 * Step TT6: {@code find shepherd_hut} finds a hut on a hala or a glade in the present-day mode of a world with the Tatras,
	 * in both scales, and nothing in the natural mode, where the huts are not built, nor in a world without the Tatras.
	 */
	@Test
	void shepherdHutsAreFoundOnlyInThePresentDayMode() {
		var hut = PolishForestsCommands.Target.SHEPHERD_HUT;
		for (LandscapeScale scale : List.of(LandscapeScale.GAMEPLAY, LandscapeScale.REALISTIC)) {
			long seed = scale == LandscapeScale.GAMEPLAY ? SEED : 4L;
			assertFound(world(seed, scale, HabitatClassifier.Mode.PRESENT_DAY, true), List.of(hut));
			assertTrue(PolishForestsCommands.locate(world(seed, scale, HabitatClassifier.Mode.NATURAL, true), hut, 0, 0) == null,
					scale.id() + ": a hut in the natural mode");
		}
		assertTrue(PolishForestsCommands.locate(world(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.PRESENT_DAY), hut, 0, 0) == null,
				"a hut without the Tatras");
	}

	@Test
	void coastAndWatersAreFoundInRealisticScale() {
		assertFound(world(LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL), List.of(PolishForestsCommands.Target.values()).stream()
				.filter(t -> t.coastal || t == PolishForestsCommands.Target.SEA || t == PolishForestsCommands.Target.OXBOW_LAKE
						|| t == PolishForestsCommands.Target.HEADWATERS || t == PolishForestsCommands.Target.PEATLAND)
				.toList());
	}

	@Test
	void peatlandIsARaisedBogOrFen() {
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			HabitatTargets.World w = world(scale, HabitatClassifier.Mode.NATURAL);
			double[] p = PolishForestsCommands.locate(w, PolishForestsCommands.Target.PEATLAND, 0, 0);
			assertTrue(p != null, scale.id() + ": no peatland");
			HabitatBiome b = Habitat.biome(w.code(p[0], p[1]));
			assertTrue(b == HabitatBiome.RAISED_BOG || b == HabitatBiome.FEN, scale.id() + ": peatland is " + b);
		}
	}

	@Test
	void cliffIsAHighShore() {
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			HabitatTargets.World w = world(scale, HabitatClassifier.Mode.NATURAL);
			LandscapeModel m = w.model();
			double[] p = PolishForestsCommands.locate(w, PolishForestsCommands.Target.CLIFF, 0, 0);
			assertTrue(p != null, m.scale().id() + ": no cliff");
			var t = m.sample(p[0], p[1]).terrain();
			System.out.printf(java.util.Locale.ROOT, "%s: cliff at (%.0f, %.0f), height %.1f m, low shore %.2f%n", m.scale().id(), p[0],
					p[1], t.cliffHeight(), t.lowShore());
			assertTrue(t.lowShore() < Calibration.LOW_SHORE && t.cliffHeight() >= PolishForestsCommands.CLIFF_MIN_HEIGHT
					&& PolishForestsCommands.hasSeawardWall(m, p[0], p[1]), m.scale().id() + ": not a cliff of a high shore");
		}
	}

	private static void assertFound(HabitatTargets.World w, List<PolishForestsCommands.Target> targets) {
		List<String> missing = new ArrayList<>();
		targets.parallelStream().forEach(t -> {
			long start = System.nanoTime();
			double[] p = PolishForestsCommands.locate(w, t, 0, 0);
			double ms = (System.nanoTime() - start) / 1e6;
			if (p == null || !PolishForestsCommands.matches(w, t, p)) {
				synchronized (missing) {
					missing.add(t.id());
				}
			}
			System.out.printf("%s: %s (%.0f ms)%n", t.id(),
					p == null ? "none" : Math.round(Math.hypot(p[0], p[1])) + " m", ms);
		});
		assertTrue(missing.isEmpty(), "not found: " + missing);
	}
}
