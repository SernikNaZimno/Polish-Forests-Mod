package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree shape planner (step V6 of docs/04-pionowy-wycinek.md, research-technika-drzew.md §3.2): a pure function
 * {@link TreeRequest} → {@link TreeVoxels} without game classes (rule ZW-1). Parametric (Weber and Penn style)
 * skeletons by archetype ({@link SpeciesShape.Archetype}), radii from the pipe model, voxelization by a 3D DDA with the
 * parent towards the root, leaf clusters at the shoot tips and leaf distances by a breadth-first search.
 *
 * <p><b>Steps.</b>
 * <ol>
 * <li><b>Allometry</b> ({@link #size}): height, breast-height diameter, crown base and crown radius from the species, the
 * age class and the layer, with a jitter from the hash of the trunk column (salt {@value #SALT}, rule ZW-2). The crown
 * base of a canopy tree follows the species' crown ratio; a lone, open-grown tree ({@link TreeRequest#openGrown}) has a
 * longer and wider crown. A subcanopy or regeneration tree is no taller than the split surface above its layer.</li>
 * <li><b>Layer split surfaces</b> ({@link #split}): two wavy surfaces over the world columns (salts
 * {@value #UPPER_SALT} and {@value #LOWER_SALT}), at a share of the stand top height of the age class at the column with
 * ±{@value #UPPER_WOBBLE} and ±{@value #LOWER_WOBBLE} blocks of smooth noise. In a stand a canopy tree keeps its leaves
 * and branches on or above the upper surface, a subcanopy tree between the two, a regeneration tree below the lower
 * one; every tree of a stand sees the same surfaces, so on level ground the leaves of two layers never share a block,
 * and the boundary between the layers is no stand-wide straight line (rule Z10). The rows next to a surface are thinned
 * (a ragged edge instead of a cut). A lone tree has no surface above or below a canopy crown. In a stand of the world
 * the surfaces stand at their height above the ground of each column ({@link TreeRequest#ground}, step V9), so on a
 * slope they follow the terrain and the leaves of the layers of neighboring trees still keep apart.</li>
 * <li><b>Skeleton:</b> a trunk with a slight lean and wobble; branches are curves of nodes about one block apart whose
 * angle from the vertical turns smoothly from the base to the tip and whose horizontal direction never reverses, so the
 * voxels make slanted staircases instead of right-angled elbows (risk R4). Conifers with whorls rotated by the golden
 * angle and a leader that stays in its column (the pine with a crown in its upper quarter to third, tufts along the
 * outer half of each branch and around the leader in the upper crown, an umbrella of few long limbs when old, dry
 * stubs below the crown; the spruce with staggered whorls, the lowest ones drooping
 * most, and a narrow spire), the birch with ascending branches whose tips hang, broad-leaved trees with a leader and
 * curved lateral branches with twigs, or (the oak) a fork into thick, crooked limbs that climb in vertical runs of one
 * to three blocks with one-block sideways jogs (axis-aligned, so they keep their radius). The branch lengths follow the
 * crown envelope ({@link SpeciesShape.Profile}), shortened to the columns the tree may use; at a forest edge (a
 * territory with {@link CrownTerritory#edge()}) branches towards the free side grow up to 30% longer, and a canopy tree
 * next to open land ({@link Open}) has live branches down to {@value #SKIRT_FROM} of its height on that side (review of
 * V9, round 1).</li>
 * <li><b>Pipe model:</b> r_parent^n = Σ r_child^n from the tips (n = 2.2–2.4), scaled so that the trunk at the crown
 * base has its share of the breast-height radius; below the crown base the cross-section r^n grows linearly to the base
 * (the branches lost by self-pruning), so the steps r + 1 → r of the thin trunk spread along the bole. Segments more
 * than 25° off an axis have at most 2 px (risk R4: thick diagonal limbs would show stairs).</li>
 * <li><b>Voxelization:</b> each segment from its parent node to its node by a 3D DDA whose steps are face-adjacent, so
 * the parent of every block is one of its six neighbors. A block takes the radius of its segment but never more than its
 * parent (the radius does not grow towards the tips); r ≤ 7 px is a thin trunk, more a full log. A segment that enters a
 * forbidden cell (outside the territory, the disc of {@value #REACH} blocks, for the lower layers next to another trunk,
 * or across a split surface) or the wood of another branch stops there, and its children with it (a smaller leaf
 * cluster marks its end); a segment that meets the wood of an ancestor, or at its start of a sibling from the same node,
 * goes on from that block. The Scots pine
 * gets the "mirror": the bark of the trunk changes at 0.45–0.55 of the height (rising with the age class) through the
 * two blocks of the transition zone (V1b), the live branches above it have the upper bark, the dry stubs keep the lower
 * one.</li>
 * <li><b>Foliage:</b> ragged ellipsoids with holes around the shoot tips, each with its own height and depth jitter;
 * large broad-leaved crowns stay empty inside (leaves in the outer shell only); leaves only in the allowed columns,
 * between the split surfaces of the layer and not in wood. In the upper half of the crown every thin block growing up
 * from its parent (the leader, upturned tips) gets a leaf on its four sides (review of V9, round 1). Thin branch tips
 * left without a leaf next to them (clipped clusters; an upward tip of the upper crown without one at its side) are
 * pruned. A breadth-first search from the wood through the tree's own leaves gives the leaf distance;
 * leaves farther than {@value TreeVoxels#MAX_DISTANCE} are dropped.</li>
 * </ol>
 *
 * <p><b>Window.</b> Every block lies within {@value #REACH} blocks of the trunk column horizontally, so in the 3 × 3
 * chunks around the trunk's chunk (risk R6); with a territory every block lies in a column of the territory, so two
 * trees of one layer never write the same column. For the subcanopy and the regeneration the wood also keeps one column
 * away from every other trunk column (the trunks of the canopy pass through their layers).
 *
 * <p><b>Determinism.</b> The result depends only on the request (thread-local working arrays are fully reset, the
 * random numbers come from the hash of the trunk column, the split surfaces from the world column); math through
 * {@link StrictMath}.
 */
public final class TreeShapePlan {
	/** Salt of the tree shape hash (rule ZW-2). */
	public static final String SALT = "feature.tree.shape";
	/** Salt of the noise of the split surface between the canopy and the subcanopy (round 1 of the V6 review). */
	public static final String UPPER_SALT = "feature.tree.layers.upper";
	/** Salt of the noise of the split surface between the subcanopy and the regeneration. */
	public static final String LOWER_SALT = "feature.tree.layers.lower";
	/**
	 * Salt of the short wave of the upper split surface (review of V9, round 1): the crowns of a layer end at the
	 * surface, so its long wave alone left level shelves of crown bottoms across a view of 20-40 blocks.
	 */
	public static final String SHORT_SALT = "feature.tree.layers.upper_short";
	/**
	 * Salt of the short wave of the lower split surface (review of V9, round 2): the crowns of the subcanopy start at it,
	 * so their bottoms made a flat ceiling of leaves just above head height across the view.
	 */
	public static final String LOWER_SHORT_SALT = "feature.tree.layers.lower_short";
	/** Largest horizontal distance of a block from the trunk column: the write window of 3 × 3 chunks. */
	public static final int REACH = CrownTerritories.MAX_REACH;
	/** Largest height of a block above the trunk base (exclusive). */
	public static final int MAX_HEIGHT = 48;
	/** Pixels of the trunk radius per centimeter of the diameter: 16 px per meter, half of the diameter. */
	public static final double PX_PER_CM = 0.08;
	/** Largest offset of the upper and the lower split surface from their mean height (blocks). */
	public static final int UPPER_WOBBLE = 3;
	public static final double LOWER_WOBBLE = 1.5;
	/** Amplitude (blocks) and wavelength (blocks) of the short wave of the upper and of the lower split surface. */
	public static final double SHORT_WOBBLE = 1.0;
	public static final double SHORT_WAVELENGTH = 7;

	private static final int SIDE = 2 * REACH + 1;
	private static final int CELLS = SIDE * SIDE * MAX_HEIGHT;

	/**
	 * Top height of a stand by age class (blocks) [S], the scale of the split surfaces: the canopy trees of the slice are
	 * 13–16 blocks tall in the pole stage, 21–28 when mature and 23–32 in old growth.
	 */
	private static final double[] STAND_TOP = {14, 25, 29};
	/** Mean height of the split surfaces as a share of the stand top height [S], to be calibrated with N2 in V9. */
	private static final double UPPER_SHARE = 0.44;
	private static final double LOWER_SHARE = 0.2;
	/** Wavelength of the split surface noise (blocks) and the spacing of its grid of corner values. */
	private static final double WOBBLE_WAVELENGTH = 13;
	private static final int GRID = 4;
	/** Share of the cells of the row next to a split surface left empty (a ragged edge). */
	private static final double EDGE_THIN = 0.45;
	/** Height of a subcanopy tree as a share of a canopy tree of the species, before its split surface caps it. */
	private static final double SUBCANOPY_HEIGHT = 0.62;
	private static final double SUBCANOPY_DBH = 0.55;
	private static final double SUBCANOPY_CROWN = 0.7;
	/** Height of the regeneration by age class (blocks), before its split surface caps it. */
	private static final double[] REGENERATION_HEIGHT = {3.0, 4.5, 5.0};
	/** An open-grown (lone) canopy tree: crown ratio added and crown radius factor. */
	private static final double OPEN_RATIO = 0.15;
	private static final double OPEN_RADIUS = 1.25;
	/** Diagonal segments: at most this radius (px) when more than {@link #DIAGONAL} off every axis (risk R4). */
	private static final int DIAGONAL_RADIUS = 2;
	/**
	 * Lowest live branch of a canopy tree on the side of open land at a forest edge, as a share of its height (review of
	 * V9, round 1), and the spacing of those branches as a multiple of the species' branch spacing.
	 */
	private static final double SKIRT_FROM = 0.2;
	/**
	 * Fewest distinct leaves next to the wood of a side branch that keep it (review of V9, round 1; distinct since round
	 * 2), the share of the blocks of the outer half of a side branch that must be covered and the leaves among the 26
	 * neighbors that cover a block (round 2).
	 */
	private static final int MIN_BRANCH_LEAVES = 6;
	private static final double MIN_TIP_COVER = 0.6;
	private static final int COVER_LEAVES = 2;
	private static final double SKIRT_SPACING = 1.3;
	/** Share of the crown length from its base above which the upward shoots are sleeved with leaves (review of V9). */
	private static final double SLEEVE_FROM = 0.5;
	/**
	 * Share of the crown length from its base above which the bare stretches of the trunk and the limbs sprout leaves, and
	 * their largest radius (px) (review of V9, round 2).
	 */
	private static final double SPROUTS_FROM = 0.1;
	private static final int SPROUTS_RADIUS = 4;
	/** Reach of the side check of the stem sleeve (review of V10, round 1; {@link CrownCover#openStem}). */
	private static final int STEM_REACH = CrownCover.STEM_REACH;
	/** Depth below the top of a broad-leaved canopy tree within which its upturned tips must be covered on every side. */
	private static final int TIP_DEPTH = 4;
	/**
	 * A suppressed canopy tree of a stand (review of V9, round 2, carried into V10): a tree at least
	 * {@value #SUPPRESSED_HEIGHT} blocks tall whose crown the territories of its neighbors cut down to fewer than
	 * {@value #SUPPRESSED_LEAVES} leaves (or fewer than one leaf per two blocks of its height) is not planned at all. Such a
	 * tree was a bare pole of a thin trunk with a tuft or a single leaf on top, plain from 25-35 blocks above the canopy; in
	 * a real stand it dies of suppression (dead standing wood belongs to step V11).
	 */
	static final int SUPPRESSED_HEIGHT = 12;
	static final int SUPPRESSED_LEAVES = 12;
	private static final double DIAGONAL = StrictMath.cos(StrictMath.toRadians(25));
	/** Share of the height where the pine's "mirror" starts, by age class (plan V6: 0.45–0.55, rising with age). */
	private static final double[] MIRROR = {0.45, 0.5, 0.55};
	private static final double GOLDEN = StrictMath.toRadians(137.5);
	private static final int AZIMUTH_BINS = 32;
	/** Largest number of nodes of one branch curve. */
	private static final int CURVE_MAX = 48;

	private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

	private TreeShapePlan() {
	}

	/** Plans the tree of the request (thread-local working arrays). */
	public static TreeVoxels plan(TreeRequest request) {
		return WORKSPACE.get().plan(request);
	}

	/** A working state for one thread (for tests that want their own). */
	public static Workspace workspace() {
		return new Workspace();
	}

	/**
	 * Height of a split surface at the world column (x, z), in blocks above the trunk base: the lowest block of the layer
	 * above {@code lower} where that layer claims the column, one above the highest block of {@code lower}.
	 * {@link CrownLayer#SUBCANOPY} gives the surface between the canopy and the subcanopy, {@link CrownLayer#REGENERATION}
	 * the one between the subcanopy and the regeneration; {@code age} is the age class of the stand at the column.
	 */
	public static int split(long worldSeed, CrownLayer lower, AgeClass age, int x, int z) {
		if (lower == CrownLayer.CANOPY) {
			throw new IllegalArgumentException("the canopy has no layer above it");
		}
		Noise upper = new Noise(worldSeed).derive(UPPER_SALT);
		Noise low = new Noise(worldSeed).derive(LOWER_SALT);
		int gx = Math.floorDiv(x, GRID);
		int gz = Math.floorDiv(z, GRID);
		double fx = (x - gx * GRID) / (double) GRID;
		double fz = (z - gz * GRID) / (double) GRID;
		double w1 = bilinear(corner(upper, gx, gz), corner(upper, gx + 1, gz), corner(upper, gx, gz + 1),
				corner(upper, gx + 1, gz + 1), fx, fz);
		double w2 = bilinear(corner(low, gx, gz), corner(low, gx + 1, gz), corner(low, gx, gz + 1), corner(low, gx + 1, gz + 1),
				fx, fz);
		int s2 = lowerSplit(age, w2 + lowerShortWave(new Noise(worldSeed).derive(LOWER_SHORT_SALT), x, z));
		return lower == CrownLayer.REGENERATION ? s2 : upperSplit(age, w1 + shortWave(new Noise(worldSeed).derive(SHORT_SALT), x, z), s2);
	}

	/** The short wave of the upper split surface at a world column, in units of {@link #UPPER_WOBBLE}. */
	private static double shortWave(Noise n, int x, int z) {
		return SHORT_WOBBLE / UPPER_WOBBLE * Math.clamp(1.6 * n.at(x, z, SHORT_WAVELENGTH), -1, 1);
	}

	/** The short wave of the lower split surface at a world column, in units of {@link #LOWER_WOBBLE}. */
	private static double lowerShortWave(Noise n, int x, int z) {
		return SHORT_WOBBLE / LOWER_WOBBLE * Math.clamp(1.6 * n.at(x, z, SHORT_WAVELENGTH), -1, 1);
	}

	/** Noise value of a corner of the split grid in [-1, 1]. */
	private static double corner(Noise n, long gx, long gz) {
		return Math.clamp(1.8 * n.fbm(gx * GRID, gz * GRID, WOBBLE_WAVELENGTH, 2, 0.5), -1, 1);
	}

	private static double bilinear(double v00, double v10, double v01, double v11, double fx, double fz) {
		double a = v00 + (v10 - v00) * fx;
		double b = v01 + (v11 - v01) * fx;
		return a + (b - a) * fz;
	}

	private static int lowerSplit(AgeClass age, double w) {
		return Math.max(3, (int) Math.round(LOWER_SHARE * STAND_TOP[age.ordinal()] + LOWER_WOBBLE * w));
	}

	private static int upperSplit(AgeClass age, double w, int lower) {
		return Math.max(lower + 3, (int) Math.round(UPPER_SHARE * STAND_TOP[age.ordinal()] + UPPER_WOBBLE * w));
	}

	/**
	 * Size of a planned tree: total height (blocks), crown base (height of the lowest live branch, blocks above the base),
	 * crown radius (blocks) and the trunk radius at the base (px).
	 */
	public record Size(double height, double crownBase, double crownRadius, double baseRadius) {
	}

	/** Size of the tree of a request (the allometry step alone). */
	public static Size size(TreeRequest request) {
		Workspace w = WORKSPACE.get();
		w.begin(request);
		return w.size;
	}

	/**
	 * The ground of the world columns around a tree in a stand (step V9): the split surfaces follow the ground, so on a
	 * slope the leaves of two layers of neighboring trees still keep apart.
	 */
	@FunctionalInterface
	public interface Ground {
		/** Y of the top ground block of world column (x, z), or {@link #UNKNOWN}. */
		int top(int x, int z);

		int UNKNOWN = Integer.MIN_VALUE;
	}

	/**
	 * Open land around a tree at a forest edge (review of V9, round 1): the world columns without forest (a field, a
	 * meadow, a heath), where no tree of a lower layer grows, so a canopy tree of the edge may carry live branches low on
	 * that side, as the trees of a real forest edge do.
	 */
	@FunctionalInterface
	public interface Open {
		/** Whether world column (x, z) is open land for sure (false when unknown). */
		boolean open(int x, int z);
	}

	/** Columns of other trunks, which the wood of a lower layer keeps away from (with the pattern of M2 trunks). */
	@FunctionalInterface
	public interface Trunks {
		/** Whether a trunk (of any layer) may stand at column (x, z). */
		boolean trunk(int x, int z);
	}

	public static final class Workspace {
		private static final byte WOOD = 1;
		private static final byte LEAF = 2;
		private static final int TRUNK = 0;
		private static final int LIMB = 1;
		private static final int BRANCH = 2;
		private static final int TWIG = 3;
		private static final int STUB = 4;
		private static final int GRID_SIDE = 2 * REACH / GRID + 3;

		// Grid of the tree's box, valid where stamp == current.
		private final int[] stamp = new int[CELLS];
		private final byte[] cell = new byte[CELLS];
		private final int[] cellIndex = new int[CELLS];
		private final byte[] cellDist = new byte[CELLS];
		private int current;
		// Allowed columns: leaves (territory, disc) and wood (also away from other trunks for the lower layers).
		private final boolean[] leafColumn = new boolean[SIDE * SIDE];
		private final boolean[] woodColumn = new boolean[SIDE * SIDE];
		// Wood of the side branches: the wood columns one block inside the leaf columns (review of V9, round 2).
		private final boolean[] branchColumn = new boolean[SIDE * SIDE];
		// Lowest and highest block of the tree's layer in each column (split surfaces).
		private final int[] floorY = new int[SIDE * SIDE];
		private final int[] capY = new int[SIDE * SIDE];
		private final double[] upperGrid = new double[GRID_SIDE * GRID_SIDE];
		private final double[] lowerGrid = new double[GRID_SIDE * GRID_SIDE];
		private final float[] reach = new float[AZIMUTH_BINS];
		// Open land of an edge tree (columns, and the azimuth bins that reach it) and the nodes of its low branches.
		private final boolean[] openColumn = new boolean[SIDE * SIDE];
		private final boolean[] skirtBin = new boolean[AZIMUTH_BINS];
		private int skirtBins;
		private int skirtFirst;
		private int skirtEnd;
		// Skeleton nodes; a node stands for the segment from its parent to it.
		private int nodes;
		private float[] nx = new float[256];
		private float[] ny = new float[256];
		private float[] nz = new float[256];
		private int[] nParent = new int[256];
		private byte[] nKind = new byte[256];
		private float[] nRadius = new float[256];
		private float[] nLeafH = new float[256];
		private float[] nLeafV = new float[256];
		private int[] nEnd = new int[256];
		private boolean[] nCut = new boolean[256];
		// The nodes of the last curve.
		private final int[] curveNodes = new int[CURVE_MAX];
		private int curveCount;
		// Wood blocks.
		private int woods;
		private int[] wPos = new int[512];
		private byte[] wRadius = new byte[512];
		private byte[] wParent = new byte[512];
		private int[] wParentIndex = new int[512];
		private int[] wOwner = new int[512];
		private byte[] wBark = new byte[512];
		private int[] wChildren = new int[512];
		private boolean[] wGone = new boolean[512];
		// The side branch each node belongs to (its first node; -1 for the trunk, limbs and stubs) and its leaves.
		private int[] root = new int[256];
		private int[] branchLeaves = new int[256];
		// The blocks of the outer half of each side branch and the covered ones; the depth of each wood block in its side
		// branch and the deepest; the wood sorted by root and the last root that counted each leaf.
		private int[] tipBlocks = new int[256];
		private int[] tipCovered = new int[256];
		private int[] depthMax = new int[256];
		private int[] wDepth = new int[512];
		private int[] order = new int[512];
		private int[] leafSeen = new int[2048];
		private int[] rootStart = new int[257];
		// Leaves.
		private int leaves;
		private int[] lPos = new int[2048];
		private int[] queue = new int[4096];
		// The request.
		private @Nullable Noise noise;
		private @Nullable Noise upperNoise;
		private @Nullable Noise lowerNoise;
		private @Nullable Noise shortNoise;
		private @Nullable Noise lowerShortNoise;
		private long noiseSeed;
		private long rng;
		private long cellSeed;
		private TreeRequest request;
		private SpeciesShape shape;
		private Size size;
		private double top;
		private double crownLength;
		private double leafH;
		private double leafV;
		private SpeciesShape.Profile profile;
		private boolean edge;
		private boolean oldPine;
		/** Height from which the spruce's leader is a spire without branches (infinite for other trees). */
		private double spireFrom;
		private int gridX0;
		private int gridZ0;

		private Workspace() {
		}

		// ------------------------------------------------------------------ request and allometry

		private void begin(TreeRequest r) {
			request = r;
			shape = SpeciesShape.of(r.species());
			if (noise == null || noiseSeed != r.worldSeed()) {
				Noise world = new Noise(r.worldSeed());
				noise = world.derive(SALT);
				upperNoise = world.derive(UPPER_SALT);
				lowerNoise = world.derive(LOWER_SALT);
				shortNoise = world.derive(SHORT_SALT);
				lowerShortNoise = world.derive(LOWER_SHORT_SALT);
				noiseSeed = r.worldSeed();
			}
			long salt = ((long) r.species().ordinal() << 8) | ((long) r.layer().ordinal() << 4) | r.age().ordinal();
			long h = noise.hash(r.x(), r.z(), salt);
			rng = h;
			cellSeed = Noise.mix(h ^ 0x5DEECE66DL);
			splitGrid(r);
			AgeClass age = r.age();
			double jh = range(0.9, 1.1);
			double jd = range(0.85, 1.15);
			double jr = range(0.85, 1.15);
			double jc = range(0.92, 1.08);
			double height = shape.height().get(age) * jh;
			double dbh = shape.dbh().get(age) * jd;
			double crownR = shape.crownRadius().get(age) * jr;
			double ratio = Math.min(0.85, shape.crownRatio().get(age) * jc);
			profile = shape.profile();
			oldPine = shape.archetype() == SpeciesShape.Archetype.PINE && age == AgeClass.OLD_GROWTH
					&& r.layer() == CrownLayer.CANOPY;
			if (oldPine) {
				// Old pines: a short, broad umbrella of few long limbs with flat tufts.
				ratio = range(0.22, 0.27);
				crownR *= 1.3;
				profile = new SpeciesShape.Profile(0.75F, 0.7F, 0.6F, 3.0F);
			}
			if (r.openGrown() && r.layer() == CrownLayer.CANOPY) {
				ratio = Math.min(0.8, ratio + OPEN_RATIO);
				crownR *= OPEN_RADIUS;
			}
			double hs = Math.clamp(0.55 + 0.15 * crownR, 0.7, 1.2);
			leafH = shape.clusterH() * hs;
			leafV = shape.clusterV() * Math.clamp(0.6 + 0.12 * crownR, 0.75, 1.15);
			if (shape.archetype() == SpeciesShape.Archetype.BROAD) {
				// Rounded clusters, not flat puffs.
				leafV = Math.max(leafV, 0.8 * leafH);
			}
			double crownBase;
			int cap = capAt(0, 0);
			switch (r.layer()) {
				case CANOPY -> crownBase = height * (1 - ratio);
				case SUBCANOPY -> {
					// Below the split surface by up to one and a half blocks (V9; V6: two and a half), so the subcanopy has no flat top.
					double drop = range(0, 1.5);
					height = Math.max(floorAt(0, 0) + 2.5, Math.min(height * SUBCANOPY_HEIGHT, cap + 1 - 0.5 * leafV - drop));
					dbh *= SUBCANOPY_DBH;
					crownR *= SUBCANOPY_CROWN;
					// No common shelf at the lower split surface: each crown starts up to three blocks above it.
					crownBase = Math.max(height * (1 - Math.min(0.75, ratio * 1.2)), floorAt(0, 0) + range(0, 3));
				}
				default -> {
					// Young trees of every height up to the split surface; the smallest are left to the saplings (V7).
					double most = Math.min(REGENERATION_HEIGHT[age.ordinal()] * jh, cap + 1 - 0.2);
					height = Math.max(2, most * range(0.6, 1.0));
					dbh = Math.max(2, height * 0.8);
					crownR = Math.min(crownR * 0.35, 1.5) * range(0.75, 1.05);
					leafH = Math.min(leafH, Math.max(1.4, crownR + 0.6));
					leafV = Math.min(leafV, 0.45 * height + 0.4);
					crownBase = height * range(0.2, 0.4);
				}
			}
			height = Math.max(2, height);
			top = height - 1;
			crownBase = Math.min(crownBase, top - 1.5);
			crownBase = Math.max(crownBase, 0.5);
			crownLength = top - crownBase;
			size = new Size(height, crownBase, crownR, Math.max(1, dbh * PX_PER_CM));
		}

		/** The corner values of the split surfaces around the tree. */
		private void splitGrid(TreeRequest r) {
			gridX0 = Math.floorDiv(r.x() - REACH, GRID);
			gridZ0 = Math.floorDiv(r.z() - REACH, GRID);
			for (int i = 0; i < GRID_SIDE; i++) {
				for (int j = 0; j < GRID_SIDE; j++) {
					upperGrid[i * GRID_SIDE + j] = corner(upperNoise, gridX0 + i, gridZ0 + j);
					lowerGrid[i * GRID_SIDE + j] = corner(lowerNoise, gridX0 + i, gridZ0 + j);
				}
			}
		}

		/** The split surfaces at a column relative to the trunk: {upper, lower}, as {@link #split}. */
		private int[] splitsAt(int dx, int dz, int[] out) {
			int x = request.x() + dx;
			int z = request.z() + dz;
			int gx = Math.floorDiv(x, GRID);
			int gz = Math.floorDiv(z, GRID);
			double fx = (x - gx * GRID) / (double) GRID;
			double fz = (z - gz * GRID) / (double) GRID;
			int i = (gx - gridX0) * GRID_SIDE + gz - gridZ0;
			double w1 = bilinear(upperGrid[i], upperGrid[i + GRID_SIDE], upperGrid[i + 1], upperGrid[i + GRID_SIDE + 1], fx, fz);
			double w2 = bilinear(lowerGrid[i], lowerGrid[i + GRID_SIDE], lowerGrid[i + 1], lowerGrid[i + GRID_SIDE + 1], fx, fz);
			AgeClass age = request.ageAt(x, z);
			int lower = lowerSplit(age, w2 + lowerShortWave(lowerShortNoise, x, z));
			int shift = request.groundShift(x, z);
			out[1] = lower + shift;
			out[0] = upperSplit(age, w1 + shortWave(shortNoise, x, z), lower) + shift;
			return out;
		}

		private final int[] splitTmp = new int[2];

		/** Lowest block of the tree's layer at the column (dx, dz). */
		private int floorAt(int dx, int dz) {
			return switch (request.layer()) {
				case CANOPY -> request.openGrown() ? 0 : splitsAt(dx, dz, splitTmp)[0];
				case SUBCANOPY -> splitsAt(dx, dz, splitTmp)[1];
				case REGENERATION -> 0;
			};
		}

		/** Highest block of the tree's layer at the column (dx, dz). */
		private int capAt(int dx, int dz) {
			return switch (request.layer()) {
				case CANOPY -> MAX_HEIGHT - 1;
				case SUBCANOPY -> splitsAt(dx, dz, splitTmp)[0] - 1;
				case REGENERATION -> splitsAt(dx, dz, splitTmp)[1] - 1;
			};
		}

		private double next() {
			rng += 0x9E3779B97F4A7C15L;
			long z = rng;
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			z ^= z >>> 31;
			return (z >>> 11) * 0x1.0p-53;
		}

		private double range(double a, double b) {
			return a + (b - a) * next();
		}

		/** A hash of a cell of this tree in [0, 1). */
		private double cellUnit(int x, int y, int z, long salt) {
			long h = Noise.mix(cellSeed ^ (TreeVoxels.pack(x, y, z) * 0x9E3779B97F4A7C15L) ^ salt);
			return (h >>> 11) * 0x1.0p-53;
		}

		// ------------------------------------------------------------------ plan

		TreeVoxels plan(TreeRequest r) {
			begin(r);
			if (++current == 0) {
				Arrays.fill(stamp, 0);
				current = 1;
			}
			nodes = 0;
			woods = 0;
			leaves = 0;
			columns(r);
			skeleton();
			pipe();
			voxelize();
			bark();
			foliage();
			prune();
			dropBareBranches();
			cover();
			stemSleeve();
			sprouts();
			trimBareTips();
			distances();
			return result();
		}

		/** Allowed columns of leaves and wood, the split surfaces and the reach of the territory by azimuth. */
		private void columns(TreeRequest r) {
			CrownTerritory t = r.territory();
			edge = t != null && t.edge();
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dx = -REACH; dx <= REACH; dx++) {
					boolean in = dx * dx + dz * dz <= REACH * REACH && (t == null || t.contains(r.x() + dx, r.z() + dz));
					int c = col(dx, dz);
					leafColumn[c] = in;
					woodColumn[c] = in;
					if (in || dx == 0 && dz == 0) {
						floorY[c] = floorAt(dx, dz);
						capY[c] = capAt(dx, dz);
					}
				}
			}
			Trunks trunks = r.layer() == CrownLayer.CANOPY ? null : r.trunks();
			if (trunks != null) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					for (int dx = -REACH; dx <= REACH; dx++) {
						if ((dx != 0 || dz != 0) && trunks.trunk(r.x() + dx, r.z() + dz)) {
							for (int ez = Math.max(-REACH, dz - 1); ez <= Math.min(REACH, dz + 1); ez++) {
								for (int ex = Math.max(-REACH, dx - 1); ex <= Math.min(REACH, dx + 1); ex++) {
									if (ex != 0 || ez != 0) {
										woodColumn[col(ex, ez)] = false;
									}
								}
							}
						}
					}
				}
			}
			woodColumn[col(0, 0)] = true;
			leafColumn[col(0, 0)] = true;
			// The branches keep one block inside the leaf columns (review of V9, round 2): a branch along the edge of the
			// territory had no column for leaves on its outer side and stood bare there (seen from above at 30 m between
			// crowns that do not meet). The trunk and the limbs keep the whole territory (the height of the forking crowns),
			// and so do the columns next to the trunk.
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dx = -REACH; dx <= REACH; dx++) {
					int c = col(dx, dz);
					branchColumn[c] = woodColumn[c] && (Math.max(Math.abs(dx), Math.abs(dz)) <= 1
							|| leaf(dx + 1, dz) && leaf(dx - 1, dz) && leaf(dx, dz + 1) && leaf(dx, dz - 1));
				}
			}
			openLand(r);
			for (int b = 0; b < AZIMUTH_BINS; b++) {
				double a = 2 * Math.PI * b / AZIMUTH_BINS;
				double c = StrictMath.cos(a);
				double s = StrictMath.sin(a);
				float d = 0;
				for (double step = 0.5; step <= REACH; step += 0.5) {
					if (!leaf(Math.round(step * c), Math.round(step * s))) {
						break;
					}
					d = (float) step;
				}
				reach[b] = d;
			}
		}

		/**
		 * The open land of a canopy tree at a forest edge (review of V9, round 1): its columns of open land take the lower
		 * split surface as their floor (no tree of a lower layer grows there), and the azimuth bins with at least two of
		 * them get low branches ({@link #skirt}).
		 */
		private void openLand(TreeRequest r) {
			skirtBins = 0;
			skirtFirst = 0;
			skirtEnd = 0;
			TreeShapePlan.Open open = r.open();
			if (open == null || !edge || r.layer() != CrownLayer.CANOPY || r.openGrown()) {
				return;
			}
			Arrays.fill(openColumn, false);
			int[] count = new int[AZIMUTH_BINS];
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dx = -REACH; dx <= REACH; dx++) {
					int c = col(dx, dz);
					if (!leafColumn[c] || dx * dx + dz * dz < 4 || !open.open(r.x() + dx, r.z() + dz)) {
						continue;
					}
					openColumn[c] = true;
					floorY[c] = Math.min(floorY[c], Math.max(1, splitsAt(dx, dz, splitTmp)[1]));
					double turns = StrictMath.atan2(dz, dx) / (2 * Math.PI);
					count[(int) Math.floorMod(Math.round((turns - Math.floor(turns)) * AZIMUTH_BINS), AZIMUTH_BINS)]++;
				}
			}
			for (int b = 0; b < AZIMUTH_BINS; b++) {
				skirtBin[b] = count[b] >= 2;
				skirtBins += skirtBin[b] ? 1 : 0;
			}
		}

		private static int col(long dx, long dz) {
			return (int) ((dx + REACH) * SIDE + dz + REACH);
		}

		private boolean leaf(long dx, long dz) {
			return dx >= -REACH && dx <= REACH && dz >= -REACH && dz <= REACH && leafColumn[col(dx, dz)];
		}

		private boolean branchAt(double x, double z) {
			long dx = Math.round(x);
			long dz = Math.round(z);
			return dx >= -REACH && dx <= REACH && dz >= -REACH && dz <= REACH && branchColumn[col(dx, dz)];
		}

		private boolean woodAt(double x, double z) {
			long dx = Math.round(x);
			long dz = Math.round(z);
			return dx >= -REACH && dx <= REACH && dz >= -REACH && dz <= REACH && woodColumn[col(dx, dz)];
		}

		private float reachAt(double azimuth) {
			double turns = azimuth / (2 * Math.PI);
			int b = (int) Math.floorMod(Math.round((turns - Math.floor(turns)) * AZIMUTH_BINS), AZIMUTH_BINS);
			return reach[b];
		}

		/** Crown envelope radius at height y above the base, towards the azimuth (with the lean of an edge tree). */
		private double envelope(double y, double azimuth) {
			double t = (y - size.crownBase()) / Math.max(0.5, crownLength);
			double e = size.crownRadius() * profile.at(t);
			if (edge) {
				double free = reachAt(azimuth) / Math.max(1, size.crownRadius());
				e *= 1 + 0.3 * Math.clamp((free - 1.2) / 0.8, 0, 1);
			}
			return e;
		}

		// ------------------------------------------------------------------ skeleton

		private int node(int parent, double x, double y, double z, int kind) {
			if (nodes == nx.length) {
				int n = nodes * 2;
				nx = Arrays.copyOf(nx, n);
				ny = Arrays.copyOf(ny, n);
				nz = Arrays.copyOf(nz, n);
				nParent = Arrays.copyOf(nParent, n);
				nKind = Arrays.copyOf(nKind, n);
				nRadius = Arrays.copyOf(nRadius, n);
				nLeafH = Arrays.copyOf(nLeafH, n);
				nLeafV = Arrays.copyOf(nLeafV, n);
				nEnd = Arrays.copyOf(nEnd, n);
				nCut = Arrays.copyOf(nCut, n);
			}
			int i = nodes++;
			nx[i] = (float) x;
			ny[i] = (float) y;
			nz[i] = (float) z;
			nParent[i] = parent;
			nKind[i] = (byte) kind;
			nRadius[i] = 0;
			nLeafH[i] = 0;
			nLeafV[i] = 0;
			nEnd[i] = -1;
			nCut[i] = false;
			return i;
		}

		private void leafAt(int node, double h, double v) {
			nLeafH[node] = (float) h;
			nLeafV[node] = (float) v;
		}

		private void skeleton() {
			int trunkTop = trunk();
			switch (shape.archetype()) {
				case PINE, SPRUCE -> conifer();
				case SLENDER -> slender();
				case BROAD -> broad(trunkTop);
			}
			skirt();
			stubs();
		}

		/**
		 * Live branches below the crown base towards the open land of a forest edge (review of V9, round 1): from
		 * {@value #SKIRT_FROM} of the height up, each towards an azimuth bin that reaches open land, so the edge seen
		 * from a field is green down to the shrubs of the mantle instead of a colonnade of bare boles.
		 */
		private void skirt() {
			skirtFirst = nodes;
			skirtEnd = nodes;
			if (skirtBins == 0) {
				return;
			}
			boolean conifer = shape.archetype() == SpeciesShape.Archetype.PINE || shape.archetype() == SpeciesShape.Archetype.SPRUCE;
			double y = Math.max(2, SKIRT_FROM * size.height()) + range(0, 1);
			double end = size.crownBase() - 0.5;
			while (y < end) {
				int pick = (int) (next() * skirtBins);
				int bin = 0;
				for (int b = 0; b < AZIMUTH_BINS; b++) {
					if (skirtBin[b] && pick-- == 0) {
						bin = b;
						break;
					}
				}
				double azimuth = 2 * Math.PI * (bin + range(-0.4, 0.4)) / AZIMUTH_BINS;
				int at = trunkNode(Math.floor(y));
				if (conifer) {
					branch(at, azimuth, size.crownRadius() * range(0.7, 1.0), 0, shape.archetype() == SpeciesShape.Archetype.SPRUCE);
				} else {
					lateral(at, azimuth, 0, Double.NaN);
				}
				// At least one block up: the trunk node at the next height must be another one.
				y = Math.max(Math.floor(y) + 1, ny[at] + shape.spacing() * SKIRT_SPACING * range(0.8, 1.2));
			}
			skirtEnd = nodes;
		}

		/**
		 * The trunk: nodes one block apart from the base to the top (or to the fork of a forking tree), with a lean and a
		 * slight wobble; straight when the lean would leave the allowed columns.
		 *
		 * @return the last trunk node
		 */
		private int trunk() {
			double phi = range(0, 2 * Math.PI);
			double lean = shape.lean() * range(0.3, 1.0);
			double wobble = 0.12 * next();
			if (shape.archetype() == SpeciesShape.Archetype.PINE || shape.archetype() == SpeciesShape.Archetype.SPRUCE) {
				// A conifer's leader stays in its column: a jog of the thin top reads as a step (review of V6).
				lean = Math.min(lean, 0.42);
				wobble = 0;
			}
			double phase = range(0, 2 * Math.PI);
			double end = forks() ? size.crownBase() + 0.15 * crownLength : top;
			int count = (int) Math.ceil(end);
			for (int attempt = 0; attempt < 2; attempt++) {
				boolean ok = true;
				for (int k = 0; k <= count && ok; k++) {
					double y = Math.min(k, end);
					ok = woodAt(trunkX(y, phi, lean, wobble, phase), trunkZ(y, phi, lean, wobble, phase));
				}
				if (ok) {
					break;
				}
				lean = 0;
				wobble = 0;
			}
			int parent = -1;
			for (int k = 0; k <= count; k++) {
				double y = Math.min(k, end);
				parent = node(parent, trunkX(y, phi, lean, wobble, phase), y, trunkZ(y, phi, lean, wobble, phase), TRUNK);
				if (y >= end) {
					break;
				}
			}
			spireFrom = shape.archetype() == SpeciesShape.Archetype.SPRUCE && top > 8 ? top - 2.2 : Double.POSITIVE_INFINITY;
			if (!forks() && spireFrom == Double.POSITIVE_INFINITY) {
				// (The spruce's spire gets its needles in foliage(): a cross around each of its blocks.)
				leafAt(parent, leafH * 0.85, leafV * 1.2);
			}
			return parent;
		}

		private double trunkX(double y, double phi, double lean, double wobble, double phase) {
			double u = y / Math.max(1, top);
			return lean * u * Math.sqrt(u) * StrictMath.cos(phi) - wobble * StrictMath.sin(y * 0.45 + phase) * StrictMath.sin(phi);
		}

		private double trunkZ(double y, double phi, double lean, double wobble, double phase) {
			double u = y / Math.max(1, top);
			return lean * u * Math.sqrt(u) * StrictMath.sin(phi) + wobble * StrictMath.sin(y * 0.45 + phase) * StrictMath.cos(phi);
		}

		/** Whether the trunk forks into limbs (the oak from the mature stage). */
		private boolean forks() {
			return shape.limbs() > 0 && request.age() != AgeClass.POLE_STAGE && request.layer() == CrownLayer.CANOPY;
		}

		/** The trunk node at the height y (the trunk nodes are the first nodes, one per block). */
		private int trunkNode(double y) {
			int k = (int) Math.floor(y);
			int i = Math.clamp(k, 0, nodes - 1);
			while (i > 0 && (nKind[i] != TRUNK || ny[i] > y)) {
				i--;
			}
			return i;
		}

		/**
		 * A curved branch from node {@code at}: nodes about one block of arc apart along the azimuth until the horizontal
		 * reach, the angle from the vertical turning linearly with the horizontal progress from {@code a0} to {@code a1}
		 * (degrees; above 90 the branch goes down). The horizontal direction is fixed, so it never reverses. The nodes are
		 * left in {@link #curveNodes}.
		 *
		 * @return the last node, or {@code at} when the reach is too short
		 */
		private int curve(int at, double azimuth, double reachH, double a0, double a1, int kind) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double x = nx[at];
			double y = ny[at];
			double z = nz[at];
			double h = 0;
			int parent = at;
			curveCount = 0;
			boolean steep = Math.max(a0, a1) < 45;
			while (reachH - h > 0.3 && curveCount < CURVE_MAX) {
				double th = StrictMath.toRadians(voxelAngle(a0 + (a1 - a0) * (h / reachH), steep));
				double sin = Math.max(0.26, StrictMath.sin(th));
				double cos = StrictMath.cos(th);
				double step = Math.min(1.0, (reachH - h) / sin);
				h += step * sin;
				x += step * sin * c;
				z += step * sin * s;
				y += step * cos;
				if (y > top + 0.5) {
					break;
				}
				parent = node(parent, x, Math.max(0.5, y), z, kind);
				curveNodes[curveCount++] = parent;
			}
			return parent;
		}

		/**
		 * The angle from the vertical (degrees) of a thin branch step, moved to one that voxelizes cleanly (risk R4): a run
		 * of single steps up and out (about 45°, a slanted line from a distance) or long runs out with single steps up or
		 * down (70–110°), not runs of two to three each way, which read as right-angled elbows. A branch steeper than 45°
		 * all along ({@code steep}) keeps its angle; any other one never gets steeper than 45° or droops more than 135°,
		 * so no branch has both a vertical run of two and a horizontal run of two.
		 */
		private static double voxelAngle(double degrees, boolean steep) {
			if (steep) {
				return degrees;
			}
			if (degrees < 52) {
				return Math.max(45, degrees);
			}
			if (degrees < 70) {
				return degrees < 61 ? 47 : 72;
			}
			if (degrees <= 110) {
				return degrees;
			}
			if (degrees < 128) {
				return degrees < 119 ? 108 : 133;
			}
			return Math.min(135, degrees);
		}

		/** Pine and spruce: whorls of branches from the crown base to the top, rotated by the golden angle. */
		private void conifer() {
			boolean spruce = shape.archetype() == SpeciesShape.Archetype.SPRUCE;
			double phase = range(0, 2 * Math.PI);
			double y = size.crownBase();
			// The spruce's spire: its top two blocks without branches (a tree taller than 8 blocks).
			double end = Math.min(spireFrom, top - 0.6);
			while (y < end) {
				int at = trunkNode(Math.floor(y));
				double ay = ny[at];
				double t = (ay - size.crownBase()) / Math.max(0.5, crownLength);
				int k = oldPine ? 2 + (next() < 0.45 ? 1 : 0)
						: shape.perWhorl() + (next() < 0.35 ? 1 : 0) - (next() < 0.3 ? 1 : 0);
				phase += GOLDEN + range(-0.3, 0.3);
				if (!spruce && t >= 0.4 && nLeafH[at] <= 0) {
					// Needles around the pine's leader in the upper crown: no bare candelabra of leader and branch bases.
					leafAt(at, leafH * 0.6, leafV * 0.7);
				}
				for (int j = 0; j < k; j++) {
					double azimuth = phase + j * 2 * Math.PI / k + range(-0.4, 0.4);
					int from = at;
					if (next() < 0.35 && ay + 1 < end) {
						// Staggered whorls: some branches a block higher.
						from = trunkNode(ay + 1);
					}
					double length = envelope(ny[from], azimuth) * range(0.72, 1.1);
					branch(from, azimuth, length, t, spruce);
				}
				y = ay + shape.spacing() * (oldPine ? 1.4 : 1) * range(0.85, 1.15);
				if (Math.floor(y) <= ay) {
					y = ay + 1;
				}
			}
		}

		/**
		 * A conifer branch from trunk node {@code at}: a curve of horizontal length {@code length}; the pine's tip turns
		 * up and carries tufts along its outer half, the spruce's droops (the lowest whorls most) with needles along all
		 * of it. A short one leaves only a cluster at the trunk.
		 */
		private void branch(int at, double azimuth, double length, double t, boolean spruce) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			length = fit(nx[at], nz[at], c, s, length, 0.6 * leafH);
			if (length < 0.9) {
				// Too short for wood: needles right at the trunk (the young top of the crown).
				int n = node(at, nx[at] + 0.6 * c, ny[at], nz[at] + 0.6 * s, STUB);
				leafAt(n, leafH * 0.75, leafV * 0.85);
				return;
			}
			double a0 = lerp(t, shape.angleLow(), shape.angleHigh()) + range(-5, 5);
			double a1;
			if (spruce) {
				// Droop: up to about 55 degrees at the crown base (one to two blocks of sag spread along the branch), little
				// in the middle, the top branches slightly ascending (bend is negative).
				a1 = a0 + -shape.bend() * 220 * StrictMath.pow(1 - t, 1.5) * range(0.6, 1.3) - 8 * t;
			} else if (oldPine) {
				a0 = lerp(t, 74, 62) + range(-5, 5);
				a1 = a0 - range(28, 40);
			} else {
				a1 = a0 - shape.bend() * 110 * range(0.7, 1.3);
			}
			int tip = curve(at, azimuth, length, a0, a1, BRANCH);
			if (tip == at) {
				return;
			}
			double scale = Math.clamp(0.75 + 0.15 * length, 0.75, 1.15);
			if (oldPine) {
				// Separate flat tufts at the ends of the limbs.
				leafAt(tip, leafH * scale * 1.15, leafV * 0.7);
			} else {
				leafAt(tip, leafH * scale, leafV);
			}
			int n = curveCount;
			for (int i = 0; i < n - 1; i++) {
				int node = curveNodes[i];
				if (spruce) {
					// Needles along the spruce branch.
					if (i >= 1) {
						leafAt(node, leafH * 0.7, leafV * 0.8);
					}
				} else if (i >= (n <= 3 ? 0 : n * 0.45)) {
					// The pine's tufts cover the outer half of the shoot (a short one all along), so no bare elbow shows.
					leafAt(node, leafH * (oldPine ? 0.8 : 0.65), leafV * (oldPine ? 0.6 : 0.75));
				}
			}
		}

		/** Shortens a horizontal reach from (x0, z0) along (c, s) until its end lies in an allowed wood column. */
		private double fit(double x0, double z0, double c, double s, double length, double margin) {
			double reachLimit = REACH - 0.5 - margin;
			length = Math.min(length, reachLimit);
			while (length > 0.6 && !branchAt(x0 + length * c, z0 + length * s)) {
				length -= 0.5;
			}
			return length;
		}

		/** Birch: a leader with single branches that rise and then hang. */
		private void slender() {
			double azimuth = range(0, 2 * Math.PI);
			double y = size.crownBase();
			while (y < top - 0.8) {
				int at = trunkNode(Math.floor(y));
				double ay = ny[at];
				double t = (ay - size.crownBase()) / Math.max(0.5, crownLength);
				azimuth += GOLDEN + range(-0.3, 0.3);
				double c = StrictMath.cos(azimuth);
				double s = StrictMath.sin(azimuth);
				double length = fit(nx[at], nz[at], c, s, envelope(ay, azimuth) * range(0.8, 1.1), 0.5 * leafH);
				if (length >= 0.9) {
					double a0 = lerp(t, shape.angleLow(), shape.angleHigh()) + range(-5, 5);
					double a1 = a0 - shape.bend() * 160 * range(0.8, 1.2);
					int tip = curve(at, azimuth, length, a0, a1, BRANCH);
					if (tip != at) {
						int n = curveCount;
						for (int i = n / 2; i < n - 1; i++) {
							leafAt(curveNodes[i], leafH * 0.75, leafV * 0.9);
						}
						leafAt(tip, leafH, leafV * 1.1);
						if (length >= 2) {
							// The hanging end of the shoot.
							int hang = node(tip, nx[tip], ny[tip] - 1.2, nz[tip], TWIG);
							leafAt(hang, leafH * 0.8, leafV * 1.2);
						}
					}
				}
				y = ay + shape.spacing() * range(0.8, 1.2);
				if (Math.floor(y) <= ay) {
					y = ay + 1;
				}
			}
		}

		/** Broad-leaved trees: lateral branches from the leader, or from the trunk and the limbs of a fork. */
		private void broad(int trunkTop) {
			double azimuth = range(0, 2 * Math.PI);
			// Laterals from the trunk (or the leader) above the crown base.
			azimuth = laterals(0, trunkTop, azimuth, Double.NaN);
			if (!forks()) {
				return;
			}
			int limbs = shape.limbs() - (request.age() == AgeClass.MATURE && next() < 0.5 ? 1 : 0);
			double phase = range(0, 2 * Math.PI);
			for (int j = 0; j < limbs; j++) {
				double a = phase + j * 2 * Math.PI / limbs + range(-0.4, 0.4);
				int first = nodes;
				int last = limb(trunkTop, a, range(0.45, 0.6));
				if (last == trunkTop) {
					continue;
				}
				azimuth = laterals(first, last, azimuth, a);
				// The limb forks again into two crooked branches that spread on to the crown's edge.
				int forks = 0;
				for (int k = 0; k < 2; k++) {
					double b = keepSigns(a + (k == 0 ? 1 : -1) * range(0.3, 0.75), a);
					int start = nodes;
					int end = limb(last, b, range(0.85, 1.0));
					if (end != last) {
						forks++;
						leafAt(end, leafH, leafV * 1.1);
						azimuth = laterals(start, end, azimuth, b);
					}
				}
				if (forks == 0) {
					leafAt(last, leafH, leafV * 1.1);
				}
			}
		}

		/**
		 * A thick, crooked limb of a forking tree (the oak): vertical runs of one to three blocks with a one-block jog
		 * sideways after each, which climbs at 30–48° from the vertical until it reaches {@code share} of the crown
		 * envelope horizontally (or the top of the crown, or a column it may not use). Every node is a whole block off its
		 * parent along one axis, so the limb keeps its radius (no diagonal segment); the jogs keep the signs of the
		 * azimuth, so the limb never turns back.
		 *
		 * @return the last node of the limb, or {@code from} when there is no room
		 */
		private int limb(int from, double azimuth, double share) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double slope = StrictMath.tan(StrictMath.toRadians(range(30, 48)));
			double x = nx[from];
			double y = ny[from];
			double z = nz[from];
			double yEnd = top - range(0.5, 2);
			double hReach = Math.max(1.5, envelope(y + 0.5 * (yEnd - y), azimuth) * share);
			// The horizontal distance from the trunk axis grows along the limb's line from where it starts.
			double h = StrictMath.hypot(x, z);
			double lineX = x;
			double lineZ = z;
			double along = 0;
			int parent = from;
			for (int guard = 0; guard < 64 && y < yEnd - 0.5; guard++) {
				int run = (int) Math.clamp(Math.round(range(0.7, 1.3) / slope), 1, 3);
				y += Math.min(run, Math.max(1, Math.round(yEnd - y)));
				parent = node(parent, x, y, z, LIMB);
				if (y >= yEnd) {
					break;
				}
				// One jog sideways: the axis that lags most behind the limb's line.
				along += 1;
				double tx = lineX + along * c;
				double tz = lineZ + along * s;
				double ex = Math.abs(tx - x);
				double ez = Math.abs(tz - z);
				boolean alongX = s == 0 || c != 0 && ex >= ez;
				double gx = x + (alongX ? Math.signum(c) : 0);
				double gz = z + (alongX ? 0 : Math.signum(s));
				h = StrictMath.hypot(gx, gz);
				if (h > hReach || !woodAt(gx, gz)) {
					break;
				}
				x = gx;
				z = gz;
				parent = node(parent, x, y, z, LIMB);
			}
			return parent;
		}

		/**
		 * Lateral branches with twigs from the chain of nodes {@code from}…{@code to} (the trunk when {@code from} is 0,
		 * otherwise a limb, whose laterals point away from the axis around {@code outward}) above the crown base.
		 *
		 * @return the azimuth of the last branch
		 */
		private double laterals(int from, int to, double azimuth, double outward) {
			boolean limb = from > 0;
			double next = size.crownBase();
			for (int i = from; i <= to; i++) {
				if (ny[i] < next || ny[i] > top - 0.7) {
					continue;
				}
				double ay = ny[i];
				double t = (ay - size.crownBase()) / Math.max(0.5, crownLength);
				if (limb) {
					azimuth = keepSigns(outward + range(-1.2, 1.2), outward);
				} else {
					azimuth += GOLDEN + range(-0.35, 0.35);
				}
				lateral(i, azimuth, t, limb ? outward : Double.NaN);
				next = ay + shape.spacing() * range(0.75, 1.25);
			}
			return azimuth;
		}

		/**
		 * An azimuth whose horizontal components do not point against those of {@code ref} (a component that would is
		 * dropped), so a side branch never turns back towards the trunk along an axis its parent went out along.
		 */
		private static double keepSigns(double azimuth, double ref) {
			if (Double.isNaN(ref)) {
				return azimuth;
			}
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double rc = StrictMath.cos(ref);
			double rs = StrictMath.sin(ref);
			if (c * rc < 0) {
				c = 0;
			}
			if (s * rs < 0) {
				s = 0;
			}
			return c == 0 && s == 0 ? ref : StrictMath.atan2(s, c);
		}

		/** One curved lateral branch of a broad-leaved tree from node {@code at}, with twigs. */
		private void lateral(int at, double azimuth, double t, double limbAzimuth) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double x0 = nx[at];
			double z0 = nz[at];
			double ay = ny[at];
			// Horizontal length so that the tip reaches the envelope: |S + L u| = E.
			double e = envelope(ay, azimuth) * range(0.85, 1.05);
			double su = x0 * c + z0 * s;
			double disc = su * su - (x0 * x0 + z0 * z0) + e * e;
			double length = disc > 0 ? -su + Math.sqrt(disc) : 0;
			length = fit(x0, z0, c, s, length, 0.5 * leafH);
			if (length < 1.0) {
				return;
			}
			double a0 = lerp(t, shape.angleLow(), shape.angleHigh()) + range(-6, 6);
			double a1 = a0 - shape.bend() * 150 * range(0.6, 1.4);
			int tip = curve(at, azimuth, length, a0, a1, BRANCH);
			if (tip == at) {
				return;
			}
			leafAt(tip, leafH, leafV);
			int n = curveCount;
			int a = curveNodes[Math.max(0, (int) (0.35 * n))];
			int b = curveNodes[Math.max(0, Math.min(n - 2, (int) (0.7 * n)))];
			if (length >= 2.5 && n >= 3) {
				twig(a, side(azimuth, limbAzimuth, range(0.5, 0.9)), length * range(0.3, 0.45), t);
				twig(b, side(azimuth, limbAzimuth, range(0.45, 0.8)), length * range(0.25, 0.4), t);
			} else if (b != tip) {
				leafAt(b, leafH * 0.8, leafV * 0.9);
			}
		}

		/** A twig azimuth off the branch by {@code turn} radians to a random side, keeping the signs of both parents. */
		private double side(double azimuth, double limbAzimuth, double turn) {
			double a = keepSigns(azimuth + (next() < 0.5 ? turn : -turn), azimuth);
			return keepSigns(a, limbAzimuth);
		}

		private void twig(int at, double azimuth, double length, double t) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double x0 = nx[at];
			double z0 = nz[at];
			double e = envelope(ny[at], azimuth) * 1.05;
			length = Math.max(1.2, length);
			// The tip stays inside the envelope.
			while (length > 1.0 && StrictMath.hypot(x0 + length * c, z0 + length * s) > e) {
				length -= 0.4;
			}
			length = fit(x0, z0, c, s, length, 0.4 * leafH);
			if (length < 1.0) {
				leafAt(at, leafH * 0.8, leafV * 0.85);
				return;
			}
			double a0 = range(58, 75);
			int tip = curve(at, azimuth, length, a0, a0 - shape.bend() * 150, TWIG);
			if (tip == at) {
				leafAt(at, leafH * 0.8, leafV * 0.85);
				return;
			}
			leafAt(tip, leafH * 0.85, leafV * 0.9);
		}

		/**
		 * Dry stubs of self-pruned branches on the bole below the crown base (one block, axis-aligned). Only on the
		 * conifers and the birch (review of V9, round 2): on the gray bole of a broad-leaved tree a one-block stub of 1 px
		 * read as the arm of a lamp post at eye height. The random draws stay the same for every species, so the rest of
		 * the tree does not change.
		 */
		private void stubs() {
			if (request.layer() == CrownLayer.REGENERATION || shape.stubs() <= 0) {
				return;
			}
			boolean broad = shape.archetype() == SpeciesShape.Archetype.BROAD;
			double y = Math.max(2, 0.25 * size.height());
			double end = size.crownBase() - 0.8;
			while (y < end) {
				if (next() < shape.stubs()) {
					int at = trunkNode(Math.floor(y));
					int dir = (int) (next() * 4);
					double dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
					double dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
					if (!broad && woodAt(nx[at] + dx, nz[at] + dz)) {
						node(at, nx[at] + dx, ny[at], nz[at] + dz, STUB);
					}
				}
				y += range(1.2, 2.2);
			}
		}

		private static double lerp(double t, double a, double b) {
			t = Math.clamp(t, 0, 1);
			return a + (b - a) * t;
		}

		// ------------------------------------------------------------------ pipe model

		/**
		 * Segment radii (px): the pipe model from the tips, scaled to the crown-base share of the base radius, the bole
		 * below the crown base growing in cross-section towards the base. Stubs have 1 px.
		 */
		private void pipe() {
			double n = shape.pipe();
			float[] acc = nRadius;
			for (int i = 0; i < nodes; i++) {
				acc[i] = 0;
			}
			for (int i = nodes - 1; i >= 0; i--) {
				if (nKind[i] == STUB) {
					continue;
				}
				if (acc[i] == 0) {
					acc[i] = 1;
				}
				if (nParent[i] >= 0) {
					acc[nParent[i]] += acc[i];
				}
			}
			// acc holds r^n with tips of 1; the radius at the crown base sets the scale.
			int base = trunkNode(size.crownBase());
			double rBase = size.baseRadius();
			double rCrown = rBase * shape.crownBaseRadius();
			double scale = rCrown / StrictMath.pow(acc[base], 1 / n);
			double rCrownN = StrictMath.pow(rCrown, n);
			double rBaseN = StrictMath.pow(rBase, n);
			double hb = Math.max(1, ny[base]);
			for (int i = 0; i < nodes; i++) {
				double r;
				if (nKind[i] == STUB) {
					r = 1;
				} else if (nKind[i] == TRUNK && i <= base) {
					double u = Math.clamp((hb - ny[i]) / hb, 0, 1);
					r = StrictMath.pow(rCrownN + (rBaseN - rCrownN) * u, 1 / n);
				} else {
					r = Math.min(rCrown, scale * StrictMath.pow(acc[i], 1 / n));
				}
				nRadius[i] = (float) Math.max(1, r);
			}
		}

		/** Radius of a segment in blocks: thin trunk 1–7 px or a full log, at most 2 px for diagonal segments. */
		private int quantized(int i) {
			double r = nRadius[i];
			int q = r >= 7.5 ? TreeVoxels.FULL : (int) Math.clamp(Math.round(r), 1, 7);
			int p = nParent[i];
			if (p >= 0 && q > DIAGONAL_RADIUS) {
				double dx = nx[i] - nx[p];
				double dy = ny[i] - ny[p];
				double dz = nz[i] - nz[p];
				double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double axis = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
				if (len > 0 && axis / len < DIAGONAL) {
					q = DIAGONAL_RADIUS;
				}
			}
			return q;
		}

		// ------------------------------------------------------------------ voxelization

		private int cellOf(int x, int y, int z) {
			return (y * SIDE + x + REACH) * SIDE + z + REACH;
		}

		private boolean inBox(int x, int y, int z) {
			return x >= -REACH && x <= REACH && z >= -REACH && z <= REACH && y >= 0 && y < MAX_HEIGHT;
		}

		private int addWood(int x, int y, int z, int radius, int parentDir, int parentIndex, int owner) {
			if (woods == wPos.length) {
				int n = woods * 2;
				wPos = Arrays.copyOf(wPos, n);
				wRadius = Arrays.copyOf(wRadius, n);
				wParent = Arrays.copyOf(wParent, n);
				wParentIndex = Arrays.copyOf(wParentIndex, n);
				wOwner = Arrays.copyOf(wOwner, n);
				wBark = Arrays.copyOf(wBark, n);
				wChildren = Arrays.copyOf(wChildren, n);
				wGone = Arrays.copyOf(wGone, n);
			}
			int i = woods++;
			wPos[i] = TreeVoxels.pack(x, y, z);
			wRadius[i] = (byte) radius;
			wParent[i] = (byte) parentDir;
			wParentIndex[i] = parentIndex;
			wOwner[i] = owner;
			wBark[i] = TreeVoxels.LOWER;
			wChildren[i] = 0;
			wGone[i] = false;
			if (parentIndex >= 0) {
				wChildren[parentIndex]++;
			}
			int c = cellOf(x, y, z);
			stamp[c] = current;
			cell[c] = WOOD;
			cellIndex[c] = i;
			return i;
		}

		private void voxelize() {
			// The root: the block above the ground, its parent the ground below.
			nEnd[0] = addWood(0, 0, 0, quantized(0), TreeVoxels.DOWN, -1, 0);
			for (int i = 1; i < nodes; i++) {
				int p = nParent[i];
				if (nCut[p] || nEnd[p] < 0) {
					nCut[i] = true;
					continue;
				}
				segment(i, p);
			}
		}

		/**
		 * Whether wood of node i may stand at (x, y, z): an allowed column and, except for the trunk below its layer and
		 * the dry stubs (both next to the trunk column, where no wood of another layer stands), between the split
		 * surfaces of the layer.
		 */
		private boolean woodCell(int i, int x, int y, int z) {
			int kind = nKind[i];
			if (!inBox(x, y, z) || !(kind == BRANCH || kind == TWIG ? branchColumn : woodColumn)[col(x, z)]) {
				return false;
			}
			int c = col(x, z);
			if (y > capY[c]) {
				return false;
			}
			if (i >= skirtFirst && i < skirtEnd && Math.max(Math.abs(x), Math.abs(z)) <= 2) {
				// A low branch of an edge tree leaves the trunk below the layer's surface, like a dry stub.
				return true;
			}
			return nKind[i] == TRUNK || nKind[i] == STUB || y >= floorY[c];
		}

		/** The blocks of the segment from node p to node i, a 3D DDA with face-adjacent steps from p's end block. */
		private void segment(int i, int p) {
			int cursor = nEnd[p];
			int pos = wPos[cursor];
			int x = TreeVoxels.unpackX(pos);
			int y = TreeVoxels.unpackY(pos);
			int z = TreeVoxels.unpackZ(pos);
			// Voxel (x, y, z) covers [x - 0.5, x + 0.5); shift by 0.5 to a grid of unit cells.
			double ax = nx[p] + 0.5;
			double ay = ny[p] + 0.5;
			double az = nz[p] + 0.5;
			double dx = nx[i] - nx[p];
			double dy = ny[i] - ny[p];
			double dz = nz[i] - nz[p];
			int sx = dx > 0 ? 1 : -1;
			int sy = dy > 0 ? 1 : -1;
			int sz = dz > 0 ? 1 : -1;
			double tdx = dx != 0 ? 1 / Math.abs(dx) : Double.POSITIVE_INFINITY;
			double tdy = dy != 0 ? 1 / Math.abs(dy) : Double.POSITIVE_INFINITY;
			double tdz = dz != 0 ? 1 / Math.abs(dz) : Double.POSITIVE_INFINITY;
			double tx = dx != 0 ? (sx > 0 ? x + 1 - ax : ax - x) * tdx : Double.POSITIVE_INFINITY;
			double ty = dy != 0 ? (sy > 0 ? y + 1 - ay : ay - y) * tdy : Double.POSITIVE_INFINITY;
			double tz = dz != 0 ? (sz > 0 ? z + 1 - az : az - z) * tdz : Double.POSITIVE_INFINITY;
			int q = quantized(i);
			boolean fresh = true;
			for (int guard = 0; guard < 4 * MAX_HEIGHT; guard++) {
				int dir;
				if (tx <= ty && tx <= tz) {
					if (tx > 1) {
						break;
					}
					x += sx;
					tx += tdx;
					dir = sx > 0 ? TreeVoxels.WEST : TreeVoxels.EAST;
				} else if (ty <= tz) {
					if (ty > 1) {
						break;
					}
					y += sy;
					ty += tdy;
					dir = sy > 0 ? TreeVoxels.DOWN : TreeVoxels.UP;
				} else {
					if (tz > 1) {
						break;
					}
					z += sz;
					tz += tdz;
					dir = sz > 0 ? TreeVoxels.NORTH : TreeVoxels.SOUTH;
				}
				if (!woodCell(i, x, y, z)) {
					nCut[i] = true;
					break;
				}
				int c = cellOf(x, y, z);
				if (stamp[c] == current && cell[c] == WOOD) {
					// Through wood that already exists: an ancestor, or (before the segment has a block of its own) a
					// sibling that left the same node. The segment goes on from that block, whose chain leads to the root;
					// the radius rule below keeps it no thicker. Wood of another branch stops it, so two branches never
					// merge into a loop or a rod (review of V6).
					int owner = wOwner[cellIndex[c]];
					if (ancestor(owner, i) || fresh && ancestor(p, owner)) {
						cursor = cellIndex[c];
						continue;
					}
					nCut[i] = true;
					break;
				}
				int r = Math.min(q, wRadius[cursor]);
				cursor = addWood(x, y, z, r, dir, cursor, i);
				fresh = false;
			}
			nEnd[i] = cursor;
		}

		/** Whether node a is node n or one of its ancestors. */
		private boolean ancestor(int a, int n) {
			for (int j = n; j >= 0; j = nParent[j]) {
				if (j == a) {
					return true;
				}
			}
			return false;
		}

		/** Barks: the pine's lower bark, transition zone and "mirror"; one bark for the other species. */
		private void bark() {
			if (shape.archetype() != SpeciesShape.Archetype.PINE) {
				return;
			}
			double mirror = (MIRROR[request.age().ordinal()] + range(-0.02, 0.02)) * size.height();
			int start = Math.max(1, (int) Math.round(mirror) - 1);
			int zone = 0;
			for (int i = 0; i < woods; i++) {
				int y = TreeVoxels.unpackY(wPos[i]);
				int owner = wOwner[i];
				if (nKind[owner] == TRUNK) {
					if (zone == 0 && y >= start) {
						wBark[i] = TreeVoxels.TRANSITION;
						zone = 1;
					} else if (zone == 1) {
						wBark[i] = TreeVoxels.TRANSITION_UPPER;
						zone = 2;
					} else {
						wBark[i] = (byte) (zone == 0 ? TreeVoxels.LOWER : TreeVoxels.UPPER);
					}
				} else if (nKind[owner] == STUB && nLeafH[owner] <= 0) {
					// A dry stub keeps the grey lower bark at any height.
					wBark[i] = TreeVoxels.LOWER;
				} else {
					wBark[i] = (byte) (y > start + 1 ? TreeVoxels.UPPER : TreeVoxels.LOWER);
				}
			}
		}

		// ------------------------------------------------------------------ foliage

		private void foliage() {
			boolean hollow = shape.hollow() > 0 && size.crownRadius() >= 3.5;
			for (int i = 0; i < nodes; i++) {
				if (nLeafH[i] <= 0) {
					continue;
				}
				double cx;
				double cy;
				double cz;
				double h = nLeafH[i];
				double v = nLeafV[i];
				if (nCut[i]) {
					// A clipped branch: a smaller cluster at its last block.
					if (nEnd[i] < 0) {
						continue;
					}
					int pos = wPos[nEnd[i]];
					cx = TreeVoxels.unpackX(pos);
					cy = TreeVoxels.unpackY(pos);
					cz = TreeVoxels.unpackZ(pos);
					h *= 0.8;
					v *= 0.9;
				} else {
					cx = nx[i];
					cy = ny[i];
					cz = nz[i];
				}
				// Each cluster with its own depth and a jitter of its height, so the bottoms do not line up.
				v *= range(0.85, 1.2);
				cy += range(-0.4, 0.4);
				cluster(cx, cy, cz, h, v, hollow);
			}
			boolean pine = shape.archetype() == SpeciesShape.Archetype.PINE;
			if (spireFrom < top || pine && !forks()) {
				// The spruce's spire is a narrow column of needles, not a bare stick with a tuft (review of V6, round 2): a
				// leaf on the four sides of each leader block and of the short shoots of the highest whorl, whose own small
				// clusters may leave their row bare; the shoot cover below adds the leaf above the top. The pine's leader
				// gets the same around its top block only (its crown below stays open).
				int from = pine ? (int) Math.floor(top) : (int) Math.floor(spireFrom) - 1;
				for (int i = 0; i < woods; i++) {
					int pos = wPos[i];
					int y = TreeVoxels.unpackY(pos);
					if (y < from || wRadius[i] > DIAGONAL_RADIUS || nKind[wOwner[i]] == STUB && nLeafH[wOwner[i]] <= 0) {
						continue;
					}
					int x = TreeVoxels.unpackX(pos);
					int z = TreeVoxels.unpackZ(pos);
					for (int side = 0; side < 4; side++) {
						int lx = x + (side == 0 ? 1 : side == 1 ? -1 : 0);
						int lz = z + (side == 2 ? 1 : side == 3 ? -1 : 0);
						if (leafCell(lx, y, lz) && stamp[cellOf(lx, y, lz)] != current) {
							addLeaf(lx, y, lz);
						}
					}
				}
			}
			cover();
		}

		/**
		 * Covers the shoots (after the clusters, and again after pruning and dropping freed cells next to the wood): a leaf
		 * above each thin live shoot, one below each branch block (round 2) and a sleeve of leaves along the shoots of the upper crown.
		 */
		private void cover() {
			boolean pine = shape.archetype() == SpeciesShape.Archetype.PINE;
			// Shoots are covered: a leaf right above every thin branch block of the crown, so no bare branch tops the
			// crown seen from above. Round 2: the branches at any radius (the thick base of a pine's whorl branch lay bare on
			// the crowns seen from 30 m) and also below the crown base; the thin trunk and limbs only within the crown.
			for (int i = 0; i < woods; i++) {
				if (wGone[i]) {
					continue;
				}
				int owner = wOwner[i];
				boolean shoot = nKind[owner] != STUB || nLeafH[owner] > 0;
				boolean branch = nKind[owner] == BRANCH || nKind[owner] == TWIG;
				int pos = wPos[i];
				int y = TreeVoxels.unpackY(pos) + 1;
				if (!shoot || wRadius[i] > DIAGONAL_RADIUS && !branch || y < size.crownBase() && !branch) {
					continue;
				}
				int x = TreeVoxels.unpackX(pos);
				int z = TreeVoxels.unpackZ(pos);
				if (leafCell(x, y, z) && free(x, y, z)) {
					addLeaf(x, y, z);
				}
				// Round 2 (after the frames): a leaf below each branch block too, where the layer allows it, so the branches at
				// the bottom of a crown do not stand bare against the sky seen from below (the "lamp-post arms" and hooks of
				// the eye views); it halves the bare wood open to the side and adds 3-10% leaves, with the canopy closure
				// within 1 percentage point.
				if (branch && leafCell(x, y - 2, z) && free(x, y - 2, z)) {
					addLeaf(x, y - 2, z);
				}
			}
			// Upward shoots of the upper crown are sleeved (review of V9, round 1): a leaf on the four sides of every thin block
			// that grows up from its parent there, so no bare stick of a leader or of an upturned tip stands above the
			// foliage (the leaf above a shoot covers it only from above; from the side, a pine's upturned tips read as a "U").
			// The leader of a pine (up to 3 px, the stem of an old pine's umbrella) all through the crown.
			// Round 2: the sideways shoots of the upper crown too, on their two sides across the shoot, and the branches at any
			// radius (seen from above at 30 m, a bare candelabra of a pine's top whorl showed between the tufts; the leaf above
			// covers it only from straight above). After the frames of round 2: below that, down to the crown base, the same
			// sleeve but only in the columns that already hold a leaf of the tree, so the hooks and forks of branches at the
			// bottom of a crown are covered from the side without widening the crown (the canopy closure stays within one
			// percentage point; a sleeve in new columns raised it by 4-6 points in the managed oak-hornbeam forest).
			double sleeveFrom = size.crownBase() + SLEEVE_FROM * crownLength;
			for (int i = 0; i < woods; i++) {
				if (wGone[i]) {
					continue;
				}
				int owner = wOwner[i];
				boolean leader = pine && nKind[owner] == TRUNK && wRadius[i] <= DIAGONAL_RADIUS + 1;
				int parent = wParent[i];
				boolean up = parent == TreeVoxels.DOWN;
				boolean branch = nKind[owner] == BRANCH || nKind[owner] == TWIG;
				boolean shoot = !up && parent != TreeVoxels.UP && branch;
				if (wRadius[i] > DIAGONAL_RADIUS && !leader && !branch || !up && !shoot || nKind[owner] == STUB && nLeafH[owner] <= 0) {
					continue;
				}
				int pos = wPos[i];
				int y = TreeVoxels.unpackY(pos);
				boolean below = y < (leader ? size.crownBase() : sleeveFrom);
				if (below && y < size.crownBase()) {
					continue;
				}
				int x = TreeVoxels.unpackX(pos);
				int z = TreeVoxels.unpackZ(pos);
				boolean alongX = parent == TreeVoxels.WEST || parent == TreeVoxels.EAST;
				for (int side = 0; side < 4; side++) {
					if (shoot && (side < 2) == alongX) {
						continue;
					}
					int lx = x + (side == 0 ? 1 : side == 1 ? -1 : 0);
					int lz = z + (side == 2 ? 1 : side == 3 ? -1 : 0);
					if (leafCell(lx, y, lz) && free(lx, y, lz) && (!below || ownLeafColumn(lx, lz))) {
						addLeaf(lx, y, lz);
					}
				}
			}
		}

		/**
		 * The stem of a broad-leaved canopy tree of a stand is never open to one side within its crown (review of V10, round
		 * 1): a trunk block at or above the lowest leaf of the tree with no wood or leaf of the tree within
		 * {@value #STEM_REACH} blocks on one of its four sides (at its height or one above) gets a leaf next to it on that
		 * side, within the layer (the four columns next to a trunk belong to its territory, {@link CrownTerritories}). A
		 * heavy neighbor took those columns, or the clusters of a crown carried on one side of the trunk left the other
		 * side empty, and where the neighbors on that side are lower the stem stood bare above their crowns for the whole
		 * length of its own crown (an ash of 37 blocks in the natural oak-hornbeam forest: a gray pole of 15 blocks seen from
		 * 10-20 blocks above the canopy). A real crown carries short shoots on that side of the stem too. A leaf outside the
		 * territory could meet a leaf of a neighbor in the same block, which the order of the decoration would settle (not
		 * deterministic). Pine and spruce keep their open crowns (the leader of a pine is sleeved in {@link #cover}).
		 */
		private void stemSleeve() {
			if (!stemSleeves() || leaves == 0) {
				return;
			}
			int lowest = Integer.MAX_VALUE;
			for (int i = 0; i < leaves; i++) {
				lowest = Math.min(lowest, TreeVoxels.unpackY(lPos[i]));
			}
			for (int i = 0; i < woods; i++) {
				if (wGone[i] || nKind[wOwner[i]] != TRUNK) {
					continue;
				}
				int pos = wPos[i];
				int x = TreeVoxels.unpackX(pos);
				int y = TreeVoxels.unpackY(pos);
				int z = TreeVoxels.unpackZ(pos);
				if (y < lowest) {
					continue;
				}
				for (int side = 0; side < 4; side++) {
					int dx = side == 0 ? 1 : side == 1 ? -1 : 0;
					int dz = side == 2 ? 1 : side == 3 ? -1 : 0;
					if (openSide(x, y, z, dx, dz) && leafCell(x + dx, y, z + dz) && free(x + dx, y, z + dz)) {
						addLeaf(x + dx, y, z + dz);
					}
				}
			}
		}

		/** A broad-leaved canopy tree of a stand: its stem is sleeved and its upturned tips at the top must be covered. */
		private boolean stemSleeves() {
			return request.layer() == CrownLayer.CANOPY && request.territory() != null && shape.archetype() != SpeciesShape.Archetype.PINE
					&& shape.archetype() != SpeciesShape.Archetype.SPRUCE;
		}

		/** Whether (x, y, z) is open to one of its four sides ({@link #openSide(int, int, int, int, int)}). */
		private boolean openSide(int x, int y, int z) {
			return openSide(x, y, z, 1, 0) || openSide(x, y, z, -1, 0) || openSide(x, y, z, 0, 1) || openSide(x, y, z, 0, -1);
		}

		/** Whether no wood or leaf of the tree stands within {@value #STEM_REACH} blocks of (x, y, z) in direction (dx, dz). */
		private boolean openSide(int x, int y, int z, int dx, int dz) {
			for (int s = 1; s <= STEM_REACH; s++) {
				for (int up = 0; up <= 1; up++) {
					int ax = x + dx * s;
					int ay = y + up;
					int az = z + dz * s;
					if (inBox(ax, ay, az) && !free(ax, ay, az)) {
						return false;
					}
				}
			}
			return true;
		}

		/** Whether the column (x, z) of the box holds a leaf of the tree at any height. */
		private boolean ownLeafColumn(int x, int z) {
			for (int y = 0; y < MAX_HEIGHT; y++) {
				if (inBox(x, y, z)) {
					int c = cellOf(x, y, z);
					if (stamp[c] == current && cell[c] == LEAF) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Sprouts on the bare stretches of the trunk and the limbs within the crown (review of V9, round 2): a block of
		 * them with at most one leaf of the tree among its 26 neighbors gets a leaf on its four sides, as the short shoots
		 * along the limbs of a broad-leaved tree; a bare pole with the arm of a branch read as a lamp post, the bare fork of
		 * an oak or a beech as a gray candelabra under the foliage.
		 */
		private void sprouts() {
			double from = size.crownBase() + SPROUTS_FROM * crownLength;
			for (int i = 0; i < woods; i++) {
				int kind = nKind[wOwner[i]];
				if (wGone[i] || kind != TRUNK && kind != LIMB || wRadius[i] > SPROUTS_RADIUS) {
					continue;
				}
				int pos = wPos[i];
				int x = TreeVoxels.unpackX(pos);
				int y = TreeVoxels.unpackY(pos);
				int z = TreeVoxels.unpackZ(pos);
				if (y < from || leavesAround(x, y, z) > 1) {
					continue;
				}
				for (int side = 0; side < 4; side++) {
					int lx = x + (side == 0 ? 1 : side == 1 ? -1 : 0);
					int lz = z + (side == 2 ? 1 : side == 3 ? -1 : 0);
					if (leafCell(lx, y, lz) && free(lx, y, lz)) {
						addLeaf(lx, y, lz);
					}
				}
			}
		}

		/**
		 * Trims the bare ends of the side branches and their twigs at any radius (review of V9, round 2), after the shoots
		 * are covered: a tip block with at most one own leaf among its 26 neighbors goes, from the tips inwards, so a branch
		 * ends at its last block with leaves around it, and one with none goes back to its parent. The thick base of a pine's
		 * whorl branch, which {@link #prune} leaves (radius over 2 px), stood bare between the crowns seen from 30 m.
		 */
		private void trimBareTips() {
			// Review of V10, round 1: an upturned branch tip within TIP_DEPTH blocks of the top of a broad-leaved canopy tree
			// that is open to one side (no wood or leaf of the tree within STEM_REACH blocks) goes too: at the edge of the
			// territory its sleeve has no columns outside, and it stood as a gray hook above the canopy.
			int tipFrom = Integer.MAX_VALUE;
			if (stemSleeves()) {
				int top = 0;
				for (int i = 0; i < woods; i++) {
					top = wGone[i] ? top : Math.max(top, TreeVoxels.unpackY(wPos[i]));
				}
				tipFrom = top - TIP_DEPTH;
			}
			for (int i = woods - 1; i > 0; i--) {
				int kind = nKind[wOwner[i]];
				if (wGone[i] || wChildren[i] > 0 || kind != BRANCH && kind != TWIG) {
					continue;
				}
				int p = wPos[i];
				int x = TreeVoxels.unpackX(p);
				int y = TreeVoxels.unpackY(p);
				int z = TreeVoxels.unpackZ(p);
				if (leavesAround(x, y, z) <= 1 || y >= tipFrom && wParent[i] == TreeVoxels.DOWN && openSide(x, y, z)) {
					wGone[i] = true;
					wChildren[wParentIndex[i]]--;
					cell[cellOf(x, y, z)] = 0;
				}
			}
		}

		/** Own leaves among the 26 neighbors of a cell (counting stops at 2). */
		private int leavesAround(int x, int y, int z) {
			int n = 0;
			for (int dx = -1; dx <= 1 && n < 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (inBox(x + dx, y + dy, z + dz)) {
							int c = cellOf(x + dx, y + dy, z + dz);
							if (stamp[c] == current && cell[c] == LEAF) {
								n++;
							}
						}
					}
				}
			}
			return n;
		}

		/** Whether a cell of the box holds neither wood nor a leaf of the tree (a pruned block is free again). */
		private boolean free(int x, int y, int z) {
			int c = cellOf(x, y, z);
			return stamp[c] != current || cell[c] == 0;
		}

		/** Whether a leaf may stand at (x, y, z): an allowed column between the split surfaces of the layer. */
		private boolean leafCell(int x, int y, int z) {
			if (!inBox(x, y, z) || !leafColumn[col(x, z)]) {
				return false;
			}
			int c = col(x, z);
			return y >= floorY[c] && y <= capY[c];
		}

		private void addLeaf(int x, int y, int z) {
			if (leaves == lPos.length) {
				lPos = Arrays.copyOf(lPos, leaves * 2);
			}
			int c = cellOf(x, y, z);
			stamp[c] = current;
			cell[c] = LEAF;
			cellIndex[c] = leaves;
			cellDist[c] = Byte.MAX_VALUE;
			lPos[leaves++] = TreeVoxels.pack(x, y, z);
		}

		private void cluster(double cx, double cy, double cz, double h, double v, boolean hollow) {
			int x0 = (int) Math.floor(cx - h + 0.5);
			int x1 = (int) Math.floor(cx + h + 0.5);
			int y0 = (int) Math.floor(cy - v + 0.5);
			int y1 = (int) Math.floor(cy + v + 0.5);
			int z0 = (int) Math.floor(cz - h + 0.5);
			int z1 = (int) Math.floor(cz + h + 0.5);
			double fill = shape.fill();
			for (int y = Math.max(y0, 0); y <= y1; y++) {
				double ey = (y - cy) / v;
				for (int x = x0; x <= x1; x++) {
					double ex = (x - cx) / h;
					for (int z = z0; z <= z1; z++) {
						double ez = (z - cz) / h;
						double e = ex * ex + ey * ey + ez * ez;
						if (e > 1 || !leafCell(x, y, z)) {
							continue;
						}
						int c = cellOf(x, y, z);
						if (stamp[c] == current) {
							continue;
						}
						if (e > 1 - 0.35 * cellUnit(x, y, z, 1)) {
							continue;
						}
						if (e > 0.2 && cellUnit(x, y, z, 2) > fill) {
							continue;
						}
						if (hollow && hollowAt(x, y, z)) {
							continue;
						}
						if (nearSplit(x, y, z) && cellUnit(x, y, z, 3) < EDGE_THIN) {
							continue;
						}
						addLeaf(x, y, z);
					}
				}
			}
		}

		/** Whether a cell lies in the row next to a split surface of its column (thinned, a ragged edge). */
		private boolean nearSplit(int x, int y, int z) {
			int c = col(x, z);
			return y == floorY[c] && floorY[c] > 0 || y == capY[c] && capY[c] < MAX_HEIGHT - 1;
		}

		/** Whether a leaf cell lies in the empty core of a large crown (below its top fifth). */
		private boolean hollowAt(int x, int y, int z) {
			double t = (y - size.crownBase()) / Math.max(0.5, crownLength);
			if (t >= 0.8) {
				return false;
			}
			double rho = Math.sqrt((double) x * x + (double) z * z);
			return rho < shape.hollow() * size.crownRadius() * profile.at(t);
		}

		/**
		 * Prunes the thin branch tips (radius ≤ 2, not the trunk, not a dry stub) without a leaf next to them, from the
		 * tips inwards: the bare ends of clusters that the territory or a split surface clipped. The top of the leader and
		 * (review of V9, round 2) the tips of the limbs at any radius end at their last block with a leaf among its 26
		 * neighbors.
		 */
		private void prune() {
			double sleeveFrom = size.crownBase() + SLEEVE_FROM * crownLength;
			for (int i = woods - 1; i > 0; i--) {
				int kind = nKind[wOwner[i]];
				boolean leaderTop = kind == TRUNK && wRadius[i] <= DIAGONAL_RADIUS + 1;
				if (wGone[i] || wChildren[i] > 0 || wRadius[i] > DIAGONAL_RADIUS && !leaderTop && kind != LIMB) {
					continue;
				}
				if (kind == STUB) {
					continue;
				}
				int p = wPos[i];
				int x = TreeVoxels.unpackX(p);
				int y = TreeVoxels.unpackY(p);
				int z = TreeVoxels.unpackZ(p);
				if (kind == LIMB) {
					// The tip of a limb (round 2: an old pine's umbrella limb, a thick oak limb) ends at its last block with an
					// own leaf around it, at any radius; it stuck out of the needles as a bare diagonal.
					if (!leafAround(x, y, z)) {
						wGone[i] = true;
						wChildren[wParentIndex[i]]--;
						cell[cellOf(x, y, z)] = 0;
					}
					continue;
				}
				if (kind == TRUNK) {
					// The top of the leader (review of V9, round 1): a squeezed crown left a bare stem standing above the
					// foliage; it ends at the last block with an own leaf around it (26 neighbors).
					if (y > size.crownBase() && !leafAround(x, y, z)) {
						wGone[i] = true;
						wChildren[wParentIndex[i]]--;
						cell[cellOf(x, y, z)] = 0;
					}
					continue;
				}
				boolean covered = false;
				// An upward tip of the upper crown needs a leaf at its side (review of V9, round 1): one only above it leaves a
				// bare stick in the side view.
				boolean upright = wParent[i] == TreeVoxels.DOWN && y >= sleeveFrom;
				for (int[] s : TreeVoxels.STEP) {
					if (upright && s[1] != 0) {
						continue;
					}
					int x2 = x + s[0];
					int y2 = y + s[1];
					int z2 = z + s[2];
					if (inBox(x2, y2, z2)) {
						int c = cellOf(x2, y2, z2);
						if (stamp[c] == current && cell[c] == LEAF) {
							covered = true;
							break;
						}
					}
				}
				if (!covered) {
					wGone[i] = true;
					wChildren[wParentIndex[i]]--;
					cell[cellOf(x, y, z)] = 0;
				}
			}
		}

		/**
		 * Drops the side branches (with their twigs) that would stand bare under the foliage of the stand, like an arm of a
		 * lamp post (review of V9, rounds 1 and 2): a branch whose cluster the territory, a split surface or the wood of the
		 * tree itself clipped. A side branch (from the trunk or a limb) goes when fewer than {@value #MIN_BRANCH_LEAVES}
		 * distinct leaves of the tree touch its wood (26 neighbors; round 1 counted a leaf once for each block it touched), or
		 * when less than {@value #MIN_TIP_COVER} of the blocks of its outer half have {@value #COVER_LEAVES} such leaves. The
		 * trunk, the limbs (their bare stretches sprout leaves instead, {@link #sprouts}: dropping a limb took a forking crown
		 * apart) and the dry stubs stay.
		 */
		private void dropBareBranches() {
			if (root.length < nodes) {
				root = new int[Math.max(nodes, root.length * 2)];
				branchLeaves = new int[root.length];
				tipBlocks = new int[root.length];
				tipCovered = new int[root.length];
				depthMax = new int[root.length];
			}
			for (int i = 0; i < nodes; i++) {
				int kind = nKind[i];
				branchLeaves[i] = 0;
				tipBlocks[i] = 0;
				tipCovered[i] = 0;
				depthMax[i] = 0;
				int parent = nParent[i];
				if (kind == TRUNK || kind == LIMB || kind == STUB) {
					root[i] = -1;
					continue;
				}
				root[i] = parent < 0 || nKind[parent] == TRUNK || nKind[parent] == LIMB ? i : root[parent];
			}
			if (wDepth.length < woods) {
				wDepth = new int[Math.max(woods, wDepth.length * 2)];
				order = new int[wDepth.length];
			}
			if (leafSeen.length < leaves) {
				leafSeen = new int[Math.max(leaves, leafSeen.length * 2)];
			}
			// The depth of each block in its side branch (parents first), and the deepest block of each branch.
			for (int i = 0; i < woods; i++) {
				int r = root[wOwner[i]];
				int parent = wParentIndex[i];
				wDepth[i] = r < 0 || parent < 0 || root[wOwner[parent]] != r ? 0 : wDepth[parent] + 1;
				if (r >= 0 && !wGone[i]) {
					depthMax[r] = Math.max(depthMax[r], wDepth[i]);
				}
			}
			// Distinct leaves of each side branch: the wood sorted by its root, a leaf counted once a root.
			countLeaves(root, branchLeaves);
			// In the wood order (parents first): a block goes with its branch, or with its parent block when that went.
			for (int i = 0; i < woods; i++) {
				int owner = wOwner[i];
				int r = root[owner];
				int parent = wParentIndex[i];
				boolean orphan = parent >= 0 && wGone[parent];
				boolean bareBranch = r >= 0 && (branchLeaves[r] < MIN_BRANCH_LEAVES || tipCovered[r] < MIN_TIP_COVER * tipBlocks[r]);
				if (wGone[i] || !orphan && !bareBranch) {
					continue;
				}
				wGone[i] = true;
				if (parent >= 0) {
					wChildren[parent]--;
				}
				int pos = wPos[i];
				cell[cellOf(TreeVoxels.unpackX(pos), TreeVoxels.unpackY(pos), TreeVoxels.unpackZ(pos))] = 0;
			}
		}

		/**
		 * Counts the distinct leaves among the 26 neighbors of the wood of each side branch ({@code roots} by node, −1 for
		 * none), the blocks of the outer half of each branch and the ones of them that are covered.
		 */
		private void countLeaves(int[] roots, int[] count) {
			// The wood of each root together (a counting sort by root, the wood order kept within a root).
			if (rootStart.length < nodes + 1) {
				rootStart = new int[Math.max(nodes + 1, rootStart.length * 2)];
			}
			Arrays.fill(rootStart, 0, nodes + 1, 0);
			for (int i = 0; i < woods; i++) {
				int r = roots[wOwner[i]];
				if (!wGone[i] && r >= 0) {
					rootStart[r + 1]++;
				}
			}
			for (int r = 0; r < nodes; r++) {
				rootStart[r + 1] += rootStart[r];
			}
			int n = rootStart[nodes];
			for (int i = 0; i < woods; i++) {
				int r = roots[wOwner[i]];
				if (!wGone[i] && r >= 0) {
					order[rootStart[r]++] = i;
				}
			}
			Arrays.fill(leafSeen, 0, leaves, -1);
			for (int k = 0; k < n; k++) {
				int i = order[k];
				int r = roots[wOwner[i]];
				int pos = wPos[i];
				int x = TreeVoxels.unpackX(pos);
				int y = TreeVoxels.unpackY(pos);
				int z = TreeVoxels.unpackZ(pos);
				int around = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (!inBox(x + dx, y + dy, z + dz)) {
								continue;
							}
							int c = cellOf(x + dx, y + dy, z + dz);
							if (stamp[c] != current || cell[c] != LEAF) {
								continue;
							}
							around++;
							int leaf = cellIndex[c];
							if (leafSeen[leaf] != r) {
								leafSeen[leaf] = r;
								count[r]++;
							}
						}
					}
				}
				if (2 * wDepth[i] >= depthMax[r]) {
					tipBlocks[r]++;
					tipCovered[r] += around >= COVER_LEAVES ? 1 : 0;
				}
			}
		}

		/** Whether an own leaf stands among the 26 neighbors of a cell. */
		private boolean leafAround(int x, int y, int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (inBox(x + dx, y + dy, z + dz)) {
							int c = cellOf(x + dx, y + dy, z + dz);
							if (stamp[c] == current && cell[c] == LEAF) {
								return true;
							}
						}
					}
				}
			}
			return false;
		}

		/** Leaf distances by a breadth-first search from the wood through the tree's own leaves. */
		private void distances() {
			if (queue.length < woods + leaves) {
				queue = new int[Math.max(queue.length * 2, woods + leaves)];
			}
			int head = 0;
			int tail = 0;
			for (int i = 0; i < woods; i++) {
				if (wGone[i]) {
					continue;
				}
				int p = wPos[i];
				int c = cellOf(TreeVoxels.unpackX(p), TreeVoxels.unpackY(p), TreeVoxels.unpackZ(p));
				cellDist[c] = 0;
				queue[tail++] = c;
			}
			while (head < tail) {
				int c = queue[head++];
				int d = cellDist[c] + 1;
				if (d > TreeVoxels.MAX_DISTANCE) {
					continue;
				}
				int z = c % SIDE;
				int x = c / SIDE % SIDE;
				int y = c / (SIDE * SIDE);
				for (int[] s : TreeVoxels.STEP) {
					int nx2 = x + s[0];
					int ny2 = y + s[1];
					int nz2 = z + s[2];
					if (nx2 < 0 || nz2 < 0 || ny2 < 0 || nx2 >= SIDE || nz2 >= SIDE || ny2 >= MAX_HEIGHT) {
						continue;
					}
					int n = (ny2 * SIDE + nx2) * SIDE + nz2;
					if (stamp[n] == current && cell[n] == LEAF && cellDist[n] > d) {
						cellDist[n] = (byte) d;
						queue[tail++] = n;
					}
				}
			}
		}

		/** A canopy tree of a stand left with a degenerate crown ({@link #SUPPRESSED_LEAVES}). */
		private boolean suppressed(int height, int leafCount) {
			return request.layer() == CrownLayer.CANOPY && request.territory() != null && height >= SUPPRESSED_HEIGHT
					&& leafCount < Math.max(SUPPRESSED_LEAVES, height / 2);
		}

		private TreeVoxels result() {
			int kept = 0;
			int[] lp = new int[leaves];
			byte[] ld = new byte[leaves];
			for (int i = 0; i < leaves; i++) {
				int p = lPos[i];
				int d = cellDist[cellOf(TreeVoxels.unpackX(p), TreeVoxels.unpackY(p), TreeVoxels.unpackZ(p))];
				if (d >= 1 && d <= TreeVoxels.MAX_DISTANCE) {
					lp[kept] = p;
					ld[kept] = (byte) d;
					kept++;
				}
			}
			// The wood without the pruned tips; the parents always precede their children.
			int[] remap = new int[woods];
			int n = 0;
			int topY = 0;
			for (int i = 0; i < woods; i++) {
				remap[i] = wGone[i] ? -1 : n++;
				if (!wGone[i]) {
					topY = Math.max(topY, TreeVoxels.unpackY(wPos[i]));
				}
			}
			if (suppressed(topY + 1, kept)) {
				return new TreeVoxels(request.x(), request.y(), request.z(), new int[0], new byte[0], new byte[0], new int[0],
						new byte[0], new int[0], new byte[0], (float) size.height());
			}
			int[] pos = new int[n];
			byte[] radius = new byte[n];
			byte[] parents = new byte[n];
			int[] parentIndex = new int[n];
			byte[] barks = new byte[n];
			for (int i = 0; i < woods; i++) {
				int j = remap[i];
				if (j < 0) {
					continue;
				}
				pos[j] = wPos[i];
				radius[j] = wRadius[i];
				parents[j] = wParent[i];
				parentIndex[j] = wParentIndex[i] < 0 ? -1 : remap[wParentIndex[i]];
				barks[j] = wBark[i];
			}
			TreeVoxels voxels = new TreeVoxels(request.x(), request.y(), request.z(), pos, radius, parents, parentIndex, barks,
					Arrays.copyOf(lp, kept), Arrays.copyOf(ld, kept), (float) size.height());
			if (sparse(topY + 1, voxels)) {
				return new TreeVoxels(request.x(), request.y(), request.z(), new int[0], new byte[0], new byte[0], new int[0],
						new byte[0], new int[0], new byte[0], (float) size.height());
			}
			return voxels;
		}

		/**
		 * A broad-leaved canopy tree of a stand whose crown the territories left sparse ({@link CrownCover#sparseCrown}, step
		 * V10): left out like a suppressed one. The open crowns of the pine and the spruce are their shape (a question for CP-B).
		 */
		private boolean sparse(int height, TreeVoxels voxels) {
			return request.layer() == CrownLayer.CANOPY && request.territory() != null && height >= SUPPRESSED_HEIGHT
					&& shape.archetype() != SpeciesShape.Archetype.PINE && shape.archetype() != SpeciesShape.Archetype.SPRUCE
					&& CrownCover.of(voxels).sparseCrown();
		}
	}
}
