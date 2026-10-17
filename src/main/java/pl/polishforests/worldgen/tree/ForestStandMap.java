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
 * <p><b>Canopy gaps</b> (only in natural stands): on a jittered grid of {@value #GAP_SPACING} blocks a cell holds a gap
 * with the chance {@value #GAP_CHANCE} (one gap per about 1.2 ha, so a sample plot of 1 ha nearly always holds one), of
 * a planned diameter in {@value #GAP_MIN}–{@value #GAP_MAX} blocks, the small ones more frequent (32 + 12 u², mean 36),
 * with a wavy outline; the centers keep a fairly even spacing, so every plot of 1 ha holds about one gap's area. The
 * crowns around a gap lean into it (crown territories: a gap is a marker), so the gap the census measures, the opening of
 * the canopy, is narrower: about 18–35 blocks across and 3–8% of the area of a natural stand, as N5 of the norms card. Gaps are tree-sized (a gap is the place of one to a few fallen trees), so they
 * keep their size in blocks in both scales, like the trees (PW-12).
 *
 * <p>Salts (rule ZW-2): {@value #CELLS_SALT} (sites, weights, meander, ramp), {@value #AGE_SALT}, {@value #NATURAL_SALT}
 * and {@value #GAPS_SALT}. Immutable and thread-safe.
 */
public final class ForestStandMap implements StandMap {
	public static final String CELLS_SALT = "feature.stand.cells";
	public static final String AGE_SALT = "feature.stand.age";
	public static final String NATURAL_SALT = "feature.stand.natural";
	public static final String GAPS_SALT = "feature.stand.canopy_gaps";

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
	/** Spacing of the gap grid (blocks, 1:1 in both scales), chance of a gap in a cell, diameters (blocks). */
	public static final double GAP_SPACING = 105;
	public static final double GAP_CHANCE = 1.0;
	public static final double GAP_MIN = 32;
	public static final double GAP_MAX = 44;

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
		this.gaps = root.derive(GAPS_SALT);
		this.k = k;
		this.spacing = SPACING * k;
		this.naturalShare = 1 - Math.clamp(managedShare, 0, 1);
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
		if (!natural(x, z)) {
			return 0;
		}
		return rawGapDepth(x, z);
	}

	/** The stand of column (x, z), with the age ramp at the borders. */
	public Stand stand(int x, int z) {
		double px = x + MEANDER * k * cells.fbm(x, z, MEANDER_WAVELENGTH * k, 2, 0.5);
		double pz = z + MEANDER * k * cells.fbm(x + 7_919.0, z - 4_201.0, MEANDER_WAVELENGTH * k, 2, 0.5);
		int gx = (int) Math.floor(px / spacing);
		int gz = (int) Math.floor(pz / spacing);
		double best = Double.MAX_VALUE;
		double second = Double.MAX_VALUE;
		int bx = 0;
		int bz = 0;
		int sx = 0;
		int sz = 0;
		for (int i = gx - 2; i <= gx + 2; i++) {
			for (int j = gz - 2; j <= gz + 2; j++) {
				double cx = (i + cells.unit(i, j, SITE_X)) * spacing;
				double cz = (j + cells.unit(i, j, SITE_Z)) * spacing;
				double d = Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz)) - WEIGHT * spacing * cells.unit(i, j, SITE_WEIGHT);
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
	 * Depth of column (x, z) inside a canopy gap of the gap grid, ignoring the stand (blocks; positive inside a gap, the
	 * distance to its outline, else 0 or less; the gaps of {@link #gapDepth} are these in natural stands).
	 */
	public double rawGapDepth(int x, int z) {
		int gx = (int) Math.floor(x / GAP_SPACING);
		int gz = (int) Math.floor(z / GAP_SPACING);
		double depth = -Double.MAX_VALUE;
		for (int i = gx - 1; i <= gx + 1; i++) {
			for (int j = gz - 1; j <= gz + 1; j++) {
				depth = Math.max(depth, gapDepthOf(i, j, x, z));
			}
		}
		return depth;
	}

	/** Depth of column (x, z) inside the gap of gap grid cell (i, j) ({@code -Double.MAX_VALUE} without a gap or far). */
	double gapDepthOf(int i, int j, int x, int z) {
		if (gaps.unit(i, j, GAP_PRESENT) >= GAP_CHANCE) {
			return -Double.MAX_VALUE;
		}
		// The center keeps 0.4 of the cell from its sides: the gaps keep a fairly even spacing, so every plot of 1 ha holds
		// about one gap's area (whole or in parts), as N5 of the norms card describes it.
		double cx = (i + 0.4 + 0.2 * gaps.unit(i, j, GAP_X)) * GAP_SPACING;
		double cz = (j + 0.4 + 0.2 * gaps.unit(i, j, GAP_Z)) * GAP_SPACING;
		double dx = x + 0.5 - cx;
		double dz = z + 0.5 - cz;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > GAP_MAX) {
			return -Double.MAX_VALUE;
		}
		double u = gaps.unit(i, j, GAP_SIZE);
		double radius = (GAP_MIN + (GAP_MAX - GAP_MIN) * u * u) / 2;
		double angle = Math.atan2(dz, dx);
		long h = gaps.hash(i, j, GAP_SHAPE);
		double p1 = (h & 0xFFFF) * (2 * Math.PI / 65_536);
		double p2 = (h >>> 16 & 0xFFFF) * (2 * Math.PI / 65_536);
		double p3 = (h >>> 32 & 0xFFFF) * (2 * Math.PI / 65_536);
		double outline = radius * (1 + 0.12 * Math.sin(2 * angle + p1) + 0.08 * Math.sin(3 * angle + p2)
				+ 0.05 * Math.sin(5 * angle + p3));
		return outline - d;
	}
}
