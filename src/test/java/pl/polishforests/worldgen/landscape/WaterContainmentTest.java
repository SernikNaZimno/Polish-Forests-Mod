package pl.polishforests.worldgen.landscape;

import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SEED;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.assertSitesContained;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Water contained within its banks at the coast, streams, oxbows and lakes in both scales, split out of
 * {@link RiverNetworkTest} (the realistic case takes about 3 minutes) so that the parallel test forks get smaller units
 * of work. The check itself is {@link RiverNetworkTest#assertSitesContained}.
 */
@Tag("slow")
class WaterContainmentTest {
	@Test
	void waterIsContainedAtCoastStreamsOxbowsAndLakesRealistic() {
		assertSitesContained(new LandscapeModel(SEED, 1.0), 2_000);
	}

	@Test
	void waterIsContainedAtCoastStreamsOxbowsAndLakesGameplay() {
		assertSitesContained(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0), 100);
	}
}
