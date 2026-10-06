package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;

/**
 * Procedural landscape model of Poland at 1:1 scale (1 unit = 1 meter).
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
	/** Offsets of the 3 × 3 blend window in the order of {@link #blend}, coded as (dx + 2) · 5 + dz + 2. */
	private static final byte[] OFFSETS_3X3 = {6, 7, 8, 11, 12, 13, 16, 17, 18};
	/** Cutoff of the blend weights: a cell farther than the nearest one by this many τ gets no weight (e⁻⁹). */
	private static final double BLEND_CUTOFF = 9.0;
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

	/**
	 * Macroregion cells (round 1 of the review of K6: a lock-free {@link DirectCache} instead of a
	 * {@code ConcurrentHashMap<Long, Cell>}, whose boxed keys cost about 2% of {@code sample} at gameplay scale).
	 */
	private final DirectCache<Cell> cells = new DirectCache<>(16_384, this::buildCell);
	private final ConcurrentHashMap<Long, Integer> lakeLevels = new ConcurrentHashMap<>();
	/** Round 2 of the review of K5: water level and basin reach of the tunnel valley lakes per traced contour. */
	private final ConcurrentHashMap<Long, TunnelLake> tunnelLakes = new ConcurrentHashMap<>();
	/** Kettle state: {@link #KETTLE_NONE}, {@link #KETTLE_MINEROTROPHIC} or {@link #KETTLE_OMBROTROPHIC}. */
	private final ConcurrentHashMap<Long, Byte> kettleExistence = new ConcurrentHashMap<>();
	/** Review of K5: traced contours of the tunnel valleys per section and block of x ({@link #buildTunnelBlock}). */
	private final DirectCache<TunnelBlock> tunnelBlocks = new DirectCache<>(256, this::buildTunnelBlock);
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

	/** Mean macroregion size in meters. */
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

	/** Approximate distance from the shoreline in meters: positive on land, negative at sea. */
	public double coastDistance(double x, double z) {
		double c = seaField(x, z);
		double e = Math.max(20.0, 400.0 * zs);
		double gx = (seaField(x + e, z) - seaField(x - e, z)) / (2 * e);
		double gz = (seaField(x, z + e) - seaField(x, z - e)) / (2 * e);
		double g = Math.sqrt(gx * gx + gz * gz);
		return c / Math.max(g, 1e-12);
	}

	/** Beach width in meters. */
	private double beachWidth() {
		return 60.0 * local;
	}

	/**
	 * Coast shape: sea floor, beach, foredune and coastal dunes on a low shore, a cliff where
	 * a plateau reaches the sea, and in places a lagoon (coastal lake) behind a spit.
	 *
	 * <p>D5 (step K5b): most of the coast is a low shore: a beach of 60 m·k, a white foredune 6–15 m high, a belt of gray
	 * dunes (hummocks of 2–8 m) and a hinterland low above the sea that rises to the compressed relief only several
	 * km·meso inland ({@link #COAST_LOW_END}). A cliff (the compressed relief with a seaward wall of 2.5 : 1) only where a
	 * moraine plateau reaches the sea, on part of it ({@link #cliffShore}: about 20% of the coast, as on the Polish coast).
	 * M1 gave every shore whose compressed relief was above 6–20 m (nearly all) a flat strip 13–24 m above the sea with a
	 * 2.5 : 1 wall, and dunes on 13–16% of the coast. The shape itself changes only within the belt of 25 km·meso, but
	 * it is part of {@link #landElevation}, from which {@link RiverNetwork} routes the rivers and limits the levels of its
	 * nodes by the lowest terrain on their path: the low hinterland (and the lagoons) lower the levels of the rivers that
	 * flow to a low shore, and the change reaches far upstream and moves some rivers (step K5: about 20% of the land of
	 * GAMEPLAY more than 6 km·meso from the sea changed by more than 5 cm, up to 175 m; in REAL about 2% near the lagoons).
	 * Whether to keep this is an open decision of the user (docs/m2/poprawka-geometrii.md, K5).
	 *
	 * <p>D2 (step K5): the lagoon only behind a low shore: its strength fades out where the hinterland rises from 3 to 6 m,
	 * its width does not depend on the shore type, and towards the ends of the lagoon along the shore its strength fades to
	 * zero while its basin keeps half its width, so the water narrows to a tip (M1: the width and the depth fell together
	 * with the shore type, and a minimum depth of 1 m cut a trench 130 m wide with walls into a shore 17 m high). The
	 * whole basin fades with its strength, so the terrain returns continuously to the shore around it.
	 *
	 * @param h     terrain height from the landscape types
	 * @param d     distance from the shoreline (positive on land)
	 * @param cliff share of a high shore with a cliff 0–1 ({@link #cliffShore})
	 */
	private double shapeCoast(double h, double d, double x, double z, double cliff) {
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
		double low = 1 - cliff;
		// Low hinterland: COAST_LOW_BASE m above the sea behind the dunes, rising to the compressed relief.
		double lowLand = Math.min(hl, COAST_LOW_BASE + (hl - COAST_LOW_BASE)
				* Noise.smoothstep(beach + COAST_GRAY_END * local, COAST_LOW_END * meso, d));
		double base = Noise.lerp(cliff, lowLand, hl);
		double cliffLimit = shore + 2.5 * Math.max(0, d - beach);
		double result = Math.max(Math.min(base, cliffLimit), shore);
		if (low <= 0) {
			return result;
		}
		// White foredune right behind the beach, 6–15 m high along the coast.
		double u = (d - beach) / (COAST_FOREDUNE * local);
		if (u > 0 && u < 1) {
			double height = 6 + 9 * (0.5 + 0.5 * coast.at(x, z, 3_000 * meso));
			double bump = Math.sin(Math.PI * u);
			// A smooth maximum (rounding of up to 1 m · bump) instead of max(): where a foredune grows out of the lowered
			// top of a partial cliff it left a sharp crease (review of K5).
			double dune = shore + low * height * bump * bump;
			double round = 2 * bump;
			result = 0.5 * (result + dune + Math.sqrt((result - dune) * (result - dune) + round * round));
		}
		// Gray dunes: hummocks of 2–8 m behind the foredune.
		double g0 = beach + 0.6 * COAST_FOREDUNE * local;
		double g1 = beach + COAST_GRAY_END * local;
		if (d > g0 && d < g1) {
			double env = Noise.smoothstep(g0, g0 + 60 * local, d) * (1 - Noise.smoothstep(g1 - 120 * local, g1, d));
			double hummock = Math.max(0, coast.at(x + 3_331, z - 1_777, 140 * local));
			double height = (2 + 6 * (0.5 + 0.5 * coast.at(x - 2_222, z + 4_444, 2_000 * meso))) * hummock;
			result = Math.max(result, lowLand + low * env * height);
		}
		// Lagoon behind a spit: a shallow lake at sea level, behind the dunes, only where the hinterland is low.
		double lagoon = Noise.smoothstep(COAST_LAGOON_0, COAST_LAGOON_1, coast.at(x + 999, z, 60_000 * meso));
		if (lagoon > 0) {
			double start = beach + COAST_LAGOON_START * local;
			// The width does not depend on the shore type; towards the ends of the lagoon it falls to half while the
			// depth falls to zero, so the water ends in a rounded tip; the landward shore is irregular (bays, peninsulas).
			double width = (1_500 + 1_500 * (0.5 + 0.5 * coast.at(x, z, 20_000 * meso))) * meso * (0.5 + 0.5 * lagoon);
			double ragged = 0.18 * width * coast.fbm(x - 555, z + 777, 2_500 * meso, 2, 0.5);
			double v = (d - start + ragged) / Math.max(1e-6, width);
			if (v > 0 && v < 1) {
				// The whole basin fades out with its strength f (not only its depth): at f → 0 the terrain returns to the
				// result itself, so the end of a lagoon along the shore and its edge towards a higher hinterland are
				// continuous (review of K5: lerp(bowl, result, −depth) left (1 − bowl) · result at depth → 0 and a step back
				// to the result where the depth or the lagoon noise reached zero).
				double bowl = Math.sin(Math.PI * v);
				double f = lagoon * low * (1 - Noise.smoothstep(3, 6, base));
				result = Math.min(result, result - bowl * f * (Math.max(0, result) + COAST_LAGOON_DEPTH));
			}
		}
		return result;
	}

	/**
	 * D5: share of a high shore with a cliff (0–1) at (x, z): only on a moraine plateau (its weight from 0.3 to 0.7) and
	 * where a coastal noise with a wavelength of 30 km·meso lies in its upper quantiles ({@link #COAST_CLIFF_Q0} to
	 * {@link #COAST_CLIFF_Q1}); the young-glacial plateau reaches the sea on about 3/4 of the coast, so about a fifth of
	 * the coast gets a cliff. A function of the position only (the same in the terrain and in {@link ColumnSample.Terrain#lowShore}).
	 */
	private double cliffShore(double x, double z, Blend b) {
		double plateau = Noise.smoothstep(0.3, 0.7, b.weight(LandscapeType.MORAINE_PLATEAU));
		if (plateau <= 0) {
			return 0;
		}
		double q = noiseQuantile(coast.at(x - 4_321, z + 1_234, 30_000 * meso));
		return plateau * Noise.smoothstep(COAST_CLIFF_Q0, COAST_CLIFF_Q1, q);
	}

	/** D5: quantiles of the coastal noise over which a moraine plateau shore turns into a cliff ({@link #cliffShore}). */
	static final double COAST_CLIFF_Q0 = 0.62;
	static final double COAST_CLIFF_Q1 = 0.78;
	/** D5: hinterland of a low shore behind the dunes (m above the sea). */
	static final double COAST_LOW_BASE = 1.5;
	/** D5: the low hinterland reaches the compressed relief at this distance from the sea (m·meso). */
	public static final double COAST_LOW_END = 6_000;
	/** D5: width of the white foredune (m·k) behind the beach. */
	static final double COAST_FOREDUNE = 130;
	/** D5: end of the gray dunes behind the beach (m·k). */
	static final double COAST_GRAY_END = 420;
	/** D2: start of the lagoon behind the beach (m·k). */
	static final double COAST_LAGOON_START = 300;
	/** D2: values of the lagoon noise (wavelength 60 km·meso) over which a lagoon fades in along the shore. */
	static final double COAST_LAGOON_0 = 0.25;
	static final double COAST_LAGOON_1 = 0.5;
	/** D2: greatest depth of a lagoon below the sea level (m). */
	static final double COAST_LAGOON_DEPTH = 6;

	/** Sea depth in meters at distance {@code off} from the shore (Baltic: shallow shelf). */
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

	/** Regional lowland base level in meters a.s.l. (about 70–190 m), varying very gently. */
	public double lowlandBaseline(double x, double z) {
		return 130.0 + 55.0 * lowlandBase.fbm(x, z, 140_000 * meso, 2, 0.5);
	}

	// ------------------------------------------------------------------ L1: macroregions

	/** Landscape type of a macroregion cell (after applying the adjacency rules). */
	public LandscapeType regionType(long cx, long cz) {
		return cell(cx, cz).type();
	}

	public Cell cell(long cx, long cz) {
		return cells.get(cx, cz);
	}

	/** A macroregion cell, computed (a pure function of its coordinates, cached by {@link #cell}). */
	private Cell buildCell(long cx, long cz) {
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
		return new Cell(cx, cz, type, px, pz, cos, sin);
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

	/** Cell center in the (warped) lookup space. */
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
	 * so the surface has no creases along the Voronoi bisectors. The window is 5 × 5 cells (A16, step K6): with the
	 * 3 × 3 window of M1 a cell of the second ring with a small weight (at gameplay scale τ = 127 m against 1400 m
	 * cells) dropped out where the window moved, a seam of up to 4.5 m in {@link #landElevation} near the Beskids. The
	 * second ring is read only where one of its cells can be within the cutoff (exact, see below).
	 */
	public Blend blend(double x, double z) {
		double lx = warpX(x, z);
		double lz = warpZ(x, z);
		long gx = (long) Math.floor(lx / regionSize);
		long gz = (long) Math.floor(lz / regionSize);
		// First the distances to the cell centers (in the result array w), then only the cells with a significant weight
		// remain in the same arrays, in the same order (n ≤ i, so we overwrite only slots that have already been read).
		// Round 1 of the review of K6: a cell is looked up in the cache (its type) only when it gets a weight; the
		// distance needs only its center, a hash of its coordinates (regionCenterX/Z, the same as Cell.centerX/Z). At
		// realistic scale most cells of the 3 × 3 window lie past the cutoff.
		Cell[] used = new Cell[9];
		double[] w = new double[9];
		byte[] offsets = OFFSETS_3X3;
		double min = Double.MAX_VALUE;
		int i = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++, i++) {
				double ddx = lx - regionCenterX(gx + dx, gz + dz);
				double ddz = lz - regionCenterZ(gx + dx, gz + dz);
				w[i] = Math.sqrt(ddx * ddx + ddz * ddz);
				min = Math.min(min, w[i]);
			}
		}
		// A16 (step K6): the second ring of the 5 × 5 window. A cell center lies at 0.15–0.85 of its cell along each
		// axis, which bounds the distance to every cell from below (ringBound). A cell whose bound is past the cutoff of
		// the weights (min + 9τ) would get no weight, so it is not looked up: the result is exactly the 5 × 5 one, and
		// the bound, though it changes from point to point, makes no seam (a skipped cell is weightless anyway). A
		// lower min found in the ring only makes the skip safer. A third ring never counts: its bound is at least 2.15
		// cells, the nearest center at most 0.85·√2 ≈ 1.2 cells away and 9τ at most 0.82 cells (τ ≤ 0.2 cells / 2.2).
		double fx = lx / regionSize - gx;
		double fz = lz / regionSize - gz;
		double reach = BLEND_CUTOFF * tau * (1 + 1e-9);
		if (regionSize * Math.min(Math.min(1.15 + fx, 2.15 - fx), Math.min(1.15 + fz, 2.15 - fz)) - min <= reach) {
			for (int dx = -2; dx <= 2; dx++) {
				double bx = ringBound(dx, fx);
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
						continue;
					}
					double bz = ringBound(dz, fz);
					if (regionSize * Math.sqrt(bx * bx + bz * bz) - min > reach) {
						continue;
					}
					if (i == used.length) {
						used = new Cell[25];
						w = Arrays.copyOf(w, 25);
						offsets = Arrays.copyOf(OFFSETS_3X3, 25);
					}
					double ddx = lx - regionCenterX(gx + dx, gz + dz);
					double ddz = lz - regionCenterZ(gx + dx, gz + dz);
					w[i] = Math.sqrt(ddx * ddx + ddz * ddz);
					offsets[i] = (byte) ((dx + 2) * 5 + dz + 2);
					min = Math.min(min, w[i]);
					i++;
				}
			}
		}
		int read = i;
		double[] tw = new double[TYPES.length];
		int n = 0;
		double total = 0;
		for (i = 0; i < read; i++) {
			double d = (w[i] - min) / tau;
			if (d < BLEND_CUTOFF) {
				double wi = Math.exp(-d);
				used[n] = cell(gx + offsets[i] / 5 - 2, gz + offsets[i] % 5 - 2);
				w[n] = wi;
				total += wi;
				n++;
			}
		}
		for (i = n; i < read; i++) {
			used[i] = null;
			w[i] = 0;
		}
		for (i = 0; i < n; i++) {
			w[i] /= total;
			tw[used[i].type().ordinal()] += w[i];
		}
		return new Blend(used, w, n, tw);
	}

	/** Warped x of the region grid at a point ({@link #blend}, {@link #secondRingWeight}). */
	private double warpX(double x, double z) {
		return x + 0.22 * regionSize * regionWarp.fbm(x, z, 0.7 * regionSize, 2, 0.5);
	}

	/** Warped z of the region grid at a point ({@link #blend}, {@link #secondRingWeight}). */
	private double warpZ(double x, double z) {
		return z + 0.22 * regionSize * regionWarp.fbm(x + 12_345, z - 6_789, 0.7 * regionSize, 2, 0.5);
	}

	/**
	 * Lower bound, in cells, of the distance along one axis from a point at {@code f} (0 ≤ f &lt; 1) of its cell to the
	 * center of the cell {@code d} cells away (centers lie at 0.15–0.85 of their cell).
	 */
	private static double ringBound(int d, double f) {
		return d < 0 ? Math.max(0, f - d - 0.85) : d > 0 ? Math.max(0, d + 0.15 - f) : Math.max(0, Math.max(f - 0.85, 0.15 - f));
	}

	/**
	 * Total blend weight of the cells of the second ring of the 5 × 5 window at a point (A16, step K6): where it is
	 * positive, the 3 × 3 window of M1 gave a different height (for tests).
	 */
	double secondRingWeight(double x, double z) {
		long gx = (long) Math.floor(warpX(x, z) / regionSize);
		long gz = (long) Math.floor(warpZ(x, z) / regionSize);
		Blend b = blend(x, z);
		double sum = 0;
		for (int i = 0; i < b.count(); i++) {
			Cell c = b.cells()[i];
			if (Math.abs(c.cx() - gx) == 2 || Math.abs(c.cz() - gz) == 2) {
				sum += b.weights()[i];
			}
		}
		return sum;
	}

	/** Landscape type weights at a point (sum = 1). */
	public double[] typeWeights(double x, double z) {
		return blend(x, z).typeWeights();
	}

	// ------------------------------------------------------------------ L2: relief

	/** Ground height of a cell without waters, in meters a.s.l. */
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
	 * It is not measured from the cell center: with small cells (gameplay scale) every cell
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
		Blend b = blend(x, z);
		return shapeCoast(elevation(b, x, z), coastDistance(x, z), x, z, cliffShore(x, z, b));
	}

	/** Full column sample: relief, waters, substrate. */
	public ColumnSample sample(double x, double z) {
		Blend b = blend(x, z);
		ReliefParts parts = new ReliefParts();
		double raw = elevation(b, x, z, parts);
		double coastD = coastDistance(x, z);
		double cliff = coastD < 25_000 * meso ? cliffShore(x, z, b) : 0;
		double surface = shapeCoast(raw, coastD, x, z, cliff);
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
					terrain(b, parts, t, raw, rawSurface, coastD, cliff, 0, Double.NaN, Double.NaN, 0, x, z), ColumnSample.Waters.NONE,
					regional.sample(x, z));
		}
		double bare = Double.NaN;
		if (coastD < beachWidth() + 400 * local) {
			dominant = LandscapeType.COASTLAND;
			// Beach and white dune without turf; further from the sea the vegetated gray dune.
			bare = beachWidth() + 220 * local * (0.6 + 0.4 * coast.at(x, z, 400 * local));
			// D5: till only on a high shore (a cliff); the dunes of a low shore are sand also above 8 m.
			substrate = surface > 8 && cliff >= 0.5 ? Substrate.GLACIAL_TILL : coastD < bare ? Substrate.BEACH_SAND : Substrate.SAND;
		}

		// Valleys and channels of the river network, and sink lakes.
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
			surface = applyLake(surface, sinkLakeHit(r));
			if (r.lakeShore() < 0 && surface < r.lakeLevel()) {
				water = r.lakeLevel();
				kind = WaterKind.LAKE;
				substrate = Substrate.LAKE_MUD;
			}
		}

		// Glacial tunnel valleys with chains of lakes (young-glacial zone only). K5.2: next to a river valley or a sink
		// lake a lake ends in a rounded shore (its width fades out with the distance from the edge of the valley cut or from
		// the shore of the sink lake, tunnelLakeAt), not in a straight line; the presence no longer fades with the valley
		// weight (M1), which cut the lakes straight along the contours of the weight and left perched lakes above valleys.
		double tunnelPresence = tunnelPresence(young, coastD);
		if (tunnelPresence > 0.05 && kind == WaterKind.NONE) {
			LakeHit lake = tunnelLakeAt(x, z, tunnelPresence, r.floorGap(), r.lakeGap());
			if (lake != null) {
				// The habitat ring keeps its width (max(tunnelBank, 150 m·k)) when the basin reaches further (round 2).
				if (lake.shoreDistance < standingShore && lake.shoreDistance <= Math.max(tunnelBank, 150 * local)) {
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
		// at its center, so we check them in every column without water.
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
				double before = surface;
				surface = applyLake(surface, k);
				if (k.shoreDistance < 0 && surface < k.level) {
					if (k.peat) {
						// K5.4: the peat lies at the level − 0.5 m, but never above the ground before the basin, so a bog
						// whose shore runs through a hollow lower than its level (missed by the points of the level) has no
						// step there; the peat follows the hollow.
						surface = Math.min(k.level - 0.5, before);
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
						r.floorChannelLevel(), r.floorChannelGradient());
		int landformBits = forms(r, parts, dominant, surface, rawSurface, coastD, water);
		// Large massif (E12): highest terrain within 3 km·mspace, only where the altitudinal belts need it.
		double summit = mountains > 0 && surface >= AltitudinalBelts.SUMMIT_FROM ? peaks.sample(x, z) : 0;
		return new ColumnSample(surface, water, kind, dominant, substrate, cover,
				terrain(b, parts, dominant, raw, rawSurface, coastD, cliff, landformBits, sandiness(x, z), bare, summit, x, z),
				waters,
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
			// face), and not e.g. on the shore of a lagoon several kilometers from the sea.
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
			double coastD, double cliff, int landformBits, double sandiness, double bare, double summit, double x, double z) {
		// Cliff edge: terrain before cutting valleys, in the CLIFF landform belt.
		double cliffHeight = (landformBits & Landform.CLIFF.bit()) != 0 ? rawSurface : 0;
		// Coastal belt: 1 where the sample gets the COASTLAND type, 0 at the edge of the belt B + D + 2000k (M2 plan §3.4).
		double coastBand = 1 - Noise.smoothstep(beachWidth() + 400 * local, beachWidth() + 2_220 * local, coastD);
		// Flysch profile of the cell used by the landform description; outside the mountains the flysch cell with the largest weight.
		// Near the sea shapeCoast compresses the whole relief (h · (0.12 + 0.88 smoothstep(0, 25 km·meso, cD))), so
		// convexity and dune height are scaled the same way; the INLAND_DUNES bit in the landforms is computed without scaling (as in M1).
		double band = 25_000 * meso;
		double coastScale = coastD >= band ? 1.0 : 0.12 + 0.88 * Noise.smoothstep(0, band, coastD);
		// Low shore with dunes or high shore with a cliff: the same share as in shapeCoast (D5, cliffShore).
		double low = coastD >= 0 && coastD < band ? 1 - cliff : 0;
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
	 * @param shoreDistance distance from the shoreline in meters, negative in the lake
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

	/** Presence of the tunnel valleys: the young-glacial zone away from the coast (K5.2: without the valley weight). */
	private double tunnelPresence(double young, double coastD) {
		return young * Noise.smoothstep(0, 1_500 * meso, coastD);
	}

	/**
	 * Glacial tunnel valley with a chain of lens-shaped lakes. A lake has a constant water level; isthmuses
	 * remain between the lakes. Some sections of the tunnel valley are dry.
	 *
	 * <p>K5.2: every fade is smooth (0..1) instead of a cut: the ends of the lens at the boundaries between the lakes of a
	 * chain and at the edge of the cut of a river valley or at the shore of a sink lake ({@link #ellipticEnd}: rounded ends),
	 * tunnel valleys oblique to the north-south axis and too narrow lakes (M1: null below 25 m·k). The water level is
	 * computed from a canonical point of the lake, a function of its key only (M1: the level depended on the order of
	 * sampling by up to 2 m).
	 *
	 * <p>Review of K5: a lake belongs to a contour of the tunnel field traced through its section once per section and
	 * block of x ({@link TunnelBlock}); the column takes the nearest traced contour. M1 and the first version of K5 found
	 * the lake by Newton steps in x at the middle of the section started from the column's own x, so where the contour
	 * curved between the column and the middle of the section the columns of one lake converged to different roots, which
	 * made stripes north to south with different keys and water levels (A1; in GAMEPLAY up to 68 m apart, with leaks).
	 * Where two traced contours are about equally near, or where a trace ends, the lake fades out, so water bodies with
	 * different keys are always separated by land. The basin fades with the lake as a shore distance that grows smoothly
	 * (slope about 1) instead of a bank of {@code tunnelBank} added over a few meters, which left walls of up to 36 m at
	 * the ends of the lakes; beyond an end of the lens (the boundary of the section, a valley, a sink lake) the shore
	 * distance grows like the distance from the rounded tip of the water.
	 *
	 * <p>Round 2 of the review of K5: the water level comes from the ground along the lake itself and the reach of the
	 * basin grows with its cut ({@link #tunnelLake}); a lake with the level below 1 m does not exist; near another contour,
	 * an end of the trace or an oblique stretch of the contour ({@link #TUNNEL_COS}) the half-width is limited by the
	 * distance itself (it changes by at most about 1 m per meter), and a lake with a wider basin ends that much further
	 * from a river valley.
	 *
	 * @param valleyGap distance beyond the edge of the cut of the nearest river valley ({@code RiverHit.floorGap})
	 * @param lakeGap   distance from the shore of the nearest sink lake, capped ({@code RiverHit.lakeGap})
	 */
	private LakeHit tunnelLakeAt(double x, double z, double presence, double valleyGap, double lakeGap) {
		// Query reach: the basin (at most tunnelBankMax) or the 150 m·k habitat ring, whichever is wider; then the reach of
		// this lake's own basin.
		TunnelShape t = tunnelShape(x, z, presence, valleyGap, lakeGap, Math.max(tunnelBankMax(), 150 * local));
		if (t == null || t.shore > Math.max(t.lake.bank, 150 * local)) {
			return null;
		}
		double depth = t.lens * t.gate * (18 + 45 * tunnel.unit(t.anchor, t.k, 7));
		// Near a sink lake the reach of the basin falls back to tunnelBank (by 1 m per meter of the gap, which is capped at
		// LAKE_GAP_MAX), so a wider basin never reaches further towards the sink lake than in K5 (at a river valley the lake
		// ends further away instead, tunnelShape).
		double bank = Math.clamp(t.lake.bank + lakeGap - TUNNEL_END_GAP * local, tunnelBank, t.lake.bank);
		return new LakeHit(t.lake.level, depth, t.shore, false, 0.35, bank,
				ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, t.key, t.half * t.lens, false);
	}

	/** Geometry of a tunnel valley lake at a column ({@link #tunnelShape}). */
	private record TunnelShape(double shore, double half, double lens, double gate, long k, long anchor, long key,
			TunnelLake lake) {
	}

	/**
	 * Round 2 of the review of K5: the water level of a tunnel valley lake and the reach of its basin
	 * ({@link #tunnelLake}).
	 */
	private record TunnelLake(int level, double bank) {
	}

	/**
	 * Shape of the tunnel valley lake at (x, z): its shore distance, half-width, lens and key, or null when the shore
	 * distance is larger than {@code limit} (also when there is no traced contour near). Pure geometry, without the
	 * water level, so the kettle ponds can test it cheaply ({@link #kettleTouchesTunnelLake}). The shore distance never
	 * falls with a larger presence or larger gaps, so (1, +∞, +∞) gives a lower bound of it.
	 */
	private TunnelShape tunnelShape(double x, double z, double presence, double valleyGap, double lakeGap, double limit) {
		// Beyond an end of the lens (a valley, a sink lake): the shore distance is at least the distance beyond the end.
		double beyond = Math.max(TUNNEL_END_GAP * local - valleyGap, TUNNEL_END_GAP * local - lakeGap);
		if (beyond > limit) {
			return null;
		}
		double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso)) * Noise.smoothstep(0.2, 0.7, presence);
		if (gate <= 0) {
			return null;
		}
		long k = (long) Math.floor(z / tunnelCell);
		if (z < tunnelBoundary(k, x)) {
			k--;
		} else if (z >= tunnelBoundary(k + 1, x)) {
			k++;
		}
		TunnelBlock block = tunnelBlocks.get(k, Math.floorDiv((long) Math.floor(x), (long) tunnelBlockWidth()));
		// The nearest traced contour (a lake) and the distance to the second nearest one, both measured along x
		// (a trace beyond its end counts as its end point, so the nearest contour changes continuously).
		double e1 = Double.POSITIVE_INFINITY;
		double e2 = Double.POSITIVE_INFINITY;
		TunnelContour c1 = null;
		TunnelContour c2 = null;
		for (TunnelContour c : block.contours) {
			double e = c.distance(x, z);
			if (e < e1) {
				e2 = e1;
				c2 = c1;
				e1 = e;
				c1 = c;
			} else if (e < e2) {
				e2 = e;
				c2 = c;
			}
		}
		if (c1 == null || !c1.lake || e1 > tunnelRelevant()) {
			return null;
		}
		// Round 2 of the review of K5: no lake at or below the sea level. Its ring then reaches a lagoon or the low
		// hinterland of a low shore, and its water stood next to the lagoon at another level, or next to dry ground below
		// the sea (GAMEPLAY beyond ±20 km, leaks of up to 17 m). The level is one per lake, so the lake is whole or absent.
		TunnelLake lake = tunnelLake(c1, k);
		if (lake.level < 1) {
			return null;
		}
		double bank = lake.bank;
		// The lake ends so far from a river valley that its basin does not reach into the cut of the valley (the shore
		// distance there is at least the reach of the basin). K5 let the basin reach up to tunnelBank − TUNNEL_END_GAP into
		// the cut; with the level from the lowest ground along the lake (round 2) it lowered the floor next to the channel
		// there (a river 17 m above the ground beside it, GAMEPLAY).
		double valleyEnd = TUNNEL_END_GAP * local + bank - 0.5 * tunnelBank;
		beyond = Math.max(beyond, valleyEnd - valleyGap);
		double u = (z - c1.z0) / c1.dz;
		if (u < c1.lo || u > c1.hi) {
			return null;
		}
		double slope = c1.slope(u);
		double cosA = 1 / Math.sqrt(1 + slope * slope);
		double dist = e1 * cosA;
		double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
		if (dist - half + beyond > limit) {
			return null;
		}
		double b0 = tunnelBoundary(k, x);
		double b1 = tunnelBoundary(k + 1, x);
		double edge = Math.min(z - b0, b1 - z);
		beyond = Math.max(beyond, tunnelSill - edge);
		// Length of the end at a valley: 1.5 half-widths (a semicircle at one half-width), at most TUNNEL_END_MAX m·k, so
		// the end is complete within the frame of the segments of the river network (RiverHit.floorGap is at least about
		// 0.5 maxWall beyond it); at a sink lake at most LAKE_GAP_MAX − TUNNEL_END_GAP m·k (the gap is capped there).
		double endLength = Math.clamp(1.5 * half, 150 * local, TUNNEL_END_MAX * local - (bank - 0.5 * tunnelBank));
		double lakeEnd = Math.min(Math.clamp(1.5 * half, 150 * local, TUNNEL_END_MAX * local),
				(RiverNetwork.LAKE_GAP_MAX - TUNNEL_END_GAP) * local);
		double lensEnd = ellipticEnd((edge - tunnelSill) / (0.35 * (b1 - b0)))
				* ellipticEnd((valleyGap - valleyEnd) / endLength)
				* ellipticEnd((lakeGap - TUNNEL_END_GAP * local) / lakeEnd);
		// The other fades: oblique tunnel valleys, too narrow lakes, another traced contour about as near, the end of the
		// trace. Each grows the shore distance by up to the reach of the basin over at least about 1.5 times that reach, so
		// the basin fades out with the lake without a wall (and without a trench along the axis of a dry tunnel valley).
		double tunnelFade = c1.run(u);
		// The free gap to the second contour across the contours (each distance along x times the cosine of its own
		// contour, clamped at the ends of its trace): it changes by at most about 2 m per meter in any direction, while the
		// difference along x changed by up to 2 · TUNNEL_TRACE_SLOPE per meter along z (round 2 of the review of K5).
		double gap = c2 == null ? Double.POSITIVE_INFINITY : e2 * c2.cosine(z) - dist;
		double gapFade = Noise.smoothstep(TUNNEL_SPLIT, TUNNEL_SPLIT + 3 * bank, gap);
		double traceFade = Noise.smoothstep(0, 1.5 * bank, tunnelFade);
		double shapeFade = Noise.smoothstep(25 * local, 60 * local, half);
		double lensFade = shapeFade * gapFade * traceFade;
		// Round 2 of the review of K5: near another contour and near the end of the trace the half-width is limited by the
		// distance itself (half the free gap beyond TUNNEL_SPLIT, the distance from the end of the trace) instead of being
		// scaled by the fades, so it changes by at most about 1 m per meter. Scaled by the fades, a half-width of up to
		// 350 m (GAMEPLAY) fell to zero over 3 or 1.5 tunnelBank, the shore distance changed by 5–8 m per meter, and the
		// ramp of the basin (0.4–1.0 of its reach) became a wall of up to 5 blocks per block on a smooth terrain.
		double h = Math.min(half * shapeFade, Math.min(0.5 * Math.max(0, gap - TUNNEL_SPLIT), tunnelFade));
		// Elliptic shore distance: the water is dist < h · lensEnd (a rounded end, ellipticEnd); towards the tip the
		// distance grows with slope at most about 1 also along the axis. Beyond an end it keeps growing with the distance.
		double tau2 = 1 - lensEnd * lensEnd;
		double shore = beyond > 0 ? Math.sqrt(dist * dist + h * h) - h + beyond
				: Math.sqrt(dist * dist + h * h * tau2) - h;
		shore += (1 - lensFade) * bank;
		if (shore > limit) {
			return null;
		}
		long anchor = c1.anchor;
		return new TunnelShape(shore, half, lensEnd * Math.min(lensFade, h / half), gate, k, anchor, c1.key, lake);
	}

	/**
	 * Review of K5: whether the influence of a kettle pond (center, reach) meets the bank of a tunnel valley lake (shore
	 * distance below {@link #TUNNEL_KEEP}, where {@link #applyLake} raises the ground towards the water level + 1 m): a
	 * grid over the disk of the kettle tests a lower bound of the shore distance (presence 1 and no valley or sink lake).
	 * Such a kettle does not exist ({@link #kettleState}), so a kettle never cuts the bank of a tunnel valley lake and
	 * never has its water or peat next to the water of the lake (review of K5: leaks of up to 57 m). A kettle may still lie
	 * on the outer flank of the basin; its level then comes from the surface after the basin ({@link #surfaceBeforeKettle}).
	 * Computed once per kettle.
	 */
	private boolean kettleTouchesTunnelLake(double kx, double kz, double reach) {
		double step = 10 * local;
		// The shore distance grows by at most about 3 per meter (the distance from the contour, the limits of the
		// half-width and the fade of the basin by 1 each, round 2 of the review of K5), so a center far enough is enough.
		if (tunnelShape(kx, kz, 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				TUNNEL_KEEP + 3 * (reach + step)) == null) {
			return false;
		}
		double limit = TUNNEL_KEEP + 2.2 * step;
		int n = (int) Math.ceil((reach + step) / step);
		for (int j = -n; j <= n; j++) {
			for (int i = -n; i <= n; i++) {
				double dx = i * step;
				double dz = j * step;
				if (dx * dx + dz * dz <= (reach + step) * (reach + step)
						&& tunnelShape(kx + dx, kz + dz, 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, limit) != null) {
					return true;
				}
			}
		}
		return false;
	}

	/** Review of K5: the bank of a tunnel valley lake that no kettle pond may touch: the reach of its raise (m). */
	private static final double TUNNEL_KEEP = 40;

	/** Review of K5: water bodies of two traced contours are separated by land at least this wide (m). */
	private static final double TUNNEL_SPLIT = 40;

	/**
	 * Round 2 of the review of K5: the largest reach of the basin of a tunnel valley lake ({@link #tunnelLake}): at most
	 * TUNNEL_BANK_MAX tunnelBank and below the sill between the lakes of a chain, where the shore distance is at least
	 * tunnelSill, so the basins of two lakes with different levels never meet at the boundary of their sections.
	 */
	private double tunnelBankMax() {
		return Math.min(TUNNEL_BANK_MAX * tunnelBank, 0.9 * tunnelSill);
	}

	/** Round 2 of the review of K5: the reach of a tunnel valley basin is at most this many tunnelBank. */
	private static final double TUNNEL_BANK_MAX = 3;
	/** Round 2 of the review of K5: the reach of a tunnel valley basin per meter of its cut (m/m). */
	private static final double TUNNEL_BANK_PER_CUT = 1.2;

	/** Width of a block of traced contours (m): two sections. */
	private double tunnelBlockWidth() {
		return 2 * tunnelCell;
	}

	/** Farthest traced contour (along x) that can still matter for a column (m). */
	private double tunnelRelevant() {
		// The second contour matters up to TUNNEL_SPLIT + 3 tunnelBank beyond the first (the fade of the basin) and up
		// to TUNNEL_SPLIT + 2 · 700 m·k (the limit of the half-width by the free gap, round 2 of the review of K5).
		// The gap is measured across the contours, so along x a contour can be up to sqrt(1 + TUNNEL_TRACE_SLOPE²) times
		// farther away than across.
		double across = 700 * local + Math.max(tunnelBankMax(), 150 * local) + TUNNEL_SPLIT
				+ Math.max(3 * tunnelBankMax(), 1_400 * local) + 50 * local;
		return across * Math.sqrt(1 + TUNNEL_TRACE_SLOPE * TUNNEL_TRACE_SLOPE);
	}

	/**
	 * Review of K5: a contour of the tunnel field (field = 0) traced through a section: it crosses the middle of the
	 * section z = (k + 0.5) · tunnelCell at {@code root}; x and the slope dx/dz at the nodes z0 + i · dz for i in [lo, hi].
	 * Beyond that range (the contour turned east-west or drifted too far) the trace ends.
	 */
	private static final class TunnelContour {
		final double z0;
		final double dz;
		final double[] xs;
		final double[] slopes;
		final int lo;
		final int hi;
		final long anchor;
		final long key;
		final boolean lake;
		/** Key of the water level of this contour's lake: from its root, the same in every block that traces it. */
		final long levelKey;
		/**
		 * Round 2 of the review of K5: at the nodes, the distance along z (m) to the nearest end of the trace or the nearest
		 * node where the contour runs more than about 39° from north ({@link #TUNNEL_COS}); 0 there.
		 */
		final double[] runs;

		TunnelContour(double z0, double dz, double[] xs, double[] slopes, int lo, int hi, long anchor, long key, boolean lake,
				long levelKey) {
			this.levelKey = levelKey;
			this.runs = new double[xs.length];
			double last = lo;
			for (int q = lo; q <= hi; q++) {
				if (q == lo || 1 / Math.sqrt(1 + slopes[q] * slopes[q]) < TUNNEL_COS) {
					last = q;
				}
				runs[q] = (q - last) * dz;
			}
			last = hi;
			for (int q = hi; q >= lo; q--) {
				if (q == hi || 1 / Math.sqrt(1 + slopes[q] * slopes[q]) < TUNNEL_COS) {
					last = q;
				}
				runs[q] = Math.min(runs[q], (last - q) * dz);
			}
			this.z0 = z0;
			this.dz = dz;
			this.xs = xs;
			this.slopes = slopes;
			this.lo = lo;
			this.hi = hi;
			this.anchor = anchor;
			this.key = key;
			this.lake = lake;
		}

		/** Distance along x from the trace at z; beyond the ends the distance from the end point. */
		double distance(double x, double z) {
			double u = (z - z0) / dz;
			if (u <= lo) {
				return Math.hypot(x - xs[lo], (lo - u) * dz);
			}
			if (u >= hi) {
				return Math.hypot(x - xs[hi], (u - hi) * dz);
			}
			int i = (int) Math.floor(u);
			double f = u - i;
			return Math.abs(x - (xs[i] + f * (xs[i + 1] - xs[i])));
		}

		/** {@link #runs} at u in [lo, hi], interpolated: it changes by at most 1 m per meter along z. */
		double run(double u) {
			int i = Math.min((int) Math.floor(u), hi - 1);
			if (i < lo) {
				return runs[lo];
			}
			double f = u - i;
			return runs[i] + f * (runs[i + 1] - runs[i]);
		}

		/** Cosine of the angle between the contour and the z axis at z, clamped to the ends of the trace. */
		double cosine(double z) {
			double s = slope(Math.clamp((z - z0) / dz, lo, hi));
			return 1 / Math.sqrt(1 + s * s);
		}

		/** Slope dx/dz at u in [lo, hi]. */
		double slope(double u) {
			int i = Math.min((int) Math.floor(u), hi - 1);
			if (i < lo) {
				return slopes[lo];
			}
			double f = u - i;
			return slopes[i] + f * (slopes[i + 1] - slopes[i]);
		}
	}

	/** Review of K5: the traced contours of one section and one block of x ({@link #buildTunnelBlock}). */
	private record TunnelBlock(TunnelContour[] contours) {
	}

	/**
	 * Traces the contours of the tunnel field that cross the middle of section k within the block bx (x from
	 * bx · tunnelBlockWidth) widened by the farthest relevant contour and the largest drift: the roots on the middle line
	 * from a fixed global grid of x (bisection), then Newton steps in x at every node in z (step 20 m·k) towards both
	 * ends of the section. A trace ends where the contour turns too far from north-south, where Newton does not
	 * converge or jumps, or where it drifts more than one section length from its root. A pure function of (k, bx): the
	 * same root and trace in every block that finds it, so neighboring blocks agree.
	 */
	private TunnelBlock buildTunnelBlock(long k, long bx) {
		double zc = (k + 0.5) * tunnelCell;
		double drift = tunnelCell;
		double margin = tunnelRelevant() + drift;
		double w = tunnelBlockWidth();
		double grid = 20 * local;
		long i0 = (long) Math.floor((bx * w - margin) / grid);
		long i1 = (long) Math.ceil(((bx + 1) * w + margin) / grid);
		double dz = 20 * local;
		int nHalf = (int) Math.ceil(0.8 * tunnelCell / dz);
		int n = 2 * nHalf + 1;
		double z0 = zc - nHalf * dz;
		List<TunnelContour> out = new ArrayList<>();
		double fPrev = tunnelField(i0 * grid, zc);
		for (long i = i0 + 1; i <= i1; i++) {
			double f = tunnelField(i * grid, zc);
			if ((f < 0) != (fPrev < 0)) {
				double lo = (i - 1) * grid;
				double hi = i * grid;
				boolean loNeg = fPrev < 0;
				for (int it = 0; it < 40; it++) {
					double mid = 0.5 * (lo + hi);
					if ((tunnelField(mid, zc) < 0) == loNeg) {
						lo = mid;
					} else {
						hi = mid;
					}
				}
				double root = 0.5 * (lo + hi);
				double[] xs = new double[n];
				double[] slopes = new double[n];
				xs[nHalf] = root;
				slopes[nHalf] = contourSlope(root, zc);
				int a = nHalf;
				int b = nHalf;
				if (Math.abs(slopes[nHalf]) <= TUNNEL_TRACE_SLOPE) {
					for (int dir = -1; dir <= 1; dir += 2) {
						double x = root;
						double s = slopes[nHalf];
						for (int q = nHalf + dir; q >= 0 && q < n; q += dir) {
							double z = z0 + q * dz;
							double xn = contourRoot(x + s * dz * dir, z);
							if (Double.isNaN(xn) || Math.abs(xn - x) > TUNNEL_TRACE_SLOPE * dz || Math.abs(xn - root) > drift) {
								break;
							}
							double sn = contourSlope(xn, z);
							if (Math.abs(sn) > TUNNEL_TRACE_SLOPE) {
								break;
							}
							x = xn;
							s = sn;
							xs[q] = x;
							slopes[q] = s;
							if (dir < 0) {
								a = q;
							} else {
								b = q;
							}
						}
					}
				}
				// The anchor as in K5: where the axis iteration from the root meets the middle of the section between
				// its irregular boundaries (the same lake and key as before wherever K5 found it consistently).
				long anchor = Math.round(tunnelAxis(root, k)[0] / (200.0 * local));
				boolean lake = a < b && tunnel.unit(anchor, k, 8) >= 0.3;
				out.add(new TunnelContour(z0, dz, xs, slopes, a, b, anchor, Noise.key(anchor, k, 2), lake,
						Noise.key(Double.doubleToLongBits(root), k, 12)));
			}
			fPrev = f;
		}
		return new TunnelBlock(out.toArray(new TunnelContour[0]));
	}

	/**
	 * The point where the axis of the tunnel valley crosses the middle of the section k: Newton steps in x started from
	 * x0, with 3 outer steps that move the middle of the section with x. It does not always converge, so it only names
	 * the lake of a traced contour (from its root; round 2: the level comes from the trace, {@link #tunnelLake}).
	 */
	private double[] tunnelAxis(double x0, long k) {
		double e = 40;
		double ax = x0;
		double az = 0.5 * (tunnelBoundary(k, ax) + tunnelBoundary(k + 1, ax));
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
		return new double[] {ax, az};
	}

	/**
	 * Round 2 of the review of K5: a lake lies only where its contour runs within about 39° of north-south (this cosine);
	 * other nodes end the lake like the end of the trace, and its half-width grows from there by at most 1 m per meter
	 * ({@link TunnelContour#runs}). K5 scaled the half-width by smoothstep(0.75, 0.92, cos), which on a curving contour
	 * changed a half-width of 240 m by 2 m per meter, a wall of 5 blocks per block in the basin (GAMEPLAY).
	 *
	 * <p>Step K6 tried 0.6 (about 53°): +13% water of tunnel valley lakes at gameplay scale, but the lakes of the oblique
	 * stretches mostly end at a river valley, where the end of the lens follows the gap beyond the valley cut in the
	 * column itself ({@code RiverHit.floorGap}). Where that gap stays near the end of the lens over a whole stretch, the
	 * shore distance stays small without water and the basin left new dry closed pits with straight creases (review of
	 * K6: 7 new or larger in both scales, up to 16 m deep, against 2 removed), so the threshold stays at 0.78.
	 */
	static final double TUNNEL_COS = 0.78;

	/** Review of K5: a trace of a tunnel valley contour ends where it runs steeper than this (dx/dz, about 68° from north). */
	private static final double TUNNEL_TRACE_SLOPE = 2.5;

	/** Root of the tunnel field along x at z by Newton steps from x0, or NaN when they do not converge. */
	private double contourRoot(double x0, double z) {
		double e = local;
		double x = x0;
		for (int it = 0; it < 12; it++) {
			double f = tunnelField(x, z);
			double d = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
			if (Math.abs(d) < 1e-15) {
				return Double.NaN;
			}
			double step = Math.clamp(f / d, -500.0 * local, 500.0 * local);
			x -= step;
			if (Math.abs(step) < 1e-3) {
				return x;
			}
		}
		return Double.NaN;
	}

	/** Slope dx/dz of the contour of the tunnel field through (x, z) (implicit derivative). */
	private double contourSlope(double x, double z) {
		double e = local;
		double fx = (tunnelField(x + e, z) - tunnelField(x - e, z)) / (2 * e);
		double fz = (tunnelField(x, z + e) - tunnelField(x, z - e)) / (2 * e);
		if (Math.abs(fx) < 1e-15) {
			return Double.POSITIVE_INFINITY;
		}
		return -fz / fx;
	}

	/** K5.2: a tunnel valley lake ends this far (m·k) beyond the edge of a valley cut or the shore of a sink lake. */
	static final double TUNNEL_END_GAP = 30;
	/**
	 * K5.2 (K4.9 of the design): longest end of a tunnel valley lake at a valley (m·k), 0.5 maxWall − TUNNEL_END_GAP, so
	 * that the end lies within the frame of the segment ({@code RiverHit.floorGap}).
	 */
	static final double TUNNEL_END_MAX = 570;

	/**
	 * K5.2: profile of the end of a lake: 0 for t ≤ 0, 1 for t ≥ 1, in between a quarter of an ellipse sqrt(1 − (1 − t)²).
	 * The width grows from the end like sqrt(t), so the end is rounded (a semicircle when its length equals the
	 * half-width), not pointed as with sqrt(smoothstep) nor cut straight.
	 */
	static double ellipticEnd(double t) {
		double c = Math.clamp(t, 0.0, 1.0);
		return Math.sqrt(c * (2 - c));
	}

	/**
	 * Round 2 of the review of K5: margin (m) between a river channel and the influence of a kettle, or between the
	 * water of the channel and the flank of the kettle basin above it ({@link #kettleState}).
	 */
	private static final double KETTLE_CHANNEL_GAP = 2;
	/** Slope of the flank of a kettle basin above its level ({@link #applyLake}). */
	private static final double KETTLE_SLOPE = 0.25;

	/** Highest chance of a kettle in a cell (pure moraine plateau). */
	private static final double KETTLE_MAX_CHANCE = 0.45;

	/**
	 * Kettle pond: in a 700 m cell (350 m at gameplay scale) at most one depression
	 * with a radius of 20–150 m. Whether the kettle exists depends on the conditions at its center, so a kettle is
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
		if (d - 1.25 * r > KETTLE_BANK) {
			return null;
		}
		// K5.3: slightly irregular shore from a noise sampled along a circle in noise space (a function of the angle, 3–5
		// lobes) instead of a planar noise with the wavelength r, which made straight stretches over the diameter of about
		// 2 grid cells of the noise. The amplitude falls to 0 at the center, so the bottom stays continuous.
		double ca = d > 1e-9 ? dx / d : 1;
		double sa = d > 1e-9 ? dz / d : 0;
		double off = 1_000 * kettle.unit(cx, cz, 7);
		double lobes = 0.75 * kettle.sample(off + 1.1 * ca, 1.1 * sa - off)
				+ 0.35 * kettle.sample(2.3 * ca - off, off + 2.3 * sa);
		double shore = d - r * (1 + 0.25 * Math.clamp(lobes, -1.0, 1.0) * Noise.smoothstep(0, 0.5 * r, d));
		if (shore > KETTLE_BANK) {
			return null;
		}
		long key = Noise.key(cx, cz, 3);
		byte state = kettleState(key, roll, kx, kz, r);
		if (state == KETTLE_NONE) {
			return null;
		}
		int level = kettleLevel(key, kx, kz, r * 1.3 + 25);
		// Review of K5: no kettle at the sea level (the low hinterland of a low shore, the shore of a lagoon): its peat
		// would lie below the sea and its water next to the lagoon.
		if (level < 1) {
			return null;
		}
		double depth = 2 + 8 * kettle.unit(cx, cz, 5);
		boolean peat = kettle.unit(cx, cz, 6) < 0.4;
		return new LakeHit(level, depth, shore, peat, KETTLE_SLOPE, KETTLE_BANK,
				peat ? ColumnSample.StandingWaterKind.KETTLE_BOG : ColumnSample.StandingWaterKind.KETTLE_POND, key, r,
				peat && state == KETTLE_OMBROTROPHIC);
	}

	/**
	 * Whether the kettle exists: chance from the region weights and no river valley at its center. The result is
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
			// Review of K5: nor where its influence (shore distance up to KETTLE_BANK) would meet the influence of a sink
			// lake or the basin of a tunnel valley lake: there it cut their banks and took its level from their bottom, so
			// their water stood next to lower ground (leaks of up to 57 m). The kettle is whole or absent.
			double reach = 1.25 * r + KETTLE_BANK + 1;
			exists = river.valleyWeight() < 0.3 && coastDistance(kx, kz) > 500 * local
					&& rivers.sinkLakeClearance(kx, kz) > reach + KETTLE_BANK && !kettleTouchesTunnelLake(kx, kz, reach);
			// Round 2 of the review of K5: where its influence meets a river channel (channelDist, the distance from the
			// bank of the nearest channel, grows by at most 1 m per meter), only when the flank of its basin stays above the
			// water of the channel there: the flank rises from the level + 1 m by the slope of the kettle basin from 5 m
			// beyond its shore (applyLake), and the bank of the channel lies at least channelDist − 1.25 r beyond the shore.
			// The channel keeps its bed and water while the kettle lowers the ground beside it, so with a channel above the
			// flank a stream stood on a narrow causeway next to a bog up to 30 m lower (GAMEPLAY, 2 of 1605 kettles). The
			// level is computed here (once per kettle, cached) only for the kettles near a channel.
			if (exists && river.channelDist() <= reach + KETTLE_CHANNEL_GAP) {
				int level = kettleLevel(key, kx, kz, r * 1.3 + 25);
				double bankShore = river.channelDist() - 1.25 * r - KETTLE_CHANNEL_GAP;
				exists = bankShore > 5 && Math.floor(river.channelLevel()) + KETTLE_CHANNEL_GAP
						<= level + KETTLE_SLOPE * (bankShore - 5);
			}
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
	 * Water level and reach of the basin of the lake of a traced tunnel valley contour, computed once per contour and
	 * cached. Round 2 of the review of K5:
	 * <ul>
	 * <li>the level is the lowest ground point (terrain before the valleys and lakes, {@link #landElevation}) along the lake
	 * itself, minus 1 m: at every node of the trace within the lens of the section (and up to TUNNEL_KEEP beyond its
	 * ends), on the axis and across it at 15 and 35 m, at a quarter, half, three quarters and the whole half-width and 15
	 * and 35 m beyond it (the strip where {@link #applyLake} raises the ground to the water level + 1 m), with the
	 * half-width of presence and gate at the node and without the other fades (an upper bound; the fades narrow the lake
	 * anywhere within it). K5 took the lowest point on a circle of 450 m·k around one canonical
	 * point of the lake; where the lake ran along a slope its bank became a dam of up to 53 m above the terrain (GAMEPLAY),
	 * and next to a lagoon the circle could miss the lagoon;</li>
	 * <li>the reach of the basin grows with the cut, TUNNEL_BANK_PER_CUT times the largest height of the ground above the
	 * bank (water level + 1 m) at those points and 1.5 tunnelBank beyond the half-width, from tunnelBank up to
	 * {@link #tunnelBankMax}: the flank of the basin passes into the terrain over 0.4–1.0 of its reach, so with a fixed
	 * reach of 70 m a cut of 60–130 m (GAMEPLAY moraine plateau) became a wall of up to 5 blocks per block.</li>
	 * </ul>
	 * A pure function of the trace, which is the same in every block that finds it ({@link #buildTunnelBlock}).
	 */
	private TunnelLake tunnelLake(TunnelContour c, long k) {
		TunnelLake cached = tunnelLakes.get(c.levelKey);
		if (cached != null) {
			return cached;
		}
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double[] offsets = new double[8];
		for (int q = c.lo; q <= c.hi; q++) {
			double x = c.xs[q];
			double z = c.z0 + q * c.dz;
			double b0 = tunnelBoundary(k, x);
			double b1 = tunnelBoundary(k + 1, x);
			double edge = Math.min(z - b0, b1 - z);
			if (edge < tunnelSill - TUNNEL_KEEP) {
				continue;
			}
			Blend b = blend(x, z);
			double young = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU);
			double gate = Noise.smoothstep(0.10, 0.35, tunnel.at(x, z, 50_000 * meso))
					* Noise.smoothstep(0.2, 0.7, tunnelPresence(young, coastDistance(x, z)));
			// Without the end of the lens: towards a rounded end the strip of the bank (shore distance below 40 m) stays
			// almost as wide as the lake while the water narrows to its tip.
			double half = gate * local * (200 + 500 * (0.5 + 0.5 * tunnel.at(x, z, 9_000 * meso)));
			// Offsets across the contour measured along x (the distance from the shore is measured across the contour).
			double across = Math.sqrt(1 + c.slopes[q] * c.slopes[q]);
			min = Math.min(min, landElevation(x, z));
			// The actual half-width lies anywhere between 0 and this bound (the other fades), so the whole band.
			offsets[0] = 15;
			offsets[1] = 35;
			offsets[2] = 0.25 * half;
			offsets[3] = 0.5 * half;
			offsets[4] = 0.75 * half;
			offsets[5] = half;
			offsets[6] = half + 15;
			offsets[7] = half + 35;
			for (double off : offsets) {
				min = Math.min(min, landElevation(x - off * across, z));
				min = Math.min(min, landElevation(x + off * across, z));
			}
			double far = (half + 1.5 * tunnelBank) * across;
			max = Math.max(max, Math.max(landElevation(x - far, z), landElevation(x + far, z)));
		}
		if (min == Double.POSITIVE_INFINITY) {
			int mid = (c.lo + c.hi) / 2;
			min = landElevation(c.xs[mid], c.z0 + mid * c.dz);
		}
		int level = (int) Math.floor(min) - 1;
		double bank = Math.clamp(TUNNEL_BANK_PER_CUT * (Math.max(max, min) - (level + 1)), tunnelBank, tunnelBankMax());
		TunnelLake lake = new TunnelLake(level, bank);
		if (tunnelLakes.size() > 100_000) {
			tunnelLakes.clear();
		}
		tunnelLakes.put(c.levelKey, lake);
		return lake;
	}

	/**
	 * K5.4 (A3, A3c): water level of a kettle: the lowest point of the surface before the kettles
	 * ({@link #surfaceBeforeKettle}: after the valleys) on a circle around the basin and at its center, minus 1 m, computed
	 * once per kettle and cached. A kettle never meets a sink lake or the bank of a tunnel valley lake
	 * ({@link #kettleState}), so the circle never crosses their water (review of K5: a circle crossing the bottom of a
	 * tunnel valley lake gave a level up to 57 m below its water, and the kettle cut its bank). M1 took the terrain before the valleys, so
	 * a kettle on the side of a valley or of the basin of a tunnel valley lake had its level up to 30 m above the
	 * valley floor, and its peat filled a bowl open towards the valley as a shelf (steps of 15–17 m). The nested queries
	 * of the river network run here, inside {@link #sample}, only after its own query has finished (its result is a
	 * record); the level is computed once per kettle (get and put, not computeIfAbsent, which must not recurse).
	 */
	private int kettleLevel(long key, double cx, double cz, double radius) {
		Integer cached = lakeLevels.get(key);
		if (cached != null) {
			return cached;
		}
		double min = surfaceBeforeKettle(cx, cz);
		for (int i = 0; i < 12; i++) {
			double a = i * (Math.PI * 2 / 12);
			min = Math.min(min, surfaceBeforeKettle(cx + radius * Math.cos(a), cz + radius * Math.sin(a)));
		}
		return putLakeLevel(key, (int) Math.floor(min) - 1);
	}

	private int putLakeLevel(long key, int level) {
		if (lakeLevels.size() > 500_000) {
			lakeLevels.clear();
		}
		lakeLevels.put(key, level);
		return level;
	}

	/**
	 * A3c: the surface at (x, z) before the kettle ponds, as {@link #sample} computes it: the terrain after the valleys of
	 * the river network. Without the channels, banks and oxbow lakes of the valley floors, which only matter within the
	 * floors (a kettle exists only away from valleys, {@link #kettleState}, and its circle reaches at most the sides), and
	 * without the sink lakes: a kettle exists only where its influence does not meet theirs (review of K5), and its level
	 * circle lies within its influence. With the outer flank of the basin of a tunnel valley lake, which a kettle may reach
	 * (but not its bank, {@link #kettleTouchesTunnelLake}).
	 */
	private double surfaceBeforeKettle(double x, double z) {
		Blend b = blend(x, z);
		double coastD = coastDistance(x, z);
		double surface = shapeCoast(elevation(b, x, z), coastD, x, z, cliffShore(x, z, b));
		if (coastD < 0 || surface < 0) {
			return surface;
		}
		double young = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU);
		double lowland = young + b.weight(LandscapeType.OLD_GLACIAL_PLAIN);
		RiverNetwork.RiverHit r = rivers.query(x, z, surface, lowland + b.weight(LandscapeType.COASTLAND),
				b.weight(LandscapeType.FOOTHILLS), b.weight(LandscapeType.BESKIDS));
		surface = r.terrain();
		double presence = tunnelPresence(young, coastD);
		if (presence > 0.05) {
			LakeHit lake = tunnelLakeAt(x, z, presence, r.floorGap(), r.lakeGap());
			if (lake != null && lake.shoreDistance <= lake.bank) {
				surface = applyLake(surface, lake);
			}
		}
		return surface;
	}

	/**
	 * For tests (step K6, review of K5): the shore distance of the tunnel valley lake at (x, z) less the reach of its
	 * basin, so at most 0 where {@link #sample} carves the basin of a tunnel valley lake (the habitat ring of
	 * {@code standingWaterKind} is narrower than the basin of a deep cut), and +∞ without a lake.
	 */
	double tunnelBasinMargin(double x, double z) {
		Blend b = blend(x, z);
		double coastD = coastDistance(x, z);
		double surface = shapeCoast(elevation(b, x, z), coastD, x, z, cliffShore(x, z, b));
		if (coastD < 0 || surface < 0) {
			return Double.POSITIVE_INFINITY;
		}
		double young = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU);
		double lowland = young + b.weight(LandscapeType.OLD_GLACIAL_PLAIN);
		double presence = tunnelPresence(young, coastD);
		if (presence <= 0.05) {
			return Double.POSITIVE_INFINITY;
		}
		RiverNetwork.RiverHit r = rivers.query(x, z, surface, lowland + b.weight(LandscapeType.COASTLAND),
				b.weight(LandscapeType.FOOTHILLS), b.weight(LandscapeType.BESKIDS));
		LakeHit lake = tunnelLakeAt(x, z, presence, r.floorGap(), r.lakeGap());
		return lake == null ? Double.POSITIVE_INFINITY : lake.shoreDistance - lake.bank;
	}

	/** The sink lake of a query result as a basin for {@link #applyLake}. */
	private static LakeHit sinkLakeHit(RiverNetwork.RiverHit r) {
		return new LakeHit(r.lakeLevel(), r.lakeDepth(), r.lakeShore(), false, 0.25, KETTLE_BANK,
				ColumnSample.StandingWaterKind.SINK_LAKE, r.lakeId(), r.lakeRadius(), false);
	}

	/**
	 * Carves the lake basin: the bottom below the water level, a flank above the water level that passes into the
	 * terrain, and a shore bank at water level + 1 m where the ground would lie lower. Thanks to this the water is always
	 * surrounded by land and the function stays continuous.
	 *
	 * <p>K5.4 (A3): the flank blends into the terrain over 0.4–1.0 of the reach of the basin (a lerp), instead of rising
	 * along a wall of 5000 m · smoothstep at the limit of influence, which left walls of up to 15 m on slopes (and up to
	 * 100 m around the sink lakes at the foot of the large massifs); a peat bog has no bank and no step of 1.5 m at its
	 * shore (there is no water to hold): its flank starts at its surface, water level − 0.5 m.
	 */
	private static double applyLake(double surface, LakeHit lake) {
		double s = lake.shoreDistance;
		if (s < 0) {
			double inner = Noise.smoothstep(0, 60, -s);
			double bottom = lake.level - 0.5 - lake.depthBelowLevel * inner;
			return Math.min(surface, bottom);
		}
		double base = lake.peat ? lake.level - 0.5 : lake.level + 1.0;
		double flank = base + lake.slope * Math.max(0, s - 5);
		double result = Noise.lerp(Noise.smoothstep(0.4 * lake.bank, lake.bank, s), Math.min(surface, flank), surface);
		double raise = lake.peat ? 0 : 1.0 - Noise.smoothstep(15, 40, s);
		if (result < lake.level + 1 && raise > 0) {
			result = Math.max(result, Noise.lerp(raise, result, lake.level + 1.0));
		}
		return result;
	}
}
