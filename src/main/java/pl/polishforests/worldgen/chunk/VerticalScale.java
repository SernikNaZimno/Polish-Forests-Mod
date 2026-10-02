package pl.polishforests.worldgen.chunk;

/**
 * Odwzorowanie wysokości n.p.m. (w metrach modelu) na wysokość w blokach oraz rama wymiaru.
 * Wartości {@link #minY()} i {@link #height()} muszą zgadzać się z plikiem typu wymiaru.
 */
public interface VerticalScale {
	/** Skala rzeczywista: 1:1 do ok. 900 m, powyżej płynnie ściśnięta; Rysy na Y 2000. */
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
	 * Skala przyjazna rozgrywce: bloki = 1,89 · metry^0,742. Niziny (130 m) leżą ok. 70 bloków nad
	 * morzem, 1000 m to ok. 318 bloków, Babia Góra ok. 475, Rysy ok. 625 (Y ok. 690). Wymiar ma
	 * wysokość 832 bloków (Y od -64 do 767).
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

	/** Liczba bloków nad poziomem morza dla wysokości {@code meters} n.p.m. */
	double blocksForMeters(double meters);

	/** Odwrotność {@link #blocksForMeters}. */
	double metersForBlocks(double blocks);

	/** Y najwyższego bloku gruntu dla powierzchni o wysokości {@code meters} n.p.m. */
	default int topBlockY(double meters) {
		return (int) Math.floor(seaLevelY() + blocksForMeters(meters)) - 1;
	}

	/** Wysokość n.p.m. górnej ściany bloku leżącego na {@code y}. */
	default double metersAboveSea(int y) {
		return metersForBlocks(y + 1 - seaLevelY());
	}
}
