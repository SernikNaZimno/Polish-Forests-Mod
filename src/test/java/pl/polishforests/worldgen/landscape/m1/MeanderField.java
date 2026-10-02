package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/**
 * Meandry rzeki jako krzywa Kinoshity (krzywa generowana sinusem, Langbein i Leopold 1966;
 * składowe skośności i spłaszczenia wg Parkera 1983): kierunek koryta zmienia się wzdłuż jego
 * długości jak {@code θ(s) = θ0 sin(2πs) + θ0³ (Js cos 6πs − Jf sin 6πs)}. W odróżnieniu od
 * przesunięcia bocznego osi taka krzywa tworzy prawdziwe pętle z zaokrąglonymi łukami.
 * <p>
 * Krzywa jest okresowa, więc dla kilkunastu wartości {@code θ0} liczona jest raz tablica odległości
 * od krzywej w układzie jednego okresu: {@code u} (wzdłuż doliny, w długościach fali, mod 1) i
 * {@code v} (w poprzek, w długościach fali). Zapytanie to interpolacja z tablicy, a blisko koryta
 * dokładna odległość od łamanej krzywej.
 */
final class MeanderField {
	/** Największy kąt odchylenia koryta od osi doliny (rad), krętość ok. 2,6. */
	static final double THETA_MAX = 1.9;
	private static final int LEVELS = 12;
	private static final int NU = 64;
	private static final int NV = 161;
	private static final double VMAX = 2.0;
	private static final int POINTS = 256;
	private static final int BUCKETS = 48;
	private static final double JS = 0.03;
	private static final double JF = 0.01;

	private static final Level[] TABLE = new Level[LEVELS];

	private MeanderField() {
	}

	/** Jedna wartość θ0: punkty krzywej (jeden okres, znormalizowany) i tablica odległości. */
	private static final class Level {
		final double theta;
		final float[] x;
		final float[] y;
		/** Indeksy punktów w kubełkach po {@code x} (z kopiami sąsiednich okresów). */
		final int[][] buckets;
		final float[] dist;
		final double amplitude;

		Level(double theta) {
			this.theta = theta;
			x = new float[POINTS + 1];
			y = new float[POINTS + 1];
			double px = 0;
			double py = 0;
			double[] xs = new double[POINTS + 1];
			double[] ys = new double[POINTS + 1];
			double ds = 1.0 / POINTS;
			for (int i = 0; i <= POINTS; i++) {
				xs[i] = px;
				ys[i] = py;
				double s = (i + 0.5) * ds;
				double th = theta * Math.sin(2 * Math.PI * s)
						+ theta * theta * theta * (JS * Math.cos(6 * Math.PI * s) - JF * Math.sin(6 * Math.PI * s));
				px += Math.cos(th) * ds;
				py += Math.sin(th) * ds;
			}
			// Normalizacja: jeden okres przesuwa się o 1 wzdłuż osi, średnie przesunięcie boczne 0.
			double period = xs[POINTS];
			double mean = 0;
			for (int i = 0; i < POINTS; i++) {
				mean += ys[i];
			}
			mean /= POINTS;
			double amp = 0;
			for (int i = 0; i <= POINTS; i++) {
				x[i] = (float) (xs[i] / period);
				y[i] = (float) ((ys[i] - mean) / period);
				amp = Math.max(amp, Math.abs(y[i]));
			}
			amplitude = amp;
			buckets = buildBuckets();
			dist = new float[NU * NV];
			for (int i = 0; i < NU; i++) {
				for (int j = 0; j < NV; j++) {
					double v = -VMAX + j * (2 * VMAX / (NV - 1));
					double e = exact((double) i / NU, v, 0.45);
					// Daleko od krzywej dokładność nie jest potrzebna (liczy się tylko przy korycie).
					dist[i * NV + j] = (float) (e == Double.MAX_VALUE ? Math.max(0.45, Math.abs(v) - amp) : e);
				}
			}
		}

		private int[][] buildBuckets() {
			// Odcinek k łamanej (punkty k, k+1) trafia do kubełków obejmujących jego zakres x,
			// także przesunięty o okres w lewo i w prawo.
			java.util.List<java.util.List<Integer>> lists = new java.util.ArrayList<>();
			for (int b = 0; b < BUCKETS; b++) {
				lists.add(new java.util.ArrayList<>());
			}
			for (int k = 0; k < POINTS; k++) {
				double lo = Math.min(x[k], x[k + 1]);
				double hi = Math.max(x[k], x[k + 1]);
				for (int shift = -1; shift <= 1; shift++) {
					int b0 = (int) Math.floor((lo + shift) * BUCKETS);
					int b1 = (int) Math.floor((hi + shift) * BUCKETS);
					for (int b = Math.max(0, b0); b <= Math.min(BUCKETS - 1, b1); b++) {
						lists.get(b).add(k * 3 + (shift + 1));
					}
				}
			}
			int[][] out = new int[BUCKETS][];
			for (int b = 0; b < BUCKETS; b++) {
				out[b] = lists.get(b).stream().mapToInt(Integer::intValue).toArray();
			}
			return out;
		}

