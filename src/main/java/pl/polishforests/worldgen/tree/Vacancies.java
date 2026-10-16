package pl.polishforests.worldgen.tree;

/**
 * Kept trunk candidates that grow no tree at all, the markers of the crown territories ({@link CrownTerritories}): their
 * cells go to the neighboring trees, so a candidate left out by the palette does not leave a hole in the canopy, and the
 * crowns of the forest edge lean out over the open ground.
 *
 * <p><b>Invariant (round 2 of the V5 review).</b> Every chunk that asks must get the same answer for a candidate, so a
 * candidate may be called vacant only when it surely gets no tree with a territory. A false "vacant" lets the
 * neighboring trunk take the cell of a tree that does grow, and the two crowns overlap (a dispute that depends on the
 * decoration order); a false "not vacant" only leaves a hole, which is safe. The vacancy of a candidate depends on the
 * ecotone tree code of its column (rule Z10: {@code ChunkEcotones}, a column takes the code of a column up to
 * {@code Ecotone.REACH} = 16 blocks away), which needs the habitats of the 3 × 3 chunks around the candidate's own chunk.
 * At the FEATURES stage of a chunk C only the chunks within radius 1 have {@code ChunkHabitats}, so the exact vacancy is
 * known for the candidates of C only; for a candidate in a neighbor of C it would need chunks at radius 2.
 *
 * <p>Hence two questions (rule (i) of the review, decision V5-e):
 * <ul>
 * <li>{@link #vacant}: the exact answer, asked only about the candidates of the trunk's own chunk, whose decoration has
 * the ecotone codes: no palette rule, or the rule gives no tree (rule chance, thinning of sparse stands, gap noise,
 * water depth; {@code TreeStandPlan}), on open ground as well as in the forest;</li>
 * <li>{@link #surelyVacant}: a part of the vacancies that needs no habitat code, asked about the candidates of the chunks
 * within radius 1: the gap noise of the stand and a chance draw at or above the highest chance of any palette rule
 * ({@code TreeStandPlan.Candidates#treeless} with {@code Palette#highestChance}), optionally water deeper than any palette
 * entry allows (the raw water depth of the candidate's chunk, which the decoration has within radius 1). It must imply
 * {@link #vacant}.</li>
 * </ul>
 * A candidate that gets any tree, a procedural one or a vanilla substitute ({@code tree_substitute/} rules, PW-3), is not
 * vacant: it keeps its cell, so the procedural crowns next to it are not granted the reach of an edge toward it. A trunk
 * that the decoration skips for another reason (a structure nearby) may be called not vacant: that leaves a hole.
 */
@FunctionalInterface
public interface Vacancies {
	/** Whether the kept candidate at column (x, z) grows no tree (exact; asked about the trunk's own chunk only). */
	boolean vacant(int x, int z);

	/**
	 * Whether the kept candidate at column (x, z) surely grows no tree whatever its habitat code (asked about the chunks
	 * within radius 1 of the trunk's chunk); must imply {@link #vacant}. None by default.
	 */
	default boolean surelyVacant(int x, int z) {
		return false;
	}
}
