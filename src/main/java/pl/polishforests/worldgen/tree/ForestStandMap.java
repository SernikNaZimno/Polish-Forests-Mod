package pl.polishforests.worldgen.tree;

import pl.polishforests.worldgen.landscape.Noise;

/**
 * The lite stand map of the world (decision PW-4 b, step V9 of docs/04-pionowy-wycinek.md): a pure function of the world
 * seed, the scale's k and the share of managed forests (slider D9, {@code PolandSettings#managedForestShare}), without
 * habitat codes, so every chunk sees the same stands (rule ZW-1) and the crown territories ({@link CrownTerritories})
 * can use it across chunk borders.
 *
 * <p><b>Stands</b> are the cells of an additively weighted Voronoi diagram of sites on a jittered grid of
 * {@value #SPACING} m·k (one site per grid cell, the weight up to {@value #WEIGHT} of the spacing), with curved borders;
 * a cell joins the stand of a neighbor with the chance {@value #MERGE_CHANCE}, so a stand has one to four cells and
 * 2–30 ha·k² ({@code StandMapTest} measures the sizes). The query point first moves by a smooth
 * field of up to {@value #MEANDER} m·k (wavelength {@value #MEANDER_WAVELENGTH} m·k), so the borders meander (rule Z10,
 * ZW-6). A column within the age ramp of a border ({@value #RAMP_MIN}–{@value #RAMP_MAX} blocks·k on each side, varying
 * along the border; at least {@value #RAMP_LEAST} blocks) takes the neighboring stand with a chance that falls linearly
 * from one half at the border to none at the end of the ramp: the stands mix there column by column, without a line.
 *
 * <p><b>Attributes of a stand</b> come from hashes of its site: natural with the chance 1 − the managed share (D9: 15%
 * by default, in both vegetation modes, PW-2), else managed; the age class by the shares of {@link #MANAGED_AGES} or
 * {@link #NATURAL_AGES}.
 *
 * <p><b>Canopy gaps</b> (only in natural stands; review of V9, round 1: no lattice): a Poisson-like process on cells of
 * {@value #GAP_CELL} blocks. A cell holds a gap with a chance by the age class of the column ({@link #GAP_CHANCE}: few in
 * the pole stage), its center anywhere in the cell; next to it a smaller second gap with the chance
 * {@value #CLUSTER_CHANCE} (gaps in clusters, as windthrow and the death of neighboring trees make them). The planned
 * diameter is log-normal (median {@value #GAP_MEDIAN}, {@value #GAP_MIN}–{@value #GAP_MAX} blocks: many gaps of one to
 * three trees, few large ones); the outline is an ellipse of axis ratio 1–{@value #GAP_ELONGATION} in a random
 * direction (the fall of the trees), with a wavy edge. The crowns around a gap lean into it (crown territories: a gap is
 * a marker), so the opening the census measures is narrower, and the smallest gaps close above the young trees they
 * hold; {@code StandCensusSimulationTest} reports the census of the gaps (N5 of the norms card). Gaps are tree-sized,
 * so they keep their size in blocks in both scales, like the trees (PW-12).
 *
 * <p>Salts (rule ZW-2): {@value #CELLS_SALT} (sites, weights, meander, ramp), {@value #AGE_SALT}, {@value #NATURAL_SALT}
 * and {@value #GAP_SALT} (the regular gap grid of V9 under {@code feature.stand.canopy_gaps} is gone; its salt is not
 * reused). Thread-safe; the only state is the cache of {@link #stand} (step O1), which never changes an answer.
 */
public final class ForestStandMap implements StandMap {
	public static final String CELLS_SALT = "feature.stand.cells";
	public static final String AGE_SALT = "feature.stand.age";
	public static final String NATURAL_SALT = "feature.stand.natural";
	public static final String GAP_SALT = "feature.stand.gap_clusters";

