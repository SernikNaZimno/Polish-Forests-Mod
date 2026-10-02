package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Landform;

/** Każdy cel komendy {@code /polskielasy znajdz} daje się znaleźć, a znalezione miejsce spełnia warunek. */
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

	private static void assertFound(LandscapeModel m, List<PolishForestsCommands.Target> targets) {
		List<String> missing = new ArrayList<>();
		targets.parallelStream().forEach(t -> {
			long start = System.nanoTime();
			double[] p = PolishForestsCommands.locate(m, t, 0, 0);
			double ms = (System.nanoTime() - start) / 1e6;
			if (p == null || !t.test.test(t.forms ? m.describe(p[0], p[1])
					: new LandscapeModel.Description(m.sample(p[0], p[1]), EnumSet.noneOf(Landform.class)))) {
				synchronized (missing) {
					missing.add(t.id());
				}
			}
			System.out.printf("%s: %s (%.0f ms)%n", t.id(),
					p == null ? "none" : Math.round(Math.hypot(p[0], p[1])) + " m", ms);
		});
		assertTrue(missing.isEmpty(), "nie znaleziono: " + missing);
	}
}
