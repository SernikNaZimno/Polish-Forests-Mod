package pl.polishforests.worldgen.chunk;

/**
 * Mapping of the elevation a.s.l. (in model meters) to the height in blocks, plus the dimension frame.
 * The values {@link #minY()} and {@link #height()} must match the dimension type file.
 */
public interface VerticalScale {
	/** Real scale: 1:1 up to about 900 m, smoothly compressed above; Rysy at Y 2000. */
	VerticalScale REAL = new VerticalScale() {
		@Override
		public int minY() {
			return PolandDimension.MIN_Y;
		}

		@Override
		public int height() {
			return PolandDimension.HEIGHT;
		}

		@Override
		public double blocksForMeters(double meters) {
			return PolandDimension.blocksForMeters(meters);
		}

		@Override
		public double metersForBlocks(double blocks) {
			return PolandDimension.metersForBlocks(blocks);
		}

		@Override
		public double metersAboveSea(int y) {
			return PolandDimension.metersAboveSea(y);
		}
	};

	/**
	 * Gameplay-friendly scale: blocks = 1.89 · meters^0.742. Lowlands (130 m) lie about 70 blocks above
	 * the sea, 1000 m is about 318 blocks, Babia Gora about 475, Rysy about 625 (Y about 690). The
	 * dimension is 832 blocks high (Y from -64 to 767).
	 */
	VerticalScale GAMEPLAY = new VerticalScale() {
		private static final double A = 1.89;
		private static final double P = 0.742;

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int height() {
			return 832;
		}

		@Override
		public double blocksForMeters(double meters) {
			double m = Math.abs(meters);
			double b = m < 1 ? A * m : A * Math.pow(m, P);
			return Math.copySign(b, meters);
		}

		@Override
		public double metersForBlocks(double blocks) {
			double b = Math.abs(blocks);
			double m = b < A ? b / A : Math.pow(b / A, 1.0 / P);
			return Math.copySign(m, blocks);
		}
	};

	int minY();

	int height();

	default int seaLevelY() {
		return PolandDimension.SEA_LEVEL_Y;
	}

	default int maxY() {
		return minY() + height() - 1;
	}

	/** Number of blocks above sea level for an elevation of {@code meters} a.s.l. */
	double blocksForMeters(double meters);

	/** Inverse of {@link #blocksForMeters}. */
	double metersForBlocks(double blocks);

	/** Y of the topmost ground block for a surface at an elevation of {@code meters} a.s.l. */
	default int topBlockY(double meters) {
		return (int) Math.floor(seaLevelY() + blocksForMeters(meters)) - 1;
	}

	/** Elevation a.s.l. of the top face of the block at {@code y}. */
	default double metersAboveSea(int y) {
		return metersForBlocks(y + 1 - seaLevelY());
	}
}
