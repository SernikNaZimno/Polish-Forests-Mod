package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tatra complex (step TT1 of docs/04-pionowy-wycinek.md, §3 "Kroki TT"): a rare high massif of the Tatra type with the
 * Podhale on one side, placed deep in a mountain range, elongated along the range axis.
 *
 * <p>Placement: one candidate per cell of a grid with the side {@link #spacing} (213 km·mspace, about 64 km at gameplay
 * scale), drawn with the probability {@link #P}; the candidate tries up to {@link #TRIES} anchor points in the middle half
 * of the cell and keeps the first one deep in the range (mountain field and Beskids weight as the large massifs M2-8,
 * with the range also along the massif) whose reach does not touch the reach of any large Beskid massif. The anchor is
 * the middle of the Podhale basin, on the range axis; the massif lies beside it, its main ridge {@link #BASIN_CENTER}
 * away across the range. A point is influenced only by the candidate of its own cell (the reach from the anchor is less
 * than a quarter of the cell, checked in the constructor), so the field is local and needs no thinning.
 *
 * <p>Relief in model meters, horizontal sizes in "real" meters times {@code mspace} (0.3 at gameplay scale), so the
 * gameplay massif is 13.5–18 × 4.5 km. Across the range (U, positive towards the Podhale) from the south: the southern
 * foot, the massif (main ridge at U' = 0, side ridges and U-shaped valleys on both sides, cirques at the valley heads,
 * the regle belt with a Giewont-type ridge on the Podhale side), the Sub-Tatra Trough, the Gubałówka-type foothills and
 * the Podhale basin. Along the range (V): the Western Tatras (domed, gneiss), the High Tatras (granite, the highest
 * summit {@code summit}) and the Belianske Tatras (limestone). The complex fades into the old terrain with the weight
 * {@code weight}: a product of smooth edges with outlines warped by noise (Z10: no straight line, no fault).
 *
 * <p>All noises are new seeds ({@code mountain.tatra}, {@code mountain.tatra.relief}, {@code mountain.tatra.geology}),
 * so the rest of the world does not change. The class does not depend on Minecraft.
 */
final class TatraField {
	/** A candidate is drawn with this probability per grid cell. */
	static final double P = 0.7;
	/** Anchor points tried per candidate. */
	static final int TRIES = 64;
	/** Anchor lattice per side over the middle half of a cell. */
	static final int GRID = 24;
	/** The anchor (the middle of the basin) lies deep in the range: mountain field and Beskids weight (as M2-8). */
	static final double MIN_FIELD = 0.90;
	static final double MIN_WEIGHT = 0.98;
	/** The range continues along the massif: mountain field at the anchor ± ALONG_CHECK · L. */
	static final double ALONG_CHECK = 0.45;
	static final double ALONG_FIELD = 0.70;

	// Sizes in "real" meters (times mspace in the model).
	/** Half-length of the massif along the range: total 45–60 km (real 57 km). */
	static final double L_MIN = 22_500;
	static final double L_MAX = 30_000;
	/** Main ridge to the northern (Podhale side) and southern foot. */
	static final double WN = 7_000;
	static final double WS = 8_000;
	/** Widths north of the massif: trough, foothills, the slope to the basin, the basin. */
	static final double TROUGH = 1_000;
	static final double FOOTHILLS = 2_000;
	static final double SLOPE = 1_200;
	static final double BASIN = 4_000;
	/** Main ridge to the middle of the basin (the anchor). */
	static final double BASIN_CENTER = WN + TROUGH + FOOTHILLS + SLOPE + BASIN / 2;
	/** Fade belts into the old terrain: from the basin edge, from the southern foot, from the massif end. */
	static final double FADE_NORTH = 10_000;
	static final double FADE_SOUTH0 = 2_000;
	static final double FADE_SOUTH1 = 10_000;
	static final double FADE_END0 = 1_500;
	static final double FADE_END1 = 10_000;
	/** Amplitude of the edge noise of the fades. */
	static final double EDGE = 700;
	/** Valley spacing along the ridge (± 12%). */
	static final double VALLEY_SPACING = 3_000;
	/** Arc of the main ridge towards the Podhale (m in the middle) and its warp. */
	static final double BOW = 1_200;
	static final double RIDGE_WARP = 600;

	/** Range of the target summit (m a.s.l.); the realized summit is within ± ROUGH of it (TT1: 2450–2530 m). */
	static final double SUMMIT_LO = 2_462;
	static final double SUMMIT_HI = 2_518;
	/** Amplitude of the fine roughness (m). */
	static final double ROUGH = 6;
	/** Hanging valleys begin above this share of the side width (over the valley heads of every valley). */
	static final double HANG_IN = 0.17;
	/** Core of the massif for the river network (no springs, no flow across), as GM_CORE of the large massifs. */
	static final double CORE = 0.3;
	/**
	 * Fade of a complex towards the shore (coast distance in m·mspace): no weight nearer than COAST_FADE0, full from
	 * COAST_FADE1. The gameplay mountain belt lies mostly 4–12 km from the shore, so the outer parts of a complex of
	 * 10 × 20 km often reach the coast; the complex lies over the terrain after shapeCoast (LandscapeModel).
	 */
	static final double COAST_FADE0 = 1_500;
	static final double COAST_FADE1 = 6_000;

	/** Tatra massif of one grid cell. */
	record Massif(long i, long j, double anchorX, double anchorZ, double x, double z, double cos, double sin,
			double halfLength, double wn, double ws, double summit, double west, double spacing, double phaseN,
			double phaseS, double peakT, double giewontT, double reach, boolean coastal) {
		/** Local coordinate across the range (towards the Podhale) of a point. */
		double u(double px, double pz) {
			return (px - x) * cos + (pz - z) * sin;
		}

		/** Local coordinate along the range. */
		double v(double px, double pz) {
			return -(px - x) * sin + (pz - z) * cos;
		}

		/** World x of the local point (u, v). */
		double worldX(double u, double v) {
			return x + u * cos - v * sin;
		}

		double worldZ(double u, double v) {
			return z + u * sin + v * cos;
		}
	}

	private static final Massif NONE = new Massif(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, false);

	/** Result of the field in a column ({@link #column}); null outside every complex. */
	static final class Column {
		/** Weight of the complex 0–1 (blend with the old terrain). */
		double weight;
		/** Height of the complex (m a.s.l.), before the blend. */
		double height;
		/** Weights of the types: TATRA and PODHALE (sum = weight). */
		double tatra;
		double podhale;
		TatraPart part = TatraPart.NONE;
		/** Bedrock of the massif, or null (flysch). */
		Substrate rock;
		/** Valley profile: 0 on the valley floor, 1 on the side ridge (0.5 beyond the massif ends); NaN off the massif. */
		double profile = Double.NaN;
		/** {@link Landform#CIRQUE} and {@link Landform#U_VALLEY} bits. */
		int landforms;
		/**
		 * Strength of the massif core for the river network (0–1): the upper massif (main ridge and valley heads) and the
		 * side ridges, so the streams rise in the lower valleys and stay in them (no flow across a ridge).
		 */
		double core;
		/** Share of the massif proper (main ridge to its foot, 0–1): only first-order streams rise there. */
		double massifShare;
		/** A pass of the main ridge (saddle between two valley heads). */
		boolean pass;
		/** Floor of a hanging valley. */
		boolean hanging;
		Massif massif;
	}

	private final LandscapeModel model;
	private final Noise place;
	private final Noise relief;
	private final Noise geology;
	private final double k;
	private final double spacing;
	private final double zs;
	private final DirectCache<Massif> cells = new DirectCache<>(1_024, this::build);

	TatraField(LandscapeModel model, Noise root, LandscapeScale scale, double zs) {
		this.model = model;
		this.place = root.derive("mountain.tatra");
		this.relief = root.derive("mountain.tatra.relief");
		this.geology = root.derive("mountain.tatra.geology");
		this.k = scale.mountainSpacing();
		this.spacing = 213_000 * k;
		this.zs = zs;
		// Only the candidate of the own cell can reach a point: the farthest point of a complex from its anchor (along: the
		// end fade; across: the southern fade on the far side of the massif) is less than a quarter of the cell away.
		double along = (L_MAX + FADE_END1 + EDGE) * k;
		double across = (BASIN_CENTER + WS + FADE_SOUTH1 + EDGE + BOW + RIDGE_WARP) * k;
		if (Math.hypot(along, across) >= 0.25 * spacing) {
			throw new IllegalStateException("Tatra reach exceeds a quarter of the grid cell");
		}
	}

	double spacing() {
		return spacing;
	}

	/** Largest distance from the center of a massif (the middle of its main ridge) at which it changes the terrain. */
	double maxReach() {
		double along = (L_MAX + FADE_END1 + EDGE) * k;
		double across = (WN + TROUGH + FOOTHILLS + SLOPE + BASIN + FADE_NORTH + EDGE + BOW + RIDGE_WARP) * k;
		return Math.hypot(along, Math.max(across, (WS + FADE_SOUTH1 + EDGE + BOW + RIDGE_WARP) * k));
	}

	// ------------------------------------------------------------------ placement

	/** The massif of the grid cell, or null. */
	Massif cell(long i, long j) {
		Massif m = cells.get(i, j);
		return m == NONE ? null : m;
	}

	private Massif build(long i, long j) {
		if (place.unit(i, j, 1) >= P) {
			return NONE;
		}
		double e = Math.max(50.0, 2_000 * zs);
		// The anchors: the points of a GRID × GRID lattice over the middle half of the cell (jittered by the cell seed) deep
		// in the mountain belt, tried in an order drawn from the cell seed (the belt is narrow: random tries mostly missed it).
		List<double[]> anchors = new ArrayList<>();
		double jx = place.unit(i, j, 101);
		double jz = place.unit(i, j, 102);
		for (int a = 0; a < GRID; a++) {
			for (int b = 0; b < GRID; b++) {
				double ax = (i + 0.25 + 0.5 * (a + jx) / GRID) * spacing;
				double az = (j + 0.25 + 0.5 * (b + jz) / GRID) * spacing;
				if (model.mountainField(ax, az) >= MIN_FIELD && model.blend(ax, az).weight(LandscapeType.BESKIDS) >= MIN_WEIGHT) {
					anchors.add(new double[] {ax, az, place.unit(i * GRID + a, j * GRID + b, 103)});
				}
			}
		}
		anchors.sort((p, q) -> Double.compare(p[2], q[2]));
		for (int tr = 0; tr < Math.min(TRIES, anchors.size()); tr++) {
			double ax = anchors.get(tr)[0];
			double az = anchors.get(tr)[1];
			double gx = model.mountainRaw(ax + e, az) - model.mountainRaw(ax - e, az);
			double gz = model.mountainRaw(ax, az + e) - model.mountainRaw(ax, az - e);
			double len = Math.hypot(gx, gz);
			if (!(len > 1e-12)) {
				continue;
			}
			double half = (L_MIN + (L_MAX - L_MIN) * place.unit(i, j, 3)) * k;
			// The range continues along the massif.
			double off = ALONG_CHECK * half;
			if (model.mountainField(ax - off * gz / len, az + off * gx / len) < ALONG_FIELD
					|| model.mountainField(ax + off * gz / len, az - off * gx / len) < ALONG_FIELD) {
				continue;
			}
			// The massif on one flank of the belt, Podhale in it: the side drawn from the cell seed first, then the other.
			double first = place.unit(i, j, 2) < 0.5 ? 1 : -1;
			for (double side : new double[] {first, -first}) {
				Massif m = candidate(i, j, ax, az, side * gx / len, side * gz / len, half);
				if (m != null) {
					return m;
				}
			}
		}
		return NONE;
	}

	/** The massif with the anchor (ax, az) and the direction (cos, sin) towards Podhale, or null when it does not fit. */
	private Massif candidate(long i, long j, double ax, double az, double cos, double sin, double half) {
		double cx = ax - BASIN_CENTER * k * cos;
		double cz = az - BASIN_CENTER * k * sin;
		double wn = WN * k * (0.94 + 0.12 * place.unit(i, j, 4));
		double ws = WS * k * (0.94 + 0.12 * place.unit(i, j, 5));
		double summit = SUMMIT_LO + (SUMMIT_HI - SUMMIT_LO) * place.unit(i, j, 6);
		double west = place.unit(i, j, 7) < 0.5 ? 1 : -1;
		double d = VALLEY_SPACING * k * (0.88 + 0.24 * place.unit(i, j, 8));
		double peakT = 0.22 + 0.3 * place.unit(i, j, 11);
		double giewontT = -0.16 + 0.1 * place.unit(i, j, 12);
		double along = half + (FADE_END1 + EDGE) * k;
		double north = wn + (TROUGH + FOOTHILLS + SLOPE + BASIN + FADE_NORTH + EDGE + BOW + RIDGE_WARP) * k;
		double south = ws + (FADE_SOUTH1 + EDGE + BOW + RIDGE_WARP) * k;
		Massif m = new Massif(i, j, ax, az, cx, cz, cos, sin, half, wn, ws, summit, west, d, place.unit(i, j, 9),
				place.unit(i, j, 10), peakT, giewontT, Math.hypot(along, Math.max(north, south)), false);
		// The massif itself (main ridge to its feet) keeps clear of the shore; the trough, Podhale and the fades may come
		// nearer and then fade out towards it (column: the coast fade).
		if (minCoast(m, half, wn, ws) < (COAST_FADE1 + 500) * k) {
			return null;
		}
		if (minCoast(m, along, north, south) < (COAST_FADE1 + 10_000) * k) {
			m = new Massif(i, j, ax, az, cx, cz, cos, sin, half, wn, ws, summit, west, d, place.unit(i, j, 9),
					place.unit(i, j, 10), peakT, giewontT, m.reach(), true);
		}
		return clearOfGreatMassifs(m, along, north, south) ? m : null;
	}

	/**
	 * Smallest coast distance on a 5 × 5 grid over the rectangle of half length {@code along} from {@code north} on the
	 * Podhale side to {@code south} on the other (the outline, the halves and the center).
	 */
	private double minCoast(Massif m, double along, double north, double south) {
		double min = Double.POSITIVE_INFINITY;
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				double fu = a < 0 ? a * south / 2 : a * north / 2;
				double fv = b * along / 2;
				min = Math.min(min, model.coastDistance(m.worldX(fu, fv), m.worldZ(fu, fv)));
			}
		}
		return min;
	}

	/** Whether no large Beskid massif (M2-8) reaches into the rectangle of the complex (its reach added on every side). */
	private boolean clearOfGreatMassifs(Massif m, double along, double north, double south) {
		double gr = model.greatMassifReach();
		double r = m.reach() + gr;
		for (LandscapeModel.GreatMassif g : model.greatMassifs(m.x() - r, m.z() - r, m.x() + r, m.z() + r)) {
			double u = m.u(g.x(), g.z());
			double v = m.v(g.x(), g.z());
			if (Math.abs(v) < along + gr && u < north + gr && u > -south - gr) {
				return false;
			}
		}
		return true;
	}

	/** The massif whose reach can contain the point, or null. */
	Massif at(double x, double z) {
		Massif m = cell((long) Math.floor(x / spacing), (long) Math.floor(z / spacing));
		if (m == null) {
			return null;
		}
		double dx = x - m.x();
		double dz = z - m.z();
		return dx * dx + dz * dz < m.reach() * m.reach() ? m : null;
	}

	/** Massifs with the center in the box. */
	List<Massif> massifs(double minX, double minZ, double maxX, double maxZ) {
		List<Massif> out = new ArrayList<>();
		long i0 = (long) Math.floor((minX - maxReach()) / spacing);
		long i1 = (long) Math.floor((maxX + maxReach()) / spacing);
		long j0 = (long) Math.floor((minZ - maxReach()) / spacing);
		long j1 = (long) Math.floor((maxZ + maxReach()) / spacing);
		for (long i = i0; i <= i1; i++) {
			for (long j = j0; j <= j1; j++) {
				Massif m = cell(i, j);
				if (m != null && m.x() >= minX && m.x() <= maxX && m.z() >= minZ && m.z() <= maxZ) {
					out.add(m);
				}
			}
		}
		return out;
	}

	/** Whether the reach of any complex can intersect the box. */
	boolean near(double minX, double minZ, double maxX, double maxZ) {
		long i0 = (long) Math.floor(minX / spacing);
		long i1 = (long) Math.floor(maxX / spacing);
		long j0 = (long) Math.floor(minZ / spacing);
		long j1 = (long) Math.floor(maxZ / spacing);
		for (long i = i0; i <= i1; i++) {
			for (long j = j0; j <= j1; j++) {
				Massif m = cell(i, j);
				if (m != null && m.x() + m.reach() > minX && m.x() - m.reach() < maxX && m.z() + m.reach() > minZ
						&& m.z() - m.reach() < maxZ) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ relief

	/**
	 * Smooth staircase on [0, 1] with three treads (valley steps); applied twice for flatter treads. Clamped at 0: near
	 * x = 0 the rounding gives tiny negative values, and a fractional power of them is NaN.
	 */
	static double stair(double x) {
		double s = x - Math.sin(6 * Math.PI * x) / (6 * Math.PI);
		return Math.max(0.0, s - Math.sin(6 * Math.PI * s) / (6 * Math.PI));
	}

	/** Bump 1 at c, 0 from |x − c| ≥ half (smooth). */
	private static double bump(double x, double c, double half) {
		double r = (x - c) / half;
		if (r <= -1 || r >= 1) {
			return 0;
		}
		double a = 1 - r * r;
		return a * a;
	}

	/** Part weights along the massif: western, high, belianske for the along coordinate t ∈ [−1, 1] (west to east). */
	private static double highShare(double t) {
		return Noise.smoothstep(-0.07, 0.03, t);
	}

	private static double belianskeShare(double t) {
		return Noise.smoothstep(0.74, 0.84, t);
	}

	/** Crest of the main ridge (m) at t, before the passes. */
	private double crest(Massif m, double t, double x, double z) {
		double nw = relief.fbm(x + 1_111, z - 2_222, 0.3 * m.halfLength(), 2, 0.5);
		double western = 2_060 + 150 * nw;
		double nh = relief.fbm(x - 3_333, z + 4_444, 0.18 * m.halfLength(), 2, 0.5);
		double baseHigh = 2_360 + 60 * nh;
		double peak = Math.exp(-((t - m.peakT()) / 0.07) * ((t - m.peakT()) / 0.07));
		double high = baseHigh + Math.max(0, m.summit() - baseHigh) * peak;
		double bel = 2_020 + 60 * nw;
		double c = Noise.lerp(highShare(t), western, high);
		c = Noise.lerp(belianskeShare(t), c, bel);
		// Ends of the massif: the crest falls to about 1450 m.
		double end = Noise.smoothstep(t < 0 ? 0.8 : 0.88, 1.0, Math.abs(t));
		return c + (1_450 - c) * end;
	}

	/** Valley coordinate φ on one side at a point (the valley axes at integer φ). */
	private double phi(Massif m, double v, double x, double z, double phase) {
		double d = m.spacing();
		return (v + 0.22 * d * relief.at(x - 777, z + 555, 1.6 * d)) / d + phase;
	}

	/** The column of the complex at (x, z), or null outside every complex. */
	Column column(double x, double z) {
		Massif m = at(x, z);
		if (m == null) {
			return null;
		}
		double k = this.k;
		double u0 = m.u(x, z);
		double v = m.v(x, z);
		double half = m.halfLength();
		double sv = v / half;
		double bow = BOW * k * (1 - Math.min(1.0, sv * sv)) + RIDGE_WARP * k * relief.at(x + 9_999, z - 9_999, 9_000 * k);
		double u = u0 - bow;
		double edge = EDGE * k * relief.at(x - 4_321, z + 1_234, 4_000 * k);
		double basinEdge = m.wn() + (TROUGH + FOOTHILLS + SLOPE + BASIN) * k;
		double wa = 1 - Noise.smoothstep(half + FADE_END0 * k, half + FADE_END1 * k, Math.abs(v) + edge);
		double wn = 1 - Noise.smoothstep(basinEdge, basinEdge + FADE_NORTH * k, u + edge);
		double ws = 1 - Noise.smoothstep(m.ws() + FADE_SOUTH0 * k, m.ws() + FADE_SOUTH1 * k, -u + edge);
		double w = wa * wn * ws;
		if (w > 0 && m.coastal()) {
			w *= Noise.smoothstep(COAST_FADE0 * k, COAST_FADE1 * k, model.coastDistance(x, z));
		}
		if (w <= 0) {
			return null;
		}
		Column c = new Column();
		c.massif = m;
		c.weight = w;
		double t = Math.clamp(m.west() * sv, -1.0, 1.0);
		double footN = 880 + 55 * relief.at(x + 2_468, z - 1_357, 12_000 * k);
		double footS = 950 + 45 * relief.at(x - 8_642, z + 7_531, 12_000 * k);
		double hills = relief.fbm(x + 6_666, z + 3_333, 2_500 * k, 2, 0.5);
		boolean north = u >= 0;
		double width = north ? m.wn() : m.ws();
		double un = Math.abs(u) / width;
		double tg = t + 0.05 * geology.at(x, z, 2_500 * k);
		double ms = 1 - Noise.smoothstep(0.86, 1.0, Math.abs(sv) + 0.03 * relief.at(x, z, 3_000 * k));
		double h;
		if (un < 1) {
			// The foot of the two sides blends across the main ridge line, so beyond the massif ends (where the massif
			// fades to its foot) the trough and the southern foot meet without a step.
			double sideN = Noise.smoothstep(-0.3, 0.3, u / m.wn());
			double foot = Noise.lerp(sideN, footS + 35 * hills, footN + 20 * hills);
			h = massifHeight(m, c, x, z, v, t, un, north, foot, ms, tg);
		} else if (north) {
			h = northProfile(m, c, x, z, u, footN, hills);
		} else {
			h = footS + (720 - footS) * Noise.smoothstep(m.ws(), m.ws() + FADE_SOUTH1 * k, -u) + 35 * hills;
			c.part = TatraPart.SOUTH_FOOT;
		}
		h += ROUGH * relief.fbm(x, z, 160 * k, 2, 0.5);
		c.height = h;
		// Types: the massif with its foot is TATRA, the Podhale from the outer edge of the trough on.
		double podhale = north ? Noise.smoothstep(m.wn() + 400 * k, m.wn() + 1_800 * k, u + 0.3 * edge) : 0;
		c.podhale = w * podhale;
		c.tatra = w - c.podhale;
		if (c.part == TatraPart.NONE) {
			c.part = north ? TatraPart.SUBTATRA_TROUGH : TatraPart.SOUTH_FOOT;
		}
		return c;
	}

	/** North of the massif: the trough, the Gubałówka-type foothills, the slope and the basin. */
	private double northProfile(Massif m, Column c, double x, double z, double u, double footN, double hills) {
		double k = this.k;
		double a = u - m.wn();
		double basin = 640 + 45 * relief.at(x + 1_111, z + 2_222, 30_000 * k) + 8 * hills;
		double top = 1_060 + 40 * hills;
		double foothills0 = TROUGH * k;
		double foothillsTop = (TROUGH + 0.45 * FOOTHILLS) * k;
		double basin0 = (TROUGH + FOOTHILLS + SLOPE) * k;
		double h;
		if (a < foothills0) {
			h = footN + 20 * hills;
			c.part = TatraPart.SUBTATRA_TROUGH;
		} else if (a < foothillsTop) {
			h = footN + 20 * hills + (top - footN - 20 * hills) * Noise.smoothstep(foothills0, foothillsTop, a);
			c.part = TatraPart.GUBALOWKA_FOOTHILLS;
		} else if (a < basin0) {
			h = top + (basin - top) * Noise.smoothstep(foothillsTop, basin0, a);
			c.part = a < (TROUGH + FOOTHILLS) * k ? TatraPart.GUBALOWKA_FOOTHILLS : TatraPart.PODHALE_BASIN;
		} else {
			h = basin;
			c.part = TatraPart.PODHALE_BASIN;
		}
		return h;
	}

	/** The massif between the main ridge (un = 0) and its foot (un = 1) on one side. */
	private double massifHeight(Massif m, Column c, double x, double z, double v, double t, double un, boolean north,
			double foot, double ms, double tg) {
		double d = m.spacing();
		double high = highShare(t);
		double crest = crest(m, t, x, z);
		double peak = Math.exp(-((t - m.peakT()) / 0.07) * ((t - m.peakT()) / 0.07));
		// Passes: where the valley heads of both sides meet at the crest.
		double phiN = phi(m, v, x, z, m.phaseN());
		double phiS = phi(m, v, x, z, m.phaseS());
		double qN0 = phiN - Math.floor(phiN + 0.5);
		double qS0 = phiS - Math.floor(phiS + 0.5);
		double gN = Math.exp(-(qN0 / 0.2) * (qN0 / 0.2));
		double gS = Math.exp(-(qS0 / 0.2) * (qS0 / 0.2));
		double passDepth = Noise.lerp(high, 330, 190);
		double lowering = passDepth * gN * gS * (1 - peak);
		double near = 1 - Noise.smoothstep(0, 0.3, un);
		double crestEff = crest - lowering * near;
		c.pass = un < 0.04 && gN * gS > 0.5;
		// Valleys of this side.
		double phi = north ? phiN : phiS;
		long j = (long) Math.floor(phi + 0.5);
		double q = (phi - j) * d;
		int sideSalt = north ? 0 : 1;
		long vi = m.i() * 4_096 + j;
		long vj = m.j() * 2 + sideSalt;
		// The cirque of the valley head: the share of valleys with one grows from the Western to the High Tatras, so its
		// strength fades over the draw (not a switch, which would cut a step into the valley where the share changes).
		double cirque = Noise.smoothstep(-0.04, 0.04, Noise.lerp(high, 0.75, 0.9) - place.unit(vi, vj, 21));
		double widthF = 0.8 + 0.4 * place.unit(vi, vj, 22);
		double uc = Noise.lerp(high, 0.15, 0.11) + 0.03 * (place.unit(vi, vj, 23) - 0.5);
		double headDrop = Noise.lerp(high, 330, 450) + 70 * place.unit(vi, vj, 24);
		double bowlDepth = 25 + 20 * place.unit(vi, vj, 25);
		double cf = crestEff - headDrop;
		double mouth = foot;
		double fl;
		if (un < uc) {
			fl = cf + (crestEff - cf) * Math.pow((uc - un) / uc, 1.6);
		} else {
			double xs = Math.clamp(1 - (un - uc) / (1 - uc), 0.0, 1.0);
			fl = mouth + (cf - mouth) * Math.pow(stair(xs), 1.35);
		}
		double unC = uc + 0.055;
		double fw = d * (0.045 + 0.06 * un) * widthF;
		if (north) {
			// Narrower gorges where the valleys cross the regle.
			fw *= 1 - 0.45 * bump(un, 0.84, 0.12);
		}
		double fc = 0.17 * d;
		double rC = Double.POSITIVE_INFINITY;
		if (cirque > 0) {
			fw += (fc - fw) * cirque * Math.exp(-((un - unC) / 0.08) * ((un - unC) / 0.08));
			rC = Math.hypot(q / fc, (un - unC) / 0.075);
			if (rC < 1) {
				double b = 1 - rC * rC;
				fl -= cirque * bowlDepth * b * b;
			}
		}
		double hd = d / 2;
		double p = (Math.abs(q) - fw) / Math.max(1e-6, hd - fw);
		p = Math.min(1.0, 0.5 * (p + Math.sqrt(p * p + 0.0025)));
		double wall = 1 - (1 - p) * (1 - p);
		// Side ridges: high for a while, then down to the foot; a little noise between the crest and the foot.
		double rs = Math.pow(1 - Math.pow(Math.min(un, 1.0), 2.5), 0.8);
		double rg = foot + (crestEff - foot) * rs + 200 * un * (1 - un) * relief.at(x + 3_210, z - 6_540, 1.2 * d);
		double giewont = 0;
		if (north) {
			// The regle: a limestone ridge parallel to the main ridge near the foot, and a Giewont-type ridge.
			double wR = Noise.smoothstep(0.58, 0.8, un);
			double regleTop = 1_360 + 110 * relief.at(x - 1_357, z + 2_468, 0.25 * m.halfLength());
			giewont = Math.exp(-((t - m.giewontT()) / 0.05) * ((t - m.giewontT()) / 0.05));
			double regle = foot + (regleTop - foot) * bump(un, 0.84, 0.15) + (1_885 - regleTop) * giewont * bump(un, 0.8, 0.1);
			rg = rg * (1 - wR) + regle * wR;
		}
		rg = Math.max(rg, fl);
		double h = fl + (rg - fl) * wall;
		if (giewont > 0.01) {
			// The Giewont-type ridge stands whole above the valleys of the regle (they pass beside it).
			h = Math.max(h, foot + (1_885 - foot) * giewont * bump(un, 0.8, 0.1));
		}
		// Hanging valleys on part of the side ridges of the High Tatras, in the upper half.
		double phiR = phi + 0.5;
		long jr = (long) Math.floor(phiR + 0.5);
		double q2 = (phiR - jr) * d;
		long ri = m.i() * 4_096 + jr;
		// The depth fades in above the valley heads (the same bound for every valley, uc ≤ 0.165) and out towards the
		// middle, so the hanging valley meets the neighbouring slopes without a step.
		if (high > 0.5 && place.unit(ri, vj, 26) < 0.45 && un > HANG_IN && un < 0.5) {
			double hangDepth = (150 + 70 * place.unit(ri, vj, 27)) * Noise.smoothstep(HANG_IN, HANG_IN + 0.1, un)
					* (1 - Noise.smoothstep(0.3, 0.5, un));
			double hf = rg - hangDepth;
			double fh = 0.035 * d;
			double p2 = (Math.abs(q2) - fh) / (0.2 * d - fh);
			if (p2 < 1 && hangDepth > 1) {
				p2 = Math.min(1.0, 0.5 * (p2 + Math.sqrt(p2 * p2 + 0.0025)));
				double cut = hf + (rg - hf) * (1 - (1 - p2) * (1 - p2));
				if (cut < h) {
					h = cut;
					c.hanging = p2 < 0.15 && hangDepth > 60;
				}
			}
		}
		c.profile = wall * ms + 0.5 * (1 - ms);
		// Landforms of the massif.
		if (ms > 0.5) {
			if (cirque > 0.5 && rC < 0.9) {
				c.landforms |= Landform.CIRQUE.bit();
			} else if (Math.abs(q) < fw * 1.15 && un > unC + 0.08 && un < 0.98) {
				c.landforms |= Landform.U_VALLEY.bit();
			}
		}
		double ridge = Noise.smoothstep(0.6, 0.85, wall) * (1 - Noise.smoothstep(0.9, 1.0, un));
		c.core = c.weight * ms * Math.max(1 - Noise.smoothstep(0.45, 0.62, un), ridge);
		c.massifShare = c.weight * ms * (1 - Noise.smoothstep(0.92, 1.02, un));
		// Parts and rock (boundaries warped by noise, Z10).
		boolean rocky = ms * (1 - Noise.smoothstep(0.96, 1.0, un + 0.03 * geology.at(x + 50, z, 800 * this.k))) > 0.5;
		if (north && un > 0.66 + 0.05 * geology.at(x, z - 77, 1_500 * this.k)) {
			c.part = TatraPart.REGLE;
			if (rocky) {
				boolean dolomite = giewont < 0.3 && (geology.at(x + 900, z + 300, 1_200 * this.k) > 0.1 || h < 1_150);
				c.rock = dolomite ? Substrate.DOLOMITE : Substrate.LIMESTONE;
			}
		} else if (tg < -0.02) {
			c.part = TatraPart.WESTERN_TATRA;
			if (rocky) {
				boolean cover = north && un > 0.25 && un < 0.62 && geology.fbm(x + 5_000, z, 1_800 * this.k, 2, 0.5) > 0.3;
				c.rock = cover ? Substrate.LIMESTONE : Substrate.CRYSTALLINE;
			}
		} else if (tg < 0.79) {
			c.part = TatraPart.HIGH_TATRA;
			if (rocky) {
				c.rock = Substrate.GRANITE;
			}
		} else {
			c.part = TatraPart.BELIANSKE_TATRA;
			if (rocky) {
				c.rock = Substrate.LIMESTONE;
			}
		}
		if (ms < 0.5) {
			c.part = north ? TatraPart.SUBTATRA_TROUGH : TatraPart.SOUTH_FOOT;
		}
		return foot + (h - foot) * ms;
	}
}
