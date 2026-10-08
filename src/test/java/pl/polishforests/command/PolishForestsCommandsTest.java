package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Calibration;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Every target of the {@code /polishforests find} command can be found, and the place found meets its condition.
 * Step K8b2: {@code find cliff} finds a cliff of a high shore (low shore share below {@code Calibration.LOW_SHORE}, at
 * least 12 m, steep seaward wall), not the high foredune of a low shore ({@link #cliffIsAHighShore}).
 */
@Tag("slow")
class PolishForestsCommandsTest {
	@Test
	void everyTargetIsFoundInGameplayScale() {
		assertFound(new LandscapeModel(20260927L, LandscapeScale.GAMEPLAY, 1.0), List.of(PolishForestsCommands.Target.values()));
	}

	@Test
	void coastAndWatersAreFoundInRealisticScale() {
		assertFound(new LandscapeModel(20260927L, 1.0), List.of(PolishForestsCommands.Target.values()).stream()
				.filter(t -> t.coastal || t == PolishForestsCommands.Target.SEA || t == PolishForestsCommands.Target.OXBOW_LAKE
						|| t == PolishForestsCommands.Target.HEADWATERS)
				.toList());
	}

	@Test
	void cliffIsAHighShore() {
		for (LandscapeModel m : List.of(new LandscapeModel(20260927L, 1.0), new LandscapeModel(20260927L, LandscapeScale.GAMEPLAY, 1.0))) {
			double[] p = PolishForestsCommands.locate(m, PolishForestsCommands.Target.CLIFF, 0, 0);
			assertTrue(p != null, m.scale().id() + ": no cliff");
			var t = m.sample(p[0], p[1]).terrain();
			System.out.printf(java.util.Locale.ROOT, "%s: cliff at (%.0f, %.0f), height %.1f m, low shore %.2f%n", m.scale().id(), p[0],
					p[1], t.cliffHeight(), t.lowShore());
			assertTrue(t.lowShore() < Calibration.LOW_SHORE && t.cliffHeight() >= PolishForestsCommands.CLIFF_MIN_HEIGHT
					&& PolishForestsCommands.hasSeawardWall(m, p[0], p[1]), m.scale().id() + ": not a cliff of a high shore");
		}
	}

	private static void assertFound(LandscapeModel m, List<PolishForestsCommands.Target> targets) {
		List<String> missing = new ArrayList<>();
		targets.parallelStream().forEach(t -> {
			long start = System.nanoTime();
			double[] p = PolishForestsCommands.locate(m, t, 0, 0);
			double ms = (System.nanoTime() - start) / 1e6;
			if (p == null || !PolishForestsCommands.matches(m, t, p)) {
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