	/** Spacing of the stand sites, m·k. */
	public static final double SPACING = 240;
	/** Largest additive weight of a site, as a share of the spacing (larger weights, larger stands). */
	public static final double WEIGHT = 0.4;
	/**
	 * Chance that the cell of a site joins the stand of its neighbor in +x or +z (up to {@value #MERGE_STEPS} times in a
	 * row), so stands of one, two, three or four cells give the spread of 2–30 ha·k².
	 */
	public static final double MERGE_CHANCE = 0.35;
	public static final int MERGE_STEPS = 3;
	/** Largest meander of the stand borders, m·k, and its wavelength. */
	public static final double MEANDER = 30;
	public static final double MEANDER_WAVELENGTH = 150;
	/** Half-width of the age ramp at a stand border, blocks·k, and its least value in blocks. */
	public static final double RAMP_MIN = 3;
	public static final double RAMP_MAX = 6;
	public static final double RAMP_LEAST = 1.5;
	/** Shares of the age classes (pole stage, mature, old growth) of managed and natural stands [S]. */
	static final double[] MANAGED_AGES = {0.20, 0.58, 0.22};
	static final double[] NATURAL_AGES = {0.10, 0.35, 0.55};
	/** Side of a gap cell (blocks, 1:1 in both scales). */
	public static final int GAP_CELL = 64;
	/** Chance of a gap in a cell by the age class of the column (pole stage, mature, old growth) [S]. */
	static final double[] GAP_CHANCE = {0.09, 0.32, 0.36};
	/** Chance of a second, smaller gap next to the first (a cluster). */
	public static final double CLUSTER_CHANCE = 0.4;
	/** Planned gap diameters (blocks): log-normal median and spread, bounds. */
	public static final double GAP_MEDIAN = 30;
	public static final double GAP_SIGMA = 0.4;
	public static final double GAP_MIN = 14;
	public static final double GAP_MAX = 50;
	/** Largest axis ratio of a gap's ellipse. */
	public static final double GAP_ELONGATION = 1.6;
	/** Largest distance of a gap's outline from its center (blocks): the neighbors of a cell are enough. */
	private static final double GAP_REACH = GAP_CELL;

	private static final long SITE_X = 1;
	private static final long SITE_Z = 2;
	private static final long SITE_WEIGHT = 3;
	private static final long DITHER = 4;
	private static final long MERGE = 5;
	private static final long GAP_PRESENT = 1;
	private static final long GAP_X = 2;
	private static final long GAP_Z = 3;
	private static final long GAP_SIZE = 4;
	private static final long GAP_SHAPE = 5;
	private static final long GAP_AXIS = 6;
	private static final long GAP_SECOND = 7;
	private static final long GAP_SECOND_SIZE = 8;
	private static final long GAP_SECOND_PLACE = 9;

	/** Size of the stand cache of {@link #stand} (2^16 columns, about 3 MB with the entries: 256 chunks). */
	private static final int STAND_CACHE_BITS = 16;

	private final StandEntry[] standCache = new StandEntry[1 << STAND_CACHE_BITS];
	private final Noise cells;
	private final Noise ages;
	private final Noise naturals;
	private final Noise gaps;
	private final double k;
	private final double spacing;
	private final double naturalShare;

	/**
	 * @param worldSeed    the world seed
	 * @param k            the scale's k ({@code LandscapeScale#local}: 1 realistic, 0.5 gameplay)
	 * @param managedShare share of managed forests (D9), 0–1
	 */
	public ForestStandMap(long worldSeed, double k, double managedShare) {
		Noise root = new Noise(worldSeed);
		this.cells = root.derive(CELLS_SALT);
		this.ages = root.derive(AGE_SALT);
		this.naturals = root.derive(NATURAL_SALT);
		this.gaps = root.derive(GAP_SALT);
		this.k = k;
		this.spacing = SPACING * k;
		this.naturalShare = 1 - Math.clamp(managedShare, 0, 1);
		this.worldSeed = worldSeed;
		this.managedShare = managedShare;
	}

	private final long worldSeed;
	private final double managedShare;

	/** Whether this map is the one of a world seed, scale and managed share. */
	public boolean matches(long worldSeed, double k, double managedShare) {
		return this.worldSeed == worldSeed && this.k == k && this.managedShare == managedShare;
	}

