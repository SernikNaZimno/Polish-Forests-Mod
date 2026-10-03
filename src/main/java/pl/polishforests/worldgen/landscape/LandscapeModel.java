package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;

/**
 * Procedural landscape model of Poland at 1:1 scale (1 unit = 1 metre).
 *
 * <p>Layers (docs/01-architektura.md, section 3.2):
 * <ul>
 * <li>L0 – zone fields: mountain ranges, extent of glaciation, lowland base level;</li>
 * <li>L1 – macroregions: Voronoi cells with warped boundaries, with a landscape type,
 * adjacency rules and their own coordinate frame along the mountain range;</li>
 * <li>L2 – relief of the landscape type, blended with smooth softmax weights at cell boundaries;</li>
 * <li>L3 – waters: tunnel valleys with chains of lakes, kettle ponds, valleys of large rivers, each with its own water level.</li>
 * </ul>
 *
 * <p>Every point is computed locally and deterministically from the seed, so the world is infinite
 * and generation can run on many threads. The class does not depend on Minecraft.
 */
public final class LandscapeModel {
	/** Mean macroregion size with the slider at 1.0; Poland has 59 macroregions on 312,700 km². */
	public static final double BASE_REGION_SIZE = 64_000.0;
	/** Width of the transition belt between macroregions (about 80% of the weight change). */
	private static final double BLEND_WIDTH = 5_000.0;
	private static final double KETTLE_BANK = 45.0;
	/** Mean of the gully profile {@code p[2]} from {@link #flyschParts} (measured on the noise); flysch convexity is measured from it. */
	private static final double GULLY_MEAN = 0.52;
	/** Number of bins of the noise CDF table (sandiness quantiles). */
	private static final int CDF_BINS = 512;
	/** CDF of the values of {@link Noise#sample} on [-1, 1], computed once from a fixed seed. */
	private static final double[] NOISE_CDF = noiseCdf();

	private final LandscapeScale scale;
	private final double regionScale;
	private final double regionSize;
	private final double tau;
	/** Scale multipliers: zones, medium landforms, local landforms, mountain spacing, channel width. */
	private final double zs;
	private final double meso;
	private final double local;
	private final double mspace;
	private final double chan;
	private final double tunnelBank;
	private final double tunnelSill;
	private final double tunnelCell;

	private final Noise zoneMountain;
	private final Noise zoneMountainMask;
	private final Noise zoneGlacial;
	private final Noise warp;
	private final Noise regionWarp;
	private final Noise regionJitter;
	private final Noise lowlandBase;
	private final Noise relief;
	private final Noise dunes;
	private final Noise moraine;
	private final Noise tunnel;
	private final Noise kettle;
	private final Noise mountain;
	private final Noise zoneSea;
	private final Noise coast;
	/** Sandiness of the deposit (M2, habitats). */
	private final Noise habitatSandiness;
	private final RiverNetwork rivers;
	/** Cached grids (M2, S3): smoothed terrain with slope and aspect, and the regional fields O, P. */
	private final CoarseTerrainField coarse;
	/** Highest terrain within 3 km·mspace (a large massif in the altitudinal belts, E12). */
	private final PeakField peaks;
	private final RegionalField regional;

	private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> lakeLevels = new ConcurrentHashMap<>();
	/** Kettle state: {@link #KETTLE_NONE}, {@link #KETTLE_MINEROTROPHIC} or {@link #KETTLE_OMBROTROPHIC}. */
	private final ConcurrentHashMap<Long, Byte> kettleExistence = new ConcurrentHashMap<>();
	private static final byte KETTLE_NONE = 0;
	private static final byte KETTLE_MINEROTROPHIC = 1;
	private static final byte KETTLE_OMBROTROPHIC = 2;

	/**
	 * Large Beskid massifs (Babia Gora type, M2-8; docs/m2/poprawka-geometrii.md, step K2): rare points on a grid with
	 * the side {@link #greatMassifSpacing}, one candidate per grid cell. Noise "mountain.great" (a new seed, never to be
	 * changed; its salts {@code unit(i, j, 1..4)} belong to the world).
	 */
	private final Noise greatMassif;
	private final double greatMassifSpacing;
	/** Semi-axes of the massif ellipse along and across the range (m). */
	private final double greatMassifRa;
	private final double greatMassifRc;
	/** Reach of a massif: G = 0 farther than sqrt(GM_REACH2) · Ra from the center. */
	private final double greatMassifReach;
	/** Rings of grid cells checked by the thinning: ceil(GM_SEPARATION · Ra / spacing). */
	private final int greatMassifRings;
	/** Accepted candidate of a grid cell after thinning ({@link #greatMassifCell}); a pure function of the cell. */
	private final ConcurrentHashMap<Long, double[]> greatMassifCells = new ConcurrentHashMap<>();
	/** Candidate of a grid cell before thinning ({@link #greatMassifEligible}); a pure function of the cell. */
	private final ConcurrentHashMap<Long, Boolean> greatMassifEligibleCache = new ConcurrentHashMap<>();
	private static final double[] GM_NONE = new double[0];
	/** A candidate is drawn with this probability and accepted only deep in the range (mountain field, Beskids weight). */
	static final double GM_P = 0.45;
	static final double GM_MIN_FIELD = 0.90;
	static final double GM_MIN_WEIGHT = 0.98;
	/** Thinning: a candidate closer than GM_SEPARATION · Ra to an eligible candidate with a smaller draw is dropped. */
	static final double GM_SEPARATION = 2.5;
	/** Range of the target summit (m a.s.l.), measured; 1550 (Pilsko type) is the tuning option S2. */
	static final double GM_SUMMIT_LO = 1_625;
	static final double GM_SUMMIT_HI = 1_735;
	/** Share of the lift that goes to the floor of the flysch relief (the rest raises the relief). */
	static final double GM_FLOOR_SHARE = 0.35;
	/** Massif core (G &gt; GM_CORE): no springs and no flow across it (river rules in {@code RiverNetwork}). */
	static final double GM_CORE = 0.3;
	/** The flysch valleys are filled near the center: smoothstep(GM_FILL0, GM_FILL1, G). */
	static final double GM_FILL0 = 0.3;
	static final double GM_FILL1 = 0.9;
	/**
	 * Soft ceiling of the summit dome (review of the design): within GM_CAP m below the target summit the height
	 * saturates towards the target instead of the global tanh saturation from 1500 m, which flattened the top into a
	 * plateau. Full from G ≥ GM_CAP_G. The envelope target is T = target + GM_CAP. 35 m instead of the 40 m of the
	 * design (S3 tuning in step K2): with 40 m one gameplay massif had 0.37 of its belt 1390–1650 m above 1650 m.
	 */
	static final double GM_CAP = 35;
	static final double GM_CAP_G = 0.3;
	/** Massif strength G = 0 for d² ≥ 1 after the outline noise d² · (1 + 0.3 · fbm) with fbm ≥ −1. */
	private static final double GM_REACH2 = 1.0 / 0.7;

	/** Large Beskid massif: center and target summit (m a.s.l.). */
	public record GreatMassif(double x, double z, double targetSummit) {
	}

	/**
	 * Macroregion cell. {@code cos}/{@code sin} describe the direction across the mountain range;
	 * mountain relief is elongated perpendicular to it, i.e. along the range.
	 */
	public record Cell(long cx, long cz, LandscapeType type, double centerX, double centerZ, double cos, double sin) {
	}

	/** Model at realistic scale. */
	public LandscapeModel(long seed, double regionScale) {
		this(seed, LandscapeScale.REALISTIC, regionScale);
	}

	/**
	 * @param scale       horizontal landscape scale
	 * @param regionScale region size multiplier from the slider in the world options
	 */
	public LandscapeModel(long seed, LandscapeScale scale, double regionScale) {
		if (!(regionScale > 0.01 && regionScale <= 4.0)) {
			throw new IllegalArgumentException("regionScale out of range (0.01, 4]: " + regionScale);
		}
		this.scale = scale;
		this.regionScale = regionScale;
		this.regionSize = scale.regionSize() * regionScale;
		this.tau = Math.min(BLEND_WIDTH, 0.2 * regionSize) / 2.2;
		this.zs = regionSize / BASE_REGION_SIZE;
		this.meso = scale.meso();
		this.local = scale.local();
		this.mspace = scale.mountainSpacing();
		this.chan = scale.channel();
		this.tunnelBank = Math.max(70.0, 140.0 * local);
		this.tunnelSill = 250.0 * local;
		this.tunnelCell = 4_500.0 * local;
		Noise root = new Noise(seed);
		this.zoneMountain = root.derive("zone.mountain");
		this.zoneMountainMask = root.derive("zone.mountain.mask");
		this.zoneGlacial = root.derive("zone.glacial");
		this.warp = root.derive("warp");
		this.regionWarp = root.derive("region.warp");
		this.regionJitter = root.derive("region.jitter");
		this.lowlandBase = root.derive("lowland.base");
		this.relief = root.derive("relief");
		this.dunes = root.derive("dunes");
		this.moraine = root.derive("moraine");
		this.tunnel = root.derive("tunnel");
		this.kettle = root.derive("kettle");
		this.mountain = root.derive("mountain");
		this.zoneSea = root.derive("zone.sea");
		this.coast = root.derive("coast");
		// Large massifs (M2-8): a new seed, so the rest of the terrain does not change.
		this.greatMassif = root.derive("mountain.great");
		this.greatMassifSpacing = scale == LandscapeScale.REALISTIC ? 60_000 : 4_000;
		this.greatMassifRa = 8_000 * mspace;
		this.greatMassifRc = 4_000 * mspace;
		this.greatMassifRings = (int) Math.ceil(GM_SEPARATION * greatMassifRa / greatMassifSpacing);
		this.greatMassifReach = Math.sqrt(GM_REACH2) * greatMassifRa;
		// New M2 noises only via "habitat.*": derived seeds are independent, so the terrain does not change.
		this.habitatSandiness = root.derive("habitat.piask");
		this.rivers = new RiverNetwork(this, root.derive("rivers"), scale);
		this.coarse = new CoarseTerrainField(this, 32.0 * local);
		this.peaks = new PeakField(this, 3_000 * mspace, 62.5 * mspace, 4);
		this.regional = new RegionalField(this, root.derive("habitat.prowincje"), zs);
	}

