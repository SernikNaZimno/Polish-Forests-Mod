package pl.polishforests.worldgen.tree;

import pl.polishforests.worldgen.landscape.Noise;

/**
 * Stand-in for the stand map of step V9 in the tests of step V5: patches of the three age classes and of natural and
 * managed stands from smooth noise (wavelengths of about one stand), and canopy gaps in natural stands. A pure function
 * of the seed and the position, like the real map; the salts are test salts, not the {@code feature.stand.*} salts of the
 * real map (rule ZW-2).
 */
final class PatchyStandMap implements StandMap {
	private final Noise age;
	private final Noise natural;
	private final Noise gaps;
	private final double natureThreshold;

	/**
	 * @param naturalShare approximate share of natural stands (0–1)
	 */
	PatchyStandMap(long seed, double naturalShare) {
		Noise root = new Noise(seed);
		this.age = root.derive("test.patchy_stand_map.age");
		this.natural = root.derive("test.patchy_stand_map.natural");
		this.gaps = root.derive("test.patchy_stand_map.gaps");
		// The noise is roughly symmetric around 0 with most values within ±0.5.
		this.natureThreshold = 0.5 - naturalShare;
	}

	@Override
	public AgeClass ageClass(int x, int z) {
		double a = age.fbm(x, z, 160, 2, 0.5);
		return a < -0.12 ? AgeClass.POLE_STAGE : a < 0.15 ? AgeClass.MATURE : AgeClass.OLD_GROWTH;
	}

	@Override
	public boolean natural(int x, int z) {
		return natural.fbm(x, z, 220, 2, 0.5) > natureThreshold;
	}

	@Override
	public boolean canopyGap(int x, int z) {
		return natural(x, z) && gaps.at(x, z, 48) > 0.4;
	}
}
