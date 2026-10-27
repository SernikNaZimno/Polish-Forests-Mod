package pl.polishforests.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.PolishForests;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.season.LeafFall;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The falling leaf particles by the season on the client (step V13 of docs/04-pionowy-wycinek.md, the user's request of
 * 2026-10-26; {@code FallingParticlesLeavesBlockMixin} asks it for the chance of every animation tick of a leaf block
 * with falling particles): the chance of {@link LeafFall} for the leaves of the slice species everywhere, and for the
 * vanilla leaves in the "Polska" world only (decision V13-g: the biome at the leaf is one of the mod's biomes, which only
 * the Polska chunk generator places; the other worlds keep the vanilla chance). A vanilla leaf block falls as its species
 * ({@code oak_leaves} and {@code dark_oak_leaves} as the oak, {@code birch_leaves} as the birch) or, for the leaves the
 * slice has no curve for, as the hornbeam (a broadleaf of the middle dates), scaled by its vanilla chance against
 * vanilla oak (the cherry petals stay ten times denser), with a phase from its position (cells of 4 x 4 columns). The
 * particle keeps the tint of the block: our leaves fall in the color of their curve, vanilla leaves in their own (with
 * Serene Seasons its autumn colors).
 */
public final class ClientLeafFall {
	private static final Map<Block, Species> VANILLA = new IdentityHashMap<>();

	static {
		VANILLA.put(Blocks.OAK_LEAVES, Species.OAK);
		VANILLA.put(Blocks.DARK_OAK_LEAVES, Species.OAK);
		VANILLA.put(Blocks.BIRCH_LEAVES, Species.BIRCH);
	}

	/** Sums of the chances asked for since the start (game test: the expected particles of our and of vanilla leaves). */
	private static double expectedOurs;
	private static double expectedVanilla;
	private static long askedOurs;
	private static long askedVanilla;

	private ClientLeafFall() {
	}

	/** The chance of a falling leaf of a leaf block at a position (the vanilla {@code original} where nothing applies). */
	public static float chance(Block block, float original, Level level, BlockPos pos) {
		int step = ClientSeasonPhase.step();
		if (step < 0) {
			return original;
		}
		if (block instanceof SpeciesLeavesBlock leaves) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(block)) {
				return original;
			}
			float c = LeafFall.chance(leaves.foliage().species(), step, state.getValue(SpeciesLeavesBlock.PHASE),
					MarcescentLeavesBlock.marcescent(state));
			askedOurs++;
			expectedOurs += c;
			return c;
		}
		if (!polish(level, pos)) {
			return original;
		}
		Species species = VANILLA.getOrDefault(block, Species.HORNBEAM);
		int phase = (int) (mix((pos.getX() >> 2) * 0x9E3779B97F4A7C15L ^ (pos.getZ() >> 2) * 0xC2B2AE3D27D4EB4FL) >>> 62);
		float c = LeafFall.chance(species, step, phase, false) * (original / LeafFall.VANILLA);
		askedVanilla++;
		expectedVanilla += c;
		return c;
	}

	/** Whether a position of a level lies in the "Polska" world (a biome of the mod). */
	static boolean polish(Level level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey().map(k -> k.identifier().getNamespace().equals(PolishForests.MOD_ID)).orElse(false);
	}

	/** {asked of our leaves, expected particles of our leaves, asked of vanilla leaves in Polska, expected of those}. */
	public static double[] counters() {
		return new double[] {askedOurs, expectedOurs, askedVanilla, expectedVanilla};
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
