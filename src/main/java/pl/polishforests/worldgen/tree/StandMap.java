package pl.polishforests.worldgen.tree;

/**
 * Lite stand map (decision PW-4 b, docs/04-pionowy-wycinek.md step V9): stands of 2–30 ha·k² with a managed or natural
 * flag (slider D9), an age class and, in natural stands only, canopy gaps of 18–45 blocks. A pure function of the world
 * seed and the position, without habitat codes, so every chunk sees the same stands (rule ZW-1); the crown territories
 * ({@link CrownTerritories}) depend on it for this reason.
 *
 * <p>Step V5 defined the interface and a uniform map ({@link #uniform}); the real map of the world is
 * {@link ForestStandMap} (step V9, its own salts {@code feature.stand.*}, rule ZW-2).
 */
public interface StandMap {
	/** Age class of the stand at column (x, z). */
	AgeClass ageClass(int x, int z);

	/** Whether the stand at column (x, z) is natural (otherwise managed). */
	boolean natural(int x, int z);

	/** Whether column (x, z) lies in a canopy gap (only in natural stands). */
	boolean canopyGap(int x, int z);

	/**
	 * How deep column (x, z) lies inside a canopy gap (blocks to its outline; 0 or less outside a gap). By default 1 in a
	 * gap and 0 elsewhere.
	 */
	default double gapDepth(int x, int z) {
		return canopyGap(x, z) ? 1 : 0;
	}

	/** One stand everywhere, without gaps (tree previews, tests). */
	static StandMap uniform(AgeClass age, boolean natural) {
		return new StandMap() {
			@Override
			public AgeClass ageClass(int x, int z) {
				return age;
			}

			@Override
			public boolean natural(int x, int z) {
				return natural;
			}

			@Override
			public boolean canopyGap(int x, int z) {
				return false;
			}
		};
	}
}