	public double regionScale() {
		return regionScale;
	}

	public LandscapeScale scale() {
		return scale;
	}

	/** Mean macroregion size in metres. */
	public double regionSize() {
		return regionSize;
	}

	/** Smoothed terrain grid (tests). */
	CoarseTerrainField coarseTerrain() {
		return coarse;
	}

	/** Regional field grid (tests). */
	RegionalField regional() {
		return regional;
	}

	// ------------------------------------------------------------------ L0: zones

	/** Mountain range noise: the zero line is the range axis (without masks; P reach tests in {@link RegionalField}). */
	double mountainRaw(double x, double z) {
		double s = zs;
		double wx = x + 60_000 * s * warp.at(x, z, 250_000 * s);
		double wz = z + 60_000 * s * warp.at(x + 7_777, z - 3_333, 250_000 * s);
		return zoneMountain.at(wx, wz, 1_100_000 * s);
	}

	/**
	 * Mountain range field in [0, 1]: 1 on the range axis. Ranges are the zero lines of a noise with a very
	 * long wavelength, cut by a mask into finite chains.
	 */
	public double mountainField(double x, double z) {
		double ridge = 1.0 - Math.abs(mountainRaw(x, z));
		ridge = ridge * ridge * ridge;
		double mask = Noise.smoothstep(-0.25, 0.25, zoneMountainMask.at(x, z, 900_000 * zs));
		// Mountains keep away from the sea (in Poland about 500 km from the coast).
		double inland = Noise.smoothstep(0.10, 0.32, seaField(x, z));
		return ridge * mask * inland;
	}

	/**
	 * Linear range field in [0, 1]: like {@link #mountainField}, but without the cube, so it falls off more slowly and reaches
	 * further from the range axis. The basis of mountain influence P in {@link RegionalField}.
	 */
	double mountainLinear(double x, double z) {
		double ridge = 1.0 - Math.abs(mountainRaw(x, z));
		double mask = Noise.smoothstep(-0.25, 0.25, zoneMountainMask.at(x, z, 900_000 * zs));
		double inland = Noise.smoothstep(0.10, 0.32, seaField(x, z));
		return ridge * mask * inland;
	}

	/** Threshold of the land-sea field; below it is sea (about 25% of the world area). */
	private static final double SEA_THRESHOLD = -0.22;

	/** Land-sea field: negative values are sea. A very long wavelength gives smooth, extensive coasts. */
	public double seaField(double x, double z) {
		// Large bays and peninsulas from fBm, with gentle shoreline arcs every few tens of km and small
		// undulations every few km on top (shoreline offsets of about 3 km and 0.7 km).
		return zoneSea.fbm(x, z, 900_000 * zs, 3, 0.45) - SEA_THRESHOLD
				+ 0.006 * zoneSea.at(x + 7_777, z - 3_333, 45_000 * zs)
				+ 0.0015 * zoneSea.at(x - 1_234, z + 5_678, 9_000 * zs);
	}

	/** Approximate distance from the shoreline in metres: positive on land, negative at sea. */
	public double coastDistance(double x, double z) {
		double c = seaField(x, z);
		double e = Math.max(20.0, 400.0 * zs);
		double gx = (seaField(x + e, z) - seaField(x - e, z)) / (2 * e);
		double gz = (seaField(x, z + e) - seaField(x, z - e)) / (2 * e);
		double g = Math.sqrt(gx * gx + gz * gz);
		return c / Math.max(g, 1e-12);
	}

	/** Beach width in metres. */
	private double beachWidth() {
		return 60.0 * local;
	}

	/**
	 * Coast shape: sea floor, beach, foredune and coastal dunes on a low shore, a cliff where
	 * a plateau reaches the sea, and in places a lagoon (coastal lake) behind a spit.
	 *
	 * @param h terrain height from the landscape types
	 * @param d distance from the shoreline (positive on land)
	 */
	private double shapeCoast(double h, double d, double x, double z) {
		double band = 25_000 * meso;
		if (d >= band) {
			return h;
		}
		if (d < 0) {
			return -seaDepth(-d);
		}
		// The terrain drops towards the sea, but part of the relief remains so that cliffs can form.
		double hl = h * (0.12 + 0.88 * Noise.smoothstep(0, band, d));
		double beach = beachWidth();
		double shore = 2.0 * Noise.smoothstep(0, beach, d);
		double cliffLimit = shore + 2.5 * Math.max(0, d - beach);
		double result = Math.max(Math.min(hl, cliffLimit), shore);
		// Low shore: foredune and dunes behind it. A high shore (cliff) has no dunes.
		double low = 1 - Noise.smoothstep(6, 20, hl);
		if (low > 0) {
			double duneWidth = 220 * local;
			double u = (d - beach) / duneWidth;
			if (u > 0 && u < 1) {
				double bump = Math.sin(Math.PI * u);
				double height = 6 + 14 * (0.5 + 0.5 * coast.at(x, z, 3_000 * meso));
				result = Math.max(result, shore + height * bump * low);
			}
			// Lagoon behind a spit: a shallow lake at sea level, separated by a dune.
			double lagoon = Noise.smoothstep(0.25, 0.5, coast.at(x + 999, z, 60_000 * meso)) * low;
			if (lagoon > 0) {
				double start = beach + duneWidth + 80 * local;
				// Width and depth decrease together with the lagoon field, so the lagoon narrows towards its ends
				// instead of ending in a straight line; the landward shore is irregular (bays, peninsulas).
				double width = (1_500 + 1_500 * (0.5 + 0.5 * coast.at(x, z, 20_000 * meso))) * meso * lagoon;
				double ragged = 0.18 * width * coast.fbm(x - 555, z + 777, 2_500 * meso, 2, 0.5);
				double v = (d - start + ragged) / Math.max(1e-6, width);
				if (v > 0 && v < 1) {
					double bowl = Noise.smoothstep(0, 0.15, v) * (1 - Noise.smoothstep(0.7, 1, v));
					double depth = (1 + 5 * lagoon) * bowl;
					result = Math.min(result, Noise.lerp(bowl, result, -depth));
				}
			}
		}
		return result;
	}

	/** Sea depth in metres at distance {@code off} from the shore (Baltic: shallow shelf). */
	private double seaDepth(double off) {
		double s = meso;
		double depth = 22 * (1 - Math.exp(-off / (2_500 * s))) + 45 * Noise.smoothstep(12_000 * s, 70_000 * s, off);
		// Bars: underwater sand ridges near the shore.
		double bars = off < 600 * s ? 0.8 * Math.sin(off / (90 * s) * Math.PI) * (1 - off / (600 * s)) : 0;
		return Math.max(0.2, depth - bars);
	}

	/** Glaciation field: positive values are the young-glacial zone. Mountains push it outwards. */
	public double glacialField(double x, double z) {
		// Near the sea young-glacial relief prevails, as in Pomerania and Masuria.
		double nearSea = 1 - Noise.smoothstep(0.0, 0.35, seaField(x, z));
		return zoneGlacial.fbm(x, z, 500_000 * zs, 2, 0.5) - 1.4 * mountainField(x, z) + 0.05 + 0.8 * nearSea;
	}

	/** Regional lowland base level in metres a.s.l. (about 70–190 m), varying very gently. */
	public double lowlandBaseline(double x, double z) {
		return 130.0 + 55.0 * lowlandBase.fbm(x, z, 140_000 * meso, 2, 0.5);
	}

	// ------------------------------------------------------------------ L1: macroregions

	/** Landscape type of a macroregion cell (after applying the adjacency rules). */
	public LandscapeType regionType(long cx, long cz) {
		return cell(cx, cz).type();
	}

