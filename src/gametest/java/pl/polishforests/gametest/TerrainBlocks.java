package pl.polishforests.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The block-by-block terrain file of the determinism test and the report of its differences between two mod sets
 * (user decision J1-q, {@code docs/00} G.32): the optimized noises, aquifer and beard of C2ME are not bit-exact, so the
 * terrain with C2ME and without it differs by single blocks, within {@link #TERRAIN_TOLERANCE_BLOCKS}. Within one mod set
 * the comparison stays exact and does not use this class.
 *
 * <p>Format (text, one area after another): a header {@code area <key>|<terrain hash>|<min y>|<max y>}, then one line per
 * column, {@code <x> <z> <y>:<state> <y>:<state> ...}, with the bottom of each run of equal blocks.
 */
final class TerrainBlocks {
	/**
	 * Differing terrain blocks allowed per area of 8 × 8 chunks between two mod sets: about twice the worst area observed in
	 * J1 (up to 8 differing columns, 1 block per column in the areas written out block by block).
	 */
	static final int TERRAIN_TOLERANCE_BLOCKS = 16;
	/**
	 * Differing decorated columns allowed per area between two mod sets: a differing terrain block also differs after the
	 * decoration, and a feature can react to it in the columns around it (J1: the same 8 columns at most, O2-i).
	 */
	static final int DECORATED_TOLERANCE_COLUMNS = 32;
	/** Differences kept per area for the report. */
	static final int REPORTED_BLOCKS = 12;
	private static final String AREA = "area ";

	private TerrainBlocks() {
	}

	/** One differing block: position and the states in this run and in the reference run. */
	record Block(int x, int y, int z, String ours, String theirs) {
		@Override
		public String toString() {
			return String.format(Locale.ROOT, "(%d, %d, %d) %s/%s", x, y, z, ours, theirs);
		}
	}

	/** Differences of one area; {@code referenceHash} is the terrain hash from the reference's header. */
	record AreaDiff(int blocks, int columns, List<Block> first, String referenceHash) {
		boolean withinTolerance() {
			return blocks <= TERRAIN_TOLERANCE_BLOCKS;
		}

		String report() {
			return String.format(Locale.ROOT, "%d blocks in %d columns differ (limit %d): %s", blocks, columns,
					TERRAIN_TOLERANCE_BLOCKS, first);
		}
	}

	/**
	 * Whether a hash file of the given name was written by the other mod set than {@code label}. Only the two names the test
	 * writes count, so a renamed reference gets the exact comparison and never a tolerance it was not meant to have.
	 */
	static boolean isOtherModSet(String referenceFileName, String label) {
		String other = label.equals("plain") ? "optimization" : "plain";
		return referenceFileName.equals(other + ".txt");
	}

	/** The block-by-block file next to the hash file {@code <label>.txt}. */
	static String blocksFileName(String label) {
		return label + "_terrain.txt.gz";
	}

	static String header(String areaKey, long terrainHash, int minY, int maxY) {
		return AREA + areaKey + "|" + Long.toHexString(terrainHash) + "|" + minY + "|" + maxY;
	}

	/**
	 * Compares two block-by-block files, area by area in the order of {@code ours}; an area missing from {@code theirs} (and
	 * any after it, the order being the same in all runs) has no entry in the result.
	 */
	static Map<String, AreaDiff> compare(BufferedReader ours, BufferedReader theirs) throws IOException {
		Map<String, AreaDiff> result = new LinkedHashMap<>();
		String a = ours.readLine();
		String b = theirs.readLine();
		while (a != null) {
			String[] head = header(a);
			while (b != null && !(b.startsWith(AREA) && header(b)[0].equals(head[0]))) {
				b = theirs.readLine();
			}
			if (b == null) {
				return result;
			}
			String referenceHash = header(b)[1];
			int maxY = Integer.parseInt(head[3]);
			int blocks = 0;
			int columns = 0;
			List<Block> first = new ArrayList<>();
			a = ours.readLine();
			b = theirs.readLine();
			while (a != null && !a.startsWith(AREA)) {
				if (b == null || b.startsWith(AREA)) {
					throw new IOException("The reference has fewer columns in the area " + head[0]);
				}
				if (!a.equals(b)) {
					int n = diffColumn(a, b, maxY, first);
					blocks += n;
					columns += n > 0 ? 1 : 0;
				}
				a = ours.readLine();
				b = theirs.readLine();
			}
			if (b != null && !b.startsWith(AREA)) {
				throw new IOException("The reference has more columns in the area " + head[0]);
			}
			result.put(head[0], new AreaDiff(blocks, columns, List.copyOf(first), referenceHash));
		}
		return result;
	}

	private static String[] header(String line) {
		String[] p = line.substring(AREA.length()).split("\\|");
		if (p.length != 4) {
			throw new IllegalArgumentException("Not an area header: " + line);
		}
		return p;
	}

	/** The number of blocks of two lines of one column that differ; the first ones go to {@code first}. */
	static int diffColumn(String ours, String theirs, int maxY, List<Block> first) {
		int[] count = new int[1];
		visitColumn(ours, theirs, maxY, (x, y, z, sp, sq) -> {
			count[0]++;
			if (first.size() < REPORTED_BLOCKS) {
				first.add(new Block(x, y, z, sp, sq));
			}
		});
		return count[0];
	}

	/** A differing block of a column: world position and the states of the two files. */
	interface BlockVisitor {
		void block(int x, int y, int z, String ours, String theirs);
	}

	/** A differing block of two files: area key and index in the file, column index within the area, position, states. */
	interface ColumnVisitor {
		void block(String areaKey, int area, int column, int x, int y, int z, String ours, String theirs);
	}

	/**
	 * Visits every differing block of two block-by-block files with the same areas and columns in the same order (step L1:
	 * the full columns of two worlds of one run).
	 */
	static void compareColumns(BufferedReader ours, BufferedReader theirs, ColumnVisitor visitor) throws IOException {
		String a = ours.readLine();
		String b = theirs.readLine();
		int area = -1;
		String key = null;
		int maxY = 0;
		int column = 0;
		while (a != null) {
			if (b == null) {
				throw new IOException("The second file is shorter");
			}
			if (a.startsWith(AREA)) {
				String[] head = header(a);
				if (!b.startsWith(AREA) || !header(b)[0].equals(head[0])) {
					throw new IOException("Different areas: " + a + " and " + b);
				}
				area++;
				key = head[0];
				maxY = Integer.parseInt(head[3]);
				column = 0;
			} else {
				if (!a.equals(b)) {
					String k = key;
					int ar = area;
					int c = column;
					visitColumn(a, b, maxY, (x, y, z, sp, sq) -> visitor.block(k, ar, c, x, y, z, sp, sq));
				}
				column++;
			}
			a = ours.readLine();
			b = theirs.readLine();
		}
	}

	/** The block id of a state text of these files ({@code Block{minecraft:stone}[...]} → {@code stone}). */
	static String blockId(String state) {
		int open = state.indexOf('{');
		int close = state.indexOf('}');
		String id = open >= 0 && close > open ? state.substring(open + 1, close) : state;
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	/** Visits the blocks of two lines of one column that differ. */
	static void visitColumn(String ours, String theirs, int maxY, BlockVisitor visitor) {
		String[] p = ours.split(" ");
		String[] q = theirs.split(" ");
		if (!p[0].equals(q[0]) || !p[1].equals(q[1])) {
			throw new IllegalArgumentException("Different columns: " + p[0] + " " + p[1] + " and " + q[0] + " " + q[1]);
		}
		int x = Integer.parseInt(p[0]);
		int z = Integer.parseInt(p[1]);
		int i = 2;
		int j = 2;
		int y = bottom(p[i]);
		if (y != bottom(q[j])) {
			throw new IllegalArgumentException("Column " + x + " " + z + " starts at different heights");
		}
		while (y <= maxY) {
			int endP = i + 1 < p.length ? bottom(p[i + 1]) : maxY + 1;
			int endQ = j + 1 < q.length ? bottom(q[j + 1]) : maxY + 1;
			int end = Math.min(endP, endQ);
			String sp = state(p[i]);
			String sq = state(q[j]);
			if (!sp.equals(sq)) {
				for (int k = y; k < end; k++) {
					visitor.block(x, k, z, sp, sq);
				}
			}
			y = end;
			i += end == endP ? 1 : 0;
			j += end == endQ ? 1 : 0;
		}
	}

	private static int bottom(String run) {
		return Integer.parseInt(run, 0, run.indexOf(':'), 10);
	}

	private static String state(String run) {
		return run.substring(run.indexOf(':') + 1);
	}
}
