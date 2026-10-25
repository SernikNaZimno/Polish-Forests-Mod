package pl.polishforests.worldgen.feature.plan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.norms.ForestCensus;

/**
 * Palette transitions along a transect (step Z1 of docs/04-pionowy-wycinek.md, norm N15): the share of the columns of
 * the band of a transect whose ecotone code ({@link Ecotone#effective} with the world's {@link ForestBlend}) is the next
 * forest of the sequence, by step, for the tree stand and the ground layer, and the width of each transition (10–90% of
 * the logistic curve from 0 to 1 that fits the steps between the middles of the two forests best, as
 * {@link ForestCensus#transect} fits the moss and litter score; m·k). Unlike the score of the plants on the ground,
 * which a single draw per column makes noisy, the share of the palettes changes from 0 to 1, so the width is measured
 * well also where the two forests' ground layers differ little. A pure function of the habitat codes (a sampler of the
 * classifier), the world seed and the blend: the same in the game test and in JUnit.
 */
public final class TransectPalettes {
	/**
	 * @param treeWidths   width of the transition of the tree stand palettes between forest r and r + 1, m·k (NaN when a
	 *                     forest has no steps)
	 * @param groundWidths the same for the ground layer palettes
	 * @param treeShares   by step, the share of the next forest's tree palette among the columns of the two forests of the
	 *                     transition the step lies in (NaN without such columns)
	 * @param groundShares the same for the ground layer
	 * @param fieldWidths  width of the transition of the expected share of the next forest from the blend's field (the
	 *                     share of forest r + 1 among the four forests at each column of forest r or r + 1, moved by the
	 *                     meander), without the noise of the columns' draws; NaN without a blend
	 */
	public record Result(double[] treeWidths, double[] groundWidths, double[] treeShares, double[] groundShares, double[] fieldWidths) {
	}

	private TransectPalettes() {
	}

