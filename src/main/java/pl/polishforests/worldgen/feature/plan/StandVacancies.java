package pl.polishforests.worldgen.feature.plan;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import pl.polishforests.worldgen.tree.Vacancies;

/**
 * The vacancies of the crown territories of the chunk being decorated (step V9 of docs/04-pionowy-wycinek.md, decision
 * V5-i): a pure function of the chunk's tree plan, the codes of its region and the data of its 8 neighbors, testable
 * without the game.
 *
 * <ul>
 * <li>{@link #vacant}: the exact answer for a candidate of the chunk, from its plan ({@link TreeStandPlan#of});</li>
 * <li>{@link #surelyVacant} for a candidate of a neighbor: no rule gives it a tree at its stand, whatever its code
 * ({@link TreeStandPlan.Palette#surelyTreeless}), or its effective tree code
 * can be proved from the region of this chunk ({@link Ecotone#provableTrees}, rule (i+)) and the decision of the
 * neighbor's plan with that code, the water of its column and the neighbor's range ramp gives no tree
 * ({@link TreeStandPlan.Candidates#decide}). Both answers are exact where given, so a "vacant" never names a candidate
 * that grows a tree (the invariant of {@link Vacancies}).</li>
 * </ul>
 * Answers are memoized; an instance serves the decoration of one chunk on one thread.
 */
public final class StandVacancies implements Vacancies {
	/** What the decoration knows of the 8 neighbors. */
	public interface Neighbors {
		/** Water depth (blocks, 0: dry) of world column (x, z) of a neighbor, or -1 when its chunk has no habitats. */
		int waterDepth(int x, int z);

		/** Range ramp of a neighbor chunk ({@link SpeciesRamp}), or null when it has no habitats. */
		float @org.jspecify.annotations.Nullable [] ramp(int chunkX, int chunkZ);
	}

	private final int chunkX;
	private final int chunkZ;
	private final boolean[] tree = new boolean[256];
	private final Ecotone.Region region;
	private final TreeStandPlan.Palette palette;
	private final TreeStandPlan.Stand stand;
	private final Neighbors neighbors;
	private final TreeStandPlan.Candidates candidates;
	private final long seed;
	/** Memo of {@link #surelyVacant} (1 vacant, 0 not; step O1: a fastutil map, see {@code TreeStandPlan.Pattern}). */
	private final Long2ByteOpenHashMap sure = new Long2ByteOpenHashMap();
	private final TreeStandPlan.Stand[] stands = new TreeStandPlan.Stand[9];

	/**
	 * @param planned the trees of the chunk's plan ({@link TreeStandPlan#of}, packed with their columns)
	 * @param region  the codes of the chunk and its 8 neighbors (the region of the ecotones)
	 * @param stand   the conditions of the chunk (its range ramp, the gap noise, the stand map and crown territories)
	 */
	public StandVacancies(int chunkX, int chunkZ, int[] planned, Ecotone.Region region, TreeStandPlan.Palette palette,
			TreeStandPlan.Stand stand, Neighbors neighbors, TreeStandPlan.Candidates candidates, long seed) {
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		for (int p : planned) {
			tree[TreeStandPlan.column(p)] = true;
		}
		this.region = region;
		this.palette = palette;
		this.stand = stand;
		this.neighbors = neighbors;
		this.candidates = candidates;
		this.seed = seed;
	}

	@Override
	public boolean vacant(int x, int z) {
		if (x >> 4 != chunkX || z >> 4 != chunkZ) {
			throw new IllegalArgumentException("exact vacancy asked outside the chunk being decorated: " + x + ", " + z);
		}
		return !tree[(x & 15) << 4 | z & 15];
	}

	@Override
	public boolean surelyVacant(int x, int z) {
		long key = (long) x << 32 | z & 0xFFFF_FFFFL;
		byte known = sure.getOrDefault(key, (byte) -1);
		if (known < 0) {
			known = (byte) (compute(x, z) ? 1 : 0);
			sure.put(key, known);
		}
		return known == 1;
	}

	private boolean compute(int x, int z) {
		if (x >> 4 == chunkX && z >> 4 == chunkZ) {
			return vacant(x, z);
		}
		if (palette.surelyTreeless(candidates, x, z, stand)) {
			return true;
		}
		int depth = neighbors.waterDepth(x, z);
		if (depth < 0) {
			return false;
		}
		int code = Ecotone.provableTrees(region, seed, Ecotone.TREES_SALT, x - (chunkX << 4), z - (chunkZ << 4));
		if (code == Ecotone.UNKNOWN) {
			return false;
		}
		TreeStandPlan.Stand s = standOf(x >> 4, z >> 4);
		return s != null && candidates.decide(x, z, code, depth, palette, s) < 0;
	}

	/** The conditions of a neighbor chunk (its own range ramp), or null without its habitats. */
	private TreeStandPlan.Stand standOf(int cx, int cz) {
		int index = (cx - chunkX + 1) * 3 + cz - chunkZ + 1;
		if (index < 0 || index > 8 || Math.abs(cx - chunkX) > 1 || Math.abs(cz - chunkZ) > 1) {
			return null;
		}
		TreeStandPlan.Stand s = stands[index];
		if (s == null) {
			float[] ramp = neighbors.ramp(cx, cz);
			if (ramp == null) {
				return null;
			}
			s = stands[index] = stand.withRamp(ramp);
		}
		return s;
	}
}
