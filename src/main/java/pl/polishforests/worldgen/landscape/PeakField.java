package pl.polishforests.worldgen.landscape;

/**
 * Siatka szczytów: najwyższy teren bez dolin ({@link LandscapeModel#landElevation}) w promieniu ok. 3 km·mspace
 * (duży masyw w piętrach górskich, E12, docs/03-m2-biomy.md §5.1, poprawka po S4).
 *
 * <p>Dwa poziomy kafli w {@link DirectCache}. Poziom 1 to {@code landElevation} w węzłach co {@code krok}
 * (62,5 m·mspace), kafle 32 × 32 węzły. Poziom 2 to maksimum poziomu 1 w kole wokół węzła co {@code oczko}
 * (4 węzły poziomu 1, czyli 250 m·mspace), kafle 8 × 8 węzłów. W kolumnie wartość interpolujemy dwuliniowo
 * z czterech węzłów oczka. Promień koła w węźle to 3 km·mspace pomniejszone o przekątną oczka, więc
 * wartość ponad progiem gwarantuje szczyt ponad progiem w promieniu 3 km·mspace od kolumny (interpolacja
 * nie przekracza największego z czterech węzłów, a każdy z nich leży najwyżej o przekątną oczka od kolumny).
 * Siatka co 62,5 m·mspace zaniża wierzchołek najwyżej o kilka metrów, czyli też po stronie ostrożnej.
 *
 * <p>Model liczy to pole tylko w Beskidach powyżej {@code Pietra.SZCZYT_OD} (ok. 1180 m), więc kafle
 * powstają tylko wokół najwyższych grzbietów. Wartości są czystą funkcją współrzędnych, więc wynik nie
 * zależy od stanu pamięci ani od kolejności wątków.
 */
final class PeakField {
	/** Węzły poziomu 1 na bok kafla. */
	private static final int T1 = 32;
	/** Węzły poziomu 2 na bok kafla. */
	private static final int T2 = 8;

	private final LandscapeModel model;
	/** Odstęp węzłów poziomu 1 (m). */
	private final double step;
	/** Odstęp węzłów poziomu 2 (m). */
	private final double cellSize;
	/** Promień koła w węźle poziomu 2, w węzłach poziomu 1. */
	private final int r1;
	/** Kwadrat promienia w węzłach poziomu 1. */
	private final double r1sq;
	/** Węzły poziomu 1 na jedno oczko poziomu 2. */
	private final int nodesPerCell;
	private final DirectCache<float[]> level1;
	private final DirectCache<float[]> level2;

	/**
	 * @param radius promień szukania szczytu wokół kolumny (m)
	 * @param step    odstęp węzłów {@code landElevation} (m)
	 * @param nodesPerCell odstęp węzłów z maksimum w węzłach poziomu 1
	 */
	PeakField(LandscapeModel model, double radius, double step, int nodesPerCell) {
		this.model = model;
		this.step = step;
		this.nodesPerCell = nodesPerCell;
		this.cellSize = step * nodesPerCell;
		double nodeRadius = radius - this.cellSize * Math.sqrt(2);
		this.r1 = (int) Math.floor(nodeRadius / step);
		this.r1sq = (nodeRadius / step) * (nodeRadius / step);
		this.level1 = new DirectCache<>(1024, this::buildLevel1);
		this.level2 = new DirectCache<>(1024, this::buildLevel2);
	}

	/** Najwyższy teren bez dolin w promieniu (m n.p.m.), interpolowany dwuliniowo z węzłów oczka. */
	double sample(double x, double z) {
		double gx = x / cellSize;
		double gz = z / cellSize;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		double v00 = node(cx, cz);
		double v10 = node(cx + 1, cz);
		double v01 = node(cx, cz + 1);
		double v11 = node(cx + 1, cz + 1);
		return (1 - fz) * ((1 - fx) * v00 + fx * v10) + fz * ((1 - fx) * v01 + fx * v11);
	}

	private double node(long ix, long iz) {
		long tx = Math.floorDiv(ix, T2);
		long tz = Math.floorDiv(iz, T2);
		float[] t = level2.get(tx, tz);
		return t[(int) (iz - tz * T2) * T2 + (int) (ix - tx * T2)];
	}

	/** Kafel poziomu 1: {@code landElevation} w 32 × 32 węzłach. */
	private float[] buildLevel1(long tx, long tz) {
		float[] out = new float[T1 * T1];
		for (int j = 0; j < T1; j++) {
			for (int i = 0; i < T1; i++) {
				out[j * T1 + i] = (float) model.landElevation((tx * T1 + i) * step, (tz * T1 + j) * step);
			}
		}
		return out;
	}

	/** Kafel poziomu 2: maksimum poziomu 1 w kole wokół każdego z 8 × 8 węzłów. */
	private float[] buildLevel2(long tx, long tz) {
		// Okno węzłów poziomu 1 obejmujące koła wszystkich węzłów kafla.
		long i0 = tx * T2 * nodesPerCell - r1;
		long j0 = tz * T2 * nodesPerCell - r1;
		int windowSide = (T2 - 1) * nodesPerCell + 2 * r1 + 1;
		float[] window = new float[windowSide * windowSide];
		for (int j = 0; j < windowSide; j++) {
			for (int i = 0; i < windowSide; i++) {
				window[j * windowSide + i] = level1At(i0 + i, j0 + j);
			}
		}
		// Połówki szerokości koła w kolejnych wierszach.
		int[] halfWidths = new int[2 * r1 + 1];
		for (int dj = -r1; dj <= r1; dj++) {
			halfWidths[dj + r1] = (int) Math.floor(Math.sqrt(Math.max(0, r1sq - (double) dj * dj)));
		}
		float[] out = new float[T2 * T2];
		for (int b = 0; b < T2; b++) {
			for (int a = 0; a < T2; a++) {
				int ci = r1 + a * nodesPerCell;
				int cj = r1 + b * nodesPerCell;
				float max = Float.NEGATIVE_INFINITY;
				for (int dj = -r1; dj <= r1; dj++) {
					int hw = halfWidths[dj + r1];
					int row = (cj + dj) * windowSide;
					for (int di = -hw; di <= hw; di++) {
						float v = window[row + ci + di];
						if (v > max) {
							max = v;
						}
					}
				}
				out[b * T2 + a] = max;
			}
		}
		return out;
	}

	private float level1At(long i, long j) {
		long tx = Math.floorDiv(i, T1);
		long tz = Math.floorDiv(j, T1);
		float[] t = level1.get(tx, tz);
		return t[(int) (j - tz * T1) * T1 + (int) (i - tx * T1)];
	}
}
