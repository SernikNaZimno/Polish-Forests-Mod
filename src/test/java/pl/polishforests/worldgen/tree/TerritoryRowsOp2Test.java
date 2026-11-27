package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Step OP2 of docs/04-pionowy-wycinek.md (the tree stand planner faster without a change of the result): the new exact
 * shortcuts give what the code before them gave.
 *
 * <ul>
 * <li>The rows of a crown territory as intervals ({@link CrownTerritories#row}) equal the scan of before, every column of
 * the row against every pair partner, on random pair sets (no partner, partners straight above or below the trunk,
 * equal powers with and without the tie going to the trunk, weights of both signs) and reaches from half a block to the
 * largest one.</li>
 * <li>The integer bound of the hollow core of a crown ({@code TreeShapePlan.Workspace.hollowSquare}) gives the answer of
 * √(x² + z²) &lt; radius for every column of the tree's box, also at radii that are exact square roots and the floating
 * point numbers next to them, zero, negative and NaN.</li>
 * </ul>
 */
class TerritoryRowsOp2Test {
	private static final int R = CrownTerritories.MAX_REACH;

	/** The rows of before step OP2: each column of the row against each partner, the first run of winning columns. */
	private static long scan(int dz, int span, long reach16, int[] px, int[] pz, int[] pa, int[] pb, boolean[] pFirst, int m) {
		long row = 0;
		for (int dx = -span; dx <= span; dx++) {
			long own = 16L * (dx * dx + dz * dz);
			boolean in = own <= reach16;
			for (int i = 0; i < m && in; i++) {
				long ex = dx - px[i];
				long ez = dz - pz[i];
				long mine = own - pa[i];
				long theirs = 16 * (ex * ex + ez * ez) - pb[i];
				if (mine > theirs || (mine == theirs && !pFirst[i])) {
					in = false;
				}
			}
			if (in) {
				row |= 1L << (dx + R);
			} else if (row != 0) {
				break;
			}
		}
		return row;
	}

	@Test
	void rowsAsTheScanOfTheColumns() {
		Random random = new Random(2026_11_27L);
		int checked = 0;
		for (int round = 0; round < 20_000; round++) {
			double reach = round % 50 == 0 ? R : 0.5 + random.nextDouble() * (R - 0.5);
			long reach16 = (long) Math.floor(16 * reach * reach);
			int span = (int) Math.floor(reach);
			int m = round % 10 == 0 ? 0 : random.nextInt(24);
			int[] px = new int[m];
			int[] pz = new int[m];
			int[] pa = new int[m];
			int[] pb = new int[m];
			boolean[] first = new boolean[m];
			for (int i = 0; i < m; i++) {
				px[i] = random.nextInt(3) == 0 ? 0 : random.nextInt(2 * 2 * R + 1) - 2 * R;
				pz[i] = random.nextInt(2 * 2 * R + 1) - 2 * R;
				if (px[i] == 0 && pz[i] == 0) {
					pz[i] = 1;
				}
				pa[i] = random.nextInt(2000);
				pb[i] = random.nextInt(3) == 0 ? 0 : random.nextInt(2000);
				first[i] = random.nextBoolean();
				if (random.nextInt(4) == 0) {
					// A tie on a column of the row: the partner's power there equals the trunk's (K divisible by 32 · px).
					int dx = random.nextInt(2 * R + 1) - R;
					int dz = random.nextInt(2 * R + 1) - R;
					long k = 32L * px[i] * dx + 32L * pz[i] * dz - 16L * (px[i] * px[i] + pz[i] * pz[i]) + pb[i];
					if (k >= 0 && k < 100_000) {
						pa[i] = (int) k;
					}
				}
			}
			for (int dz = -span; dz <= span; dz++) {
				assertEquals(scan(dz, span, reach16, px, pz, pa, pb, first, m), CrownTerritories.row(dz, span, reach16, px, pz, pa, pb, first, m),
						"round " + round + ", row " + dz);
				checked++;
			}
		}
		System.out.println("[op2] territory rows equal to the scan of the columns: " + checked);
	}

	@Test
	void hollowCoreBoundAsTheSquareRoot() {
		Random random = new Random(11);
		double[] limits = new double[4000];
		int n = 0;
		for (int q = 0; q <= 600 && n < limits.length - 10; q += 7) {
			double root = Math.sqrt(q);
			limits[n++] = root;
			limits[n++] = Math.nextUp(root);
			limits[n++] = Math.nextDown(root);
		}
		limits[n++] = 0;
		limits[n++] = -0.0;
		limits[n++] = -1.5;
		limits[n++] = Double.NaN;
		while (n < limits.length) {
			limits[n++] = random.nextDouble() * 20;
		}
		for (double limit : limits) {
			long bound = TreeShapePlan.Workspace.hollowSquare(limit);
			for (int x = -R; x <= R; x++) {
				for (int z = -R; z <= R; z++) {
					boolean before = !Double.isNaN(limit) && Math.sqrt((double) x * x + (double) z * z) < limit;
					boolean now = bound > 0 && (long) x * x + (long) z * z < bound;
					assertEquals(before, now, "limit " + limit + " at " + x + ", " + z);
				}
			}
		}
	}
}
