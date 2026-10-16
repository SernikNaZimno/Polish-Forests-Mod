package pl.polishforests.worldgen.norms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;

/**
 * A snapshot of the blocks of an area for the census ({@link ForestCensus}): for each column of a rectangle the ground
 * (top ground block), the block standing on it (a ground plant) and the cells from 1 to {@link #height} blocks above
 * the ground, reduced to what the census reads: air, leaves, wood standing up or lying on its side, other blocks. Wood
 * cells carry their volume, thickness and species ({@link Wood}). Columns may also carry the species recorded when the
 * generator placed a woody feature there ({@link #record}), which tells the species of the vanilla placeholder trees of
 * M2 (an oak log is oak, linden, maple, ash or elm).
 *
 * <p>Filled by the game test from the world, or by hand in unit tests. Coordinates are world block coordinates; dy is
 * the height above the ground block of the column (1 = the block standing on the ground).
 */
public final class CensusGrid {
	public static final byte AIR = 0;
	public static final byte LEAVES = 1;
	/** Wood of a trunk or branch standing up (vanilla log of axis Y, thin trunk with its parent below or above). */
	public static final byte WOOD_UP = 2;
	/** Wood on its side (vanilla log of axis X or Z, thin trunk with a horizontal parent): branches and lying logs. */
	public static final byte WOOD_SIDE = 3;
	/** Any other block that is not air: ground plants above dy 1, snow, structures, stones. */
	public static final byte OTHER = 4;
	/** Ground of a column without one (outside the area of interest, or nothing solid below). */
	public static final int NO_GROUND = Integer.MIN_VALUE;

	/**
	 * A wood cell.
	 *
	 * @param volume   wood volume in m³ (a full vanilla log 1, a thin trunk of radius r px π r² / 256)
	 * @param diameter thickness in m (a full log 1, a thin trunk 2r / 16)
	 * @param species  species of the block (thin trunks), or null for a vanilla log
	 * @param block    block id, the label of a tree of unknown species
	 */
	public record Wood(double volume, double diameter, @Nullable Species species, String block) {
	}

	/** A full vanilla log block of unknown species. */
	public static Wood log(String block) {
		return new Wood(1, 1, null, block);
	}

	/** A thin trunk block of radius r px (1–7). */
	public static Wood trunk(int radius, Species species, String block) {
		return new Wood(Math.PI * radius * radius / 256.0, 2 * radius / 16.0, species, block);
	}

	private final int minX;
	private final int minZ;
	private final int sizeX;
	private final int sizeZ;
	private final int height;
	private final int[] ground;
	private final byte[] cells;
	private final @Nullable String[] plant;
	private final @Nullable String[] groundBlock;
	private final @Nullable Species[] record;
	private final Map<Long, Wood> wood = new HashMap<>();

	/**
	 * An empty grid (no ground anywhere) over the rectangle [minX, minX + sizeX) × [minZ, minZ + sizeZ), with
	 * {@code height} cells above the ground of each column.
	 */
	public CensusGrid(int minX, int minZ, int sizeX, int sizeZ, int height) {
		this.minX = minX;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeZ = sizeZ;
		this.height = height;
		this.ground = new int[sizeX * sizeZ];
		Arrays.fill(ground, NO_GROUND);
		this.cells = new byte[sizeX * sizeZ * height];
		this.plant = new String[sizeX * sizeZ];
		this.groundBlock = new String[sizeX * sizeZ];
		this.record = new Species[sizeX * sizeZ];
	}

	public int minX() {
		return minX;
	}

