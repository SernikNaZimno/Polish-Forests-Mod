package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class NoiseTest {
	@Test
	void analyticDerivativesMatchFiniteDifferences() {
		Noise n = new Noise(42);
		double[] d = new double[2];
		double h = 1e-6;
		for (int i = 0; i < 2_000; i++) {
			double x = i * 0.7317 - 300;
			double z = i * -0.4131 + 125;
			double v = n.sampleD(x, z, d);
			assertEquals(n.sample(x, z), v, 1e-12);
			double fx = (n.sample(x + h, z) - n.sample(x - h, z)) / (2 * h);
			double fz = (n.sample(x, z + h) - n.sample(x, z - h)) / (2 * h);
			assertEquals(fx, d[0], 1e-5, "d/dx w " + x + "," + z);
			assertEquals(fz, d[1], 1e-5, "d/dz w " + x + "," + z);
		}
	}

	@Test
	void keyDoesNotCollideForMirroredCells() {
		assertNotEquals(Noise.key(1, -21, 1), Noise.key(-1, 21, 1));
		assertNotEquals(Noise.key(0, 1, 1), Noise.key(1, 0, 1));
	}
}
