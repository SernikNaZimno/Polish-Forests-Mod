package pl.polishforests.worldgen.feature.plan;

import java.util.concurrent.atomic.AtomicLongArray;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Wide ecotones of the forests of the vertical slice (step Z1 of docs/04-pionowy-wycinek.md, decision CP-B.1, rule Z10):
 * between the zonal forests of the lowland sequence of fertility ({@link #FORESTS}: fresh pine forest, mixed pine-oak
 * forest, mixed forest and oak-hornbeam forest) the palettes of the tree stand, the understory and the ground layer change
 * over a belt of about {@value #RADIUS} m·k instead of the ramp of {@link Ecotone}, which can only reach
 * {@value Ecotone#REACH} blocks (the codes of the chunk's 8 neighbors; in the realistic scale the composition changed over
 * about 25 m, at the lower bound of the norm N15 of 20–100 m).
 *
 * <p><b>Share field.</b> The habitat of the world is sampled on a lattice of {@value #SPACING} m·k (the classifier on a
 * sample of the landscape model, the same pure function the chunks are filled with; memoized in a table shared by all
 * threads, {@link Lattice}). At each lattice point the share of each of the four forests is the kernel-weighted share of
 * the lattice points of that forest (without a zone) within {@value #RADIUS} m·k, with the weight (1 − d²/R²)²; between
 * the lattice points the shares are interpolated bilinearly. At a straight border between two of the forests the share of
 * the other side falls smoothly from 50% at the border to 0 at R (10–90% of the change over about 0.94 R), and it follows
 * the true shape of the border, so the belt has no straight line where the border has none; the column is moved by the
 * meander of {@link Ecotone} before the field is read.
 *
 * <p><b>Mixing.</b> A column whose ecotone code ({@link Ecotone#effective}) is one of the four forests without a zone
 * draws a uniform value v (its own hash per layer, in patches for the ground layer) and takes the canonical code of
 * another forest b ({@link #code}) when v falls into b's share; the shares are normalized by the forests' sum (at least
 * {@value #MIN_TOTAL}), so the composition is continuous across a border between two forests: on both sides a column has
 * the other forest's palette with the share of the field. The pairs of the four forests do not mix in the ramp of
 * {@link Ecotone} when a region carries a blend ({@link Ecotone.Region#withBlend}); their soils still do (layer SOIL, the
 * sharp substrate border of N15 stays sharp in the top blocks).
 *
 * <p>Pure functions of the world seed, the scale's k and the classifier (mode), so the result does not depend on the
 * order in which chunks are decorated, and a neighbor's tree code stays provable ({@link Ecotone#provableTrees}).
 */
public final class ForestBlend {
	/** The forests of the blend: the zonal forests of the slice transect Bśw → BMśw → LMśw → grąd (docs/04 §6, N15). */
	public static final HabitatBiome[] FORESTS = {HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST,
			HabitatBiome.MIXED_FOREST, HabitatBiome.OAK_HORNBEAM_FOREST};
	/** Radius of the kernel (m·k). */
	public static final double RADIUS = 48;
	/** Spacing of the lattice (m·k); in blocks at least 2. */
	public static final double SPACING = 8;
	/** Least sum of the forests' shares by which the shares are normalized (see the class description). */
	public static final double MIN_TOTAL = 0.5;
	/** Salt of the draws of the blend (a new field, a new salt), mixed with each layer's ecotone salt. */
	public static final long DRAW_SALT = 0x2B1E_7D5F_0C39L;
	/**
	 * Lowland woodlands next to the forests of the slice whose trees the slice forests take over near them (trees only,
	 * {@link #takeover}): pine forests of other sites, bog woodland, alder carr, floodplain forests, lowland beech forest.
	 */
	private static final long NEIGHBORS = bits(HabitatBiome.DRY_PINE_FOREST, HabitatBiome.MOIST_PINE_FOREST,
			HabitatBiome.COASTAL_PINE_FOREST, HabitatBiome.BOG_WOODLAND, HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.ELM_ASH_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST);
	/**
	 * Share of the slice forests among the land without a zone within the radius ({@link #takeover}) at which the takeover
	 * starts, and at which it is complete (smoothstep between).
	 */
	public static final double TAKEOVER_FROM = 0.55;
	public static final double TAKEOVER_TO = 0.85;
	/** Least sum of the slice forests' shares for a takeover (some slice forest within the radius). */
	public static final double TAKEOVER_MIN = 0.001;
	/** Number of shares of the field: the four forests, then the other land without a zone. */
	public static final int SHARES = 5;
	/** Salt of the draw of the takeover (a new field, a new salt), mixed with the tree layer's ecotone salt. */
	public static final long TAKEOVER_SALT = 0x4F2D_A61C_83E5L;

	private static long bits(HabitatBiome... biomes) {
		long m = 0;
		for (HabitatBiome b : biomes) {
			m |= 1L << b.ordinal();
		}
		return m;
	}

	/** Lattice points classified (samples of the landscape model, the blend's cost; diagnostics of the slice test). */
	public static final java.util.concurrent.atomic.LongAdder SAMPLES = new java.util.concurrent.atomic.LongAdder();

	/** Forest site type of each forest's canonical code. */
	private static final ForestSiteType[] SITES = {ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.FRESH_MIXED_CONIFEROUS,
			ForestSiteType.FRESH_MIXED_BROADLEAVED, ForestSiteType.FRESH_BROADLEAVED};
	private static final byte[] MEMBER = new byte[64];

	static {
		for (int i = 0; i < FORESTS.length; i++) {
			MEMBER[FORESTS[i].ordinal()] = (byte) (i + 1);
		}
	}

	/** The habitat code of a world column (the classifier on the landscape model's sample). */
	@FunctionalInterface
	public interface Sampler {
		int code(int x, int z);
	}

	private final long worldSeed;
	private final double k;
	private final int spacing;
	private final int radius;
	private final Sampler sampler;
	private final Lattice lattice = new Lattice();
	/** Kernel stencil: lattice offsets (di, dj) within the radius and their weights, normalized to a sum of 1. */
	private final int[] stencilI;
	private final int[] stencilJ;
	private final float[] stencilW;

	/**
	 * @param worldSeed the world seed
	 * @param k         the scale's k (Z7)
	 * @param sampler   the habitat code of a world column; must be a pure function and safe for concurrent use
	 */
	public ForestBlend(long worldSeed, double k, Sampler sampler) {
		this.worldSeed = worldSeed;
		this.k = k;
		this.sampler = sampler;
		this.spacing = (int) Math.max(2, Math.round(SPACING * k));
		this.radius = (int) Math.max(2L * spacing, Math.round(RADIUS * k));
		int reach = radius / spacing;
		int n = 0;
		int[] si = new int[(2 * reach + 1) * (2 * reach + 1)];
		int[] sj = new int[si.length];
		double[] sw = new double[si.length];
		double sum = 0;
		double r2 = (double) radius * radius;
		for (int i = -reach; i <= reach; i++) {
			for (int j = -reach; j <= reach; j++) {
				double d2 = (double) (i * i + j * j) * spacing * spacing;
				if (d2 >= r2) {
					continue;
				}
				double t = 1 - d2 / r2;
				si[n] = i;
				sj[n] = j;
				sw[n] = t * t;
				sum += t * t;
				n++;
			}
		}
		stencilI = java.util.Arrays.copyOf(si, n);
		stencilJ = java.util.Arrays.copyOf(sj, n);
		stencilW = new float[n];
		for (int s = 0; s < n; s++) {
			stencilW[s] = (float) (sw[s] / sum);
		}
	}

	public long worldSeed() {
		return worldSeed;
	}

	public double k() {
		return k;
	}

	/** Spacing of the lattice in blocks. */
	public int spacing() {
		return spacing;
	}

	/** Radius of the kernel in blocks. */
	public int radius() {
		return radius;
	}

	/** Index (1–4) of the code's biome among {@link #FORESTS} when the code has no zone, else 0 (also for -1). */
	public static int member(int code) {
		if (code == Ecotone.UNKNOWN || Habitat.zone(code) != Zone.NONE) {
			return 0;
		}
		return MEMBER[code & 63];
	}

	/**
	 * The canonical code of forest {@code b} (0–3) at a column of code {@code base}: the forest with its fresh site type,
	 * the typical association, its soil and the range flags of the column (the palettes of the four forests match only
	 * their biome).
	 */
	public static int code(int base, int b) {
		HabitatBiome biome = FORESTS[b];
		return Habitat.pack(biome, Zone.NONE, SITES[b], Association.TYPICAL, LandCover.FOREST, Habitat.flags(base),
				Soil.forBiome(biome, Zone.NONE));
	}

	/**
	 * Category of lattice point (i, j), the column (i·s + s/2, j·s + s/2) for the spacing s (the centers of the lattice's
	 * cells, so a border between two columns lies halfway between two points): the forest's index 1–4, 5 for other land
	 * without a zone, 0 for water and the zones (memoized).
	 */
	int category(int i, int j) {
		int c = lattice.get(i, j);
		if (c < 0) {
			int half = spacing / 2;
			int code = sampler.code(i * spacing + half, j * spacing + half);
			c = member(code);
			if (c == 0 && Habitat.zone(code) == Zone.NONE && Habitat.cover(code) != LandCover.WATER) {
				c = 5;
			}
			SAMPLES.increment();
			lattice.put(i, j, c);
		}
		return c;
	}

	/**
	 * Shares of the four forests and of the other land without a zone at lattice point (i, j) (out[0..4], the kernel sum
	 * over the lattice).
	 */
	void node(int i, int j, float[] out, int offset) {
		float a = 0;
		float b = 0;
		float c = 0;
		float d = 0;
		float e = 0;
		for (int s = 0; s < stencilW.length; s++) {
			switch (category(i + stencilI[s], j + stencilJ[s])) {
				case 1 -> a += stencilW[s];
				case 2 -> b += stencilW[s];
				case 3 -> c += stencilW[s];
				case 4 -> d += stencilW[s];
				case 5 -> e += stencilW[s];
				default -> {
				}
			}
		}
		out[offset] = a;
		out[offset + 1] = b;
		out[offset + 2] = c;
		out[offset + 3] = d;
		out[offset + 4] = e;
	}

	/**
	 * The blended code of a column: {@code code} its ecotone code (a member, {@link #member}), {@code s} the shares of the
	 * four forests at the column (moved by the meander), {@code v} the column's draw in [0, 1).
	 */
	static int pick(int code, float[] s, double v) {
		int a = member(code) - 1;
		if (a < 0) {
			return code;
		}
		double total = Math.max(MIN_TOTAL, s[0] + s[1] + s[2] + s[3]);
		double acc = 0;
		for (int b = 0; b < 4; b++) {
			if (b == a) {
				continue;
			}
			acc += s[b] / total;
			if (v < acc) {
				return code(code, b);
			}
		}
		return code;
	}

	/**
	 * Whether the slice forests take over the trees of a column of this code near them ({@link #takeover}): a lowland
	 * woodland of {@link #NEIGHBORS} without a zone or in a spring area (a stream's ash-alder belt in an oak-hornbeam forest).
	 */
	public static boolean takenOver(int code) {
		if (code == Ecotone.UNKNOWN || member(code) > 0 || (NEIGHBORS >>> (code & 63) & 1) == 0) {
			return false;
		}
		Zone z = Habitat.zone(code);
		return z == Zone.NONE || z == Zone.SPRING_AREA;
	}

	/**
	 * The tree code of a column of a neighboring woodland ({@link #takenOver}) near the slice forests (step Z1, decision
	 * CP-B.1: no band of the M2 placeholder trees next to the planner trees). q is the share of the slice forests among
	 * the land without a zone within the radius (water and zones such as the spring area itself do not count); with the
	 * chance smoothstep of q between {@value #TAKEOVER_FROM} and {@value #TAKEOVER_TO} the column takes the canonical code
	 * of one of the slice forests by their shares. A stream's spring-area belt or a patch surrounded by the slice forests
	 * (q near 1) grows their trees, however wide it is within the radius; a large woodland next to them (q about 0.5 at
	 * its edge, falling inside) keeps its own trees.
	 */
	static int takeover(int code, float[] s, double v) {
		double total = s[0] + s[1] + s[2] + s[3];
		if (total < TAKEOVER_MIN) {
			return code;
		}
		double q = total / (total + s[4]);
		double t = Math.clamp((q - TAKEOVER_FROM) / (TAKEOVER_TO - TAKEOVER_FROM), 0, 1);
		double p = t * t * (3 - 2 * t);
		if (!(v < p)) {
			return code;
		}
		double u = v / p * total;
		double acc = 0;
		int last = 0;
		for (int b = 0; b < 4; b++) {
			if (s[b] <= 0) {
				continue;
			}
			last = b;
			acc += s[b];
			if (u < acc) {
				return code(code, b);
			}
		}
		return code(code, last);
	}

	/**
	 * The draw of a column at world (x, z) for a layer (its ecotone salt), uniform in [0, 1): one per column, or one per
	 * patch of {@code patch} blocks whose border is shifted by up to a block per column (the clumps of the ground layer,
	 * as in {@link Ecotone#effective}).
	 */
	public static double draw(long worldSeed, long salt, int x, int z, int patch) {
		long seed = Noise.mix(worldSeed ^ Noise.mix(salt ^ DRAW_SALT));
		long h;
		if (patch <= 1) {
			h = hash(seed, x, z);
		} else {
			long j = hash(seed ^ 0x3C6E_F372_FE94_F82BL, x, z);
			int jx = (int) ((j & 0xFF) % 3) - 1;
			int jz = (int) ((j >>> 8 & 0xFF) % 3) - 1;
			h = hash(seed ^ 0x1F83_D9AB_FB41_BD6BL, Math.floorDiv(x + jx, patch), Math.floorDiv(z + jz, patch));
		}
		return TreeStandPlan.unit(h);
	}

	private static long hash(long seed, int x, int z) {
		return Noise.mix(seed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL));
	}

	/** The share field around one chunk: the lattice points it needs, computed on first use. */
	public Field field(int chunkX, int chunkZ) {
		return new Field(chunkX, chunkZ);
	}

	/**
	 * The share field of the columns a chunk's decoration reads: chunk-local −{@value Ecotone#REACH} −
	 * {@value Ecotone#WARP} … 15 + {@value Ecotone#REACH} + {@value Ecotone#WARP} (the region and the meander).
	 */
	public final class Field {
		private final int i0;
		private final int j0;
		private final int n;
		private final float[] shares;
		private final boolean[] done;

		Field(int chunkX, int chunkZ) {
			int margin = Ecotone.REACH + Ecotone.WARP;
			i0 = Math.floorDiv((chunkX << 4) - margin - spacing, spacing);
			j0 = Math.floorDiv((chunkZ << 4) - margin - spacing, spacing);
			int i1 = Math.floorDiv((chunkX << 4) + 15 + margin, spacing) + 1;
			n = i1 - i0 + 1;
			shares = new float[n * n * SHARES];
			done = new boolean[n * n];
		}

		/** The shares of the four forests and of the other land without a zone at world (x, z), into {@code out[0..4]}. */
		public void at(int x, int z, float[] out) {
			int half = spacing / 2;
			int i = Math.floorDiv(x - half, spacing);
			int j = Math.floorDiv(z - half, spacing);
			float fx = (x - half - i * spacing) / (float) spacing;
			float fz = (z - half - j * spacing) / (float) spacing;
			int c00 = node(i, j);
			int c10 = node(i + 1, j);
			int c01 = node(i, j + 1);
			int c11 = node(i + 1, j + 1);
			for (int b = 0; b < SHARES; b++) {
				float top = shares[c00 + b] + (shares[c10 + b] - shares[c00 + b]) * fx;
				float bottom = shares[c01 + b] + (shares[c11 + b] - shares[c01 + b]) * fx;
				out[b] = top + (bottom - top) * fz;
			}
		}

		/** Sum of the four forests' shares at world (x, z) (how close the forests of the slice are). */
		public float total(int x, int z) {
			float[] s = new float[SHARES];
			at(x, z, s);
			return s[0] + s[1] + s[2] + s[3];
		}

		private int node(int i, int j) {
			int a = i - i0;
			int b = j - j0;
			if (a < 0 || b < 0 || a >= n || b >= n) {
				throw new IllegalArgumentException("column outside the chunk's field: lattice point " + i + ", " + j);
			}
			int c = a * n + b;
			if (!done[c]) {
				ForestBlend.this.node(i, j, shares, SHARES * c);
				done[c] = true;
			}
			return SHARES * c;
		}
	}

	/**
	 * Categories of the lattice points, a direct-mapped table shared by all threads without locks (as the candidate
	 * table of step O1): one {@code long} per slot holds the lattice point and its category, so a reader never sees half
	 * of another thread's entry, and an overwritten entry is only computed again.
	 */
	private static final class Lattice {
		private static final int BITS = 17;
		private static final int COORD_BITS = 26;
		private static final long COORD_MASK = (1L << COORD_BITS) - 1;
		private final AtomicLongArray slots = new AtomicLongArray(1 << BITS);

		private static boolean fits(int g) {
			return g >= -(1 << (COORD_BITS - 1)) && g < 1 << (COORD_BITS - 1);
		}

		private static long cell(int i, int j) {
			return (i & COORD_MASK) << 38 | (j & COORD_MASK) << 12;
		}

		private static int slot(int i, int j) {
			return (int) (Noise.mix(i * 0x9E37_79B9_7F4A_7C15L + j * 0xC2B2_AE3D_27D4_EB4FL) >>> (64 - BITS));
		}

		int get(int i, int j) {
			if (!fits(i) || !fits(j)) {
				return -1;
			}
			long e = slots.getOpaque(slot(i, j));
			if ((e & 1) == 0 || (e & ~0xFFFL) != cell(i, j)) {
				return -1;
			}
			return (int) (e >>> 1 & 7);
		}

		void put(int i, int j, int category) {
			if (fits(i) && fits(j)) {
				slots.setOpaque(slot(i, j), cell(i, j) | (long) category << 1 | 1);
			}
		}
	}
}
