package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Root planner (step I1 of docs/04-pionowy-wycinek.md, the user's request of 2026-10-25): the root flares over the
 * ground and the root system in the soil of one tree of the tree planner ({@link TreeShapePlan}), a pure function of the
 * request (rule ZW-1, salt {@value #SALT}, ZW-2) → {@link RootVoxels}. Numbers are estimates [S] after the root habits of
 * the species (oak: a heart root system with a taproot when young and deep sinkers; beech, linden, hornbeam: heart roots
 * with strong buttresses; spruce: a shallow plate of long surface roots; pine: a deep taproot and few laterals; birch:
 * shallow, thin roots).
 *
 * <p><b>Root flares</b> (buttresses): an old or mature canopy tree with a trunk base of at least {@value #FLARE_BASE} px
 * gets 0–6 flares ({@link Habit}: many and strong on beech, oak, linden and hornbeam, fewer on maple and ash, long, thin
 * and flat on spruce, few and short on pine and birch; a mature tree about 60% of the count and one block shorter). Up to
 * four start from the faces of the trunk base, each along its face with a turn of up to 35°, the others fork off sideways
 * from the first block of one of them; each runs 1–4 blocks over the ground at the level of the trunk base with a radius
 * falling from one or two px below the trunk's, so it tapers into the ground. A flare block stands only on level ground
 * (the column's ground one below the trunk base), never face to face with another flare of the tree, and only where the
 * block and its eight neighbors lie in the tree's own Voronoi cell among the trunk candidates (the kept candidates of the
 * M2 pattern, {@link TreeShapePlan.Trunks}; see below): so two flares of neighboring trees, or a flare and another trunk,
 * never touch, not even at an edge or a corner, and a felling mod never takes two trees for one.
 *
 * <p><b>Roots in the soil</b>: a root collar under the trunk column (dy -2, under the top ground block, which the trunk's
 * model reaches into), a taproot below it for oak, pine, ash and maple, and 2–8 lateral roots fanning out at even
 * azimuths under the crown (about 0.65–1.0 × the crown radius, at most {@value #REACH} blocks), in the soil at a depth of
 * {@value #COVER}–3 blocks below the top of each column (on a slope they follow the ground; the spruce stays at
 * {@value #COVER}), thinning to 1 px, each with a side root; sinkers go down from the laterals of oak, beech, linden,
 * hornbeam and maple to the depth of the species (at most {@value #MAX_DEPTH}). Every cell of the root system lies under
 * at least {@value #COVER} blocks of ground (the top block and the one under it stay as they are: the surface keeps its
 * blocks and the pits of the windthrows, which dig at most two blocks, never meet a root), in a known column (the caller
 * leaves out water and structures) and in the columns of the tree's own Voronoi cell among the trunk candidates (ties to
 * the lower x, then z), so the roots of two trees never share a block and the result does not depend on the order in
 * which the trees are written. A cell whose side faces a lower column (a bank, a step of the ground; the planner knows the
 * ground of the columns) is rooted dirt instead of a root ({@link RootVoxels#EXPOSED}), and half of them let a root stick
 * one block out of the wall ({@link RootVoxels#PROTRUDING}). About {@value #DIRT_SHARE} of the soil cells next to a root
 * become rooted dirt.
 *
 * <p><b>Window.</b> Flares lie within {@value #FLARE_LENGTH} blocks of the trunk, roots within the disc of
 * {@value #REACH} blocks: inside the 3 × 3 chunks around the trunk's chunk.
 *
 * <p><b>Determinism.</b> The result depends only on the request (thread-local working arrays are reset by a stamp, the
 * random numbers come from the hash of the trunk column).
 */
public final class RootPlan {
	/** Salt of the root hash (rule ZW-2: a new field, a new salt). */
	public static final String SALT = "feature.tree.roots";
	/** Largest horizontal distance of a root from the trunk column, blocks. */
	public static final int REACH = 8;
	/** Longest root flare, blocks. */
	public static final int FLARE_LENGTH = 4;
	/** Least trunk base radius (px) of a tree with root flares. */
	public static final int FLARE_BASE = 3;
	/** Blocks of ground above every root cell outside the trunk column (the root collar under the trunk has one). */
	public static final int COVER = 2;
	/** Deepest root cell below the top of its column. */
	public static final int MAX_DEPTH = 6;
	/** Largest difference of the ground of a root's column from the trunk's ground, blocks. */
	public static final int MAX_STEP = 6;
	/** Share of the soil cells next to a root that become rooted dirt. */
	public static final double DIRT_SHARE = 0.3;

	/** Root habit of a species [S]. */
	record Habit(int flaresMin, int flaresMax, int flareLength, int flareLess, boolean flatFlares, int lateralsMin,
			int lateralsMax, double spread, int lateralDepth, int depth, int tap, boolean sinkers) {
	}

	static Habit habit(Species species) {
		return switch (species) {
			case OAK -> new Habit(4, 6, 3, 1, false, 4, 6, 0.8, 3, 5, 4, true);
			case BEECH -> new Habit(4, 6, 3, 1, false, 5, 7, 0.85, 3, 4, 0, true);
			case LINDEN -> new Habit(4, 6, 3, 1, false, 5, 7, 0.8, 3, 4, 0, true);
			case HORNBEAM -> new Habit(4, 6, 2, 1, false, 5, 6, 0.75, 3, 4, 0, true);
			case NORWAY_MAPLE -> new Habit(3, 5, 2, 1, false, 4, 6, 0.75, 3, 4, 2, true);
			case ASH -> new Habit(3, 5, 2, 1, false, 4, 6, 0.8, 3, 4, 3, false);
			case SPRUCE -> new Habit(4, 6, 4, 2, true, 6, 8, 1.0, 2, 2, 0, false);
			case SCOTS_PINE -> new Habit(0, 3, 1, 2, false, 3, 5, 0.75, 3, 3, 5, false);
			case BIRCH -> new Habit(0, 3, 1, 2, false, 4, 5, 0.65, 2, 3, 0, false);
			default -> throw new IllegalArgumentException("no root habit for " + species);
		};
	}

	/**
	 * The request: a tree of a species, layer and age class with its trunk base at (x, y, z) (the block above the ground
	 * of the trunk column) and the radius of its lowest trunk block (px, {@link TreeVoxels#radius}(0); a full log counts
	 * as 7).
	 *
	 * @param ground the top ground block of the columns around, {@link TreeShapePlan.Ground#UNKNOWN} where no root may go
	 *               (water, structures, beyond the write window)
	 * @param trunks the trunk candidates around (the kept candidates of the M2 pattern), or null for a lone tree
	 */
	public record Request(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z, int baseRadius,
			TreeShapePlan.Ground ground, TreeShapePlan.@Nullable Trunks trunks, @Nullable Kinds kinds) {
		/** A request whose candidates all count as trees with flares (the most careful rule). */
		public Request(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z, int baseRadius,
				TreeShapePlan.Ground ground, TreeShapePlan.@Nullable Trunks trunks) {
			this(species, layer, age, worldSeed, x, y, z, baseRadius, ground, trunks, null);
		}
	}

	/**
	 * What may grow at a trunk candidate, from its site of the crown territories (a pure function of the world seed and
	 * the column, {@code CrownTerritories#site}; the tree stand plants a tree of that layer and age class there or none):
	 * {@link #NO_ROOTS} (the regeneration), {@link #ROOTS} (roots only: the subcanopy and the canopy of the pole stage) or
	 * {@link #FLARES} (roots and possibly flares: the canopy of mature and old stands).
	 */
	@FunctionalInterface
	public interface Kinds {
		int kind(int x, int z);

		int NO_ROOTS = 0;
		int ROOTS = 1;
		int FLARES = 2;

		/** The kind of a tree of a layer and age class. */
		static int of(CrownLayer layer, AgeClass age) {
			return layer == CrownLayer.REGENERATION ? NO_ROOTS : layer == CrownLayer.CANOPY && age != AgeClass.POLE_STAGE ? FLARES : ROOTS;
		}
	}

	private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

	private RootPlan() {
	}

	/** Plans the roots of the request (thread-local working arrays). */
	public static RootVoxels plan(Request request) {
		return WORKSPACE.get().plan(request);
	}

	/** Whether a species has a root habit (the species of the tree planner). */
	public static boolean supported(Species species) {
		return SpeciesShape.supported(species);
	}

	/**
	 * The lateral roots of a tree as the planner draws them: per lateral its azimuth (radians, 0 = east, π/2 = south),
	 * horizontal length (blocks) and first radius (px). The windthrows of the dead wood planner turn the roots of a fallen
	 * tree up with its root plate from the same values (step I1, {@link DeadwoodPlan}).
	 */
	public static double[][] laterals(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int z, int baseRadius) {
		Habit h = habit(species);
		Random rnd = new Random(noise(worldSeed).hash(x, z, salt(species, layer, age) ^ 0x1A7E_0000_0000L));
		boolean young = age == AgeClass.POLE_STAGE || layer != CrownLayer.CANOPY;
		int n = young ? 2 + (int) (rnd.next() * 2) : h.lateralsMin() + (int) (rnd.next() * (h.lateralsMax() - h.lateralsMin() + 1));
		double crown = SpeciesShape.of(species).crownRadius().get(age) * (layer == CrownLayer.CANOPY ? 1.0 : 0.7);
		int collar = collarRadius(layer, baseRadius);
		double a0 = rnd.next() * 2 * Math.PI;
		double[][] out = new double[n][];
		for (int i = 0; i < n; i++) {
			double azimuth = a0 + 2 * Math.PI * (i + 0.5 * (rnd.next() - 0.5)) / n;
			double length = Math.clamp(h.spread() * crown * (0.75 + 0.4 * rnd.next()) * (young ? 0.6 : 1.0), 2, REACH);
			int radius = Math.clamp(collar - 1 - (i % 2), 1, young ? 1 : 3);
			out[i] = new double[] {azimuth, length, radius};
		}
		return out;
	}

	private static int collarRadius(CrownLayer layer, int baseRadius) {
		return Math.clamp(Math.min(baseRadius, 7), 1, layer == CrownLayer.CANOPY ? 5 : 2);
	}

	private static long salt(Species species, CrownLayer layer, AgeClass age) {
		return ((long) species.ordinal() << 8) | ((long) layer.ordinal() << 4) | age.ordinal();
	}

	/** The noise of the salt for the last world seed of a thread. */
	private record SeededNoise(long worldSeed, Noise noise) {
	}

	private static final ThreadLocal<SeededNoise> NOISE = new ThreadLocal<>();

	private static Noise noise(long worldSeed) {
		SeededNoise n = NOISE.get();
		if (n == null || n.worldSeed() != worldSeed) {
			n = new SeededNoise(worldSeed, new Noise(worldSeed).derive(SALT));
			NOISE.set(n);
		}
		return n.noise();
	}

	/** SplitMix random numbers from a seed. */
	static final class Random {
		private long state;

		Random(long seed) {
			state = seed;
		}

		double next() {
			state += 0x9E3779B97F4A7C15L;
			long z = state;
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			z ^= z >>> 31;
			return (z >>> 11) * 0x1.0p-53;
		}
	}

	/** Working arrays of one thread. */
	private static final class Workspace {
		private static final int SIDE = 2 * REACH + 1;
		/** dy of the box: from -DEPTH_OFF to HEIGHT - DEPTH_OFF - 1. */
		private static final int DEPTH_OFF = 16;
		private static final int HEIGHT = 24;
		private static final int CELLS = SIDE * SIDE * HEIGHT;
		private static final byte NONE = 0;
		private static final byte ROOT = 1;
		private static final byte DIRT = 2;
		private static final byte FLARE = 3;

		private final int[] stamp = new int[CELLS];
		private final byte[] cell = new byte[CELLS];
		private final int[] index = new int[CELLS];
		private int current;
		// Columns: their top (memo) and whether they belong to the tree (0 unknown, 1 own, 2 not).
		private final int[] columnStamp = new int[SIDE * SIDE];
		private final int[] columnTop = new int[SIDE * SIDE];
		private final byte[] owned = new byte[SIDE * SIDE];
		// Candidates around the trunk (other than the trunk itself), by distance.
		private int[] kx = new int[128];
		private int[] kz = new int[128];
		private int[] kd = new int[128];
		private byte[] kk = new byte[128];
		private int candidates;
		// Output lists.
		private int flares;
		private int[] fPos = new int[32];
		private byte[] fRadius = new byte[32];
		private byte[] fParent = new byte[32];
		private int[] fParentIndex = new int[32];
		private int roots;
		private int[] rPos = new int[512];
		private byte[] rRadius = new byte[512];
		private byte[] rParent = new byte[512];
		private int[] rParentIndex = new int[512];
		private byte[] rKind = new byte[512];
		private int dirts;
		private int[] dPos = new int[1024];
		// The request.
		private Request request;
		private int top0;
		private Random rnd;
		private long cellSeed;

		RootVoxels plan(Request r) {
			request = r;
			if (++current == 0) {
				Arrays.fill(stamp, 0);
				Arrays.fill(columnStamp, 0);
				current = 1;
			}
			flares = 0;
			roots = 0;
			dirts = 0;
			if (r.layer() == CrownLayer.REGENERATION || !supported(r.species())) {
				return RootVoxels.empty(r.x(), r.y(), r.z());
			}
			top0 = r.y() - 1;
			if (top(0, 0) != top0) {
				return RootVoxels.empty(r.x(), r.y(), r.z());
			}
			long h = noise(r.worldSeed()).hash(r.x(), r.z(), salt(r.species(), r.layer(), r.age()));
			rnd = new Random(h);
			cellSeed = Noise.mix(h ^ 0x2545F4914F6CDD1DL);
			candidates(r);
			Habit habit = habit(r.species());
			int base = Math.min(r.baseRadius(), 7);
			flares(habit, base);
			underground(habit, base);
			exposure();
			rootedDirt();
			return result();
		}

		// ------------------------------------------------------------------ columns

		private int col(int dx, int dz) {
			return (dx + REACH) * SIDE + dz + REACH;
		}

		private boolean inReach(int dx, int dz) {
			return Math.abs(dx) <= REACH && Math.abs(dz) <= REACH;
		}

		/**
		 * Whether a column lies within {@value #REACH} blocks of the trunk (the disc of the root system): every candidate that
		 * can be nearer such a column than the trunk is then within twice that, in the box of {@link #candidates}.
		 */
		private boolean inDisc(int dx, int dz) {
			return dx * dx + dz * dz <= REACH * REACH;
		}

		/** Top of the ground of a column (relative offsets), or UNKNOWN; also UNKNOWN when it differs too much. */
		private int top(int dx, int dz) {
			int c = col(dx, dz);
			if (columnStamp[c] != current) {
				columnStamp[c] = current;
				int t = request.ground().top(request.x() + dx, request.z() + dz);
				columnTop[c] = t;
				owned[c] = 0;
			}
			int t = columnTop[c];
			if (t == TreeShapePlan.Ground.UNKNOWN || Math.abs(t - (request.y() - 1)) > MAX_STEP) {
				return TreeShapePlan.Ground.UNKNOWN;
			}
			return t;
		}

		private void candidates(Request r) {
			candidates = 0;
			if (r.trunks() == null) {
				return;
			}
			int reach = 2 * REACH + 1;
			r.trunks().forEach(r.x() - reach, r.z() - reach, r.x() + reach, r.z() + reach, (cx, cz) -> {
				if (cx == r.x() && cz == r.z()) {
					return;
				}
				if (candidates == kx.length) {
					kx = Arrays.copyOf(kx, 2 * candidates);
					kz = Arrays.copyOf(kz, 2 * candidates);
					kd = Arrays.copyOf(kd, 2 * candidates);
					kk = Arrays.copyOf(kk, 2 * candidates);
				}
				kx[candidates] = cx - r.x();
				kz[candidates] = cz - r.z();
				kd[candidates] = (cx - r.x()) * (cx - r.x()) + (cz - r.z()) * (cz - r.z());
				kk[candidates] = (byte) (r.kinds() == null ? Kinds.FLARES : r.kinds().kind(cx, cz));
				candidates++;
			});
			// Sort by distance (then x, then z) for the early exit of the region tests; a simple insertion sort.
			for (int i = 1; i < candidates; i++) {
				int x = kx[i];
				int z = kz[i];
				int d = kd[i];
				byte k = kk[i];
				int j = i - 1;
				while (j >= 0 && (kd[j] > d || kd[j] == d && (kx[j] > x || kx[j] == x && kz[j] > z))) {
					kx[j + 1] = kx[j];
					kz[j + 1] = kz[j];
					kd[j + 1] = kd[j];
					kk[j + 1] = kk[j];
					j--;
				}
				kx[j + 1] = x;
				kz[j + 1] = z;
				kd[j + 1] = d;
				kk[j + 1] = k;
			}
		}

		/**
		 * Whether a column belongs to the tree's Voronoi cell among the candidates: strictly nearer than every other one, a
		 * tie going to the lower x, then z (both trees decide alike, so the cells never overlap).
		 */
		private boolean own(int dx, int dz) {
			int c = col(dx, dz);
			top(dx, dz);
			if ((owned[c] & 3) == 0) {
				owned[c] |= (byte) (nearer(dx, dz, Kinds.ROOTS) ? 1 : 2);
			}
			return (owned[c] & 3) == 1;
		}

		/** Whether a column belongs to the tree's Voronoi cell among the candidates that may grow flares. */
		private boolean ownFlare(int dx, int dz) {
			int c = col(dx, dz);
			top(dx, dz);
			if ((owned[c] & 12) == 0) {
				owned[c] |= (byte) (nearer(dx, dz, Kinds.FLARES) ? 4 : 8);
			}
			return (owned[c] & 12) == 4;
		}

		/**
		 * Whether column (dx, dz) is nearer the trunk than every other candidate of at least the given kind ({@link Kinds}),
		 * a tie going to the lower x, then z: the tree's Voronoi cell among them (exact in integers).
		 */
		private boolean nearer(int dx, int dz, int kind) {
			int dt = dx * dx + dz * dz;
			for (int i = 0; i < candidates; i++) {
				// A candidate more than 2 rt from the trunk is more than rt from the column (rt the column's distance).
				if (kd[i] > 4 * dt) {
					break;
				}
				if (kk[i] < kind) {
					continue;
				}
				int ex = dx - kx[i];
				int ez = dz - kz[i];
				int dk = ex * ex + ez * ez;
				if (dk < dt || dk == dt && (kx[i] < 0 || kx[i] == 0 && kz[i] < 0)) {
					return false;
				}
			}
			return true;
		}

		/** Whether any other candidate (of any kind) stands within one block of column (dx, dz), diagonals too. */
		private boolean nextToCandidate(int dx, int dz) {
			for (int i = 0; i < candidates; i++) {
				if (kd[i] > (Math.abs(dx) + Math.abs(dz) + 2) * (Math.abs(dx) + Math.abs(dz) + 2)) {
					break;
				}
				if (Math.abs(kx[i] - dx) <= 1 && Math.abs(kz[i] - dz) <= 1) {
					return true;
				}
			}
			return false;
		}

		// ------------------------------------------------------------------ cells

		private int cellOf(int dx, int dy, int dz) {
			return (col(dx, dz) * HEIGHT) + dy + DEPTH_OFF;
		}

		private boolean inBox(int dx, int dy, int dz) {
			return inReach(dx, dz) && dy >= -DEPTH_OFF && dy < HEIGHT - DEPTH_OFF;
		}

		private byte at(int dx, int dy, int dz) {
			if (!inBox(dx, dy, dz)) {
				return NONE;
			}
			int c = cellOf(dx, dy, dz);
			return stamp[c] == current ? cell[c] : NONE;
		}

		private void mark(int dx, int dy, int dz, byte kind, int i) {
			int c = cellOf(dx, dy, dz);
			stamp[c] = current;
			cell[c] = kind;
			index[c] = i;
		}

		private double cellUnit(int dx, int dy, int dz, long salt) {
			long k = Noise.mix(cellSeed ^ Noise.key(dx * 73856093L + dz, dy, salt));
			return (k >>> 11) * 0x1.0p-53;
		}

		// ------------------------------------------------------------------ flares

		private static final int[][] FACES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

		private void flares(Habit h, int base) {
			Request r = request;
			if (r.layer() != CrownLayer.CANOPY || r.age() == AgeClass.POLE_STAGE || base < FLARE_BASE) {
				return;
			}
			boolean old = r.age() == AgeClass.OLD_GROWTH;
			double drawn = h.flaresMin() + rnd.next() * (h.flaresMax() - h.flaresMin() + 1);
			int count = (int) Math.floor(old ? drawn : 0.6 * drawn);
			int length = old ? h.flareLength() : Math.max(1, h.flareLength() - 1);
			int start = Math.min(base - h.flareLess(), 6);
			if (count == 0 || start < 1) {
				return;
			}
			int primaries = Math.min(4, count);
			int r0 = (int) (rnd.next() * 4);
			int[] faces = switch (primaries) {
				case 1 -> new int[] {r0};
				case 2 -> new int[] {r0, (r0 + 2) & 3};
				case 3 -> new int[] {r0, (r0 + 1) & 3, (r0 + 2) & 3};
				default -> new int[] {0, 1, 2, 3};
			};
			boolean[] used = new boolean[4];
			int[] first = new int[4];
			Arrays.fill(first, -1);
			for (int f : faces) {
				used[f] = true;
				double azimuth = Math.atan2(FACES[f][1], FACES[f][0]) + Math.toRadians(35) * (2 * rnd.next() - 1);
				int len = Math.max(1, length - (rnd.next() < 0.35 ? 1 : 0));
				first[f] = flare(0.5, 0.5, -1, azimuth, len, start, h.flatFlares());
			}
			// Forks off the first block of a flare towards a free face (a diagonal between the two faces).
			for (int k = 0, f = r0; k < 4 && count > primaries; k++, f = (f + 1) & 3) {
				int side = (f + 1) & 3;
				if (first[f] < 0 || used[side]) {
					continue;
				}
				used[side] = true;
				int parent = first[f];
				double azimuth = Math.atan2(FACES[f][1] + FACES[side][1], FACES[f][0] + FACES[side][0]);
				int sx = RootVoxels.unpackX(fPos[parent]);
				int sz = RootVoxels.unpackZ(fPos[parent]);
				if (flare(sx + 0.5, sz + 0.5, parent, azimuth, Math.max(1, length - 1), fRadius[parent] - 1, h.flatFlares()) >= 0) {
					count--;
				}
			}
		}

		/**
		 * Lays one flare from the column (cx, cz) (cell centers; the trunk or a flare block {@code parent}) along the
		 * azimuth by a 2D grid walk; returns the index of its first block, or -1.
		 */
		private int flare(double cx, double cz, int parent, double azimuth, int length, int radius, boolean flat) {
			double ux = Math.cos(azimuth);
			double uz = Math.sin(azimuth);
			int x = (int) Math.floor(cx);
			int z = (int) Math.floor(cz);
			double tMaxX = ux > 0 ? (x + 1 - cx) / ux : ux < 0 ? (cx - x) / -ux : Double.MAX_VALUE;
			double tMaxZ = uz > 0 ? (z + 1 - cz) / uz : uz < 0 ? (cz - z) / -uz : Double.MAX_VALUE;
			double tDX = ux == 0 ? Double.MAX_VALUE : 1 / Math.abs(ux);
			double tDZ = uz == 0 ? Double.MAX_VALUE : 1 / Math.abs(uz);
			int firstIndex = -1;
			int prev = parent;
			int r = radius;
			for (int k = 0; k < length && r >= 1; k++) {
				int px = x;
				int pz = z;
				if (tMaxX < tMaxZ) {
					x += ux > 0 ? 1 : -1;
					tMaxX += tDX;
				} else {
					z += uz > 0 ? 1 : -1;
					tMaxZ += tDZ;
				}
				if (!flareCell(x, z, px, pz)) {
					break;
				}
				int dir = TreeVoxelsDirs.of(px - x, 0, pz - z);
				int i = addFlare(x, z, r, dir, prev);
				if (firstIndex < 0) {
					firstIndex = i;
				}
				prev = i;
				// Broad-leaved flares taper by a third of their radius a block (at least 1 px: 6, 4, 3, 2 px), so they slope into
				// the ground; the flat surface roots of the spruce by 1 px every second block.
				r -= flat ? k % 2 : Math.max(1, (int) Math.round(r / 3.0));
				r = Math.min(r, fRadius[i]);
			}
			return firstIndex;
		}

		private boolean flareCell(int x, int z, int px, int pz) {
			if (Math.abs(x) > FLARE_LENGTH + 1 || Math.abs(z) > FLARE_LENGTH + 1 || (x == 0 && z == 0)) {
				return false;
			}
			if (top(x, z) != top0 || at(x, 0, z) != NONE) {
				return false;
			}
			// No other trunk candidate within one block, and the flare block and its eight neighbors in the tree's own Voronoi
			// cell among the candidates that may grow flares: a flare of another tree, whose block and neighbors lie in that
			// tree's cell, can then never be next to it.
			if (nextToCandidate(x, z)) {
				return false;
			}
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					int nx = x + ox;
					int nz = z + oz;
					if (!(nx == 0 && nz == 0) && (!inReach(nx, nz) || !ownFlare(nx, nz))) {
						return false;
					}
				}
			}
			// Never face to face with the trunk (but from it) or another flare block (but its parent).
			for (int[] f : FACES) {
				int nx = x + f[0];
				int nz = z + f[1];
				if (nx == px && nz == pz) {
					continue;
				}
				if (nx == 0 && nz == 0 || at(nx, 0, nz) == FLARE) {
					return false;
				}
			}
			return true;
		}

		private int addFlare(int x, int z, int radius, int dir, int parent) {
			if (flares == fPos.length) {
				fPos = Arrays.copyOf(fPos, 2 * flares);
				fRadius = Arrays.copyOf(fRadius, 2 * flares);
				fParent = Arrays.copyOf(fParent, 2 * flares);
				fParentIndex = Arrays.copyOf(fParentIndex, 2 * flares);
			}
			fPos[flares] = RootVoxels.pack(x, 0, z);
			fRadius[flares] = (byte) radius;
			fParent[flares] = (byte) dir;
			fParentIndex[flares] = parent;
			mark(x, 0, z, FLARE, flares);
			return flares++;
		}

		// ------------------------------------------------------------------ roots in the soil

		private void underground(Habit h, int base) {
			Request r = request;
			boolean young = r.age() == AgeClass.POLE_STAGE || r.layer() != CrownLayer.CANOPY;
			int collarR = collarRadius(r.layer(), base);
			// The root collar under the top ground block of the trunk column.
			int collar = addRoot(0, -2, 0, collarR, TreeVoxels.UP, -1);
			// The taproot.
			int tap = young ? Math.min(h.tap(), 2) : h.tap();
			int parent = collar;
			for (int k = 1; k <= tap; k++) {
				int dy = -2 - k;
				if (top0 - (r.y() + dy) > MAX_DEPTH) {
					break;
				}
				parent = addRoot(0, dy, 0, Math.max(1, collarR - k), TreeVoxels.UP, parent);
			}
			double[][] laterals = laterals(r.species(), r.layer(), r.age(), r.worldSeed(), r.x(), r.z(), base);
			int depth = young ? Math.min(h.depth(), 3) : h.depth();
			for (int i = 0; i < laterals.length; i++) {
				double azimuth = laterals[i][0];
				double length = laterals[i][1];
				int radius = (int) laterals[i][2];
				int d0 = h.lateralDepth() > COVER && i % 2 == 1 ? COVER + 1 : COVER;
				int end = strand(collar, 0, 0, azimuth, length, radius, d0, h.lateralDepth());
				if (end < 0) {
					continue;
				}
				// A side root from about the middle, and the sinkers.
				if (!young || i % 2 == 0) {
					branchOff(collar, i, azimuth, length, h, depth);
				}
			}
		}

		/** The side root and the sinkers of lateral i (drawn again along its path from the collar). */
		private void branchOff(int collar, int i, double azimuth, double length, Habit h, int depth) {
			Random b = new Random(Noise.mix(cellSeed ^ (0x51DEL + i)));
			double at = length * (0.3 + 0.25 * b.next());
			double turn = Math.toRadians(25 + 20 * b.next()) * (b.next() < 0.5 ? -1 : 1);
			int[] from = along(azimuth, at);
			if (from != null) {
				int fromIndex = rootAt(from[0], from[1]);
				if (fromIndex >= 0) {
					strand(fromIndex, from[0], from[1], azimuth + turn, length * 0.45, 1, COVER, h.lateralDepth());
				}
			}
			if (h.sinkers()) {
				for (double share : new double[] {0.35, 0.7}) {
					int[] s = along(azimuth, length * share);
					if (s == null) {
						continue;
					}
					int si = rootAt(s[0], s[1]);
					if (si < 0) {
						continue;
					}
					int x = RootVoxels.unpackX(rPos[si]);
					int y = RootVoxels.unpackY(rPos[si]);
					int z = RootVoxels.unpackZ(rPos[si]);
					int top = top(x, z);
					int prev = si;
					for (int dy = y - 1; top - (request.y() + dy) <= depth && prev >= 0; dy--) {
						prev = rootCell(x, dy, z, 1, TreeVoxels.UP, prev);
					}
				}
			}
		}

		/** The column of the grid walk along the azimuth at the horizontal distance s (from the trunk center), or null. */
		private int @Nullable [] along(double azimuth, double s) {
			int x = (int) Math.floor(0.5 + s * Math.cos(azimuth));
			int z = (int) Math.floor(0.5 + s * Math.sin(azimuth));
			return inDisc(x, z) ? new int[] {x, z} : null;
		}

		/** The highest root block of the column (dx, dz), or -1. */
		private int rootAt(int dx, int dz) {
			for (int dy = HEIGHT - DEPTH_OFF - 1; dy >= -DEPTH_OFF; dy--) {
				if (at(dx, dy, dz) == ROOT) {
					return index[cellOf(dx, dy, dz)];
				}
			}
			return -1;
		}

		/**
		 * One root from root block {@code from} (in column (fx, fz)) along the azimuth for {@code length} blocks, at a depth
		 * running from d0 to d1 blocks below the top of each column, the radius falling to 1 px; returns its last block or
		 * -1 when it could not start.
		 */
		private int strand(int from, int fx, int fz, double azimuth, double length, int radius, int d0, int d1) {
			double ux = Math.cos(azimuth);
			double uz = Math.sin(azimuth);
			double cx = fx + 0.5;
			double cz = fz + 0.5;
			int x = fx;
			int z = fz;
			double tMaxX = ux > 0 ? (x + 1 - cx) / ux : ux < 0 ? (cx - x) / -ux : Double.MAX_VALUE;
			double tMaxZ = uz > 0 ? (z + 1 - cz) / uz : uz < 0 ? (cz - z) / -uz : Double.MAX_VALUE;
			double tDX = ux == 0 ? Double.MAX_VALUE : 1 / Math.abs(ux);
			double tDZ = uz == 0 ? Double.MAX_VALUE : 1 / Math.abs(uz);
			int prev = from;
			int y = RootVoxels.unpackY(rPos[from]);
			int steps = Math.max(1, (int) Math.round(length));
			int last = -1;
			for (int k = 1; k <= steps; k++) {
				int px = x;
				int pz = z;
				if (tMaxX < tMaxZ) {
					x += ux > 0 ? 1 : -1;
					tMaxX += tDX;
				} else {
					z += uz > 0 ? 1 : -1;
					tMaxZ += tDZ;
				}
				if (!inDisc(x, z)) {
					break;
				}
				int top = top(x, z);
				if (top == TreeShapePlan.Ground.UNKNOWN || !own(x, z)) {
					break;
				}
				double t = k / (double) steps;
				int d = (int) Math.round(d0 + (d1 - d0) * t);
				int want = top - d - request.y();
				int r = Math.max(1, (int) Math.round(radius * (1 - 0.7 * t)));
				if (want < y) {
					// Down first in the old column, then across.
					for (int dy = y - 1; dy >= want && prev >= 0; dy--) {
						prev = rootCell(px, dy, pz, r, TreeVoxels.UP, prev);
					}
					y = want;
				}
				if (prev < 0) {
					break;
				}
				prev = rootCell(x, y, z, r, TreeVoxelsDirs.of(px - x, 0, pz - z), prev);
				if (prev < 0) {
					break;
				}
				if (want > y) {
					// Across first, then up in the new column.
					for (int dy = y + 1; dy <= want && prev >= 0; dy++) {
						prev = rootCell(x, dy, z, r, TreeVoxels.DOWN, prev);
					}
					y = want;
				}
				if (prev < 0) {
					break;
				}
				last = prev;
			}
			return last;
		}

		/**
		 * Adds a root block at (dx, dy, dz) with its parent, or goes on through an existing root of the tree there; returns
		 * the block's index, or -1 where no root may be (see the class comment).
		 */
		private int rootCell(int dx, int dy, int dz, int radius, int dir, int parent) {
			if (!inBox(dx, dy, dz) || !inDisc(dx, dz)) {
				return -1;
			}
			byte k = at(dx, dy, dz);
			if (k == ROOT) {
				return index[cellOf(dx, dy, dz)];
			}
			int top = top(dx, dz);
			if (top == TreeShapePlan.Ground.UNKNOWN) {
				return -1;
			}
			int y = request.y() + dy;
			boolean trunkColumn = dx == 0 && dz == 0;
			if (y > top - (trunkColumn ? 1 : COVER) || top - y > MAX_DEPTH || !trunkColumn && !own(dx, dz)) {
				return -1;
			}
			return addRoot(dx, dy, dz, Math.min(radius, rRadius[parent]), dir, parent);
		}

		private int addRoot(int dx, int dy, int dz, int radius, int dir, int parent) {
			if (roots == rPos.length) {
				rPos = Arrays.copyOf(rPos, 2 * roots);
				rRadius = Arrays.copyOf(rRadius, 2 * roots);
				rParent = Arrays.copyOf(rParent, 2 * roots);
				rParentIndex = Arrays.copyOf(rParentIndex, 2 * roots);
				rKind = Arrays.copyOf(rKind, 2 * roots);
			}
			rPos[roots] = RootVoxels.pack(dx, dy, dz);
			rRadius[roots] = (byte) Math.clamp(radius, 1, 7);
			rParent[roots] = (byte) dir;
			rParentIndex[roots] = parent;
			rKind[roots] = RootVoxels.ROOT;
			mark(dx, dy, dz, ROOT, roots);
			return roots++;
		}

		// ------------------------------------------------------------------ walls of the ground and rooted dirt

		/** Root cells with a side towards a lower or unknown column turn into rooted dirt; half of them push a root out. */
		private void exposure() {
			int n = roots;
			for (int i = 0; i < n; i++) {
				int x = RootVoxels.unpackX(rPos[i]);
				int dy = RootVoxels.unpackY(rPos[i]);
				int z = RootVoxels.unpackZ(rPos[i]);
				int y = request.y() + dy;
				int outX = 0;
				int outZ = 0;
				boolean exposed = false;
				for (int[] f : FACES) {
					int nx = x + f[0];
					int nz = z + f[1];
					int top = inReach(nx, nz) ? top(nx, nz) : TreeShapePlan.Ground.UNKNOWN;
					if (top == TreeShapePlan.Ground.UNKNOWN || y > top) {
						exposed = true;
						if (top != TreeShapePlan.Ground.UNKNOWN && outX == 0 && outZ == 0) {
							outX = f[0];
							outZ = f[1];
						}
					}
				}
				if (!exposed) {
					continue;
				}
				rKind[i] = RootVoxels.EXPOSED;
				int ox = x + outX;
				int oz = z + outZ;
				if ((outX != 0 || outZ != 0) && inDisc(ox, oz) && at(ox, dy, oz) == NONE && own(ox, oz) && cellUnit(x, dy, z, 0xB4A1L) < 0.5) {
					int p = addRoot(ox, dy, oz, 1, TreeVoxelsDirs.of(-outX, 0, -outZ), i);
					rKind[p] = RootVoxels.PROTRUDING;
				}
			}
		}

		private static final int[][] SIX = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

		private void rootedDirt() {
			for (int i = 0; i < roots; i++) {
				if (rKind[i] != RootVoxels.ROOT) {
					continue;
				}
				int x = RootVoxels.unpackX(rPos[i]);
				int dy = RootVoxels.unpackY(rPos[i]);
				int z = RootVoxels.unpackZ(rPos[i]);
				for (int[] s : SIX) {
					int nx = x + s[0];
					int ny = dy + s[1];
					int nz = z + s[2];
					if (!inBox(nx, ny, nz) || !inDisc(nx, nz) || at(nx, ny, nz) != NONE || cellUnit(nx, ny, nz, 0xD127L) >= DIRT_SHARE) {
						continue;
					}
					int top = top(nx, nz);
					int y = request.y() + ny;
					if (top == TreeShapePlan.Ground.UNKNOWN || y > top - COVER || top - y > MAX_DEPTH || !(nx == 0 && nz == 0)
							&& !own(nx, nz)) {
						continue;
					}
					if (dirts == dPos.length) {
						dPos = Arrays.copyOf(dPos, 2 * dirts);
					}
					dPos[dirts++] = RootVoxels.pack(nx, ny, nz);
					mark(nx, ny, nz, DIRT, -1);
				}
			}
		}

		private RootVoxels result() {
			Request r = request;
			return new RootVoxels(r.x(), r.y(), r.z(), Arrays.copyOf(fPos, flares), Arrays.copyOf(fRadius, flares),
					Arrays.copyOf(fParent, flares), Arrays.copyOf(fParentIndex, flares), Arrays.copyOf(rPos, roots),
					Arrays.copyOf(rRadius, roots), Arrays.copyOf(rParent, roots), Arrays.copyOf(rParentIndex, roots),
					Arrays.copyOf(rKind, roots), Arrays.copyOf(dPos, dirts));
		}
	}

	/** Directions as {@link TreeVoxels#DOWN} … {@link TreeVoxels#EAST} from a unit step. */
	static final class TreeVoxelsDirs {
		private TreeVoxelsDirs() {
		}

		static int of(int dx, int dy, int dz) {
			if (dy < 0) {
				return TreeVoxels.DOWN;
			}
			if (dy > 0) {
				return TreeVoxels.UP;
			}
			if (dz < 0) {
				return TreeVoxels.NORTH;
			}
			if (dz > 0) {
				return TreeVoxels.SOUTH;
			}
			return dx < 0 ? TreeVoxels.WEST : TreeVoxels.EAST;
		}
	}
}
