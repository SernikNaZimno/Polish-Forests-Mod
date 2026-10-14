package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

	static HabitatTargets.World world(LandscapeScale scale, HabitatClassifier.Mode mode) {
		return new HabitatTargets.World(new LandscapeModel(SEED, scale, 1.0), new HabitatClassifier(SEED, scale, mode));
	}

	@Test
	void everyTargetIsFoundInGameplayScale() {
		assertFound(world(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL), List.of(PolishForestsCommands.Target.values()));
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
