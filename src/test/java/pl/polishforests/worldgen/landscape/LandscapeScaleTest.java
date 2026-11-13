package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandDimension;

/**
 * Step TT5: the realistic blocks per meter of the model ({@link LandscapeScale#realBlocksPerMeter}, the model does not
 * depend on Minecraft and repeats the constants of the curve) equal the derivative of the curve of the dimension
 * ({@link PolandDimension#blocksForMeters}).
 */
class LandscapeScaleTest {
	@Test
	void realBlocksPerMeterIsTheDerivativeOfTheDimensionCurve() {
		assertEquals(PolandDimension.KNEE_METERS, LandscapeScale.REAL_KNEE);
		assertEquals(PolandDimension.COMPRESSION, LandscapeScale.REAL_COMPRESSION);
		for (double m = -50; m <= 2_600; m += 7.3) {
			double h = 0.01;
			double numeric = (PolandDimension.blocksForMeters(m + h) - PolandDimension.blocksForMeters(m - h)) / (2 * h);
			// Below 880 m the curve is the identity; the derivative of its softplus part there is at most 0.8% off 1.
			double tolerance = m > 870 && m < 890 ? 0.01 : 1e-5;
			assertEquals(numeric, LandscapeScale.realBlocksPerMeter(m), tolerance, "at " + m + " m");
		}
		assertEquals(1 / PolandDimension.COMPRESSION, LandscapeScale.realBlocksPerMeter(2_500), 1e-3);
	}
}