		/** Dokładna odległość od krzywej; {@code u} w [0, 1). Szuka w kubełkach do odległości {@code limit}. */
		double exact(double u, double v, double limit) {
			double best = Double.MAX_VALUE;
			int center = (int) Math.floor(u * BUCKETS);
			for (int r = 0; r < BUCKETS; r++) {
				// Kubełki w odległości r od środka są co najmniej (r - 1) / BUCKETS dalej wzdłuż osi.
				double gap = (r - 1.0) / BUCKETS;
				if (gap > 0 && gap * gap >= best) {
					break;
				}
				if (gap > limit) {
					break;
				}
				for (int side = -1; side <= 1; side += 2) {
					if (r == 0 && side == 1) {
						continue;
					}
					int b = center + side * r;
					if (b < 0 || b >= BUCKETS) {
						continue;
					}
					for (int code : buckets[b]) {
						int k = code / 3;
						double shift = code % 3 - 1;
						best = Math.min(best, segDistSq(u, v, x[k] + shift, y[k], x[k + 1] + shift, y[k + 1]));
					}
				}
			}
			return best == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(best);
		}

		double table(double u, double v) {
			double fu = u * NU;
			int i0 = (int) Math.floor(fu);
			double tu = fu - i0;
			int i1 = (i0 + 1) % NU;
			i0 = Math.floorMod(i0, NU);
			double fv = (v + VMAX) / (2 * VMAX) * (NV - 1);
			int j0 = Math.clamp((int) Math.floor(fv), 0, NV - 2);
			double tv = Math.clamp(fv - j0, 0.0, 1.0);
			double a = dist[i0 * NV + j0] + (dist[i0 * NV + j0 + 1] - dist[i0 * NV + j0]) * tv;
			double b = dist[i1 * NV + j0] + (dist[i1 * NV + j0 + 1] - dist[i1 * NV + j0]) * tv;
			return a + (b - a) * tu;
		}

		double distance(double u, double v) {
			if (theta == 0) {
				return Math.abs(v);
			}
			if (Math.abs(v) > VMAX) {
				return table(u, Math.copySign(VMAX, v)) + Math.abs(v) - VMAX;
			}
			double d = table(u, v);
			// Przy korycie tablica jest zbyt zgrubna: dokładna odległość od łamanej.
			if (d < 0.15) {
				double e = exact(u, v, 0.3);
				return e == Double.MAX_VALUE ? d : e;
			}
			return d;
		}
	}

	private static double segDistSq(double px, double py, double ax, double ay, double bx, double by) {
		double vx = bx - ax;
		double vy = by - ay;
		double l2 = vx * vx + vy * vy;
		double t = l2 < 1e-18 ? 0 : Math.clamp(((px - ax) * vx + (py - ay) * vy) / l2, 0.0, 1.0);
		double dx = px - (ax + vx * t);
		double dy = py - (ay + vy * t);
		return dx * dx + dy * dy;
	}

	private static Level level(int k) {
		Level l = TABLE[k];
		if (l == null) {
			synchronized (TABLE) {
				l = TABLE[k];
				if (l == null) {
					l = new Level(THETA_MAX * k / (LEVELS - 1));
					TABLE[k] = l;
				}
			}
		}
		return l;
	}

	/**
	 * Odległość od koryta w długościach fali.
	 *
	 * @param u     położenie wzdłuż doliny w długościach fali (dowolne, okresowe)
	 * @param v     położenie w poprzek doliny w długościach fali
	 * @param theta kąt {@code θ0} (rad), od 0 (prosto) do {@link #THETA_MAX}
	 */
	static double distance(double u, double v, double theta) {
		double f = Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1);
		int k = Math.min(LEVELS - 2, (int) Math.floor(f));
		double w = f - k;
		double uu = u - Math.floor(u);
		double a = level(k).distance(uu, v);
		if (w < 1e-9) {
			return a;
		}
		return a + (level(k + 1).distance(uu, v) - a) * w;
	}

	/** Największe odchylenie koryta od osi doliny w długościach fali. */
	static double amplitude(double theta) {
		double f = Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1);
		int k = Math.min(LEVELS - 2, (int) Math.floor(f));
		double w = f - k;
		return level(k).amplitude + (level(k + 1).amplitude - level(k).amplitude) * w;
	}

	/** Kąt θ0 dający krętość (długość koryta / długość doliny) w przybliżeniu {@code 1 / J0(θ0)}. */
	static double thetaForSinuosity(double sinuosity) {
		double target = 1.0 / Math.max(1.0, sinuosity);
		// J0 maleje monotonicznie na [0, 2,4]; bisekcja.
		double lo = 0;
		double hi = THETA_MAX;
		for (int i = 0; i < 40; i++) {
			double mid = 0.5 * (lo + hi);
			if (besselJ0(mid) > target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return 0.5 * (lo + hi);
	}

	private static double besselJ0(double x) {
		double sum = 0;
		double term = 1;
		double q = x * x / 4;
		for (int k = 0; k < 30; k++) {
			sum += term;
			term *= -q / ((k + 1.0) * (k + 1.0));
		}
		return sum;
	}
}
