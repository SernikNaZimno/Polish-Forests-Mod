package pl.polskielasy.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.Landform;

/** Każdy cel komendy {@code /polskielasy znajdz} daje się znaleźć, a znalezione miejsce spełnia warunek. */
class PolskaCommandsTest {
	@Test
	void everyTargetIsFoundInGameplayScale() {
		assertFound(new LandscapeModel(20260927L, LandscapeScale.GAMEPLAY, 1.0), List.of(PolskaCommands.Target.values()));
	}

	@Test
	void coastAndWatersAreFoundInRealisticScale() {
		assertFound(new LandscapeModel(20260927L, 1.0), List.of(PolskaCommands.Target.values()).stream()
				.filter(t -> t.coastal || t == PolskaCommands.Target.MORZE || t == PolskaCommands.Target.STARORZECZE
						|| t == PolskaCommands.Target.ZRODLO)
				.toList());
	}

	private static void assertFound(LandscapeModel m, List<PolskaCommands.Target> targets) {
		List<String> missing = new ArrayList<>();
		targets.parallelStream().forEach(t -> {
			long start = System.nanoTime();
			double[] p = PolskaCommands.locate(m, t, 0, 0);
			double ms = (System.nanoTime() - start) / 1e6;
			if (p == null || !t.test.test(t.forms ? m.describe(p[0], p[1])
					: new LandscapeModel.Description(m.sample(p[0], p[1]), EnumSet.noneOf(Landform.class)))) {
				synchronized (missing) {
					missing.add(t.id());
				}
			}
			System.out.printf("%s: %s (%.0f ms)%n", t.id(),
					p == null ? "brak" : Math.round(Math.hypot(p[0], p[1])) + " m", ms);
		});
		assertTrue(missing.isEmpty(), "nie znaleziono: " + missing);
	}
}