	/**
	 * A stand: the grid cell of its site (its identity), its age class and whether it is natural.
	 *
	 * @param siteX grid cell of the site, x
	 * @param siteZ grid cell of the site, z
	 */
	public record Stand(int siteX, int siteZ, AgeClass age, boolean natural) {
		/** A number identifying the stand. */
		public long id() {
			return (long) siteX << 32 | siteZ & 0xFFFF_FFFFL;
		}
	}

	@Override
	public AgeClass ageClass(int x, int z) {
		return stand(x, z).age();
	}

	@Override
	public boolean natural(int x, int z) {
		return stand(x, z).natural();
	}

	@Override
	public boolean canopyGap(int x, int z) {
		return gapDepth(x, z) > 0;
	}

	@Override
	public double gapDepth(int x, int z) {
		Stand stand = stand(x, z);
		return stand.natural() ? rawGapDepth(x, z, stand.age()) : 0;
	}

	/**
	 * The stand of column (x, z), with the age ramp at the borders. Step O1: memoized in a direct-mapped cache shared by
	 * the generation threads ({@link #STAND_CACHE_BITS}); the stand is a pure function of the column, so a cached answer
	 * is the computed one. Entries are immutable records (final fields), so a thread that reads an entry another thread
	 * has just written sees it whole; a lost or overwritten entry is only computed again.
	 */
	public Stand stand(int x, int z) {
		long key = (long) x << 32 | z & 0xFFFF_FFFFL;
		int slot = (int) (Noise.mix(key) >>> (64 - STAND_CACHE_BITS));
		StandEntry e = standCache[slot];
		if (e != null && e.key() == key) {
			return e.stand();
		}
		Stand stand = computeStand(x, z);
		standCache[slot] = new StandEntry(key, stand);
		return stand;
	}

	/** A cached stand of a column ({@link #stand}). */
	private record StandEntry(long key, Stand stand) {
	}

	/**
	 * The sites of the 5 × 5 grid cells around one cell (position and weight, in the order of {@link #computeStand}'s
	 * loop), the last ones of a thread. Step O2: neighboring columns that miss the stand cache mostly fall into the same
	 * cell, and the 75 hashes of the sites were about half of {@code computeStand}; the values are the same expressions of
	 * the same cells, so the stand is the same.
	 */
	private static final class Sites {
		int gx;
		int gz;
		boolean valid;
		final double[] cx = new double[25];
		final double[] cz = new double[25];
		final double[] weight = new double[25];
	}

	private final ThreadLocal<Sites> sites = ThreadLocal.withInitial(Sites::new);

	private Sites sites(int gx, int gz) {
		Sites s = sites.get();
		if (s.valid && s.gx == gx && s.gz == gz) {
			return s;
		}
		for (int i = gx - 2; i <= gx + 2; i++) {
			for (int j = gz - 2; j <= gz + 2; j++) {
				int k = (i - gx + 2) * 5 + j - gz + 2;
				s.cx[k] = (i + cells.unit(i, j, SITE_X)) * spacing;
				s.cz[k] = (j + cells.unit(i, j, SITE_Z)) * spacing;
				s.weight[k] = WEIGHT * spacing * cells.unit(i, j, SITE_WEIGHT);
			}
		}
		s.gx = gx;
		s.gz = gz;
		s.valid = true;
		return s;
	}

	/** The stand of column (x, z) computed without the caches (tests of steps O1 and O2). */
	Stand standUncached(int x, int z) {
		sites.get().valid = false;
		return computeStand(x, z);
	}

