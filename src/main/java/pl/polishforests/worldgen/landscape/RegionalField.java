package pl.polishforests.worldgen.landscape;

/**
 * Regional fields: oceanicity O and mountain influence P, variant V3 from the ecology report (docs/03-m2-biomy.md §3.2, §9).
 *
 * <p>O = clamp(0.15 + 0.45·N + 0.25·Wz + 0.15·S, 0, 1), where
 * <ul>
 * <li>N – climate provinces: smoothstep of the quantile of a noise with a 900 km·zs wavelength (the quantile is uniformly
 * distributed on [0, 1], smoothstep concentrates the values near 0 and 1);</li>
 * <li>Wz – share of sea at 8 points to the west (−X, the sunset side) every 60 km·zs, with weights 1/k
 * (westerly winds); sea is {@link LandscapeModel#seaField} &lt; 0 with a soft boundary of ±0.02;</li>
 * <li>S = 1 − smoothstep(0, 150 km·zs, {@link LandscapeModel#coastDistance}) – proximity of the sea.</li>
 * </ul>
 * P is the mean over a window of 5 × 5 nodes (about 80 km·zs) of smoothstep(0.465, 0.915, linear range field), where
 * the linear field is {@link LandscapeModel#mountainLinear}: the mountain range field without the cube from
 * {@link LandscapeModel#mountainField}, so it reaches further from the range axis than the mountains and foothills. The thresholds put
 * the P = 0.5 boundary at a median of about 220 km·zs from the range axis (zero line of {@link LandscapeModel#mountainRaw}; 10 seeds
 * together, per-seed medians 180–280 km·zs; plan: 150–250 km·zs). Averaging limits the drop of P to
 * 1/80 per km·zs: without it the range masks (end of the chain, offset from the sea) gave up to 0.05 per km·zs.
 *
 * <p>Nodes lie every 16 km·zs (about 350 m at gameplay scale); the value in a column is a bilinear interpolation.
 * A tile has 8 × 8 cells (9 × 9 nodes) and is immutable; a node costs a few µs, and a tile at realistic scale
 * covers 128 km. The result depends neither on query order nor on the cache state.
 */
final class RegionalField {
	/** Cells per tile side. */
	static final int TILE = 8;
	private static final int N = TILE + 1;
	/** Number of slots in the tile cache. */
	static final int CACHE_SLOTS = 1024;
	/** Node spacing at zs = 1 (m). */
	static final double SPACING = 16_000;
	/** Wavelength of the climate province noise at zs = 1 (m). */
	private static final double PROVINCE_WAVELENGTH = 900_000;
	/** Step of the western points at zs = 1 (m) and their number. */
	private static final double WEST_STEP = 60_000;
	private static final int WEST_POINTS = 8;
	/** Sum of the 1/k weights of the western points. */
	private static final double WEST_WEIGHT_SUM;
	/** Reach of the sea proximity term at zs = 1 (m). */
	private static final double SEA_REACH = 150_000;
	/** P thresholds on the linear range field. */
	static final double P0 = 0.465;
	static final double P1 = 0.915;
	/** P averaging radius in nodes (window of 5 × 5 nodes, about 80 km·zs). */
	private static final int P_RADIUS = 2;

	static {
		double s = 0;
		for (int k = 1; k <= WEST_POINTS; k++) {
			s += 1.0 / k;
		}
		WEST_WEIGHT_SUM = s;
	}

	private final LandscapeModel model;
	private final Noise provinces;
	private final double zs;
	private final double spacing;
	private final double inv;
	private final DirectCache<float[]> tiles;

	/**
	 * @param provinces climate province noise ({@code habitat.*}, so the terrain does not change)
	 * @param zs        zone scale multiplier (region size / 64 km)
	 */
	RegionalField(LandscapeModel model, Noise provinces, double zs) {
		this(model, provinces, zs, CACHE_SLOTS);
	}

	private RegionalField(LandscapeModel model, Noise provinces, double zs, int slots) {
		this.model = model;
		this.provinces = provinces;
		this.zs = zs;
		this.spacing = SPACING * zs;
		this.inv = 1.0 / spacing;
		this.tiles = new DirectCache<>(slots, this::build);
	}

