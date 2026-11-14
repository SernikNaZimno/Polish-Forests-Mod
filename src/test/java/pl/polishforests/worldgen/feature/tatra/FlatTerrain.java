package pl.polishforests.worldgen.feature.tatra;

import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

/**
 * A synthetic ground for the tests of the Tatra detail planners (step TT6): the top of each column from a function, solid
 * ground at and below it, an optional list of holes and of water cells.
 */
final class FlatTerrain implements TerrainView {
	private final IntBinaryOperator tops;
	private final Predicate<long[]> hole;
	private final Predicate<long[]> water;
	private Predicate<long[]> logs = c -> false;
	private BiPredicate<Integer, Integer> writable = (x, z) -> true;

	/** The same ground with logs (tree trunks) in the cells the predicate accepts. */
	FlatTerrain withLogs(Predicate<long[]> logs) {
		this.logs = logs;
		return this;
	}

	/** The same ground that a plan may write only into the columns the predicate accepts (the chunk of the builder). */
	FlatTerrain writableOnly(BiPredicate<Integer, Integer> writable) {
		this.writable = writable;
		return this;
	}

	FlatTerrain(IntBinaryOperator tops) {
		this(tops, c -> false, c -> false);
	}

	/**
	 * @param hole  cells (x, y, z) that are not solid although they lie under the top (a cave)
	 * @param water cells that hold water
	 */
	FlatTerrain(IntBinaryOperator tops, Predicate<long[]> hole, Predicate<long[]> water) {
		this.tops = tops;
		this.hole = hole;
		this.water = water;
	}

	@Override
	public int top(int x, int z) {
		return tops.applyAsInt(x, z);
	}

	@Override
	public boolean solid(int x, int y, int z) {
		return y <= top(x, z) && !hole.test(new long[] {x, y, z}) && !water.test(new long[] {x, y, z});
	}

	@Override
	public boolean fluid(int x, int y, int z) {
		return water.test(new long[] {x, y, z});
	}

	@Override
	public boolean writable(int x, int z) {
		return writable.test(x, z);
	}

	@Override
	public boolean log(int x, int y, int z) {
		return logs.test(new long[] {x, y, z});
	}
}