	/**
	 * The palette transitions of a transect from (x0, z0) to (x1, z1) (block centers) in {@code steps} steps: a column
	 * within {@code band} blocks of the line belongs to the step nearest to its projection; {@code rank[i]} is the forest
	 * (index into {@code sequence}) of step i or -1.
	 */
	public static Result of(ForestBlend.Sampler sampler, @Nullable ForestBlend blend, long worldSeed, double k, List<HabitatBiome> sequence,
			double x0, double z0, double x1, double z1, int steps, int[] rank, double band) {
		double len = Math.hypot(x1 - x0, z1 - z0);
		double ux = (x1 - x0) / len;
		double uz = (z1 - z0) / len;
		double stepLength = len / steps;
		int forests = sequence.size();
		int[][] trees = new int[steps + 1][forests];
		int[][] ground = new int[steps + 1][forests];
		// Expected share of each forest from the blend's field, summed over the band columns of the sequence's forests.
		double[][] expected = new double[steps + 1][forests];
		int[] expectedColumns = new int[steps + 1];
		float[] shares = new float[4];
		int minCx = Math.floorDiv((int) Math.floor(Math.min(x0, x1) - band - 1), 16);
		int maxCx = Math.floorDiv((int) Math.ceil(Math.max(x0, x1) + band + 1), 16);
		int minCz = Math.floorDiv((int) Math.floor(Math.min(z0, z1) - band - 1), 16);
		int maxCz = Math.floorDiv((int) Math.ceil(Math.max(z0, z1) + band + 1), 16);
		Map<Long, int[]> codes = new HashMap<>();
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				// Only the chunks the band crosses (the distance of the chunk's center from the line within reach).
				double mx = (cx << 4) + 8 - x0;
				double mz = (cz << 4) + 8 - z0;
				double along = mx * ux + mz * uz;
				if (Math.abs(-mx * uz + mz * ux) > band + 12 || along < -band - 12 || along > len + band + 12) {
					continue;
				}
				int[][] chunks = new int[9][];
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						chunks[(dx + 1) * 3 + dz + 1] = codes.computeIfAbsent(key(cx + dx, cz + dz), c -> classify(sampler, c));
					}
				}
				Ecotone.Region region = Ecotone.Region.of(cx, cz, chunks, k).withBlend(blend);
				int[] t = Ecotone.effective(region, Ecotone.Layer.TREES, worldSeed, Ecotone.TREES_SALT, 1);
				int[] g = Ecotone.effective(region, Ecotone.Layer.PLANTS, worldSeed, Ecotone.GROUND_SALT, Ecotone.GROUND_PATCH);
				byte[] warp = region.warp(worldSeed);
				for (int i = 0; i < 256; i++) {
					double dx = (cx << 4) + (i >> 4) + 0.5 - x0;
					double dz = (cz << 4) + (i & 15) + 0.5 - z0;
					int step = (int) Math.round((dx * ux + dz * uz) / stepLength);
					if (step < 0 || step > steps || Math.abs(-dx * uz + dz * ux) > band) {
						continue;
					}
					int a = sequence.indexOf(Habitat.biome(t[i]));
					if (a >= 0) {
						trees[step][a]++;
					}
					int b = sequence.indexOf(Habitat.biome(g[i]));
					if (b >= 0) {
						ground[step][b]++;
					}
					int own = region.at(i >> 4, i & 15);
					if (blend != null && ForestBlend.member(own) > 0 && sequence.equals(List.of(ForestBlend.FORESTS))) {
						region.blendField().at((cx << 4) + (i >> 4) + warp[2 * i], (cz << 4) + (i & 15) + warp[2 * i + 1], shares);
						double total = Math.max(ForestBlend.MIN_TOTAL, shares[0] + shares[1] + shares[2] + shares[3]);
						int m = ForestBlend.member(own) - 1;
						for (int f = 0; f < forests; f++) {
							// The column keeps its own forest with the rest of the share (as ForestBlend.pick draws).
							expected[step][f] += f == m ? 1 - (total - shares[m]) / total : shares[f] / total;
						}
						expectedColumns[step]++;
					}
				}
			}
		}
		int[] first = new int[forests];
		int[] last = new int[forests];
		java.util.Arrays.fill(first, -1);
		for (int i = 0; i <= steps; i++) {
			if (rank[i] >= 0) {
				if (first[rank[i]] < 0) {
					first[rank[i]] = i;
				}
				last[rank[i]] = i;
			}
		}
		double[] treeShares = new double[steps + 1];
		double[] groundShares = new double[steps + 1];
		java.util.Arrays.fill(treeShares, Double.NaN);
		java.util.Arrays.fill(groundShares, Double.NaN);
		double[] treeWidths = new double[forests - 1];
		double[] groundWidths = new double[forests - 1];
		double[] fieldWidths = new double[forests - 1];
		java.util.Arrays.fill(fieldWidths, Double.NaN);
		for (int r = 0; r + 1 < forests; r++) {
			if (first[r] < 0 || first[r + 1] < 0) {
				treeWidths[r] = Double.NaN;
				groundWidths[r] = Double.NaN;
				continue;
			}
			int from = (first[r] + last[r]) / 2;
			int to = (first[r + 1] + last[r + 1]) / 2;
			treeWidths[r] = width(trees, r, from, to, treeShares, stepLength, k);
			groundWidths[r] = width(ground, r, from, to, groundShares, stepLength, k);
			if (blend != null) {
				double[] share = new double[steps + 1];
				java.util.Arrays.fill(share, Double.NaN);
				for (int i = from; i <= to; i++) {
					double n = expected[i][r] + expected[i][r + 1];
					if (expectedColumns[i] > 0 && n > 0) {
						share[i] = 100 * expected[i][r + 1] / n;
					}
				}
				fieldWidths[r] = 2 * Math.log(9) * ForestCensus.fitLogistic(share, from, to, 0, 100)[0] * stepLength / k;
			}
		}
		return new Result(treeWidths, groundWidths, treeShares, groundShares, fieldWidths);
	}

	/** Fits the share of forest r + 1 among the columns of forests r and r + 1 of the steps from..to; the width in m·k. */
	private static double width(int[][] counts, int r, int from, int to, double[] shares, double stepLength, double k) {
		double[] share = new double[counts.length];
		java.util.Arrays.fill(share, Double.NaN);
		for (int i = from; i <= to; i++) {
			int n = counts[i][r] + counts[i][r + 1];
			if (n > 0) {
				share[i] = 100.0 * counts[i][r + 1] / n;
				shares[i] = share[i] / 100;
			}
		}
		double[] fit = ForestCensus.fitLogistic(share, from, to, 0, 100);
		return 2 * Math.log(9) * fit[0] * stepLength / k;
	}

	private static long key(int cx, int cz) {
		return (long) cx << 32 | cz & 0xFFFF_FFFFL;
	}

	private static int[] classify(ForestBlend.Sampler sampler, long key) {
		int cx = (int) (key >> 32);
		int cz = (int) key;
		int[] out = new int[256];
		for (int i = 0; i < 256; i++) {
			out[i] = sampler.code((cx << 4) + (i >> 4), (cz << 4) + (i & 15));
		}
		return out;
	}
}