	/** The same fields with a different number of tile cache slots (eviction tests). */
	RegionalField withSlots(int slots) {
		return new RegionalField(model, provinces, zs, slots);
	}

	/** Node spacing (m). */
	double spacing() {
		return spacing;
	}

	/** O and P at a point (bilinear interpolation from the nodes). */
	ColumnSample.Region sample(double x, double z) {
		double gx = x * inv;
		double gz = z * inv;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		long tx = Math.floorDiv(cx, TILE);
		long tz = Math.floorDiv(cz, TILE);
		float[] t = tiles.get(tx, tz);
		int i00 = ((int) (cz - tz * TILE) * N + (int) (cx - tx * TILE)) * 2;
		int i10 = i00 + 2;
		int i01 = i00 + 2 * N;
		int i11 = i01 + 2;
		double w00 = (1 - fx) * (1 - fz);
		double w10 = fx * (1 - fz);
		double w01 = (1 - fx) * fz;
		double w11 = fx * fz;
		double o = w00 * t[i00] + w10 * t[i10] + w01 * t[i01] + w11 * t[i11];
		double p = w00 * t[i00 + 1] + w10 * t[i10 + 1] + w01 * t[i01 + 1] + w11 * t[i11 + 1];
		// The weights sum to 1 up to rounding; clamp keeps the range [0, 1] exact.
		return new ColumnSample.Region(Math.clamp(o, 0.0, 1.0), Math.clamp(p, 0.0, 1.0));
	}

	/** Oceanicity at a node (without interpolation). */
	double oceanicity(double x, double z) {
		// The quantile gives a uniform distribution; smoothstep pushes the provinces towards the poles (oceanic, continental),
		// which narrows the belt without beech and spruce to about 14% of land (about 18–21% with the quantile alone).
		double n = Noise.smoothstep(0, 1, LandscapeModel.noiseQuantile(provinces.at(x, z, PROVINCE_WAVELENGTH * zs)));
		double wz = 0;
		for (int k = 1; k <= WEST_POINTS; k++) {
			double sea = 1 - Noise.smoothstep(-0.02, 0.02, model.seaField(x - k * WEST_STEP * zs, z));
			wz += sea / k;
		}
		wz /= WEST_WEIGHT_SUM;
		double s = 1 - Noise.smoothstep(0, SEA_REACH * zs, model.coastDistance(x, z));
		return Math.clamp(0.15 + 0.45 * n + 0.25 * wz + 0.15 * s, 0.0, 1.0);
	}

	/** Mountain influence at a point before averaging over the node window (without interpolation). */
	double mountainInfluence(double x, double z) {
		return Noise.smoothstep(P0, P1, model.mountainLinear(x, z));
	}

	/** Tile (tx, tz): O and P at 9 × 9 nodes; P averaged over a window of 5 × 5 nodes. */
	private float[] build(long tx, long tz) {
		long ix0 = tx * TILE;
		long iz0 = tz * TILE;
		int r = P_RADIUS;
		int w = N + 2 * r;
		double[] p = new double[w * w];
		for (int j = 0; j < w; j++) {
			for (int i = 0; i < w; i++) {
				p[j * w + i] = mountainInfluence((ix0 + i - r) * spacing, (iz0 + j - r) * spacing);
			}
		}
		double norm = 1.0 / ((2 * r + 1) * (2 * r + 1));
		float[] out = new float[2 * N * N];
		for (int b = 0; b < N; b++) {
			for (int a = 0; a < N; a++) {
				double sum = 0;
				for (int j = b; j <= b + 2 * r; j++) {
					for (int i = a; i <= a + 2 * r; i++) {
						sum += p[j * w + i];
					}
				}
				int k = (b * N + a) * 2;
				out[k] = (float) oceanicity((ix0 + a) * spacing, (iz0 + b) * spacing);
				out[k + 1] = (float) Math.clamp(sum * norm, 0.0, 1.0);
			}
		}
		return out;
	}
}
