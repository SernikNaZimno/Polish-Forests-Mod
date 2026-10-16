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
 * age class and the layer, with a jitter from the hash of the trunk column (salt {@value #SALT}, rule ZW-2). The layers
 * keep to height bands ({@link #band}): the leaves of a canopy tree lie above those of the subcanopy, which lie above
 * those of the regeneration, so on level ground the crowns of different layers never share a block.</li>
 * <li><b>Skeleton:</b> a trunk with a slight lean and wobble; conifers with whorls (the pine with a crown in its upper
 * quarter to third and dry stubs below it, the spruce with a cone of horizontal branches whose tips droop), the birch with
 * ascending branches whose tips hang, broad-leaved trees with a leader and lateral branches with twigs, or (the oak) a
 * fork into thick limbs close to the axis. The branch lengths follow the crown envelope ({@link SpeciesShape.Profile}),
 * shortened to the columns the tree may use; at a forest edge (a territory with {@link CrownTerritory#edge()}) branches
 * towards the free side grow up to 30% longer.</li>
 * <li><b>Pipe model:</b> r_parent^n = Σ r_child^n from the tips (n = 2.2–2.4), scaled so that the trunk at the crown
 * base has its share of the breast-height radius; below the crown base the cross-section r^n grows linearly to the base
 * (the branches lost by self-pruning), so the steps r + 1 → r of the thin trunk spread along the bole. Segments more
 * than 25° off an axis have at most 2 px (risk R4: thick diagonal limbs would show stairs).</li>
 * <li><b>Voxelization:</b> each segment from its parent node to its node by a 3D DDA whose steps are face-adjacent, so
 * the parent of every block is one of its six neighbors. A block takes the radius of its segment but never more than its
 * parent (the radius does not grow towards the tips); r ≤ 7 px is a thin trunk, more a full log. A segment that enters a
 * forbidden column (outside the territory, the disc of {@value #REACH} blocks or, for the lower layers, next to another
 * trunk) stops there, and its children with it (a smaller leaf cluster marks its end); a segment that meets wood already
 * placed (its parent, or a sibling from the same node) goes on from that block. The Scots pine gets the "mirror": the bark
 * of the trunk changes at 0.45–0.55 of the height (rising with the age class) through the two blocks of the transition
 * zone (V1b), the branches above it have the upper bark.</li>
 * <li><b>Foliage:</b> ragged ellipsoids with holes around the shoot tips (tufts of the pine, needles along the spruce
 * branches); large broad-leaved crowns stay empty inside (leaves in the outer shell only); leaves only in the allowed
 * columns, in the band of the layer and not in wood. A breadth-first search from the wood through the tree's own leaves
 * gives the leaf distance; leaves farther than {@value TreeVoxels#MAX_DISTANCE} are dropped.</li>
 * </ol>
 *
 * <p><b>Window.</b> Every block lies within {@value #REACH} blocks of the trunk column horizontally, so in the 3 × 3
 * chunks around the trunk's chunk (risk R6); with a territory every block lies in a column of the territory, so two
 * trees of one layer never write the same column. For the subcanopy and the regeneration the wood also keeps one column
 * away from every other trunk column (the trunks of the canopy pass through their bands).
 *
 * <p><b>Determinism.</b> The result depends only on the request (thread-local working arrays are fully reset, the
 * random numbers come from the hash of the trunk column); math through {@link StrictMath}.
 */
public final class TreeShapePlan {
	/** Salt of the tree shape hash (rule ZW-2). */
	public static final String SALT = "feature.tree.shape";
	/** Largest horizontal distance of a block from the trunk column: the write window of 3 × 3 chunks. */
	public static final int REACH = CrownTerritories.MAX_REACH;
	/** Largest height of a block above the trunk base (exclusive). */
	public static final int MAX_HEIGHT = 48;
	/** Pixels of the trunk radius per centimeter of the diameter: 16 px per meter, half of the diameter. */
	public static final double PX_PER_CM = 0.08;

	private static final int SIDE = 2 * REACH + 1;
	private static final int CELLS = SIDE * SIDE * MAX_HEIGHT;

	/**
	 * Height bands of the leaves by layer and age class (blocks above the trunk base, inclusive) [S]: canopy from the
	 * first value up, subcanopy between the second and the third, regeneration up to the fourth. A tree of a lower layer
	 * is no taller than its band allows, a canopy tree has no branch below its band. To be calibrated with N2 in V9.
	 */
	private static final int[][] BANDS = {
			// canopy min, subcanopy min, subcanopy max, regeneration max
			{6, 3, 5, 2}, // pole stage
			{13, 5, 12, 4}, // mature
			{16, 5, 15, 4}, // old growth
	};
	/** Height of a subcanopy tree as a share of a canopy tree of the species, before its band caps it. */
	private static final double SUBCANOPY_HEIGHT = 0.62;
	private static final double SUBCANOPY_DBH = 0.55;
	private static final double SUBCANOPY_CROWN = 0.7;
	/** Height of the regeneration by age class (blocks), before its band caps it. */
	private static final double[] REGENERATION_HEIGHT = {2.5, 4.0, 4.5};
	/** Diagonal segments: at most this radius (px) when more than {@link #DIAGONAL} off every axis (risk R4). */
	private static final int DIAGONAL_RADIUS = 2;
	private static final double DIAGONAL = StrictMath.cos(StrictMath.toRadians(25));
	/** Share of the height where the pine's "mirror" starts, by age class (plan V6: 0.45–0.55, rising with age). */
	private static final double[] MIRROR = {0.45, 0.5, 0.55};
	private static final double GOLDEN = StrictMath.toRadians(137.5);
	private static final int AZIMUTH_BINS = 32;

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
	 * Leaf band of a layer and age class, {min, max} blocks above the trunk base (inclusive); the canopy has no upper
	 * limit.
	 */
	public static int[] band(CrownLayer layer, AgeClass age) {
		int[] b = BANDS[age.ordinal()];
		return switch (layer) {
			case CANOPY -> new int[] {b[0], MAX_HEIGHT - 1};
			case SUBCANOPY -> new int[] {b[1], b[2]};
			case REGENERATION -> new int[] {0, b[3]};
		};
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

		// Grid of the tree's box, valid where stamp == current.
		private final int[] stamp = new int[CELLS];
		private final byte[] cell = new byte[CELLS];
		private final int[] cellIndex = new int[CELLS];
		private final byte[] cellDist = new byte[CELLS];
		private int current;
		// Allowed columns: leaves (territory, disc) and wood (also away from other trunks for the lower layers).
		private final boolean[] leafColumn = new boolean[SIDE * SIDE];
		private final boolean[] woodColumn = new boolean[SIDE * SIDE];
		private final float[] reach = new float[AZIMUTH_BINS];
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
		// Wood blocks.
		private int woods;
		private int[] wPos = new int[512];
		private byte[] wRadius = new byte[512];
		private byte[] wParent = new byte[512];
		private int[] wParentIndex = new int[512];
		private int[] wOwner = new int[512];
		private byte[] wBark = new byte[512];
		// Leaves.
		private int leaves;
		private int[] lPos = new int[2048];
		private int[] queue = new int[4096];
		// The request.
		private @Nullable Noise noise;
		private long noiseSeed;
		private long rng;
		private long cellSeed;
		private TreeRequest request;
		private SpeciesShape shape;
		private Size size;
		private double top;
		private double crownLength;
		private int minLeafY;
		private int maxLeafY;
		private double leafH;
		private double leafV;
		private SpeciesShape.Profile profile;
		private boolean edge;

		private Workspace() {
		}

		// ------------------------------------------------------------------ request and allometry

		private void begin(TreeRequest r) {
			request = r;
			shape = SpeciesShape.of(r.species());
			if (noise == null || noiseSeed != r.worldSeed()) {
				noise = new Noise(r.worldSeed()).derive(SALT);
				noiseSeed = r.worldSeed();
			}
			long salt = ((long) r.species().ordinal() << 8) | ((long) r.layer().ordinal() << 4) | r.age().ordinal();
			long h = noise.hash(r.x(), r.z(), salt);
			rng = h;
			cellSeed = Noise.mix(h ^ 0x5DEECE66DL);
			AgeClass age = r.age();
			int[] band = band(r.layer(), age);
			minLeafY = band[0];
			maxLeafY = band[1];
			double jh = range(0.9, 1.1);
			double jd = range(0.85, 1.15);
			double jr = range(0.85, 1.15);
			double jc = range(0.92, 1.08);
			double height = shape.height().get(age) * jh;
			double dbh = shape.dbh().get(age) * jd;
			double crownR = shape.crownRadius().get(age) * jr;
			double ratio = Math.min(0.85, shape.crownRatio().get(age) * jc);
			profile = shape.profile();
			if (shape.archetype() == SpeciesShape.Archetype.PINE && age == AgeClass.OLD_GROWTH) {
				// Old pines: a flatter, umbrella-like top.
				profile = new SpeciesShape.Profile(profile.base(), Math.min(0.75F, profile.peak() + 0.15F),
						Math.min(0.6F, profile.top() + 0.25F), profile.shape() * 1.5F);
			}
			double hs = Math.clamp(0.55 + 0.15 * crownR, 0.7, 1.2);
			leafH = shape.clusterH() * hs;
			leafV = shape.clusterV() * Math.clamp(0.6 + 0.12 * crownR, 0.75, 1.15);
			double droop = Math.max(0, -shape.bend()) * crownR * 1.2;
			double crownBase;
			switch (r.layer()) {
				case CANOPY -> crownBase = Math.max(height * (1 - ratio), minLeafY + leafV + 0.5 + droop + range(0, 2));
				case SUBCANOPY -> {
					// Below the top of the band by up to three blocks (a third of a narrow band), so the subcanopy is no flat
					// layer, but tall enough for a crown in the band.
					double drop = range(0, Math.min(3, 0.3 * (maxLeafY - minLeafY)));
					height = Math.max(minLeafY + 2.5, Math.min(height * SUBCANOPY_HEIGHT, maxLeafY + 1 - leafV - drop));
					dbh *= SUBCANOPY_DBH;
					crownR *= SUBCANOPY_CROWN;
					crownBase = Math.max(height * (1 - Math.min(0.8, ratio * 1.25)), minLeafY + leafV + 0.3 + droop);
				}
				default -> {
					height = Math.min(REGENERATION_HEIGHT[age.ordinal()] * jh, maxLeafY + 1 - leafV * 0.7);
					dbh = Math.max(2, height * 0.8);
					crownR = Math.min(crownR * 0.35, 1.5);
					crownBase = height * 0.3;
				}
			}
			height = Math.max(2, height);
			top = height - 1;
			crownBase = Math.min(crownBase, top - 1.5);
			crownBase = Math.max(crownBase, 0.5);
			crownLength = top - crownBase;
			size = new Size(height, crownBase, crownR, Math.max(1, dbh * PX_PER_CM));
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
			distances();
			return result();
		}

		/** Allowed columns of leaves and wood and the reach of the territory by azimuth. */
		private void columns(TreeRequest r) {
			CrownTerritory t = r.territory();
			edge = t != null && t.edge();
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dx = -REACH; dx <= REACH; dx++) {
					boolean in = dx * dx + dz * dz <= REACH * REACH && (t == null || t.contains(r.x() + dx, r.z() + dz));
					leafColumn[col(dx, dz)] = in;
					woodColumn[col(dx, dz)] = in;
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

		private static int col(long dx, long dz) {
			return (int) ((dx + REACH) * SIDE + dz + REACH);
		}

		private boolean leaf(long dx, long dz) {
			return dx >= -REACH && dx <= REACH && dz >= -REACH && dz <= REACH && leafColumn[col(dx, dz)];
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
			stubs();
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
			if (!forks()) {
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

		/** Pine and spruce: whorls of branches from the crown base to the top. */
		private void conifer() {
			boolean spruce = shape.archetype() == SpeciesShape.Archetype.SPRUCE;
			double phase = range(0, 2 * Math.PI);
			double y = size.crownBase();
			while (y < top - 0.6) {
				int at = trunkNode(Math.floor(y));
				double ay = ny[at];
				double t = (ay - size.crownBase()) / Math.max(0.5, crownLength);
				int k = shape.perWhorl() + (next() < 0.35 ? 1 : 0) - (next() < 0.25 ? 1 : 0);
				phase += range(0.6, 1.4);
				for (int j = 0; j < k; j++) {
					double azimuth = phase + j * 2 * Math.PI / k + range(-0.35, 0.35);
					double length = envelope(ay, azimuth) * range(0.8, 1.1);
					double angle = StrictMath.toRadians(lerp(t, shape.angleLow(), shape.angleHigh()));
					branch(at, azimuth, length, angle, spruce);
				}
				y = ay + shape.spacing() * range(0.85, 1.15);
				if (Math.floor(y) <= ay) {
					y = ay + 1;
				}
			}
		}

		/**
		 * A conifer branch from trunk node {@code at}: horizontal length {@code length}, angle from the vertical, the tip
		 * bent by {@link SpeciesShape#bend()}; a short one leaves only a cluster at the trunk.
		 */
		private void branch(int at, double azimuth, double length, double angle, boolean spruce) {
			double c = StrictMath.cos(azimuth);
			double s = StrictMath.sin(azimuth);
			double x0 = nx[at];
			double z0 = nz[at];
			length = fit(x0, z0, c, s, length, 0.6 * leafH);
			if (length < 0.9) {
				// Too short for wood: needles right at the trunk (the young top of the crown).
				int n = node(at, x0 + 0.6 * c, ny[at], z0 + 0.6 * s, STUB);
				leafAt(n, leafH * 0.75, leafV * 0.85);
				return;
			}
			double rise = length / StrictMath.tan(angle);
			double bend = shape.bend() * length;
			int mid = node(at, x0 + 0.5 * length * c, ny[at] + 0.5 * rise + 0.2 * bend, z0 + 0.5 * length * s, BRANCH);
			int tip = node(mid, x0 + length * c, ny[at] + rise + bend, z0 + length * s, BRANCH);
			double scale = Math.clamp(0.75 + 0.15 * length, 0.75, 1.15);
			leafAt(tip, leafH * scale, leafV);
			// Needles along the spruce branch; the pine's tuft reaches back over the outer half of the shoot.
			leafAt(mid, leafH * (spruce ? 0.85 : 0.7), leafV * (spruce ? 0.9 : 0.8));
		}

		/** Shortens a horizontal reach from (x0, z0) along (c, s) until its end lies in an allowed wood column. */
		private double fit(double x0, double z0, double c, double s, double length, double margin) {
			double reachLimit = REACH - 0.5 - margin;
			length = Math.min(length, reachLimit);
			while (length > 0.6 && !woodAt(x0 + length * c, z0 + length * s)) {
				length -= 0.5;
			}
			return length;
		}

		/** Birch: a leader with single ascending branches whose tips hang down. */
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
				double angle = StrictMath.toRadians(lerp(t, shape.angleLow(), shape.angleHigh()));
				if (length >= 0.9) {
					double rise = length / StrictMath.tan(angle);
					int mid = node(at, nx[at] + 0.55 * length * c, ay + 0.6 * rise, nz[at] + 0.55 * length * s, BRANCH);
					int tip = node(mid, nx[at] + length * c, ay + rise + shape.bend() * length, nz[at] + length * s, BRANCH);
					leafAt(mid, leafH * 0.8, leafV * 0.9);
					leafAt(tip, leafH, leafV * 1.1);
					if (length >= 2) {
						// The hanging end of the shoot.
						int hang = node(tip, nx[tip] + 0.3 * c, ny[tip] - 1.2, nz[tip] + 0.3 * s, TWIG);
						leafAt(hang, leafH * 0.8, leafV * 1.2);
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
			azimuth = laterals(0, trunkTop, azimuth, 0);
			if (!forks()) {
				return;
			}
			int limbs = shape.limbs() - (request.age() == AgeClass.MATURE && next() < 0.5 ? 1 : 0);
			double phase = range(0, 2 * Math.PI);
			for (int j = 0; j < limbs; j++) {
				double a = phase + j * 2 * Math.PI / limbs + range(-0.4, 0.4);
				double c = StrictMath.cos(a);
				double s = StrictMath.sin(a);
				double tilt = StrictMath.toRadians(range(12, 24));
				double height = top - ny[trunkTop] - range(0, 1.5);
				int count = Math.max(1, (int) Math.round(height));
				int parent = trunkTop;
				double x = nx[trunkTop];
				double y = ny[trunkTop];
				double z = nz[trunkTop];
				int first = -1;
				for (int k = 1; k <= count; k++) {
					// The limb leans out more towards its top.
					double tk = tilt * (1 + 0.6 * k / count);
					double nxv = x + StrictMath.sin(tk) * c;
					double nzv = z + StrictMath.sin(tk) * s;
					if (StrictMath.hypot(nxv, nzv) > envelope(y + 1, a) * 0.6 || !woodAt(nxv, nzv)) {
						nxv = x;
						nzv = z;
					}
					x = nxv;
					z = nzv;
					y += StrictMath.cos(tk);
					parent = node(parent, x, y, z, LIMB);
					if (first < 0) {
						first = parent;
					}
				}
				leafAt(parent, leafH, leafV * 1.1);
				azimuth = laterals(first, parent, azimuth, a);
			}
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
					azimuth = outward + range(-1.3, 1.3);
				} else {
					azimuth += GOLDEN + range(-0.35, 0.35);
				}
				lateral(i, azimuth, t);
				next = ay + shape.spacing() * range(0.75, 1.25);
			}
			return azimuth;
		}

		/** One lateral branch of a broad-leaved tree from node {@code at}, with twigs. */
		private void lateral(int at, double azimuth, double t) {
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
			double angle = StrictMath.toRadians(lerp(t, shape.angleLow(), shape.angleHigh()) + range(-6, 6));
			double rise = length / StrictMath.tan(angle);
			double bend = shape.bend() * length;
			int a = node(at, x0 + 0.4 * length * c, ay + 0.4 * rise, z0 + 0.4 * length * s, BRANCH);
			int b = node(a, x0 + 0.75 * length * c, ay + 0.75 * rise + 0.3 * bend, z0 + 0.75 * length * s, BRANCH);
			int tip = node(b, x0 + length * c, ay + rise + bend, z0 + length * s, BRANCH);
			leafAt(tip, leafH, leafV);
			if (length >= 2.5) {
				twig(a, azimuth + (next() < 0.5 ? 1 : -1) * range(0.7, 1.1), length * range(0.3, 0.45), t);
				twig(b, azimuth + (next() < 0.5 ? 1 : -1) * range(0.6, 1.0), length * range(0.25, 0.4), t);
			} else {
				leafAt(b, leafH * 0.8, leafV * 0.9);
			}
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
			double rise = length * range(0.15, 0.5) + shape.bend() * length;
			int tip = node(at, x0 + length * c, ny[at] + rise, z0 + length * s, TWIG);
			leafAt(tip, leafH * 0.85, leafV * 0.9);
		}

		/** Dry stubs of self-pruned branches on the bole below the crown base (one block, axis-aligned). */
		private void stubs() {
			if (request.layer() == CrownLayer.REGENERATION || shape.stubs() <= 0) {
				return;
			}
			double y = Math.max(2, 0.25 * size.height());
			double end = size.crownBase() - 0.8;
			while (y < end) {
				if (next() < shape.stubs()) {
					int at = trunkNode(Math.floor(y));
					int dir = (int) (next() * 4);
					double dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
					double dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
					if (woodAt(nx[at] + dx, nz[at] + dz)) {
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
			}
			int i = woods++;
			wPos[i] = TreeVoxels.pack(x, y, z);
			wRadius[i] = (byte) radius;
			wParent[i] = (byte) parentDir;
			wParentIndex[i] = parentIndex;
			wOwner[i] = owner;
			wBark[i] = TreeVoxels.LOWER;
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
				if (!inBox(x, y, z) || !woodColumn[col(x, z)]) {
					nCut[i] = true;
					break;
				}
				int c = cellOf(x, y, z);
				if (stamp[c] == current && cell[c] == WOOD) {
					// Through wood that already exists (an ancestor, or a sibling that left the same node): the segment
					// goes on from that block, whose chain leads to the root; the radius rule below keeps it no thicker.
					cursor = cellIndex[c];
					continue;
				}
				int r = Math.min(q, wRadius[cursor]);
				cursor = addWood(x, y, z, r, dir, cursor, i);
			}
			nEnd[i] = cursor;
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
				if (nKind[wOwner[i]] == TRUNK) {
					if (zone == 0 && y >= start) {
						wBark[i] = TreeVoxels.TRANSITION;
						zone = 1;
					} else if (zone == 1) {
						wBark[i] = TreeVoxels.TRANSITION_UPPER;
						zone = 2;
					} else {
						wBark[i] = (byte) (zone == 0 ? TreeVoxels.LOWER : TreeVoxels.UPPER);
					}
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
				cluster(cx, cy, cz, h, v, hollow);
			}
			// Shoots are covered: a leaf right above every thin branch block of the crown, so no bare branch tops the
			// crown seen from above.
			for (int i = 0; i < woods; i++) {
				int owner = wOwner[i];
				boolean shoot = nKind[owner] != STUB || nLeafH[owner] > 0;
				int pos = wPos[i];
				int y = TreeVoxels.unpackY(pos) + 1;
				if (!shoot || wRadius[i] > DIAGONAL_RADIUS || y < size.crownBase() || y < minLeafY || y > maxLeafY) {
					continue;
				}
				int x = TreeVoxels.unpackX(pos);
				int z = TreeVoxels.unpackZ(pos);
				if (inBox(x, y, z) && leafColumn[col(x, z)] && stamp[cellOf(x, y, z)] != current) {
					addLeaf(x, y, z);
				}
			}
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
			for (int y = Math.max(y0, minLeafY); y <= Math.min(y1, maxLeafY); y++) {
				double ey = (y - cy) / v;
				for (int x = x0; x <= x1; x++) {
					double ex = (x - cx) / h;
					for (int z = z0; z <= z1; z++) {
						double ez = (z - cz) / h;
						double e = ex * ex + ey * ey + ez * ez;
						if (e > 1 || !inBox(x, y, z) || !leafColumn[col(x, z)]) {
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
						addLeaf(x, y, z);
					}
				}
			}
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

		/** Leaf distances by a breadth-first search from the wood through the tree's own leaves. */
		private void distances() {
			if (queue.length < woods + leaves) {
				queue = new int[Math.max(queue.length * 2, woods + leaves)];
			}
			int head = 0;
			int tail = 0;
			for (int i = 0; i < woods; i++) {
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
			byte[] parents = Arrays.copyOf(wParent, woods);
			return new TreeVoxels(request.x(), request.y(), request.z(), Arrays.copyOf(wPos, woods), Arrays.copyOf(wRadius, woods),
					parents, Arrays.copyOf(wParentIndex, woods), Arrays.copyOf(wBark, woods), Arrays.copyOf(lp, kept),
					Arrays.copyOf(ld, kept), (float) size.height());
		}
	}
}
