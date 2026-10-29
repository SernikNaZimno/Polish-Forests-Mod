package pl.polishforests.worldgen.cave;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.surface.Material;

/**
 * The caves of one chunk (step J1): the blocks of the surface plan that the caves change, by section. A carved block is
 * air, water or lava; a few blocks next to the caves change material (the sandstone of a sand ceiling, the stone of a
 * gravel ceiling, the grass of a dirt floor open to the sky). Column index {@code x * 16 + z} as in the surface plan.
 */
public final class CaveCarving {
	private static final Material[] MATERIALS = Material.values();

	private final int minY;
	/** Per section from the bottom of the world: material ordinal + 1 of a changed block, 0 = the plan's block. */
	private final byte[][] sections;
	private final int[] lowest = new int[256];
	private final int[] highest = new int[256];
	private final int[] solidTop = new int[256];
	private final int[] fluidTop = new int[256];
	private long[] marks = new long[16];
	private int markCount;
	private int carved;

	CaveCarving(int minY, int height) {
		this.minY = minY;
		this.sections = new byte[(height + 15) >> 4][];
		Arrays.fill(lowest, Integer.MAX_VALUE);
		Arrays.fill(highest, Integer.MIN_VALUE);
		Arrays.fill(fluidTop, Integer.MIN_VALUE);
	}

	private static int index(int i, int y) {
		return (y & 15) << 8 | (i & 15) << 4 | i >> 4;
	}

	/** Changes the block of column {@code i} at {@code y}; a carved block extends the carved range of the column. */
	void set(int i, int y, Material m) {
		int s = (y - minY) >> 4;
		byte[] section = sections[s];
		if (section == null) {
			section = new byte[4096];
			sections[s] = section;
		}
		section[index(i, y)] = (byte) (m.ordinal() + 1);
		if (isCave(m)) {
			lowest[i] = Math.min(lowest[i], y);
			highest[i] = Math.max(highest[i], y);
			carved++;
		}
	}

	/** The changed block of column {@code i} at {@code y}, or null where the plan's block stays. */
	public @Nullable Material get(int i, int y) {
		int s = (y - minY) >> 4;
		if (s < 0 || s >= sections.length) {
			return null;
		}
		byte[] section = sections[s];
		if (section == null) {
			return null;
		}
		int v = section[index(i, y)];
		return v == 0 ? null : MATERIALS[v - 1];
	}

	/** Whether column {@code i} is a cave (air, water or lava) at {@code y}. */
	public boolean carved(int i, int y) {
		if (y < lowest[i] || y > highest[i]) {
			return false;
		}
		Material m = get(i, y);
		return m != null && isCave(m);
	}

	/**
	 * Changed blocks of a section ({@code (y - minY) >> 4}) as material ordinal + 1 (0 = the plan's block), in the order
	 * of the section palette ({@code dy << 8 | z << 4 | x}), or null when the caves change nothing there. Not to be
	 * modified.
	 */
	public byte @Nullable [] section(int fromBottom) {
		return fromBottom >= 0 && fromBottom < sections.length ? sections[fromBottom] : null;
	}

	/** Lowest and highest carved Y of column {@code i} ({@link Integer#MAX_VALUE} / {@link Integer#MIN_VALUE} if none). */
	public int lowest(int i) {
		return lowest[i];
	}

	public int highest(int i) {
		return highest[i];
	}

	/** Y of the highest ground block of column {@code i} after the caves (the plan's top unless an entrance opened it). */
	public int solidTop(int i) {
		return solidTop[i];
	}

	/** Y of the highest cave fluid above {@link #solidTop} in column {@code i}, or {@link Integer#MIN_VALUE}. */
	public int fluidTop(int i) {
		return fluidTop[i];
	}

	void tops(int i, int solid, int fluid) {
		solidTop[i] = solid;
		fluidTop[i] = fluid;
	}

	/** Number of carved blocks (air, water and lava) in the chunk. */
	public int carvedBlocks() {
		return carved;
	}

	void mark(int x, int y, int z) {
		if (markCount == marks.length) {
			marks = Arrays.copyOf(marks, marks.length * 2);
		}
		marks[markCount++] = net.minecraft.core.BlockPos.asLong(x, y, z);
	}

	/** Number of cave fluid blocks the vanilla aquifer asked to update after generation (post-processing). */
	public int markCount() {
		return markCount;
	}

	/** Packed position ({@link net.minecraft.core.BlockPos#asLong}) of mark {@code k}. */
	public long mark(int k) {
		return marks[k];
	}

	/** Whether a material is a cave: air or a fluid of the aquifers. */
	public static boolean isCave(Material m) {
		return m == Material.AIR || m == Material.WATER || m == Material.LAVA;
	}
}
