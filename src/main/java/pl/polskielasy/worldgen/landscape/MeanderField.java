package pl.polskielasy.worldgen.landscape;

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

		/**
		 * Dwie dokładne odległości w jednym przejściu: {@code out[o]} jak {@link #exact} (ta sama wartość,
		 * łącznie z {@code Double.MAX_VALUE}) i {@code out[o + 1]} z kubełkami za granicą okresu (indeks poza
		 * [0, BUCKETS)) branymi modulo, z łamaną przesuniętą o okres. {@link #exact} ich nie przeszukuje, więc
		 * przy u blisko 0 i 1 pomija łamaną po drugiej stronie granicy i ma tam skok. Zostaje w terenie
		 * i tablicy (M1), a pole d i brzeg wypukły używają drugiej wartości.
		 */
		void exactPair(double u, double v, double limit, double[] out, int o) {
			double bestIn = Double.MAX_VALUE;
			double bestAll = Double.MAX_VALUE;
			int center = (int) Math.floor(u * BUCKETS);
			for (int r = 0; r < BUCKETS; r++) {
				double gap = (r - 1.0) / BUCKETS;
				// bestAll ≤ bestIn, więc warunek z exact kończy też szukanie bestAll.
				if (gap > 0 && gap * gap >= bestIn) {
					break;
				}
				if (gap > limit) {
					break;
				}
				for (int side = -1; side <= 1; side += 2) {
					if (r == 0 && side == 1) {
						continue;
					}
					int bb = center + side * r;
					boolean in = bb >= 0 && bb < BUCKETS;
					double period = Math.floorDiv(bb, BUCKETS);
					for (int code : buckets[Math.floorMod(bb, BUCKETS)]) {
						int k = code / 3;
						double shift = code % 3 - 1 + period;
						double dd = segDistSq(u, v, x[k] + shift, y[k], x[k + 1] + shift, y[k + 1]);
						if (in) {
							bestIn = Math.min(bestIn, dd);
						}
						bestAll = Math.min(bestAll, dd);
					}
				}
			}
			out[o] = bestIn == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(bestIn);
			out[o + 1] = bestAll == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(bestAll);
		}

		/**
		 * Czy punkt leży po wewnętrznej stronie łuku krzywej w jej najbliższym punkcie (szukanym jak
		 * w {@link #exactPair}, z kubełkami za granicą okresu): znak krzywizny {@code dθ/ds} odcinka łamanej razy strona punktu względem niego.
		 */
		boolean inner(double u, double v, double limit) {
			double best = Double.MAX_VALUE;
			int bestK = -1;
			double bestShift = 0;
			int center = (int) Math.floor(u * BUCKETS);
			for (int r = 0; r < BUCKETS; r++) {
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
					int bb = center + side * r;
					double period = Math.floorDiv(bb, BUCKETS);
					for (int code : buckets[Math.floorMod(bb, BUCKETS)]) {
						int k = code / 3;
						double shift = code % 3 - 1 + period;
						double d = segDistSq(u, v, x[k] + shift, y[k], x[k + 1] + shift, y[k + 1]);
						if (d < best) {
							best = d;
							bestK = k;
							bestShift = shift;
						}
					}
				}
			}
			if (bestK < 0) {
				return false;
			}
			double ax = x[bestK] + bestShift;
			double ay = y[bestK];
			double side = (x[bestK + 1] + bestShift - ax) * (v - ay) - (y[bestK + 1] - ay) * (u - ax);
			// Odcinek k ma kierunek θ(s) w s = (k + 0,5) / POINTS (jak przy budowie krzywej).
			double s = (bestK + 0.5) / POINTS;
			double curvature = theta * 2 * Math.PI * Math.cos(2 * Math.PI * s) - theta * theta * theta * 6 * Math.PI
					* (JS * Math.sin(6 * Math.PI * s) + JF * Math.cos(6 * Math.PI * s));
			return side * curvature > 0;
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

		/**
		 * {@link #distance} (do {@code out[o]}) i jej wersja ciągła (do {@code out[o + 1]}): zamiast
		 * przełączenia tablica → dokładna odległość przy 0,15 płynne przejście w przedziale
		 * [{@link #SMOOTH_LO}, {@link #SMOOTH_HI}], a dokładna odległość bez skoku przy zawinięciu u
		 * ({@link #exactPair}). Pierwsza wartość jest identyczna z {@link #distance}.
		 */
		void distances(double u, double v, double[] out, int o) {
			if (theta == 0) {
				out[o] = out[o + 1] = Math.abs(v);
				return;
			}
			if (Math.abs(v) > VMAX) {
				out[o] = out[o + 1] = table(u, Math.copySign(VMAX, v)) + Math.abs(v) - VMAX;
				return;
			}
			double d = table(u, v);
			if (d >= SMOOTH_HI) {
				out[o] = out[o + 1] = d;
				return;
			}
			// Teren: wersja z M1 (exact tylko poniżej 0,15). Pole d: bez skoku przy zawinięciu u.
			exactPair(u, v, 0.3, out, o);
			double e = out[o + 1] == Double.MAX_VALUE ? d : out[o + 1];
			out[o] = d < 0.15 && out[o] != Double.MAX_VALUE ? out[o] : d;
			out[o + 1] = d <= SMOOTH_LO ? e : e + (d - e) * Noise.smoothstep(SMOOTH_LO, SMOOTH_HI, d);
		}
	}

	/** Przedział wartości z tablicy, w którym {@link Level#distances} przechodzi płynnie na dokładną odległość. */
	private static final double SMOOTH_LO = 0.12;
	private static final double SMOOTH_HI = 0.20;

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

	/**
	 * Odległość jak w {@link #distance} (do {@code out[0]}, ta sama wartość) i odległość ciągła (do
	 * {@code out[1]}). {@link #distance} przełącza się z tablicy na dokładną odległość od łamanej, gdy
	 * wartość z tablicy spadnie poniżej 0,15 długości fali, i ma tam skok do ok. 0,015 λ; dokładna
	 * odległość z M1 ma też skok przy zawinięciu u (patrz {@link Level#exactPair}). Dla brzegu i koryta
	 * to bez znaczenia, ale pole d (odległość od koryta, {@link ColumnSample.Wody#odlKoryta}) musi być
	 * ciągłe. {@code out} ma co najmniej 4 miejsca (dwa ostatnie to bufor).
	 */
	static void distances(double u, double v, double theta, double[] out) {
		double f = Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1);
		int k = Math.min(LEVELS - 2, (int) Math.floor(f));
		double w = f - k;
		double uu = u - Math.floor(u);
		level(k).distances(uu, v, out, 0);
		if (w < 1e-9) {
			return;
		}
		level(k + 1).distances(uu, v, out, 2);
		out[0] = out[0] + (out[2] - out[0]) * w;
		out[1] = out[1] + (out[3] - out[1]) * w;
	}

	/**
	 * Czy punkt leży po wewnętrznej (wypukłej) stronie łuku meandra, w układzie jak w
	 * {@link #distance}. Liczone na najbliższym poziomie tablicy θ0 (k ≥ 1, czyli θ ≥ ok. 0,09); bez meandrów false.
	 */
	static boolean innerSide(double u, double v, double theta) {
		int k = (int) Math.round(Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1));
		if (k == 0) {
			return false;
		}
		return level(k).inner(u - Math.floor(u), v, 0.5);
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
