package pl.polishforests.worldgen.landscape;

/**
 * River meanders as a Kinoshita curve (sine-generated curve, Langbein and Leopold 1966;
 * skewness and flattening terms after Parker 1983): the channel direction changes along its
 * length as {@code θ(s) = θ0 sin(2πs) + θ0³ (Js cos 6πs − Jf sin 6πs)}. Unlike a lateral
 * offset of the axis, such a curve forms real loops with rounded bends.
 * <p>
 * The curve is periodic, so for a dozen or so values of {@code θ0} a table of distances
 * from the curve is computed once in the frame of one period: {@code u} (along the valley, in wavelengths, mod 1) and
 * {@code v} (across, in wavelengths). A query is an interpolation from the table, and close to the channel
 * the exact distance from the polyline of the curve.
 */
final class MeanderField {
	/** Largest deflection angle of the channel from the valley axis (rad), sinuosity about 2.6. */
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

	/** One value of θ0: curve points (one period, normalised) and the distance table. */
	private static final class Level {
		final double theta;
		final float[] x;
		final float[] y;
		/** Point indices in buckets by {@code x} (with copies from the neighboring periods). */
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
			// Normalisation: one period advances by 1 along the axis, mean lateral offset 0.
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
					// Far from the curve accuracy is not needed (it matters only near the channel).
					dist[i * NV + j] = (float) (e == Double.MAX_VALUE ? Math.max(0.45, Math.abs(v) - amp) : e);
				}
			}
		}

		private int[][] buildBuckets() {
			// Polyline segment k (points k, k+1) goes into the buckets covering its x range,
			// also shifted by one period to the left and to the right.
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

		/** Exact distance from the curve; {@code u} in [0, 1). Searches the buckets up to distance {@code limit}. */
		double exact(double u, double v, double limit) {
			double best = Double.MAX_VALUE;
			int center = (int) Math.floor(u * BUCKETS);
			for (int r = 0; r < BUCKETS; r++) {
				// Buckets at distance r from the center are at least (r - 1) / BUCKETS further along the axis.
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
		 * Two exact distances in one pass: {@code out[o]} like {@link #exact} (the same value,
		 * including {@code Double.MAX_VALUE}) and {@code out[o + 1]} with buckets beyond the period boundary (index outside
		 * [0, BUCKETS)) taken modulo, with the polyline shifted by one period. {@link #exact} does not search them, so
		 * with u close to 0 and 1 it misses the polyline on the other side of the boundary and has a jump there. It stays in the terrain
		 * and the table (M1), while the d field and the convex bank use the second value.
		 */
		void exactPair(double u, double v, double limit, double[] out, int o) {
			double bestIn = Double.MAX_VALUE;
			double bestAll = Double.MAX_VALUE;
			int center = (int) Math.floor(u * BUCKETS);
			for (int r = 0; r < BUCKETS; r++) {
				double gap = (r - 1.0) / BUCKETS;
				// bestAll ≤ bestIn, so the stop condition from exact also ends the search for bestAll.
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
		 * Whether the point lies on the inner side of the curve's bend at its nearest point (searched as
		 * in {@link #exactPair}, with buckets beyond the period boundary): the sign of the curvature {@code dθ/ds} of the polyline segment times the side of the point relative to it.
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
			// Segment k has direction θ(s) at s = (k + 0.5) / POINTS (as when building the curve).
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
			// Near the channel the table is too coarse: exact distance from the polyline.
			if (d < 0.15) {
				double e = exact(u, v, 0.3);
				return e == Double.MAX_VALUE ? d : e;
			}
			return d;
		}

		/**
		 * {@link #distance} (into {@code out[o]}) and its continuous version (into {@code out[o + 1]}): instead of
		 * switching table → exact distance at 0.15, a smooth transition over the interval
		 * [{@link #SMOOTH_LO}, {@link #SMOOTH_HI}], and the exact distance without a jump where u wraps
		 * ({@link #exactPair}). The first value is identical to {@link #distance}.
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
			// Terrain: the M1 version (exact only below 0.15). The d field: no jump where u wraps.
			exactPair(u, v, 0.3, out, o);
			double e = out[o + 1] == Double.MAX_VALUE ? d : out[o + 1];
			out[o] = d < 0.15 && out[o] != Double.MAX_VALUE ? out[o] : d;
			out[o + 1] = d <= SMOOTH_LO ? e : e + (d - e) * Noise.smoothstep(SMOOTH_LO, SMOOTH_HI, d);
		}
	}

	/** Range of table values over which {@link Level#distances} blends smoothly into the exact distance. */
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
	 * Distance from the channel in wavelengths.
	 *
	 * @param u     position along the valley in wavelengths (any value, periodic)
	 * @param v     position across the valley in wavelengths
	 * @param theta angle {@code θ0} (rad), from 0 (straight) to {@link #THETA_MAX}
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
	 * Distance as in {@link #distance} (into {@code out[0]}, the same value) and the continuous distance (into
	 * {@code out[1]}). {@link #distance} switches from the table to the exact distance from the polyline when
	 * the table value drops below 0.15 wavelengths, and has a jump of up to about 0.015 λ there; the exact
	 * M1 distance also has a jump where u wraps (see {@link Level#exactPair}). For the bank and the channel
	 * this does not matter, but the d field (distance from the channel, {@link ColumnSample.Waters#channelDist}) must be
	 * continuous. {@code out} has at least 4 slots (the last two are a buffer).
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
	 * Whether the point lies on the inner (convex) side of a meander bend, in the same frame as
	 * {@link #distance}. Computed on the nearest θ0 table level (k ≥ 1, i.e. θ ≥ about 0.09); false without meanders.
	 */
	static boolean innerSide(double u, double v, double theta) {
		int k = (int) Math.round(Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1));
		if (k == 0) {
			return false;
		}
		return level(k).inner(u - Math.floor(u), v, 0.5);
	}

	/** Number of edges of the polyline of the arc of an oxbow lake (half a period of the curve, every second point). */
	private static final int ARC_SEGMENTS = (POINTS / 2) / 2;

	/**
	 * K5.1: distance from the arc of a former loop, half a period of the Kinoshita curve of the level nearest to
	 * {@code theta}, from inflection point to inflection point ({@code s ∈ [0.25, 0.75]}), in the frame of the arc: {@code du}
	 * along the valley from the apex of the arc, {@code dv} across it, positive outwards (in wavelengths). The arc is
	 * trimmed to the parameter {@code a ∈ [a0, a1]} (0 and 1 are the inflection points). Result: {@code out[0]} the
	 * distance, {@code out[1]} the parameter a of the nearest point of the arc, {@code out[2]} and {@code out[3]} (step K8c)
	 * its du and dv.
	 */
	static void arcDistance(double du, double dv, double theta, double a0, double a1, double[] out) {
		Level l = level(arcLevel(theta));
		int i0 = POINTS / 4;
		double best = Double.MAX_VALUE;
		double bestA = 0;
		double bestU = 0;
		double bestV = 0;
		int first = (int) Math.floor(a0 * ARC_SEGMENTS);
		int last = Math.min(ARC_SEGMENTS - 1, (int) Math.ceil(a1 * ARC_SEGMENTS) - 1);
		for (int q = first; q <= last; q++) {
			// Edge q of the arc: the points i0 + 2q and i0 + 2q + 2, trimmed to [a0, a1].
			double qa = Math.max(a0, (double) q / ARC_SEGMENTS);
			double qb = Math.min(a1, (q + 1.0) / ARC_SEGMENTS);
			double ax = arcX(l, i0, qa) - 0.5;
			double ay = arcY(l, i0, qa);
			double bx = arcX(l, i0, qb) - 0.5;
			double by = arcY(l, i0, qb);
			double vx = bx - ax;
			double vy = by - ay;
			double l2 = vx * vx + vy * vy;
			double t = l2 < 1e-18 ? 0 : Math.clamp(((du - ax) * vx + (dv - ay) * vy) / l2, 0.0, 1.0);
			double ex = du - (ax + vx * t);
			double ey = dv - (ay + vy * t);
			double d2 = ex * ex + ey * ey;
			if (d2 < best) {
				best = d2;
				bestA = qa + (qb - qa) * t;
				bestU = ax + vx * t;
				bestV = ay + vy * t;
			}
		}
		out[0] = Math.sqrt(best);
		out[1] = bestA;
		out[2] = bestU;
		out[3] = bestV;
	}

	/**
	 * K8c (review round 1): the point of the arc of {@link #arcDistance} with the parameter {@code a} (clamped to [0, 1]) on
	 * the same polyline, in the same frame: {@code out[0]} its du, {@code out[1]} its dv (wavelengths).
	 */
	static void arcPoint(double theta, double a, double[] out) {
		Level l = level(arcLevel(theta));
		int i0 = POINTS / 4;
		double c = Math.clamp(a, 0.0, 1.0) * ARC_SEGMENTS;
		int q = Math.min(ARC_SEGMENTS - 1, (int) Math.floor(c));
		double qa = (double) q / ARC_SEGMENTS;
		double qb = (q + 1.0) / ARC_SEGMENTS;
		double f = c - q;
		double ax = arcX(l, i0, qa);
		double ay = arcY(l, i0, qa);
		out[0] = ax + (arcX(l, i0, qb) - ax) * f - 0.5;
		out[1] = ay + (arcY(l, i0, qb) - ay) * f;
	}

	/** K5.1: table level of the arc of {@link #arcDistance} (the nearest level, at least 1). */
	private static int arcLevel(double theta) {
		return Math.max(1, (int) Math.round(Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1)));
	}

	/** Position of the point of the arc with the parameter a (linear interpolation between the points of the curve). */
	private static double arcX(Level l, int i0, double a) {
		double f = i0 + a * (POINTS / 2.0);
		int i = Math.min(POINTS - 1, (int) Math.floor(f));
		return l.x[i] + (l.x[i + 1] - l.x[i]) * (f - i);
	}

	private static double arcY(Level l, int i0, double a) {
		double f = i0 + a * (POINTS / 2.0);
		int i = Math.min(POINTS - 1, (int) Math.floor(f));
		return l.y[i] + (l.y[i + 1] - l.y[i]) * (f - i);
	}

	/**
	 * K5.1: extent of the arc of a former loop in the frame of {@link #arcDistance} (wavelengths): along the valley from
	 * {@code lo} to {@code hi}, across up to {@code top}. A record (final fields), so a bound computed by one thread is
	 * seen whole by the others.
	 */
	record ArcBounds(double lo, double hi, double top) {
	}

	/** K5.1: {@link ArcBounds} of the arc of the level nearest to {@code theta}, computed once per level. */
	static ArcBounds arcBounds(double theta) {
		int k = arcLevel(theta);
		ArcBounds b = ARC_BOUNDS[k];
		if (b == null) {
			// A pure function of the level: a thread that computes it again stores the same values.
			Level l = level(k);
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			double top = -Double.MAX_VALUE;
			for (int i = POINTS / 4; i <= 3 * POINTS / 4; i++) {
				lo = Math.min(lo, l.x[i] - 0.5);
				hi = Math.max(hi, l.x[i] - 0.5);
				top = Math.max(top, l.y[i]);
			}
			b = new ArcBounds(lo, hi, top);
			ARC_BOUNDS[k] = b;
		}
		return b;
	}

	private static final ArcBounds[] ARC_BOUNDS = new ArcBounds[LEVELS];

	/** Largest deviation of the channel from the valley axis in wavelengths. */
	static double amplitude(double theta) {
		double f = Math.clamp(theta / THETA_MAX, 0.0, 1.0) * (LEVELS - 1);
		int k = Math.min(LEVELS - 2, (int) Math.floor(f));
		double w = f - k;
		return level(k).amplitude + (level(k + 1).amplitude - level(k).amplitude) * w;
	}

	/** Angle θ0 giving a sinuosity (channel length / valley length) of approximately {@code 1 / J0(θ0)}. */
	static double thetaForSinuosity(double sinuosity) {
		double target = 1.0 / Math.max(1.0, sinuosity);
		// J0 decreases monotonically on [0, 2.4]; bisection.
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
