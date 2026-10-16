package pl.polishforests.worldgen.tree;

import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * A stand-in for the vacancies of the game (step V9, round 2 of the V5 review) on the real chance draws and gap test of
 * the tree stand plan ({@link TreeStandPlan.Candidates#treeless}): a forest in which a share of the candidates grows a
 * tree (a rule of share × {@link TreeStandPlan#DENSITY} trees per chunk, with gaps from a test noise), open ground (a
 * test field) without trees, and a palette whose densest rule has {@value #DENSEST} trees per chunk, as the montane
 * spruce forest and the densest rules of {@code tree_stand.json}.
 * <ul>
 * <li>{@link #vacant} (exact, as the decoration of the candidate's own chunk knows it from the ecotone code): open
 * ground, a gap, or a draw at or above the chance of the forest rule;</li>
 * <li>{@link #surelyVacant} (no habitat code): a gap or a draw at or above the highest chance of the palette.</li>
 * </ul>
 */
final class PaletteVacancies implements Vacancies {
	/** Trees per chunk of the densest rule of the palette. */
	static final float DENSEST = 12;

	private final TreeStandPlan.Candidates candidates;
	private final TreeStandPlan.Stand stand;
	private final double chance;
	private final double highest;
	private final @Nullable Vacancies open;

	/**
	 * @param share share of the forest candidates that grow a tree (the chance is raised for the gaps, which take
	 *              {@value TreeStandPlan#GAP_SHARE} of the area; from 1 − {@value TreeStandPlan#GAP_SHARE} on every
	 *              candidate outside a gap is a tree)
	 * @param open  open ground, or null
	 */
	PaletteVacancies(long seed, double share, @Nullable Vacancies open) {
		this.candidates = new TreeStandPlan.Candidates(seed);
		this.stand = new TreeStandPlan.Stand(new float[] {1, 1, 1, 1}, new Noise(seed).derive("test.crown_territory.gaps"), 1);
		this.chance = TreeStandPlan.chance(share * TreeStandPlan.DENSITY, true);
		this.highest = Math.max(chance, TreeStandPlan.chance(DENSEST, true));
		this.open = open;
	}

	@Override
	public boolean vacant(int x, int z) {
		return (open != null && open.vacant(x, z)) || candidates.treeless(x, z, chance, stand);
	}

	@Override
	public boolean surelyVacant(int x, int z) {
		return candidates.treeless(x, z, highest, stand);
	}
}
