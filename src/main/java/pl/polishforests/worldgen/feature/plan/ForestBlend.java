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
 * Wide ecotones of the forests with the trees of the tree planner (step Z1 of docs/04-pionowy-wycinek.md, decision CP-B.1,
 * rule Z10; step F1: any forest of {@link #FORESTS}): between the zonal forests of the lowland sequence of fertility of the
 * vertical slice ({@link #TRANSECT}: fresh pine forest, mixed pine-oak forest, mixed forest and oak-hornbeam forest) and
 * the forests the steps F add (step F1: the lowland beech forest) the palettes of the tree stand, the understory and the
 * ground layer change over a belt of about {@value #RADIUS} m·k instead of the ramp of {@link Ecotone}, which can only
 * reach {@value Ecotone#REACH} blocks (the codes of the chunk's 8 neighbors; in the realistic scale the composition changed
 * over about 25 m, at the lower bound of the norm N15 of 20–100 m). Only the pairs of {@link #blends} mix in the belt; the
 * other pairs of these forests (step F1: the beech forest and the fresh pine forest) keep the ramp of {@link Ecotone}.
 *
 * <p><b>Share field.</b> The habitat of the world is sampled on a lattice of {@value #SPACING} m·k (the classifier on a
 * sample of the landscape model, the same pure function the chunks are filled with; memoized in a table shared by all
 * threads, {@link Lattice}). At each lattice point the share of each of the forests is the kernel-weighted share of
 * the lattice points of that forest (without a zone) within {@value #RADIUS} m·k, with the weight (1 − d²/R²)²; between
 * the lattice points the shares are interpolated bilinearly. At a straight border between two of the forests the share of
 * the other side falls smoothly from 50% at the border to 0 at R (10–90% of the change over about 0.94 R), and it follows
 * the true shape of the border, so the belt has no straight line where the border has none; the column is moved by the
 * meander of {@link Ecotone} before the field is read.
 *
 * <p><b>Mixing.</b> A column whose ecotone code ({@link Ecotone#effective}) is one of the forests without a zone draws a
 * uniform value v (its own hash per layer, in patches for the ground layer) and takes the canonical code of another forest
 * b it blends with ({@link #code}, {@link #blends}) when v falls into b's share; the shares are normalized by the sum of
 * the column's forest and its partners (at least {@value #MIN_TOTAL}, {@link #pickTotal}), so the composition is
 * continuous across a border between two forests: on both sides a column has the other forest's palette with the share of
 * the field. The blending pairs do not mix in the ramp of
 * {@link Ecotone} when a region carries a blend ({@link Ecotone.Region#withBlend}); their soils still do (layer SOIL, the
 * sharp substrate border of N15 stays sharp in the top blocks).
 *
 * <p>Pure functions of the world seed, the scale's k and the classifier (mode), so the result does not depend on the
 * order in which chunks are decorated, and a neighbor's tree code stays provable ({@link Ecotone#provableTrees}).
 */
public final class ForestBlend {
	/**
	 * The forests of the blend, the forests with the trees of the tree planner (step F1): the zonal forests of the slice
	 * transect Bśw → BMśw → LMśw → grąd (docs/04 §6, N15; {@link #TRANSECT}, their indices 0–3 kept), then the forests of
	 * the steps F in their order (F1: the lowland beech forest; F2: the alder carr and the ash-alder forest; F3: the
	 * upland fir forest and the Carpathian beech forest; F4: the dry, the moist pine forest, the bog woodland and the
	 * coastal pine forest; F5: the elm-ash and the willow-poplar floodplain forest). Each step F
	 * appended its forests here (steps F1-F5 also took them off the takeover list of step Z1, removed in step F7); since
	 * step F7 every pair blends ({@link #BLENDS}).
	 */
	public static final HabitatBiome[] FORESTS = {HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST,
			HabitatBiome.MIXED_FOREST, HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST,
			HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST, HabitatBiome.UPLAND_FIR_FOREST,
			HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.DRY_PINE_FOREST, HabitatBiome.MOIST_PINE_FOREST,
			HabitatBiome.BOG_WOODLAND, HabitatBiome.COASTAL_PINE_FOREST, HabitatBiome.ELM_ASH_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.MONTANE_SPRUCE_FOREST, HabitatBiome.GRAY_ALDER_FOREST,
			HabitatBiome.DWARF_PINE_SCRUB};
	/** The forests of the slice transect, the first four of {@link #FORESTS} (norm N15, {@code TransectPalettes}). */
	public static final HabitatBiome[] TRANSECT = java.util.Arrays.copyOf(FORESTS, 4);
	/** Index of the lowland beech forest in {@link #FORESTS} (step F1). */
	public static final int BEECH = 4;
	/** Indices of the alder carr and the ash-alder forest in {@link #FORESTS} (step F2). */
	public static final int ALDER_CARR = 5;
	public static final int ASH_ALDER = 6;
	/** Indices of the upland fir forest and the Carpathian (montane) beech forest in {@link #FORESTS} (step F3). */
	public static final int UPLAND_FIR = 7;
	public static final int MONTANE_BEECH = 8;
	/** Indices of the dry and the moist pine forest, the bog woodland and the coastal pine forest in {@link #FORESTS} (step F4). */
	public static final int DRY_PINE = 9;
	public static final int MOIST_PINE = 10;
	public static final int BOG_WOODLAND = 11;
	public static final int COASTAL_PINE = 12;
	/** Indices of the elm-ash and the willow-poplar floodplain forest in {@link #FORESTS} (step F5). */
	public static final int ELM_ASH = 13;
	public static final int WILLOW_POPLAR = 14;
	/** Indices of the montane spruce forest, the gray alder forest and the dwarf pine scrub in {@link #FORESTS} (step F6). */
	public static final int MONTANE_SPRUCE = 15;
	public static final int GRAY_ALDER = 16;
	public static final int DWARF_PINE = 17;
	/**
	 * Pairs of {@link #FORESTS} that mix in the blend's belt (bit b of entry a). Steps Z1 to F6 named the pairs of
	 * neighbors on related sites (the transect, then each step's forests with theirs) and let the other pairs meet in the
	 * ramp of {@link Ecotone}; step F7 (docs/04 "Projekt F7", rule Z10) blends every pair, so any two forests that meet
	 * change over the belt of about {@value #RADIUS} m·k, also across a sharp change of the site (beech and fresh pine
	 * forest, fir and fresh pine forest, carr and beech forest). The open land, the scrub and the waterside zones meet the
	 * forests in the ramp, and the water edge stays sharp.
	 */
	private static final int[] BLENDS = allPairs();
	/** Radius of the kernel (m·k). */
	public static final double RADIUS = 48;
	/** Spacing of the lattice (m·k); in blocks at least 2. */
	public static final double SPACING = 8;
	/** Least sum of the forests' shares by which the shares are normalized (see the class description). */
	public static final double MIN_TOTAL = 0.5;
	/** Salt of the draws of the blend (a new field, a new salt), mixed with each layer's ecotone salt. */
	public static final long DRAW_SALT = 0x2B1E_7D5F_0C39L;
	/*
	 * Steps Z1 to F5 had a takeover: the forests of the blend took over the trees of the lowland woodlands next to them that
	 * were not yet forests of the planner (NEIGHBORS, with the salt 0x4F2D_A61C_83E5L); since step F5 none was left, and
	 * step F7 removed the mechanism (docs/04 "Projekt F7"; the result is unchanged, the list was empty).
	 */
	/** Number of shares of the field: the forests of {@link #FORESTS}, then the other land without a zone. */
	public static final int SHARES = FORESTS.length + 1;
	/** Index of the share of the other land without a zone. */
	public static final int OTHER = FORESTS.length;

	/** Step F7: every pair of {@link #FORESTS} blends (the forests that never meet have no share in each other's belt). */
	private static int[] allPairs() {
		int[] out = new int[FORESTS.length];
		for (int a = 0; a < FORESTS.length; a++) {
			out[a] = (1 << FORESTS.length) - 1 & ~(1 << a);
		}
		return out;
	}

	/** Whether forests a and b (indices into {@link #FORESTS}) mix in the blend's belt. */
	public static boolean blends(int a, int b) {
		return a != b && (BLENDS[a] >>> b & 1) != 0;
	}

	/** Whether two codes are two different forests of {@link #FORESTS} without a zone that mix in the blend's belt. */
	public static boolean blendsCodes(int own, int other) {
		int a = member(own) - 1;
		int b = member(other) - 1;
		return a >= 0 && b >= 0 && blends(a, b);
	}

	/** Lattice points classified (samples of the landscape model, the blend's cost; diagnostics of the slice test). */
	public static final java.util.concurrent.atomic.LongAdder SAMPLES = new java.util.concurrent.atomic.LongAdder();
	/** Kernel sums of lattice nodes computed (not found in the shared node table; diagnostics of the slice test). */
	public static final java.util.concurrent.atomic.LongAdder NODES = new java.util.concurrent.atomic.LongAdder();

	/**
	 * Forest site type of each forest's canonical code (the lowland beech forest: its fertile form, Galio-Fagetum; the
	 * alder carr: the eutrophic Ribeso-Alnetum, Ol; the ash-alder forest: OlJ; step F3: the upland fir forest its
	 * mesotrophic Abietetum polonicum, LMwyż, and the Carpathian beech forest its fertile Dentario glandulosae-Fagetum, LG;
	 * step F4: the dry pine forest Bs, the moist pine forest Bw, the bog woodland its pine form Bb, the coastal pine forest
	 * behind the wind belt Bśw; step F5: both floodplain forests Lł; step F6: the montane spruce forest its poor
	 * Plagiothecio-Piceetum, BG, the gray alder forest LłG and the dwarf pine scrub its subalpine site).
	 */
	private static final ForestSiteType[] SITES = {ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.FRESH_MIXED_CONIFEROUS,
			ForestSiteType.FRESH_MIXED_BROADLEAVED, ForestSiteType.FRESH_BROADLEAVED, ForestSiteType.FRESH_BROADLEAVED,
			ForestSiteType.ALDER_SWAMP, ForestSiteType.ASH_ALDER_SWAMP, ForestSiteType.UPLAND_MIXED_BROADLEAVED,
			ForestSiteType.MOUNTAIN_BROADLEAVED, ForestSiteType.DRY_CONIFEROUS, ForestSiteType.MOIST_CONIFEROUS,
			ForestSiteType.BOGGY_CONIFEROUS, ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.RIPARIAN, ForestSiteType.RIPARIAN,
			ForestSiteType.MOUNTAIN_CONIFEROUS, ForestSiteType.MOUNTAIN_RIPARIAN, ForestSiteType.SUBALPINE_SCRUB};
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
	private final Nodes nodes = new Nodes();
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

	/** Index (1–n) of the code's biome among {@link #FORESTS} when the code has no zone, else 0 (also for -1). */
	public static int member(int code) {
		if (code == Ecotone.UNKNOWN || Habitat.zone(code) != Zone.NONE) {
			return 0;
		}
		return MEMBER[code & 63];
	}

	/**
	 * The canonical code of forest {@code b} (an index into {@link #FORESTS}) at a column of code {@code base}: the forest
	 * with its fresh site type, the typical association, its soil and the range flags of the column (the palettes of the
	 * forests of the transect match only their biome; the lowland beech forest's canonical code is its fertile form).
	 */
	public static int code(int base, int b) {
		HabitatBiome biome = FORESTS[b];
		return Habitat.pack(biome, Zone.NONE, SITES[b], Association.TYPICAL, LandCover.FOREST, Habitat.flags(base),
				Soil.forBiome(biome, Zone.NONE));
	}

	/**
	 * Step TT4: the code a column of code {@code base} takes for its partner forest {@code b} in {@link #pick}: the
	 * canonical code ({@link #code}), except in the stone pine series of the Tatras (Z10: the spruce-stone pine forest and
	 * the dwarf pine with stone pines blend into each other and keep their stone pines across the belt): a column of the
	 * series takes the montane spruce forest and the dwarf pine scrub with the series' association. Every other column
	 * (no association of the series outside the Tatra granite and gneiss) takes the canonical code as before the step.
	 */
	public static int partner(int base, int b) {
		int code = code(base, b);
		if (Habitat.association(base) == Association.PINO_CEMBRAE_PICEETUM && (b == MONTANE_SPRUCE || b == DWARF_PINE)) {
			return Habitat.withAssociation(code, Association.PINO_CEMBRAE_PICEETUM);
		}
		return code;
	}

	/**
	 * Category of lattice point (i, j), the column (i·s + s/2, j·s + s/2) for the spacing s (the centers of the lattice's
	 * cells, so a border between two columns lies halfway between two points): the forest's index 1–n of {@link #FORESTS},
	 * n + 1 for other land without a zone, 0 for water and the zones (memoized).
	 */
	int category(int i, int j) {
		return entry(i, j) & Lattice.CATEGORY_MASK;
	}

	/**
	 * The lattice entry of point (i, j): its category ({@link #category}) and, in bit 7 ({@link #STONE_PINE_VARIANT}, step
	 * TT4), whether the point is the spruce-stone pine forest variant of the montane spruce forest (memoized).
	 */
	private int entry(int i, int j) {
		int e = lattice.get(i, j);
		if (e < 0) {
			int half = spacing / 2;
			int code = sampler.code(i * spacing + half, j * spacing + half);
			int c = member(code);
			if (c == 0 && Habitat.zone(code) == Zone.NONE && Habitat.cover(code) != LandCover.WATER) {
				c = OTHER + 1;
			}
			e = c;
			if (c == MONTANE_SPRUCE + 1 && Habitat.association(code) == Association.PINO_CEMBRAE_PICEETUM) {
				e |= STONE_PINE_VARIANT;
			}
			SAMPLES.increment();
			lattice.put(i, j, e);
		}
		return e;
	}

	/** Step TT4: the bit of a lattice entry that marks the spruce-stone pine forest variant of the montane spruce forest. */
	static final int STONE_PINE_VARIANT = 1 << 6;

	/**
	 * Shares of the forests and of the other land without a zone at lattice point (i, j) (out[0..{@link #OTHER}], the
	 * kernel sum over the lattice; each share summed in the order of the stencil, the same floats as before step F1).
	 */
	void node(int i, int j, float[] out, int offset) {
		float[] sum = new float[SHARES];
		for (int s = 0; s < stencilW.length; s++) {
			int c = category(i + stencilI[s], j + stencilJ[s]);
			if (c > 0) {
				sum[c - 1] += stencilW[s];
			}
		}
		System.arraycopy(sum, 0, out, offset, SHARES);
	}

	/**
	 * {@link #node(int, int, float[], int)} through the node table shared by all threads ({@link Nodes}): the kernel sum of
	 * a lattice point is computed once while it stays in the table, not again by each chunk whose field covers it (a
	 * chunk's field holds about 100 nodes in the realistic scale and about 300 in the gameplay scale, each a sum over
	 * {@link #stencilSize()} lattice points). The sum is a pure function of the lattice point, so the result does not
	 * depend on whether it came from the table.
	 */
	void sharedNode(int i, int j, float[] out, int offset) {
		Nodes.Node e = nodes.get(i, j);
		if (e == null) {
			node(i, j, out, offset);
			NODES.increment();
			nodes.put(new Nodes.Node(i, j, out, offset));
			return;
		}
		System.arraycopy(e.s, 0, out, offset, SHARES);
	}

	/**
	 * The forest of {@link #FORESTS} at the lattice point nearest to world (x, z) (its category, memoized), or null for
	 * other land, water and the zones.
	 */
	public @org.jspecify.annotations.Nullable HabitatBiome forestAt(int x, int z) {
		int c = category(Math.floorDiv(x, spacing), Math.floorDiv(z, spacing));
		return c >= 1 && c <= FORESTS.length ? FORESTS[c - 1] : null;
	}

	/**
	 * The stand profiles of the world's stand map (step F1, {@link pl.polishforests.worldgen.tree.StandProfile}): the
	 * profile of the forest at the nearest lattice point ({@link #forestAt}), a pure function of the world.
	 */
	public pl.polishforests.worldgen.tree.StandProfile.Field standProfiles() {
		return (x, z) -> {
			// Step TT4: the spruce-stone pine forest variant of the montane spruce forest has its own profile.
			int e = entry(Math.floorDiv(x, spacing), Math.floorDiv(z, spacing));
			if ((e & STONE_PINE_VARIANT) != 0) {
				return pl.polishforests.worldgen.tree.StandProfile.STONE_PINE;
			}
			int c = e & Lattice.CATEGORY_MASK;
			return pl.polishforests.worldgen.tree.StandProfile.of(c >= 1 && c <= FORESTS.length ? FORESTS[c - 1] : null);
		};
	}

	/** Number of lattice points in the kernel's stencil. */
	public int stencilSize() {
		return stencilW.length;
	}

	/**
	 * The blended code of a column: {@code code} its ecotone code (a member, {@link #member}), {@code s} the shares of the
	 * forests at the column (moved by the meander), {@code v} the column's draw in [0, 1). The column takes a partner of its
	 * forest ({@link #blends}) with the partner's share over {@link #pickTotal}.
	 */
	static int pick(int code, float[] s, double v) {
		int a = member(code) - 1;
		if (a < 0) {
			return code;
		}
		double total = pickTotal(a, s);
		double acc = 0;
		for (int b = 0; b < FORESTS.length; b++) {
			if (!blends(a, b)) {
				continue;
			}
			acc += s[b] / total;
			if (v < acc) {
				return partner(code, b);
			}
		}
		return code;
	}

	/**
	 * The sum the shares of a column of forest a are normalized by in {@link #pick}: the shares of a and of its partners
	 * ({@link #blends}) summed as floats in the order of {@link #FORESTS} (a forest of the transect far from the beech
	 * forest: the sum of step Z1 plus a share 0), at least {@value #MIN_TOTAL}.
	 */
	public static double pickTotal(int a, float[] s) {
		float sum = 0;
		for (int b = 0; b < FORESTS.length; b++) {
			if (b == a || blends(a, b)) {
				sum += s[b];
			}
		}
		return Math.max(MIN_TOTAL, sum);
	}

	/** Sum of the shares of all forests of {@link #FORESTS} (floats, in their order). */
	public static float forestTotal(float[] s) {
		float sum = 0;
		for (int b = 0; b < FORESTS.length; b++) {
			sum += s[b];
		}
		return sum;
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

		/** The shares of the forests and of the other land without a zone at world (x, z), into {@code out[0..OTHER]}. */
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

		/** Sum of the forests' shares at world (x, z) (how close the forests with the planner trees are). */
		public float total(int x, int z) {
			float[] s = new float[SHARES];
			at(x, z, s);
			return forestTotal(s);
		}

		private int node(int i, int j) {
			int a = i - i0;
			int b = j - j0;
			if (a < 0 || b < 0 || a >= n || b >= n) {
				throw new IllegalArgumentException("column outside the chunk's field: lattice point " + i + ", " + j);
			}
			int c = a * n + b;
			if (!done[c]) {
				ForestBlend.this.sharedNode(i, j, shares, SHARES * c);
				done[c] = true;
			}
			return SHARES * c;
		}
	}

	/**
	 * Shares of the lattice nodes (the kernel sums), a direct-mapped table shared by all threads without locks: a slot
	 * holds an immutable entry with its lattice point (final fields, so a reader sees a whole entry or none), and an
	 * overwritten entry is only computed again.
	 */
	private static final class Nodes {
		private static final int BITS = 16;
		private final java.util.concurrent.atomic.AtomicReferenceArray<Node> slots =
				new java.util.concurrent.atomic.AtomicReferenceArray<>(1 << BITS);

		static final class Node {
			final int i;
			final int j;
			final float[] s;

			Node(int i, int j, float[] s, int offset) {
				this.i = i;
				this.j = j;
				this.s = java.util.Arrays.copyOfRange(s, offset, offset + SHARES);
			}
		}

		private static int slot(int i, int j) {
			return (int) (Noise.mix(i * 0xC2B2_AE3D_27D4_EB4FL + j * 0x9E37_79B9_7F4A_7C15L) >>> (64 - BITS));
		}

		@org.jspecify.annotations.Nullable Node get(int i, int j) {
			Node e = slots.getOpaque(slot(i, j));
			return e != null && e.i == i && e.j == j ? e : null;
		}

		void put(Node e) {
			slots.setRelease(slot(e.i, e.j), e);
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
		/**
		 * Bits 1–6 of an entry hold the category (up to 62 forests), bit 7 the stone pine variant (step TT4,
		 * {@link #STONE_PINE_VARIANT}); bits 8–11 stay free.
		 */
		static final int CATEGORY_MASK = 63;
		/** The bits of an entry's value: the category and the variant. */
		private static final int VALUE_MASK = 127;
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
			return (int) (e >>> 1 & VALUE_MASK);
		}

		void put(int i, int j, int category) {
			if (fits(i) && fits(j)) {
				slots.setOpaque(slot(i, j), cell(i, j) | (long) category << 1 | 1);
			}
		}
	}
}