	public Cell cell(long cx, long cz) {
		long key = Noise.key(cx, cz, 1);
		Cell cached = cells.get(key);
		if (cached != null) {
			return cached;
		}
		LandscapeType type = rawRegionType(cx, cz);
		if (type == LandscapeType.BESKIDS) {
			// Rule: the Beskids never border a lowland directly; foothills lie in between.
			outer:
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && rawRegionType(cx + dx, cz + dz).isLowland()) {
						type = LandscapeType.FOOTHILLS;
						break outer;
					}
				}
			}
		}
		double px = regionCenterX(cx, cz);
		double pz = regionCenterZ(cx, cz);
		// Direction across the range: gradient of the raw mountain field (does not change sign on the range axis).
		double e = Math.max(50.0, 2_000 * zs);
		double gx = mountainRaw(px + e, pz) - mountainRaw(px - e, pz);
		double gz = mountainRaw(px, pz + e) - mountainRaw(px, pz - e);
		double len = Math.sqrt(gx * gx + gz * gz);
		double cos = len > 1e-12 ? gx / len : 1;
		double sin = len > 1e-12 ? gz / len : 0;
		Cell c = new Cell(cx, cz, type, px, pz, cos, sin);
		if (cells.size() > 200_000) {
			cells.clear();
		}
		cells.put(key, c);
		return c;
	}

	private LandscapeType rawRegionType(long cx, long cz) {
		double px = regionCenterX(cx, cz);
		double pz = regionCenterZ(cx, cz);
		double m = mountainField(px, pz);
		if (m >= 0.80) {
			return LandscapeType.BESKIDS;
		}
		if (m >= 0.55) {
			return LandscapeType.FOOTHILLS;
		}
		if (glacialField(px, pz) > 0.0) {
			return regionJitter.unit(cx, cz, 3) < 0.35 ? LandscapeType.OUTWASH_PLAIN : LandscapeType.MORAINE_PLATEAU;
		}
		return regionJitter.unit(cx, cz, 4) < 0.12 ? LandscapeType.OUTWASH_PLAIN : LandscapeType.OLD_GLACIAL_PLAIN;
	}

	/** Cell centre in the (warped) lookup space. */
	double regionCenterX(long cx, long cz) {
		return (cx + 0.15 + 0.7 * regionJitter.unit(cx, cz, 1)) * regionSize;
	}

	double regionCenterZ(long cx, long cz) {
		return (cz + 0.15 + 0.7 * regionJitter.unit(cx, cz, 2)) * regionSize;
	}

	/** {@link LandscapeType#values()} once (values() copies the array on every call). */
	private static final LandscapeType[] TYPES = LandscapeType.values();

	/** Cells influencing a point and their weights (sum = 1). */
	public record Blend(Cell[] cells, double[] weights, int count, double[] typeWeights) {
		public LandscapeType dominant() {
			int best = 0;
			for (int i = 1; i < typeWeights.length; i++) {
				if (typeWeights[i] > typeWeights[best]) {
					best = i;
				}
			}
			return TYPES[best];
		}

		public double weight(LandscapeType t) {
			return typeWeights[t.ordinal()];
		}
	}

	/**
	 * Cell weights at a point. The boundaries are warped by noise and the weights are a softmax of distances,
	 * so the surface has no creases along the Voronoi bisectors.
	 */
	public Blend blend(double x, double z) {
		double a = 0.22 * regionSize;
		double lx = x + a * regionWarp.fbm(x, z, 0.7 * regionSize, 2, 0.5);
		double lz = z + a * regionWarp.fbm(x + 12_345, z - 6_789, 0.7 * regionSize, 2, 0.5);
		long gx = (long) Math.floor(lx / regionSize);
		long gz = (long) Math.floor(lz / regionSize);
		// First all 9 cells and their distances (in the result arrays), then only the cells with a significant weight
		// remain in the same arrays, in the same order (n ≤ i, so we overwrite only slots that have
		// already been read).
		Cell[] used = new Cell[9];
		double[] w = new double[9];
		double min = Double.MAX_VALUE;
		int i = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++, i++) {
				Cell c = cell(gx + dx, gz + dz);
				used[i] = c;
				double ddx = lx - c.centerX();
				double ddz = lz - c.centerZ();
				w[i] = Math.sqrt(ddx * ddx + ddz * ddz);
				min = Math.min(min, w[i]);
			}
		}
		double[] tw = new double[TYPES.length];
		int n = 0;
		double total = 0;
		for (i = 0; i < 9; i++) {
			double d = (w[i] - min) / tau;
			if (d < 9.0) {
				double wi = Math.exp(-d);
				used[n] = used[i];
				w[n] = wi;
				total += wi;
				n++;
			}
		}
		for (i = n; i < 9; i++) {
			used[i] = null;
			w[i] = 0;
		}
		for (i = 0; i < n; i++) {
			w[i] /= total;
			tw[used[i].type().ordinal()] += w[i];
		}
		return new Blend(used, w, n, tw);
	}

	/** Landscape type weights at a point (sum = 1). */
	public double[] typeWeights(double x, double z) {
		return blend(x, z).typeWeights();
	}

	// ------------------------------------------------------------------ L2: relief

	/** Ground height of a cell without waters, in metres a.s.l. */
	double cellElevation(Cell c, double x, double z) {
		return cellElevation(c, x, z, null, 0, flysch(c.type()) ? greatMassifAt(x, z) : null);
	}

	/** Whether the type has the flysch relief that a large massif lifts (FOOTHILLS, BESKIDS). */
	private static boolean flysch(LandscapeType t) {
		return t == LandscapeType.FOOTHILLS || t == LandscapeType.BESKIDS;
	}

	/**
	 * {@link #greatMassifAt} at the point when the blend has a flysch cell, else null: computed once per column for all
	 * its cells (it depends only on the point).
	 */
	private double[] greatMassifFor(Blend b, double x, double z) {
		for (int i = 0; i < b.count(); i++) {
			if (flysch(b.cells()[i].type())) {
				return greatMassifAt(x, z);
			}
		}
		return null;
	}

	/**
	 * Relief components recorded while computing the height in {@link #sample} (output context of
	 * {@link #cellElevation}). They do not affect the height; the object is local to one sample.
	 */
	private static final class ReliefParts {
		/** Sum of the short-wave components weighted by the cell weights. */
		double convexity;
		/** Dune height (from the outwash plain cell) and moraine ridge height (from the plateau cell). */
		double duneHeight;
		double moraineRidgeHeight;
		/** Massif strength (from the Beskids cell). */
		double massif;
		/** Longitudinal valley profile p[0] of the foothills and Beskids cell with the largest weight (as in {@link #describe}). */
		double foothillsWeight = -1;
		double foothillsProfile = Double.NaN;
		double beskidsWeight = -1;
		double beskidsProfile = Double.NaN;

		void flysch(LandscapeType type, double w, double profile) {
			if (type == LandscapeType.FOOTHILLS) {
				if (w > foothillsWeight) {
					foothillsWeight = w;
					foothillsProfile = profile;
				}
			} else if (w > beskidsWeight) {
				beskidsWeight = w;
				beskidsProfile = profile;
			}
		}
	}

	/**
	 * Like {@link #cellElevation(Cell, double, double)}; when {@code o} is not null, adds to it
	 * the relief components of the cell with weight {@code w}. The height is identical in both variants. {@code gm} is
	 * the large massif at the point ({@link #greatMassifAt}, null outside the reach of every massif), used by the flysch
	 * types only.
	 */
	private double cellElevation(Cell c, double x, double z, ReliefParts o, double w, double[] gm) {
		return switch (c.type()) {
			case OUTWASH_PLAIN -> lowlandBaseline(x, z) + outwashPlainRelief(x, z, o, w);
			case MORAINE_PLATEAU -> lowlandBaseline(x, z) + 22.0 + moraineRelief(x, z, o, w);
			case OLD_GLACIAL_PLAIN -> plainElevation(x, z, o, w);
			case FOOTHILLS -> foothillElevation(c, x, z, o, w, gm);
			case BESKIDS -> mountainElevation(c, x, z, o, w, gm);
			case COASTLAND, SEA -> lowlandBaseline(x, z);
		};
	}

	/** Old glacial plain: flat, with gentle undulation. */
	private double plainElevation(double x, double z, ReliefParts o, double w) {
		double fine = 2.0 * relief.fbm(x, z, 500 * local, 2, 0.5);
		if (o != null) {
			o.convexity += w * fine;
		}
		return lowlandBaseline(x, z) - 8.0 + 6.0 * relief.fbm(x, z, 3_000 * local, 3, 0.5) + fine;
	}

	/** Outwash plain: a gentle tilt from large-scale noise, fine undulation and dune fields. */
	private double outwashPlainRelief(double x, double z, ReliefParts o, double w) {
		double tilt = 12.0 * relief.at(x, z, 25_000 * meso);
		double ripple = 1.5 * relief.fbm(x + 311, z - 97, 400 * local, 2, 0.5);
		double dune = duneHeight(x, z);
		if (o != null) {
			o.convexity += w * (ripple + dune);
			o.duneHeight = dune;
		}
		return tilt + ripple + dune;
	}

	/** Dune height above the outwash plain surface (0 outside dune fields). */
	private double duneHeight(double x, double z) {
		double field = Noise.smoothstep(0.15, 0.45, dunes.at(x, z, 18_000 * meso));
		if (field <= 0) {
			return 0.0;
		}
		// Dunes elongated from west to east (prevailing westerly winds).
		double d = dunes.ridged(x / 3.0, z, 700 * local, 2, 0.45);
		return field * 22.0 * Noise.smoothstep(0.45, 0.95, d);
	}

	/** Moraine plateau: hummocks with a 300–1500 m wavelength and belts of end moraine ridges. */
	private double moraineRelief(double x, double z, ReliefParts o, double w) {
		double hills = 11.0 * relief.fbm(x, z, 1_100 * local, 4, 0.55);
		double ridge = moraineRidge(x, z);
		if (o != null) {
			o.convexity += w * (hills + ridge);
			o.moraineRidgeHeight = ridge;
		}
		return hills + ridge;
	}

	/** Height of the end moraine ridges (0 outside the moraine belts). */
	private double moraineRidge(double x, double z) {
		double belt = Noise.smoothstep(0.10, 0.40, moraine.at(x, z, 35_000 * meso));
		if (belt <= 0) {
			return 0.0;
		}
		double wx = x + 1_500 * local * moraine.at(x, z, 6_000 * local);
		double r = moraine.ridged(wx, z / 2.5, 4_200 * local, 3, 0.5);
		return belt * 95.0 * Math.pow(r, 1.6);
	}

	/**
	 * Coordinate across the range (u) and along the range (v), measured from the global origin.
	 * It is not measured from the cell centre: with small cells (gameplay scale) every cell
	 * would then sample the same patch of noise and the mountains would be systematically too low.
	 */
	private static double across(Cell c, double x, double z) {
		return x * c.cos() + z * c.sin();
	}

	private static double along(Cell c, double x, double z) {
		return -x * c.sin() + z * c.cos();
	}

	/**
	 * Valley profile: 0 on the valley axis (V cross-section), 1 on the ridge (rounded top).
	 * {@code n} is the value of the noise whose zero contour defines the valley axis.
	 */
	private static double valleyProfile(double n, double width) {
		return Math.tanh(1.3 * Math.abs(n) / width) / 0.96;
	}

	/**
	 * Flysch relief: longitudinal valleys along the range, transverse valleys dividing the ridges into
	 * summits and passes, dome modulation and fine roughness.
	 *
	 * @param p components from {@link #flyschParts}
	 */
	private static double flyschRelief(double[] p) {
		return p[0] * (0.45 + 0.55 * p[1]) * (0.8 + 0.2 * p[2]) * p[3];
	}

	/**
	 * Flysch convexity (m): the gullies' contribution to the relief relative to their mean, plus fine roughness. Ridges
	 * and longitudinal valleys (wavelength of a few km) are not included, because the profile p[0] describes them.
	 */
	private static double flyschConvexity(double relief, double[] p, double rough) {
		return relief * p[0] * (0.45 + 0.55 * p[1]) * 0.2 * (p[2] - GULLY_MEAN) * p[3] + rough;
	}

	/**
	 * Components of the flysch relief: {longitudinal valley profile, transverse valley profile, gullies, domes}.
	 * The profiles are 0 on the valley axis and about 1 on the ridge.
	 */
	private double[] flyschParts(Cell c, double x, double z, double spacing) {
		double u = across(c, x, z);
		double v = along(c, x, z);
		double wu = u + 0.35 * spacing * mountain.fbm(x - 999, z, 1.6 * spacing, 2, 0.5);
		double wv = v + 0.35 * spacing * mountain.fbm(x, z + 999, 1.6 * spacing, 2, 0.5);
		double main = valleyProfile(mountain.sample(wu / spacing + 0.5, wv / (3.0 * spacing)), 0.55);
		double cross = valleyProfile(mountain.sample(wu / (1.2 * spacing) - 7.3, wv / (0.45 * spacing)), 0.6);
		// Gullies and small valleys on the slopes: a dense, warped network with a spacing of about 0.2 of the valley spacing.
		double g = 0.2 * spacing;
		double gx = x + 0.5 * g * mountain.at(x + 111, z - 222, 0.8 * g);
		double gz = z + 0.5 * g * mountain.at(x - 333, z + 444, 0.8 * g);
		double gully = valleyProfile(mountain.sample(gx / g + 3.1, gz / (1.7 * g)), 0.5);
		double domes = 0.75 + 0.25 * mountain.at(x + 4_321, z, 1.8 * spacing);
		return new double[] {main, cross, gully, domes};
	}

	private double flyschSpacing(LandscapeType type) {
		return (type == LandscapeType.BESKIDS ? 5_200 : 3_200) * mspace;
	}

	/** Foothills: rounded hills elongated along the range, 300–600 m a.s.l., relief 100–250 m. */
	private double foothillElevation(Cell c, double x, double z, ReliefParts o, double w, double[] gm) {
		double m = mountainField(x, z);
		double floor = 270.0 + 90.0 * Noise.smoothstep(0.45, 0.85, m);
		double relief = 170.0 + 90.0 * Noise.smoothstep(0.5, 0.85, m);
		double rough = 12.0 * relief(x, z, 700 * local);
		double[] p = flyschParts(c, x, z, 3_200 * mspace);
		// A large massif lifts the foothills cells in its reach as well (at gameplay scale the Beskids band is only one
		// or two region cells wide, so the dome often reaches over the type boundary). Outside the reach nothing changes.
		if (gm != null) {
			double lift = greatMassifLift(gm, floor + relief);
			floor += GM_FLOOR_SHARE * lift;
			relief += (1 - GM_FLOOR_SHARE) * lift;
			greatMassifFill(gm[0], p);
		}
		if (o != null) {
			o.convexity += w * flyschConvexity(relief, p, rough);
			o.flysch(LandscapeType.FOOTHILLS, w, p[0]);
		}
		double h = floor + relief * flyschRelief(p) + rough;
		return gm == null ? h : greatMassifCeiling(gm, h);
	}

	/** Beskids: 500–1725 m a.s.l., relief 400–900 m, slopes 15–30°. */
	private double mountainElevation(Cell c, double x, double z, ReliefParts o, double w, double[] gm) {
		double m = mountainField(x, z);
		double axis = Noise.smoothstep(0.82, 1.0, m);
		double floor = 480.0 + 180.0 * axis;
		double relief = 520.0 + 380.0 * axis;
		// Isolated higher massifs like Babia Gora or Pilsko.
		double massif = Noise.smoothstep(0.35, 0.8, mountain.at(x - 55_555, z + 22_222, 30_000 * meso));
		relief += 350.0 * massif;
		double rough = 25.0 * relief(x, z, 900 * local);
		double[] p = flyschParts(c, x, z, 5_200 * mspace);
		// Large massif (M2-8): brings the envelope of the ridges to the target summit, fills the flysch valleys near the
		// center and rounds the domes. Outside the reach of every massif nothing changes.
		if (gm != null) {
			double lift = greatMassifLift(gm, floor + relief);
			floor += GM_FLOOR_SHARE * lift;
			relief += (1 - GM_FLOOR_SHARE) * lift;
			greatMassifFill(gm[0], p);
		}
		if (o != null) {
			o.convexity += w * flyschConvexity(relief, p, rough);
			o.flysch(LandscapeType.BESKIDS, w, p[0]);
			o.massif = gm == null ? massif : Math.max(massif, gm[0]);
		}
		double h = floor + relief * flyschRelief(p) + rough;
		return gm == null ? saturate(h) : greatMassifCeiling(gm, h);
	}

	/** Soft saturation above 1500 m: the highest Beskid summits are about 1725 m (Babia Gora). */
	private static double saturate(double h) {
		return h > 1_500 ? 1_500 + 250 * Math.tanh((h - 1_500) / 250) : h;
	}

	/**
	 * Lift of the envelope of the flysch ridges (floor + relief) by the large massif {@code gm} = {G, target}: towards
	 * the envelope target T = target + GM_CAP with the weight G. Where the envelope is already above T it is lowered
	 * only as far as the flysch valleys are filled (S3 tuning in step K2): a lowering of the whole reach shifted the
	 * river network around a massif on a high envelope (a new sink lake and a 1 km deep slot in the dome), while a
	 * lift only upwards (design) left a plateau of 5.5 km² above 1650 m on the filled valleys of such a massif.
	 */
	private static double greatMassifLift(double[] gm, double envelope) {
		double strength = gm[0];
		double lift = gm[1] + GM_CAP - envelope;
		return strength * (lift >= 0 ? lift : lift * Noise.smoothstep(GM_FILL0, GM_FILL1, strength));
	}

	/** Near the massif center the flysch valleys are filled and the domes rounded (profiles {@code p} changed in place). */
	private static void greatMassifFill(double strength, double[] p) {
		double fill = Noise.smoothstep(GM_FILL0, GM_FILL1, strength);
		p[0] += fill * (1 - p[0]);
		p[1] += fill * (1 - p[1]);
		p[3] += strength * (1 - p[3]);
	}

	/**
	 * Top of a large massif: the saturation from 1500 m, which in the dome (weight smoothstep(0, GM_CAP_G, G)) gives way
	 * to a soft ceiling just below the target, so the top is a dome and not a plateau (review of the design). Below the
	 * knee nothing changes.
	 */
	private static double greatMassifCeiling(double[] gm, double h) {
		double saturated = saturate(h);
		double capWeight = Noise.smoothstep(0, GM_CAP_G, gm[0]);
		if (capWeight > 0) {
			double knee = gm[1] - GM_CAP;
			double capped = h <= knee ? h : knee + GM_CAP * Math.tanh((h - knee) / GM_CAP);
			saturated += capWeight * (capped - saturated);
		}
		return saturated;
	}

	// ------------------------------------------------------------------ large massifs (M2-8)

	/**
	 * Large massif at a point: {G, target summit}, or null outside the reach of every massif. G = (1 − d²)³ on an
	 * ellipse elongated along the range (semi-axes Ra × Rc, outline warped by noise, d² · (1 + 0.3 · fbm)).
	 *
	 * <p>The reaches of two massifs never overlap: G &gt; 0 needs d² &lt; 1 / 0.7 before the outline noise, i.e. less than
	 * 1.2 Ra from the center, and the thinning in {@link #greatMassifCell} keeps the centers at least
	 * GM_SEPARATION · Ra = 2.5 Ra apart. So at most one massif counts at any point and the lift is continuous (the
	 * review asked for a maximum over the massifs, which was needed only with the incomplete thinning of the
	 * prototype). The strongest one is taken in any case.
	 */
	double[] greatMassifAt(double x, double z) {
		long gi = (long) Math.floor(x / greatMassifSpacing);
		long gj = (long) Math.floor(z / greatMassifSpacing);
		// Only the cells whose candidates (at 0.2–0.8 of the cell) can be within the reach: at realistic scale the
		// reach (9.6 km) is shorter than 0.2 of the cell (12 km), so only the own cell; the result is the same.
		double margin = greatMassifReach - 0.2 * greatMassifSpacing;
		double fx = x - gi * greatMassifSpacing;
		double fz = z - gj * greatMassifSpacing;
		long i0 = fx < margin ? gi - 1 : gi;
		long i1 = greatMassifSpacing - fx < margin ? gi + 1 : gi;
		long j0 = fz < margin ? gj - 1 : gj;
		long j1 = greatMassifSpacing - fz < margin ? gj + 1 : gj;
		double outline = Double.NaN;
		double[] best = null;
		for (long i = i0; i <= i1; i++) {
			for (long j = j0; j <= j1; j++) {
				double[] c = greatMassifCell(i, j);
				if (c.length == 0) {
					continue;
				}
				double d2 = greatMassifD2(c, x, z);
				if (d2 >= GM_REACH2) {
					continue;
				}
				if (Double.isNaN(outline)) {
					outline = 1 + 0.3 * greatMassif.fbm(x, z, 0.6 * greatMassifRa, 2, 0.5);
				}
				d2 *= outline;
				if (d2 >= 1) {
					continue;
				}
				double t = 1 - d2;
				double g = t * t * t;
				if (best == null || g > best[0]) {
					best = new double[] {g, c[4]};
				}
			}
		}
		return best;
	}

	/** Squared elliptic distance of the point from the massif center (1 on the outline, without the outline noise). */
	private double greatMassifD2(double[] c, double x, double z) {
		double dx = x - c[0];
		double dz = z - c[1];
		double across = dx * c[2] + dz * c[3];
		double along = -dx * c[3] + dz * c[2];
		return along * along / (greatMassifRa * greatMassifRa) + across * across / (greatMassifRc * greatMassifRc);
	}

	/** Semi-axis Ra of a large massif along the range (m). */
	double greatMassifRa() {
		return greatMassifRa;
	}

	/** Semi-axis Rc of a large massif across the range (m). */
	double greatMassifRc() {
		return greatMassifRc;
	}

	/** Strength G of the large massif at a point (0 outside the reach of every massif). */
	double greatMassifStrength(double x, double z) {
		double[] g = greatMassifAt(x, z);
		return g == null ? 0 : g[0];
	}

	/** Whether the point lies in the core of a large massif (G &gt; GM_CORE): no springs and no flow across it. */
	boolean greatMassifCore(double x, double z) {
		return greatMassifStrength(x, z) > GM_CORE;
	}

	/**
	 * Whether the reach of any large massif can intersect the box: a quick test before {@link #greatMassifCore} on
	 * many points (river rules).
	 */
	boolean greatMassifNear(double minX, double minZ, double maxX, double maxZ) {
		double reach = greatMassifReach;
		long i0 = (long) Math.floor((minX - reach) / greatMassifSpacing);
		long i1 = (long) Math.floor((maxX + reach) / greatMassifSpacing);
		long j0 = (long) Math.floor((minZ - reach) / greatMassifSpacing);
		long j1 = (long) Math.floor((maxZ + reach) / greatMassifSpacing);
		for (long i = i0; i <= i1; i++) {
			for (long j = j0; j <= j1; j++) {
				double[] c = greatMassifCell(i, j);
				if (c.length > 0 && c[0] > minX - reach && c[0] < maxX + reach && c[1] > minZ - reach
						&& c[1] < maxZ + reach) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Accepted candidate of the grid cell (i, j): {x, z, cos, sin, target summit} of the massif center, or an empty
	 * array. The candidate lies at (i + 0.2 + 0.6 · u2, j + 0.2 + 0.6 · u3) · spacing and is dropped when an eligible
	 * candidate with a smaller draw u1 lies closer than GM_SEPARATION · Ra, checked in {@link #greatMassifRings}
	 * rings of cells (so the thinning is complete also when the separation exceeds the cell, at gameplay scale).
	 * The direction across the range is the gradient of {@link #mountainRaw}, as in {@link #cell}. Cached with
	 * get/put and not computeIfAbsent, because the computation reads the neighboring cells.
	 */
	double[] greatMassifCell(long i, long j) {
		if (greatMassif.unit(i, j, 1) >= GM_P) {
			return GM_NONE;
		}
		long key = Noise.key(i, j, 7);
		double[] cached = greatMassifCells.get(key);
		if (cached != null) {
			return cached;
		}
		double[] r = GM_NONE;
		if (greatMassifEligible(i, j)) {
			double px = greatMassifX(i, j);
			double pz = greatMassifZ(i, j);
			double u = greatMassif.unit(i, j, 1);
			double separation = GM_SEPARATION * greatMassifRa;
			boolean ok = true;
			for (long di = -greatMassifRings; di <= greatMassifRings && ok; di++) {
				for (long dj = -greatMassifRings; dj <= greatMassifRings && ok; dj++) {
					if ((di != 0 || dj != 0) && greatMassif.unit(i + di, j + dj, 1) < u && greatMassifEligible(i + di, j + dj)) {
						ok = Math.hypot(greatMassifX(i + di, j + dj) - px, greatMassifZ(i + di, j + dj) - pz) >= separation;
					}
				}
			}
			if (ok) {
				double e = Math.max(50.0, 2_000 * zs);
				double gx = mountainRaw(px + e, pz) - mountainRaw(px - e, pz);
				double gz = mountainRaw(px, pz + e) - mountainRaw(px, pz - e);
				double len = Math.sqrt(gx * gx + gz * gz);
				double cos = len > 1e-12 ? gx / len : 1;
				double sin = len > 1e-12 ? gz / len : 0;
				double target = GM_SUMMIT_LO + (GM_SUMMIT_HI - GM_SUMMIT_LO) * greatMassif.unit(i, j, 4);
				r = new double[] {px, pz, cos, sin, target};
			}
		}
		if (greatMassifCells.size() > 100_000) {
			greatMassifCells.clear();
		}
		greatMassifCells.put(key, r);
		return r;
	}

	private double greatMassifX(long i, long j) {
		return (i + 0.2 + 0.6 * greatMassif.unit(i, j, 2)) * greatMassifSpacing;
	}

	private double greatMassifZ(long i, long j) {
		return (j + 0.2 + 0.6 * greatMassif.unit(i, j, 3)) * greatMassifSpacing;
	}

	/** Candidate before thinning: draw u1 &lt; GM_P and the center deep in the range (cached with get/put). */
	private boolean greatMassifEligible(long i, long j) {
		if (greatMassif.unit(i, j, 1) >= GM_P) {
			return false;
		}
		long key = Noise.key(i, j, 8);
		Boolean cached = greatMassifEligibleCache.get(key);
		if (cached != null) {
			return cached;
		}
		double px = greatMassifX(i, j);
		double pz = greatMassifZ(i, j);
		boolean ok = mountainField(px, pz) >= GM_MIN_FIELD && blend(px, pz).weight(LandscapeType.BESKIDS) >= GM_MIN_WEIGHT;
		if (greatMassifEligibleCache.size() > 100_000) {
			greatMassifEligibleCache.clear();
		}
		greatMassifEligibleCache.put(key, ok);
		return ok;
	}

	/** Large massifs with the center in the box, in the order of grid cells (i, then j). */
	public List<GreatMassif> greatMassifs(double minX, double minZ, double maxX, double maxZ) {
		List<GreatMassif> out = new ArrayList<>();
		long i0 = (long) Math.floor(minX / greatMassifSpacing);
		long i1 = (long) Math.floor(maxX / greatMassifSpacing);
		long j0 = (long) Math.floor(minZ / greatMassifSpacing);
		long j1 = (long) Math.floor(maxZ / greatMassifSpacing);
		for (long i = i0; i <= i1; i++) {
			for (long j = j0; j <= j1; j++) {
				double[] c = greatMassifCell(i, j);
				if (c.length > 0 && c[0] >= minX && c[0] <= maxX && c[1] >= minZ && c[1] <= maxZ) {
					out.add(new GreatMassif(c[0], c[1], c[4]));
				}
			}
		}
		return out;
	}

	/**
	 * Large massif whose center is nearest to the point (tests, preview frames, the golden patch great_massif), or
	 * null when there is none within 200 grid cells.
	 */
	public GreatMassif nearestGreatMassif(double x, double z) {
		long gi = (long) Math.floor(x / greatMassifSpacing);
		long gj = (long) Math.floor(z / greatMassifSpacing);
		double[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int ring = 0; ring <= 200; ring++) {
			for (long i = gi - ring; i <= gi + ring; i++) {
				for (long j = gj - ring; j <= gj + ring; j++) {
					if (Math.max(Math.abs(i - gi), Math.abs(j - gj)) != ring) {
						continue;
					}
					double[] c = greatMassifCell(i, j);
					if (c.length > 0) {
						double d = Math.hypot(c[0] - x, c[1] - z);
						if (d < bestD) {
							bestD = d;
							best = c;
						}
					}
				}
			}
			// Every cell of the next ring lies at least ring · spacing from the point.
			if (best != null && ring * greatMassifSpacing >= bestD) {
				break;
			}
		}
		return best == null ? null : new GreatMassif(best[0], best[1], best[4]);
	}

	private double relief(double x, double z, double wavelength) {
		return relief.fbm(x, z, wavelength, 3, 0.5);
	}

	// ------------------------------------------------------------------ sampling

	private double elevation(Blend b, double x, double z) {
		double[] gm = greatMassifFor(b, x, z);
		double h = 0;
		for (int i = 0; i < b.count(); i++) {
			h += b.weights()[i] * cellElevation(b.cells()[i], x, z, null, 0, gm);
		}
		return h;
	}

	/** Like {@link #elevation(Blend, double, double)}, recording the relief components into {@code o}. */
	private double elevation(Blend b, double x, double z, ReliefParts o) {
		double[] gm = greatMassifFor(b, x, z);
		double h = 0;
		for (int i = 0; i < b.count(); i++) {
			h += b.weights()[i] * cellElevation(b.cells()[i], x, z, o, b.weights()[i], gm);
		}
		return h;
	}

	/** Regional fields O and P at a point, without a full sample (regional map preview and tests). */
	ColumnSample.Region region(double x, double z) {
		return regional.sample(x, z);
	}

	/** Ground height after blending the cells and shaping the coast, still without rivers and lakes. */
	public double landElevation(double x, double z) {
		return shapeCoast(elevation(blend(x, z), x, z), coastDistance(x, z), x, z);
	}

	/** Full column sample: relief, waters, substrate. */
	public ColumnSample sample(double x, double z) {
		Blend b = blend(x, z);
		ReliefParts parts = new ReliefParts();
		double raw = elevation(b, x, z, parts);
		double coastD = coastDistance(x, z);
		double surface = shapeCoast(raw, coastD, x, z);
		double rawSurface = surface;
		LandscapeType dominant = b.dominant();
		double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
				+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN);
		double young = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU);
		double foothills = b.weight(LandscapeType.FOOTHILLS);
		double mountains = b.weight(LandscapeType.BESKIDS);

		Substrate substrate = dominant.defaultSubstrate();
		int water = ColumnSample.NO_WATER;
		WaterKind kind = WaterKind.NONE;

		// Sea and lagoons (at sea level).
		if (coastD < 0 || surface < 0) {
			LandscapeType t = coastD < 0 ? LandscapeType.SEA : LandscapeType.COASTLAND;
			Substrate sub = coastD < 0 && -coastD > 3_000 * meso ? Substrate.LAKE_MUD : Substrate.SAND;
			return new ColumnSample(surface, 0, WaterKind.SEA, t, coastD < 0 ? sub : Substrate.LAKE_MUD, 30.0,
					terrain(b, parts, t, raw, rawSurface, coastD, 0, Double.NaN, Double.NaN, 0, x, z), ColumnSample.Waters.NONE,
					regional.sample(x, z));
		}
		double bare = Double.NaN;
		if (coastD < beachWidth() + 400 * local) {
			dominant = LandscapeType.COASTLAND;
			// Beach and white dune without turf; further from the sea the vegetated gray dune.
			bare = beachWidth() + 220 * local * (0.6 + 0.4 * coast.at(x, z, 400 * local));
			substrate = surface > 8 ? Substrate.GLACIAL_TILL : coastD < bare ? Substrate.BEACH_SAND : Substrate.SAND;
		}

		// Valleys and channels of the river network, and sink lakes.
		double valley = 0;
		RiverNetwork.RiverHit r = rivers.query(x, z, surface, lowland + b.weight(LandscapeType.COASTLAND), foothills,
				mountains);
		surface = r.terrain();
		boolean inSinkLake = r.lakeLevel() != ColumnSample.NO_WATER && r.lakeShore() < 0;
		// Nearest standing water (habitat fields): sink lake, oxbow lake, tunnel valley lake, kettle pond.
		// Local variables instead of an object, because sample is called for every column (cost, M2 plan §3.1);
		// later sources replace earlier ones only when their shore is closer.
		double standingShore = Double.POSITIVE_INFINITY;
		int standingLevel = ColumnSample.NO_WATER;
		ColumnSample.StandingWaterKind standingKind = ColumnSample.StandingWaterKind.NONE;
		boolean standingOmbrotrophic = false;
		long standingId = 0;
		double standingRadius = Double.NaN;
		// Sink lake in a ring up to 150 m·k (alder carr, M2 plan §4.4); it changes the terrain only up to KETTLE_BANK.
		if (r.ringLevel() != ColumnSample.NO_WATER && r.ringShore() < standingShore) {
			standingShore = r.ringShore();
			standingLevel = r.ringLevel();
			standingKind = ColumnSample.StandingWaterKind.SINK_LAKE;
			standingId = r.ringId();
			standingRadius = r.ringRadius();
		}
		if (r.order() > 0 && !inSinkLake && !r.inChannel() && r.oxbowShore() < standingShore) {
			standingShore = r.oxbowShore();
			standingLevel = r.oxbowMirror();
			standingKind = ColumnSample.StandingWaterKind.OXBOW_LAKE;
			standingOmbrotrophic = false;
			standingId = r.oxbowId();
			standingRadius = r.oxbowWidth();
		}
		if (r.order() > 0 && !inSinkLake) {
			valley = r.valleyWeight();
			if (r.inFloor()) {
				substrate = lowland > 0.5 ? Substrate.ALLUVIUM : Substrate.RIVERBED;
			}
			if (!r.inChannel() && surface < r.bankLevel()) {
				surface = r.bankLevel();
			}
			if (r.inChannel()) {
				surface = Math.min(surface, r.channelBottom());
				if (surface < r.waterLevel()) {
					water = r.waterLevel();
					kind = WaterKind.RIVER;
					substrate = Substrate.RIVERBED;
				}
			} else if (r.oxbowLevel() != ColumnSample.NO_WATER) {
				surface = Math.min(surface, r.oxbowLevel() - 0.3 - r.oxbowDepth());
				water = r.oxbowLevel();
				kind = WaterKind.OXBOW;
				substrate = Substrate.LAKE_MUD;
			}
		}
		if (kind == WaterKind.NONE && r.lakeLevel() != ColumnSample.NO_WATER && r.lakeShore() < KETTLE_BANK) {
			LakeHit sinkLake = new LakeHit(r.lakeLevel(), r.lakeDepth(), r.lakeShore(), false, 0.25, KETTLE_BANK,
					ColumnSample.StandingWaterKind.SINK_LAKE, r.lakeId(), r.lakeRadius(), false);
			surface = applyLake(surface, sinkLake);
			if (r.lakeShore() < 0 && surface < r.lakeLevel()) {
				water = r.lakeLevel();
				kind = WaterKind.LAKE;
				substrate = Substrate.LAKE_MUD;
			}
			valley = Math.max(valley, 1 - Noise.smoothstep(0, 200 * local, r.lakeShore()));
		}

		// Glacial tunnel valleys with chains of lakes (young-glacial zone only, outside river valleys).
		// Tunnel valley presence fades out smoothly in river valleys, so lakes are not cut off at the valley edge.
		double tunnelPresence = young * (1 - Noise.smoothstep(0.1, 0.45, valley)) * Noise.smoothstep(0, 1_500 * meso, coastD);
		if (tunnelPresence > 0.05 && kind == WaterKind.NONE) {
			LakeHit lake = tunnelLakeAt(x, z, tunnelPresence);
			if (lake != null) {
				if (lake.shoreDistance < standingShore) {
					standingShore = lake.shoreDistance;
					standingLevel = lake.level;
					standingKind = lake.kind;
					standingOmbrotrophic = lake.ombrotrophic;
					standingId = lake.id;
					standingRadius = lake.radius;
				}
				// Beyond the basin (habitat ring up to 150 m·k) the lake does not change the terrain.
				if (lake.shoreDistance <= lake.bank) {
					surface = applyLake(surface, lake);
				}
				// Water only inside the shoreline; outside it a 15 m strip always has a bank at water level + 1 m.
				if (lake.shoreDistance < 0 && surface < lake.level) {
					water = lake.level;
					kind = WaterKind.LAKE;
					substrate = Substrate.LAKE_MUD;
				}
			}
		}

		// Kettle ponds on the moraine plateau and outwash plain. Whether a kettle exists is decided by the conditions
		// at its centre, so we check them in every column without water.
		if (kind == WaterKind.NONE) {
			LakeHit k = kettleAt(x, z);
			if (k != null) {
				if (k.shoreDistance < standingShore) {
					standingShore = k.shoreDistance;
					standingLevel = k.level;
					standingKind = k.kind;
					standingOmbrotrophic = k.ombrotrophic;
					standingId = k.id;
					standingRadius = k.radius;
				}
				surface = applyLake(surface, k);
				if (k.shoreDistance < 0 && surface < k.level) {
					if (k.peat) {
						surface = k.level - 0.5;
						substrate = Substrate.PEAT;
					} else {
						water = k.level;
						kind = WaterKind.KETTLE;
						substrate = Substrate.LAKE_MUD;
					}
				}
			}
		}

		double cover = switch (dominant.belt()) {
			case SEA, LOWLAND -> 40.0 + 60.0 * (0.5 + 0.5 * relief.at(x, z, 20_000 * meso));
			case FOOTHILLS -> 3.0 + 4.0 * (0.5 + 0.5 * relief.at(x, z, 800));
			case MOUNTAINS -> 1.0 + 2.5 * (0.5 + 0.5 * relief.at(x, z, 600));
		};

		ColumnSample.Waters waters = r.order() == 0 && standingKind == ColumnSample.StandingWaterKind.NONE ? ColumnSample.Waters.NONE
				: new ColumnSample.Waters(r.order(), r.source(), r.channelDist(), r.channelWidth(), r.channelLevel(),
						r.inFloor(), r.floorU(), r.floorHalf(), r.slope(), r.convexBank(), standingShore, standingLevel, standingKind,
						standingOmbrotrophic, standingId, standingRadius, r.floorChannelDist(), r.floorChannelWidth(),
						r.floorChannelLevel());
		int landformBits = forms(r, parts, dominant, surface, rawSurface, coastD, water);
		// Large massif (E12): highest terrain within 3 km·mspace, only where the altitudinal belts need it.
		double summit = mountains > 0 && surface >= AltitudinalBelts.SUMMIT_FROM ? peaks.sample(x, z) : 0;
		return new ColumnSample(surface, water, kind, dominant, substrate, cover,
				terrain(b, parts, dominant, raw, rawSurface, coastD, landformBits, sandiness(x, z), bare, summit, x, z), waters,
				regional.sample(x, z));
	}

	/**
	 * Landforms from the conditions of {@link #describe} moved into the sample, without extra samples: the
	 * {@link Landform#bit()} bits of the landforms in {@link Landform#FROM_SAMPLE}. A separate method so that {@link #sample} does not grow.
	 */
	private int forms(RiverNetwork.RiverHit r, ReliefParts parts, LandscapeType dominant, double surface, double rawSurface,
			double coastD, int water) {
		int landformBits = 0;
		if (r.order() > 0 && r.source() && (r.inChannel() || r.inFloor())) {
			landformBits |= Landform.HEADWATERS.bit();
		}
		boolean wet = water != ColumnSample.NO_WATER && water > (int) Math.floor(surface);
		if (dominant == LandscapeType.COASTLAND && coastD >= 0 && !wet) {
			// Sea shore landforms only in the belt where they form (beach, foredune, cliff
			// face), and not e.g. on the shore of a lagoon several kilometres from the sea.
			double beach = beachWidth();
			if (surface > 8 && rawSurface > 8 && coastD < beach + rawSurface / 2.5 + 20 * local) {
				landformBits |= Landform.CLIFF.bit();
			} else if (surface < 3 && coastD < 1.3 * beach) {
				landformBits |= Landform.BEACH.bit();
			} else if (surface >= 3 && coastD >= 0.8 * beach && coastD < beach + 220 * local) {
				landformBits |= Landform.COASTAL_DUNES.bit();
			}
		}
		if (dominant == LandscapeType.OUTWASH_PLAIN && parts.duneHeight > 4) {
			landformBits |= Landform.INLAND_DUNES.bit();
		}
		if (dominant == LandscapeType.MORAINE_PLATEAU && parts.moraineRidgeHeight > 25) {
			landformBits |= Landform.END_MORAINE.bit();
		}
		if (dominant == LandscapeType.FOOTHILLS || dominant == LandscapeType.BESKIDS) {
			double p0 = dominant == LandscapeType.FOOTHILLS ? parts.foothillsProfile : parts.beskidsProfile;
			if (p0 > 0.9) {
				landformBits |= Landform.RIDGE.bit();
			}
			if (p0 < 0.15) {
				landformBits |= Landform.MOUNTAIN_VALLEY.bit();
			}
		}
		return landformBits;
	}

	/**
	 * Relief record from the macroregion weights, the components recorded in {@code parts} and the smoothed terrain grid
	 * at point (x, z).
	 */
	private ColumnSample.Terrain terrain(Blend b, ReliefParts parts, LandscapeType dominant, double raw, double rawSurface,
			double coastD, int landformBits, double sandiness, double bare, double summit, double x, double z) {
		// Cliff edge: terrain before cutting valleys, in the CLIFF landform belt.
		double cliffHeight = (landformBits & Landform.CLIFF.bit()) != 0 ? rawSurface : 0;
		// Coastal belt: 1 where the sample gets the COASTLAND type, 0 at the edge of the belt B + D + 2000k (M2 plan §3.4).
		double coastBand = 1 - Noise.smoothstep(beachWidth() + 400 * local, beachWidth() + 2_220 * local, coastD);
		// Flysch profile of the cell used by the landform description; outside the mountains the flysch cell with the largest weight.
		// Near the sea shapeCoast compresses the whole relief (h · (0.12 + 0.88 smoothstep(0, 25 km·meso, cD))), so
		// convexity and dune height are scaled the same way; the INLAND_DUNES bit in the landforms is computed without scaling (as in M1).
		double band = 25_000 * meso;
		double coastScale = coastD >= band ? 1.0 : 0.12 + 0.88 * Noise.smoothstep(0, band, coastD);
		// Low shore with dunes or high shore with a cliff: the same "low" as in shapeCoast, from the height before the coast shaping.
		double low = coastD >= 0 && coastD < band ? 1 - Noise.smoothstep(6, 20, raw * coastScale) : 0;
		CoarseTerrainField.CoarseSample g = coarse.sample(x, z);
		double ridgeProfile = switch (dominant) {
			case FOOTHILLS -> parts.foothillsProfile;
			case BESKIDS -> parts.beskidsProfile;
			default -> parts.beskidsWeight > parts.foothillsWeight ? parts.beskidsProfile : parts.foothillsProfile;
		};
		return new ColumnSample.Terrain(rawSurface, coastD, b.weight(LandscapeType.OUTWASH_PLAIN),
				b.weight(LandscapeType.MORAINE_PLATEAU), b.weight(LandscapeType.OLD_GLACIAL_PLAIN),
				b.weight(LandscapeType.FOOTHILLS), b.weight(LandscapeType.BESKIDS), coastBand, landformBits, parts.convexity * coastScale,
				parts.duneHeight * coastScale,
				ridgeProfile, parts.massif, summit, cliffHeight, low, bare, sandiness, g.sBar(), g.slope(), g.aspect());
	}

	/** Sandiness of the deposit 0–1: quantile of a noise with a 2 km·k wavelength, so the share of sands is a simple threshold. */
	private double sandiness(double x, double z) {
		return noiseQuantile(habitatSandiness.at(x, z, 2_000 * local));
	}

	/** Quantile of a {@link Noise#sample} value (CDF from {@link #noiseCdf}): uniformly distributed on [0, 1]. */
	public static double noiseQuantile(double v) {
		double f = Math.clamp((v + 1) * 0.5 * CDF_BINS, 0.0, CDF_BINS);
		int k = Math.min(CDF_BINS - 1, (int) f);
		return NOISE_CDF[k] + (NOISE_CDF[k + 1] - NOISE_CDF[k]) * (f - k);
	}

	/**
	 * CDF of the noise values from a grid of 512 × 512 points (about 90 × 75 noise cells) for a fixed seed.
	 * The noise distribution does not depend on the world seed, and the table is always the same.
	 */
	private static double[] noiseCdf() {
		Noise n = new Noise(0x5A4D_1E5AL);
		long[] hist = new long[CDF_BINS];
		int total = 0;
		for (int i = 0; i < 512; i++) {
			for (int j = 0; j < 512; j++) {
				double v = n.sample(i * 0.1731 + 0.37, j * 0.1469 - 0.11);
				int k = Math.clamp((long) Math.floor((v + 1) * 0.5 * CDF_BINS), 0, CDF_BINS - 1);
				hist[k]++;
				total++;
			}
		}
		double[] cdf = new double[CDF_BINS + 1];
		long acc = 0;
		for (int k = 0; k < CDF_BINS; k++) {
			acc += hist[k];
			cdf[k + 1] = (double) acc / total;
		}
		return cdf;
	}

	// ------------------------------------------------------------------ landform description

	/** Column sample and the landforms recognised at a point. */
	public record Description(ColumnSample sample, Set<Landform> forms) {
	}

	/**
	 * Terrain description at a point: the column sample and the recognised landforms. Landforms from {@link ColumnSample.Terrain#landformBits()}
	 * are taken from the sample; summit, pass and tunnel valley need extra samples, so the description costs more
	 * than {@link #sample} and serves commands and tools, not generation.
	 */
	public Description describe(double x, double z) {
		ColumnSample s = sample(x, z);
		ColumnSample.Terrain t = s.terrain();
		ColumnSample.Waters w = s.waters();
		Set<Landform> f = EnumSet.noneOf(Landform.class);
		LandscapeType type = s.type();

		double coastD = t.coastD();
		switch (s.waterKind()) {
			case RIVER -> {
				f.add(w.streamOrder() == 1 ? Landform.STREAM : Landform.RIVER);
				if (coastD < 3_000 * meso) {
					f.add(Landform.RIVER_MOUTH);
				}
			}
			case LAKE -> f.add(Landform.TUNNEL_VALLEY_LAKE);
			case KETTLE -> f.add(Landform.KETTLE_POND);
			case OXBOW -> f.add(Landform.OXBOW_LAKE);
			case SEA -> {
				if (coastD >= 0) {
					f.add(Landform.LAGOON);
				}
			}
			default -> {
			}
		}
		// Headwaters, sea shore landforms, dunes, moraine ridges, ridges and mountain valleys are already recognised by sample.
		t.addLandforms(f);
		if (s.substrate() == Substrate.PEAT) {
			f.add(Landform.KETTLE_BOG);
		}
		if (s.substrate() == Substrate.ALLUVIUM) {
			f.add(Landform.VALLEY_FLOOR);
		}

		if (w.streamOrder() > 0 && !s.hasWater() && !w.inFloor() && s.surface() < t.rawSurface() - 2) {
			f.add(Landform.VALLEY_SLOPE);
		}
		double young = t.wOutwashPlain() + t.wMorainePlateau();
		if (young > 0.05) {
			double[] tc = tunnelChannel(x, z, young);
			if (tc != null && tc[1] < tc[2]) {
				f.add(Landform.TUNNEL_VALLEY);
			}
		}

		if (type == LandscapeType.FOOTHILLS || type == LandscapeType.BESKIDS) {
			Blend b = blend(x, z);
			Cell cell = null;
			double best = -1;
			for (int i = 0; i < b.count(); i++) {
				if (b.cells()[i].type() == type && b.weights()[i] > best) {
					best = b.weights()[i];
					cell = b.cells()[i];
				}
			}
			double spacing = flyschSpacing(type);
			double[] p = flyschParts(cell, x, z, spacing);
			// On a large massif the flysch valleys are filled as in the terrain (M2-8): the dome is a ridge and its summit
			// a SUMMIT, not a pass.
			double[] gm = greatMassifAt(x, z);
			if (gm != null) {
				greatMassifFill(gm[0], p);
			}
			if (p[0] > 0.8 && p[1] < 0.3) {
				f.add(Landform.MOUNTAIN_PASS);
			}
			if (p[0] > 0.75 && isLocalMaximum(x, z, s.surface(), 0.06 * spacing)) {
				f.add(Landform.SUMMIT);
			}
			if (type == LandscapeType.BESKIDS) {
				// Nominal belt boundary (without the aspect and noise correction), the same one as in habitat/AltitudinalBelts.
				f.add(AltitudinalBelts.isUpperMontane(s.surface()) ? Landform.UPPER_MONTANE : Landform.LOWER_MONTANE);
			}
		}
		return new Description(s, f);
	}

	/** Whether the point is higher than 16 points on a circle of radius r and 8 on a circle of radius r/2. */
	private boolean isLocalMaximum(double x, double z, double h, double r) {
		for (int ring = 1; ring <= 2; ring++) {
			double rr = r * ring / 2.0;
			int n = ring == 1 ? 8 : 16;
			for (int i = 0; i < n; i++) {
				double a = i * (2 * Math.PI / n);
				if (landElevation(x + rr * Math.cos(a), z + rr * Math.sin(a)) >= h) {
					return false;
				}
			}
		}
		return true;
	}

	/** Tunnel valley geometry at a point: {gate, distance from the axis, half-width} or null. */
	private double[] tunnelChannel(double x, double z, double presence) {
		double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso))
				* Noise.smoothstep(0.2, 0.7, presence);
		if (gate <= 0) {
			return null;
		}
		double e = 40;
		double gx = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
		double gz = (tunnelField(x, z + e) - tunnelField(x, z - e)) / (2 * e);
		double grad = Math.sqrt(gx * gx + gz * gz);
		if (grad < 1e-12) {
			return null;
		}
		double dist = Math.abs(tunnelField(x, z)) / grad;
		double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
		return new double[] {gate, dist, half};
	}

	// ------------------------------------------------------------------ L3: waters

	/**
	 * @param shoreDistance distance from the shoreline in metres, negative in the lake
	 * @param slope         slope of the basin flank above the water level (tangent)
	 * @param bank          reach of the basin's influence beyond the shore
	 * @param kind          kind of water body (habitat fields)
	 * @param id            hash of the water body, constant across the whole body
	 * @param radius        kettle radius or lake half-width at this place
	 * @param ombrotrophic  ombrotrophic peat kettle
	 */
	private record LakeHit(int level, double depthBelowLevel, double shoreDistance, boolean peat, double slope,
			double bank, ColumnSample.StandingWaterKind kind, long id, double radius, boolean ombrotrophic) {
	}

	/** Tunnel valley field: sparse contours of a warped noise, elongated from north to south. */
	private double tunnelField(double x, double z) {
		double wx = x + 1_500 * meso * tunnel.at(x, z, 6_000 * meso);
		return tunnel.sample(wx / (22_000 * meso), z / (60_000 * meso));
	}

	/** Boundary number k between the lakes of a chain, irregular and dependent on the cross-valley position. */
	private double tunnelBoundary(long k, double x) {
		return k * tunnelCell + 0.3 * tunnelCell * tunnel.sample(x / (15_000 * meso), k * 0.618_034);
	}

	/**
	 * Glacial tunnel valley with a chain of lens-shaped lakes. A lake has a constant water level; isthmuses
	 * remain between the lakes. Some sections of the tunnel valley are dry.
	 */
	private LakeHit tunnelLakeAt(double x, double z, double presence) {
		double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso))
				* Noise.smoothstep(0.2, 0.7, presence);
		if (gate <= 0) {
			return null;
		}
		double n = tunnelField(x, z);
		double e = 40;
		double gx = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
		double gz = (tunnelField(x, z + e) - tunnelField(x, z - e)) / (2 * e);
		double grad = Math.sqrt(gx * gx + gz * gz);
		if (grad < 1e-12) {
			return null;
		}
		double dist = Math.abs(n) / grad;
		double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
		// Query reach: the basin (tunnelBank) or the 150 m·k habitat ring, whichever is wider.
		double reach = Math.max(tunnelBank, 150 * local);
		if (half < 25 * local || dist > half + reach) {
			return null;
		}
		long k = (long) Math.floor(z / tunnelCell);
		if (z < tunnelBoundary(k, x)) {
			k--;
		} else if (z >= tunnelBoundary(k + 1, x)) {
			k++;
		}
		double b0 = tunnelBoundary(k, x);
		double b1 = tunnelBoundary(k + 1, x);
		double edge = Math.min(z - b0, b1 - z);
		double lens = Math.sqrt(Noise.smoothstep(tunnelSill, tunnelSill + 0.35 * (b1 - b0), edge));
		double shore = dist - half * lens;
		if (shore > reach) {
			return null;
		}
		// A lake is identified by the point where the tunnel valley axis crosses the middle of its section.
		// The fixed point of the iteration is the same for all columns of the lake.
		double ax = x;
		double az = 0.5 * (b0 + b1);
		for (int outer = 0; outer < 3; outer++) {
			for (int it = 0; it < 12; it++) {
				double f = tunnelField(ax, az);
				double d = (tunnelField(ax + e, az) - tunnelField(ax - e, az)) / (2 * e);
				if (Math.abs(d) < 1e-15) {
					break;
				}
				double step = Math.clamp(f / d, -2_000.0, 2_000.0);
				ax -= step;
				if (Math.abs(step) < 1e-4) {
					break;
				}
			}
			az = 0.5 * (tunnelBoundary(k, ax) + tunnelBoundary(k + 1, ax));
		}
		long anchor = Math.round(ax / (200.0 * local));
		if (tunnel.unit(anchor, k, 8) < 0.3) {
			return null;
		}
		long key = Noise.key(anchor, k, 2);
		double depth = lens * gate * (18 + 45 * tunnel.unit(anchor, k, 7));
		int level = lakeLevel(key, ax, az, 450 * local);
		return new LakeHit(level, depth, shore, false, 0.35, tunnelBank, ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE,
				key, half * lens, false);
	}

	/** Highest chance of a kettle in a cell (pure moraine plateau). */
	private static final double KETTLE_MAX_CHANCE = 0.45;

	/**
	 * Kettle pond: in a 700 m cell (350 m at gameplay scale) at most one depression
	 * with a radius of 20–150 m. Whether the kettle exists depends on the conditions at its centre, so a kettle is
	 * always either whole or absent, and its whole shore zone fits inside the cell.
	 */
	private LakeHit kettleAt(double x, double z) {
		double cell = 700 * local;
		long cx = (long) Math.floor(x / cell);
		long cz = (long) Math.floor(z / cell);
		double roll = kettle.unit(cx, cz, 1);
		if (roll > KETTLE_MAX_CHANCE) {
			return null;
		}
		double r = local * (20 + 130 * Math.pow(kettle.unit(cx, cz, 2), 2));
		double margin = 1.3 * r + KETTLE_BANK + 5;
		double kx = cx * cell + margin + (cell - 2 * margin) * kettle.unit(cx, cz, 3);
		double kz = cz * cell + margin + (cell - 2 * margin) * kettle.unit(cx, cz, 4);
		double dx = x - kx;
		double dz = z - kz;
		double d = Math.sqrt(dx * dx + dz * dz);
		// Slightly irregular shore.
		double shore = d - r * (1 + 0.25 * kettle.at(x, z, Math.max(30, r)));
		if (shore > KETTLE_BANK) {
			return null;
		}
		long key = Noise.key(cx, cz, 3);
		byte state = kettleState(key, roll, kx, kz, r);
		if (state == KETTLE_NONE) {
			return null;
		}
		int level = lakeLevel(key, kx, kz, r * 1.3 + 25);
		double depth = 2 + 8 * kettle.unit(cx, cz, 5);
		boolean peat = kettle.unit(cx, cz, 6) < 0.4;
		return new LakeHit(level, depth, shore, peat, 0.25, KETTLE_BANK,
				peat ? ColumnSample.StandingWaterKind.KETTLE_BOG : ColumnSample.StandingWaterKind.KETTLE_POND, key, r,
				peat && state == KETTLE_OMBROTROPHIC);
	}

	/**
	 * Whether the kettle exists: chance from the region weights and no river valley at its centre. The result is
	 * cached per kettle, because every column in its zone checks the same conditions. An existing kettle
	 * is ombrotrophic ({@link #KETTLE_OMBROTROPHIC}) when its shore lies further than 300 m·k from a channel.
	 */
	private byte kettleState(long key, double roll, double kx, double kz, double r) {
		Byte cached = kettleExistence.get(key);
		if (cached != null) {
			return cached;
		}
		Blend cb = blend(kx, kz);
		double chance = KETTLE_MAX_CHANCE * cb.weight(LandscapeType.MORAINE_PLATEAU)
				+ 0.15 * cb.weight(LandscapeType.OUTWASH_PLAIN);
		boolean exists = roll <= chance;
		boolean ombrotrophic = false;
		if (exists) {
			double lowland = cb.weight(LandscapeType.OUTWASH_PLAIN) + cb.weight(LandscapeType.MORAINE_PLATEAU)
					+ cb.weight(LandscapeType.OLD_GLACIAL_PLAIN);
			RiverNetwork.RiverHit river = rivers.query(kx, kz, landElevation(kx, kz), lowland,
					cb.weight(LandscapeType.FOOTHILLS), cb.weight(LandscapeType.BESKIDS));
			exists = river.valleyWeight() < 0.3 && coastDistance(kx, kz) > 500 * local;
			// Ombrotrophic peat: a basin fed only by precipitation, away from watercourses (ecology report, §0.1).
			ombrotrophic = river.channelDist() - 1.3 * r > 300 * local;
		}
		byte state = !exists ? KETTLE_NONE : ombrotrophic ? KETTLE_OMBROTROPHIC : KETTLE_MINEROTROPHIC;
		if (kettleExistence.size() > 500_000) {
			kettleExistence.clear();
		}
		kettleExistence.put(key, state);
		return state;
	}

	/**
	 * Lake water level: the lowest ground point on a circle around the basin minus 1 m, computed once
	 * per lake and cached.
	 */
	private int lakeLevel(long key, double cx, double cz, double radius) {
		Integer cached = lakeLevels.get(key);
		if (cached != null) {
			return cached;
		}
		double min = landElevation(cx, cz);
		for (int i = 0; i < 12; i++) {
			double a = i * (Math.PI * 2 / 12);
			min = Math.min(min, landElevation(cx + radius * Math.cos(a), cz + radius * Math.sin(a)));
		}
		int level = (int) Math.floor(min) - 1;
		if (lakeLevels.size() > 500_000) {
			lakeLevels.clear();
		}
		lakeLevels.put(key, level);
		return level;
	}

	/**
	 * Carves the lake basin: the bottom below the water level, a flank above the water level fading out to the limit of influence,
	 * and a shore bank at water level + 1 m where the ground would lie lower. Thanks to this the water
	 * is always surrounded by land and the function stays continuous.
	 */
	private static double applyLake(double surface, LakeHit lake) {
		double s = lake.shoreDistance;
		if (s < 0) {
			double inner = Noise.smoothstep(0, 60, -s);
			double bottom = lake.level - 0.5 - lake.depthBelowLevel * inner;
			return Math.min(surface, bottom);
		}
		double flank = lake.level + 1.0 + lake.slope * Math.max(0, s - 5)
				+ 5_000.0 * Noise.smoothstep(0.7 * lake.bank, lake.bank, s);
		double result = Math.min(surface, flank);
		double raise = 1.0 - Noise.smoothstep(15, 40, s);
		if (result < lake.level + 1 && raise > 0) {
			result = Math.max(result, Noise.lerp(raise, result, lake.level + 1.0));
		}
		return result;
	}
}