	private Stand computeStand(int x, int z) {
		double px = x + MEANDER * k * cells.fbm(x, z, MEANDER_WAVELENGTH * k, 2, 0.5);
		double pz = z + MEANDER * k * cells.fbm(x + 7_919.0, z - 4_201.0, MEANDER_WAVELENGTH * k, 2, 0.5);
		int gx = (int) Math.floor(px / spacing);
		int gz = (int) Math.floor(pz / spacing);
		Sites sites = sites(gx, gz);
		double best = Double.MAX_VALUE;
		double second = Double.MAX_VALUE;
		int bx = 0;
		int bz = 0;
		int sx = 0;
		int sz = 0;
		for (int i = gx - 2; i <= gx + 2; i++) {
			for (int j = gz - 2; j <= gz + 2; j++) {
				int k = (i - gx + 2) * 5 + j - gz + 2;
				double cx = sites.cx[k];
				double cz = sites.cz[k];
				double d = Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz)) - sites.weight[k];
				if (d < best) {
					second = best;
					sx = bx;
					sz = bz;
					best = d;
					bx = i;
					bz = j;
				} else if (d < second) {
					second = d;
					sx = i;
					sz = j;
				}
			}
		}
		double border = (second - best) / 2;
		double ramp = Math.max(RAMP_LEAST, k * (RAMP_MIN + (RAMP_MAX - RAMP_MIN) * (0.5 + 0.5 * Math.clamp(
				cells.at(x - 3_301.0, z + 911.0, 40 * k) / 0.6, -1, 1))));
		if (border < ramp && cells.unit(x, z, DITHER) < 0.5 * (1 - border / ramp)) {
			bx = sx;
			bz = sz;
		}
		return site(bx, bz);
	}

	/**
	 * The stand of the site in grid cell (i, j): the cell joins its neighbor's stand ({@link #MERGE_CHANCE}), so the
	 * stand is the one of the last cell of the chain.
	 */
	public Stand site(int i, int j) {
		for (int step = 0; step < MERGE_STEPS; step++) {
			double u = cells.unit(i, j, MERGE);
			if (u >= MERGE_CHANCE) {
				break;
			}
			if (u < MERGE_CHANCE / 2) {
				i++;
			} else {
				j++;
			}
		}
		return root(i, j);
	}

	/** The stand whose own site is grid cell (i, j). */
	public Stand root(int i, int j) {
		boolean natural = naturals.unit(i, j, 1) < naturalShare;
		double[] shares = natural ? NATURAL_AGES : MANAGED_AGES;
		double u = ages.unit(i, j, natural ? 2 : 1);
		AgeClass age = u < shares[0] ? AgeClass.POLE_STAGE : u < shares[0] + shares[1] ? AgeClass.MATURE : AgeClass.OLD_GROWTH;
		return new Stand(i, j, age, natural);
	}

	/** The spacing of the stand sites in blocks (SPACING · k). */
	public double spacing() {
		return spacing;
	}

	/**
	 * Depth of column (x, z) inside a canopy gap of a natural stand of the age class {@code age}, ignoring the stand map
	 * (blocks; positive inside a gap, the distance to its outline, else 0 or less; the gaps of {@link #gapDepth} are
	 * these with the column's own stand). The gaps of the pole stage are among those of the mature stands, and these among
	 * those of the old growth, so a gap is cut at a border of two age classes only where it loses its place.
	 */
	public double rawGapDepth(int x, int z, AgeClass age) {
		int gx = Math.floorDiv(x, GAP_CELL);
		int gz = Math.floorDiv(z, GAP_CELL);
		double chance = GAP_CHANCE[age.ordinal()];
		double depth = -Double.MAX_VALUE;
		for (int i = gx - 1; i <= gx + 1; i++) {
			for (int j = gz - 1; j <= gz + 1; j++) {
				depth = Math.max(depth, gapDepthOf(i, j, x, z, chance));
			}
		}
		return depth;
	}

	/**
	 * Depth of column (x, z) inside the gaps of gap cell (i, j) when a cell holds a gap with the chance {@code chance}
	 * ({@code -Double.MAX_VALUE} without a gap or far).
	 */
	double gapDepthOf(int i, int j, int x, int z, double chance) {
		if (gaps.unit(i, j, GAP_PRESENT) >= chance) {
			return -Double.MAX_VALUE;
		}
		double cx = (i + gaps.unit(i, j, GAP_X)) * GAP_CELL;
		double cz = (j + gaps.unit(i, j, GAP_Z)) * GAP_CELL;
		double dx = x + 0.5 - cx;
		double dz = z + 0.5 - cz;
		if (dx * dx + dz * dz > GAP_REACH * GAP_REACH) {
			return -Double.MAX_VALUE;
		}
		double first = diameter(gaps.unit(i, j, GAP_SIZE));
		double depth = ellipse(i, j, 0, dx, dz, first / 2);
		if (gaps.unit(i, j, GAP_SECOND) < CLUSTER_CHANCE) {
			// A smaller gap next to the first one: its center near the first one's outline, so the two touch or overlap.
			double second = Math.min(first, diameter(0.5 * gaps.unit(i, j, GAP_SECOND_SIZE)));
			double angle = 2 * Math.PI * gaps.unit(i, j, GAP_SECOND_PLACE);
			double off = 0.5 * (first + second) * 0.75;
			depth = Math.max(depth, ellipse(i, j, 1, dx - off * Math.cos(angle), dz - off * Math.sin(angle), second / 2));
		}
		return depth;
	}

	/** A planned gap diameter from a uniform number: log-normal, bounded. */
	static double diameter(double u) {
		double n = inverseNormal(Math.clamp(u, 1e-6, 1 - 1e-6));
		return Math.clamp(GAP_MEDIAN * Math.exp(GAP_SIGMA * n), GAP_MIN, GAP_MAX);
	}

	/** Depth inside an elongated, wavy gap of mean radius {@code radius} at the offset (dx, dz) from its center. */
	private double ellipse(int i, int j, int k, double dx, double dz, double radius) {
		long h = gaps.hash(i, j, GAP_SHAPE + 16L * k);
		double axis = Math.PI * ((h >>> 48 & 0xFFFF) / 65_536.0);
		double ratio = 1 + (GAP_ELONGATION - 1) * gaps.unit(i, j, GAP_AXIS + 16L * k);
		double c = Math.cos(axis);
		double s = Math.sin(axis);
		double u = (dx * c + dz * s) / Math.sqrt(ratio);
		double v = (-dx * s + dz * c) * Math.sqrt(ratio);
		double d = Math.sqrt(u * u + v * v);
		double angle = Math.atan2(v, u);
		double p1 = (h & 0xFFFF) * (2 * Math.PI / 65_536);
		double p2 = (h >>> 16 & 0xFFFF) * (2 * Math.PI / 65_536);
		double p3 = (h >>> 32 & 0xFFFF) * (2 * Math.PI / 65_536);
		double outline = radius * (1 + 0.1 * Math.sin(2 * angle + p1) + 0.07 * Math.sin(3 * angle + p2)
				+ 0.05 * Math.sin(5 * angle + p3));
		return outline - d;
	}

	/** The inverse of the standard normal distribution (rational approximation of P. J. Acklam, relative error below 1e-8). */
	static double inverseNormal(double p) {
		double low = 0.02425;
		if (p < low) {
			double q = Math.sqrt(-2 * Math.log(p));
			return tail(q);
		}
		if (p > 1 - low) {
			double q = Math.sqrt(-2 * Math.log(1 - p));
			return -tail(q);
		}
		double q = p - 0.5;
		double r = q * q;
		return (((((-3.969683028665376e+01 * r + 2.209460984245205e+02) * r - 2.759285104469687e+02) * r
				+ 1.383577518672690e+02) * r - 3.066479806614716e+01) * r + 2.506628277459239e+00) * q
				/ (((((-5.447609879822406e+01 * r + 1.615858368580409e+02) * r - 1.556989798598866e+02) * r
				+ 6.680131188771972e+01) * r - 1.328068155288572e+01) * r + 1);
	}

	private static double tail(double q) {
		return (((((-7.784894002430293e-03 * q - 3.223964580411365e-01) * q - 2.400758277161838e+00) * q
				- 2.549732539343734e+00) * q + 4.374664141464968e+00) * q + 2.938163982698783e+00)
				/ ((((7.784695709041462e-03 * q + 3.224671290700398e-01) * q + 2.445134137142996e+00) * q
				+ 3.754408661907416e+00) * q + 1);
	}
}
