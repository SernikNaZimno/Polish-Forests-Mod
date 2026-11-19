package pl.polishforests.entity.jay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.squirrel.TreeSpace;

/**
 * What the Eurasian jay needs to know of the forest around it (step FA6, docs/04 "Projekt FA6"): the canopy top and the
 * ground of a column, the cruise band of {@link JayGeometry}, whether a straight flight fits, and the perches.
 *
 * <ul>
 * <li><b>Flight space</b> ({@link #segmentClear}): no collision at all in the hitbox (the leaves included: a jay flies round
 * the crowns, not through them; the trunks of the mod with their arms by their live shape, {@link TreeSpace#collision}),
 * no fluid, loaded chunks, at the sweep points of {@link JayGeometry#sweepPoints}; under the canopy ceiling
 * ({@link #aboveShare}).
 * <li><b>Perch</b> ({@link #perch}): the top of a leaf block (the edge of a crown) or of a log (a branch of the mod, a
 * trunk, a vanilla log) at least {@value #PERCH_ABOVE_GROUND} blocks above the ground, with the hitbox free on it and under
 * the canopy: leaves above it in its column (under a crown: preferred), or a local canopy top at least
 * {@value #PERCH_COVER} blocks over it (the top of a lower crown beside taller ones).
 * <li><b>Approach</b> ({@link #approach}): the point beside a perch from which the last short leg lands on it.
 * </ul>
 */
public final class JaySpace {
	/** Least height of a perch above the ground (blocks). */
	public static final int PERCH_ABOVE_GROUND = 2;
	/** Least height of the local canopy top above an uncovered perch (blocks). */
	public static final double PERCH_COVER = 2.0;
	/** The approach point beside a perch: to the side and up (blocks); above a ground spot (blocks). */
	public static final double APPROACH_SIDE = 1.25;
	public static final double APPROACH_RISE = 0.35;
	public static final double APPROACH_DROP = 1.5;
	/** Longest scan down a column for a perch (blocks). */
	static final int MAX_SCAN = 40;

	private JaySpace() {
	}

	/** The canopy top of a column: the first free height above the highest block that blocks motion, leaves included. */
	public static int canopyTop(Level level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}