	public int minZ() {
		return minZ;
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeZ() {
		return sizeZ;
	}

	/** Cells above the ground of each column. */
	public int height() {
		return height;
	}

	public boolean contains(int x, int z) {
		return x >= minX && x < minX + sizeX && z >= minZ && z < minZ + sizeZ;
	}

	/** Index of the column (x, z) in the grid (x-major). */
	public int column(int x, int z) {
		if (!contains(x, z)) {
			throw new IllegalArgumentException("(" + x + ", " + z + ") outside the grid");
		}
		return (x - minX) * sizeZ + (z - minZ);
	}

	public int columnX(int column) {
		return minX + column / sizeZ;
	}

	public int columnZ(int column) {
		return minZ + column % sizeZ;
	}

	/** Y of the ground block of the column, or {@link #NO_GROUND}. */
	public int ground(int x, int z) {
		return ground[column(x, z)];
	}

	int groundAt(int column) {
		return ground[column];
	}

	/** Sets the ground of a column, with the id of its top block (moss blocks count as moss). */
	public CensusGrid setGround(int x, int z, int y, String block) {
		int c = column(x, z);
		ground[c] = y;
		groundBlock[c] = block;
		return this;
	}

	/** The cell dy blocks above the ground of the column (1 ≤ dy ≤ {@link #height}). */
	public byte cell(int x, int dy, int z) {
		return cellAt(column(x, z), dy);
	}

	byte cellAt(int column, int dy) {
		return cells[column * height + dy - 1];
	}

	/** Sets a non-wood cell (air, leaves, other). */
	public CensusGrid set(int x, int dy, int z, byte kind) {
		if (kind == WOOD_UP || kind == WOOD_SIDE) {
			throw new IllegalArgumentException("wood cells need their wood, use setWood");
		}
		check(dy);
		int c = column(x, z);
		cells[c * height + dy - 1] = kind;
		wood.remove(key(c, dy));
		return this;
	}

	/** Sets a wood cell standing up ({@code up}) or on its side. */
	public CensusGrid setWood(int x, int dy, int z, boolean up, Wood w) {
		check(dy);
		int c = column(x, z);
		cells[c * height + dy - 1] = up ? WOOD_UP : WOOD_SIDE;
		wood.put(key(c, dy), w);
		return this;
	}

	/** The wood of a wood cell, or null. */
	public @Nullable Wood wood(int x, int dy, int z) {
		return woodAt(column(x, z), dy);
	}

	@Nullable Wood woodAt(int column, int dy) {
		return wood.get(key(column, dy));
	}

	/** Id of the plant standing on the ground of the column (the block at dy 1 when it is a plant), or null. */
	public @Nullable String plant(int x, int z) {
		return plant[column(x, z)];
	}

	@Nullable String plantAt(int column) {
		return plant[column];
	}

	/**
	 * Sets the ground plant of the column: the id of the block at dy 1 (e.g. {@code minecraft:fern}); a block of the mod
	 * whose mossy state is on gets the suffix {@link ForestCensus#MOSSY}.
	 */
	public CensusGrid setPlant(int x, int z, @Nullable String id) {
		plant[column(x, z)] = id;
		return this;
	}

	/** Id of the ground block of the column, or null without ground. */
	public @Nullable String groundBlock(int x, int z) {
		return groundBlock[column(x, z)];
	}

	@Nullable String groundBlockAt(int column) {
		return groundBlock[column];
	}

	/** Species recorded when a woody feature was placed with its origin in this column, or null. */
	public @Nullable Species record(int x, int z) {
		return record[column(x, z)];
	}

	@Nullable Species recordAt(int column) {
		return record[column];
	}

	public CensusGrid setRecord(int x, int z, @Nullable Species species) {
		record[column(x, z)] = species;
		return this;
	}

	/**
	 * Differences from another grid over the same rectangle and height (ground, ground block, plant, record, every cell
	 * and its wood), at most {@code max} of them as text; empty when the grids are the same.
	 */
	public List<String> differences(CensusGrid other, int max) {
		List<String> out = new ArrayList<>();
		if (other.minX != minX || other.minZ != minZ || other.sizeX != sizeX || other.sizeZ != sizeZ || other.height != height) {
			out.add("different rectangles or heights");
			return out;
		}
		for (int c = 0; c < ground.length && out.size() < max; c++) {
			String at = "(" + columnX(c) + ", " + columnZ(c) + ")";
			if (ground[c] != other.ground[c] || !Objects.equals(groundBlock[c], other.groundBlock[c])) {
				out.add(at + ": ground " + ground[c] + " " + groundBlock[c] + " vs " + other.ground[c] + " " + other.groundBlock[c]);
				continue;
			}
			if (!Objects.equals(plant[c], other.plant[c])) {
				out.add(at + ": plant " + plant[c] + " vs " + other.plant[c]);
			}
			if (record[c] != other.record[c]) {
				out.add(at + ": record " + record[c] + " vs " + other.record[c]);
			}
			for (int dy = 1; dy <= height && out.size() < max; dy++) {
				if (cellAt(c, dy) != other.cellAt(c, dy) || !Objects.equals(woodAt(c, dy), other.woodAt(c, dy))) {
					out.add(at + " dy " + dy + ": " + cellAt(c, dy) + " " + woodAt(c, dy) + " vs " + other.cellAt(c, dy) + " "
							+ other.woodAt(c, dy));
				}
			}
		}
		return out;
	}

	private void check(int dy) {
		if (dy < 1 || dy > height) {
			throw new IllegalArgumentException("dy " + dy + " outside 1.." + height);
		}
	}

	private static long key(int column, int dy) {
		return (long) column << 16 | dy;
	}
}
