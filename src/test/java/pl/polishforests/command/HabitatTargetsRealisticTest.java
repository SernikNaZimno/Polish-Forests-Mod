package pl.polishforests.command;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Step S9: every biome and every zone of {@code find biome} and {@code find zone} is found in the realistic scale (as
 * {@link HabitatTargetsTest} in the gameplay scale; a class of its own, because the test forks get whole classes).
 */
@Tag("slow")
class HabitatTargetsRealisticTest {
	@Test
	void everyBiomeAndZoneIsFoundInRealisticScale() {
		HabitatTargetsTest.assertEverythingFound(LandscapeScale.REALISTIC);
	}
}
