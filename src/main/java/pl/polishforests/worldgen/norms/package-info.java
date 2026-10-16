/**
 * The norms card of the vertical slice (docs/04-pionowy-wycinek.md §6, step V4): the target values of a realistic Polish
 * forest with their sources ({@link pl.polishforests.worldgen.norms.ForestNorms}) and the census that measures a sample
 * plot of the world against them ({@link pl.polishforests.worldgen.norms.ForestCensus} on a block snapshot,
 * {@link pl.polishforests.worldgen.norms.CensusGrid}). Pure Java without game classes, so the counting is checked on
 * hand-built grids in unit tests; the game test {@code slice} fills the snapshot from the world.
 */
package pl.polishforests.worldgen.norms;
