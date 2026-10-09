package pl.polishforests.worldgen.feature.plan;

import pl.polishforests.worldgen.habitat.Calibration;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Range ramp of the tree stand (docs/03-m2-biomy.md §9): at the level of the biome and the habitat flags the range
 * thresholds on O (oceanicity) and P (mountain influence) are hard; in the tree stand the weight of a species with a
 * range flag grows from 0 at the threshold to the full weight {@value #WIDTH} beyond it, and the ramp is shifted by
 * ±{@value #JITTER} with a noise of wavelength {@value #WAVELENGTH} m·k, so the edge of a range is not a line. O and P are
 * practically constant over a chunk, so the factors are computed once per chunk ({@code ChunkHabitats} keeps O and P of
 * the chunk center). Hornbeam has an elevation rule only and keeps its full weight.
 */
public final class SpeciesRamp {
	/** Width of the ramp in units of O and P (2 × the ±0.05 of §9). */
	public static final double WIDTH = 0.10;
	/** Amplitude of the noise shift of the ramp. */
	public static final double JITTER = 0.05;
	/** Wavelength of the shift noise in meters at the realistic scale (§9: 2 km·k). */
	public static final double WAVELENGTH = 2_000;

	private SpeciesRamp() {
	}

	/**
	 * Factors of the four flag bits (beech, fir, spruce, hornbeam) for a chunk.
	 *
	 * @param o     oceanicity O of the chunk
	 * @param p     mountain influence P of the chunk
	 * @param shift the ramp noise at the chunk center, roughly in [−1, 1]
	 */
	public static float[] factors(double o, double p, double shift) {
		double s = JITTER * shift;
		return new float[] {
				ramp(Math.max(o - Calibration.BEECH_O, p - Calibration.BEECH_P) + s),
				ramp(p - Calibration.FIR_P + s),
				ramp(Math.max(Calibration.SPRUCE_O - o, p - Calibration.SPRUCE_P) + s),
				1};
	}

	/** Factors for a chunk at block (x, z) with the shift noise of the world. */
	public static float[] factors(double o, double p, Noise shift, double x, double z, double k) {
		return factors(o, p, shift.at(x, z, WAVELENGTH * k));
	}

	/** Smooth ramp from 0 at a margin of 0 to 1 at a margin of {@link #WIDTH}. */
	static float ramp(double margin) {
		return (float) Noise.smoothstep(0, WIDTH, margin);
	}
}