	/** The ground of a column: the first free height above the highest block that blocks motion, leaves left out. */
	public static int ground(Level level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/**
	 * The local canopy top around a point: the highest canopy top of the columns within {@link JayGeometry#LOCAL_RADIUS}
	 * on a lattice of {@link JayGeometry#LOCAL_STEP} (only loaded chunks).
	 */
	public static int localCanopy(Level level, double x, double z) {
		int cx = Mth.floor(x);
		int cz = Mth.floor(z);
		int r = JayGeometry.LOCAL_RADIUS;
		int best = Integer.MIN_VALUE;
		for (int dx = -r; dx <= r; dx += JayGeometry.LOCAL_STEP) {
			for (int dz = -r; dz <= r; dz += JayGeometry.LOCAL_STEP) {
				int px = cx + dx;
				int pz = cz + dz;
				if (level.hasChunk(px >> 4, pz >> 4)) {
					best = Math.max(best, canopyTop(level, px, pz));
				}
			}
		}
		return best == Integer.MIN_VALUE ? canopyTop(level, cx, cz) : best;
	}

	/** The jay's hitbox with its feet at a point. */
	public static AABB box(Vec3 feet, float width, float height) {
		return TreeSpace.box(feet, width, height);
	}

	/** Whether the hitbox fits at a point (no collision at all, no fluid, a loaded chunk). */
	public static boolean free(Level level, Vec3 feet, float width, float height) {
		return TreeSpace.free(level, box(feet, width, height));
	}

	/** Whether the hitbox fits at every sweep point of the straight segment from {@code a} to {@code b}. */
	public static boolean segmentClear(Level level, Vec3 a, Vec3 b, float width, float height) {
		int n = JayGeometry.sweepPoints(a.distanceTo(b));
		for (int k = 1; k < n; k++) {
			if (!free(level, JayGeometry.sweepPoint(a, b, k, n), width, height)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The share of the points of a segment (every {@link JayGeometry#PLAN_STEP} blocks, the end included, the start left
	 * out) that lie over the canopy ceiling of their place ({@link JayGeometry#plannedUnder} against
	 * {@link #localCanopy}).
	 */
	public static double aboveShare(Level level, Vec3 a, Vec3 b, float height) {
		int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / JayGeometry.PLAN_STEP));
		int above = 0;
		for (int k = 1; k <= n; k++) {
			Vec3 p = a.lerp(b, k / (double) n);
			if (!JayGeometry.plannedUnder(p.y, height, localCanopy(level, p.x, p.z))) {
				above++;
			}
		}
		return above / (double) n;
	}

	/** Whether feet at a point are in the cruise band of its column. */
	public static boolean inBand(Level level, Vec3 p, float height) {
		int g = ground(level, Mth.floor(p.x), Mth.floor(p.z));
		return JayGeometry.inBand(p.y, g, localCanopy(level, p.x, p.z), height);
	}

	/** A random feet height in the cruise band of a column (the lower half more often), or NaN without a band. */
	public static double cruiseHeight(Level level, double x, double z, float height, RandomSource random) {
		int g = ground(level, Mth.floor(x), Mth.floor(z));
		int c = localCanopy(level, x, z);
		double lo = JayGeometry.cruiseLow(g, c, height);
		double hi = JayGeometry.cruiseHigh(g, c, height);
		if (Double.isNaN(lo) || hi < lo) {
			return Double.NaN;
		}
		double f = random.nextFloat();
		return lo + (hi - lo) * f * (0.4 + 0.6 * f);
	}

	// ------------------------------------------------------------------ perches

	/**
	 * A perch: the feet position on top of the block, whether it is a branch (a log) rather than leaves, and whether it lies
	 * under a crown (leaves of its own column above it) rather than on the edge of one.
	 */
	public record Perch(Vec3 feet, boolean branch, boolean underCrown) {
	}

	/** Perches found under a crown and on the edge of one (counters of the fauna game test). */
	public static final LongAdder PERCHES_UNDER = new LongAdder();
	public static final LongAdder PERCHES_EDGE = new LongAdder();

	/**
	 * The perch of a column (see the class comment), scanning down from the local canopy top to
	 * {@value #PERCH_ABOVE_GROUND} blocks above the ground, or null. Stage 2 (choice FA6-r): the highest perch under a crown
	 * of the column (a branch or the top of a lower crown, reached from below) comes first; the top of the column on the edge
	 * of a taller crown only when the column has none. Only perches with a free side to land from count
	 * ({@link #approach}): a branch or a pocket inside a crown, closed by the leaves, is no perch.
	 */
	public static @Nullable Perch perch(Level level, int x, int z, float width, float height, RandomSource random) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		int g = ground(level, x, z);
		int top = canopyTop(level, x, z);
		int local = localCanopy(level, x + 0.5, z + 0.5);
		int start = Math.min(local, g + MAX_SCAN);
		Perch edge = null;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = start; y >= g + PERCH_ABOVE_GROUND - 1; y--) {
			BlockState s = level.getBlockState(p.set(x, y, z));
			boolean leaf = s.is(BlockTags.LEAVES);
			if (!leaf && !s.is(BlockTags.LOGS)) {
				continue;
			}
			VoxelShape shape = s.getCollisionShape(level, p, CollisionContext.empty());
			if (shape.isEmpty()) {
				continue;
			}
			double feetY = y + shape.max(Direction.Axis.Y);
			if (feetY < g + PERCH_ABOVE_GROUND) {
				break;
			}
			boolean under = top > feetY + 1.0;
			if (!under && (edge != null || local < feetY + PERCH_COVER)) {
				continue;
			}
			Vec3 feet = new Vec3(x + 0.5, feetY, z + 0.5);
			if (!free(level, feet, width, height) || approach(level, feet, false, width, height, random) == null) {
				continue;
			}
			if (under) {
				PERCHES_UNDER.increment();
				return new Perch(feet, !leaf, true);
			}
			edge = new Perch(feet, !leaf, false);
		}
		if (edge != null) {
			PERCHES_EDGE.increment();
		}
		return edge;
	}

	/**
	 * A point beside a perch or a ground spot from which the jay flies the last short leg onto it (stage 2, choice FA6-s):
	 * for a perch {@value #APPROACH_SIDE} blocks to a side and {@value #APPROACH_RISE} up, for the ground
	 * {@value #APPROACH_DROP} straight above; the hitbox free there and on the leg to the spot. Null when none of the sides
	 * (in a random order) fits.
	 */
	public static @Nullable Vec3 approach(Level level, Vec3 spot, boolean ground, float width, float height, RandomSource random) {
		if (ground) {
			Vec3 a = spot.add(0, APPROACH_DROP, 0);
			return free(level, a, width, height) && segmentClear(level, a, spot, width, height) ? a : null;
		}
		int first = random.nextInt(8);
		for (int i = 0; i < 8; i++) {
			double angle = (first + i) % 8 * Math.PI / 4;
			Vec3 a = spot.add(Math.cos(angle) * APPROACH_SIDE, APPROACH_RISE, Math.sin(angle) * APPROACH_SIDE);
			if (free(level, a, width, height) && segmentClear(level, a, spot, width, height)) {
				return a;
			}
		}
		return null;
	}

	/**
	 * Up to {@code limit} perches in a ring {@code min}-{@code max} blocks around a point (horizontal), from {@code tries}
	 * random columns; with {@code away} given only those on the far side of the point from it.
	 */
	public static List<Perch> perchesNear(Level level, Vec3 at, int min, int max, int tries, int limit, float width, float height,
			RandomSource random, @Nullable Vec3 away) {
		List<Perch> out = new ArrayList<>();
		for (int i = 0; i < tries && out.size() < limit; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			if (away != null) {
				double base = Math.atan2(at.z - away.z, at.x - away.x);
				angle = base + (random.nextDouble() - 0.5) * Math.PI * 0.9;
			}
			double d = min + random.nextDouble() * (max - min);
			int x = Mth.floor(at.x + Math.cos(angle) * d);
			int z = Mth.floor(at.z + Math.sin(angle) * d);
			Perch perch = perch(level, x, z, width, height, random);
			if (perch != null) {
				out.add(perch);
			}
		}
		// under a crown first (reached from below), the edges of the crowns after them
		out.sort((a, b) -> Boolean.compare(b.underCrown(), a.underCrown()));
		return out;
	}
}
