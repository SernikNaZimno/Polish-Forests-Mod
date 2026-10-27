package pl.polishforests.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.season.LeafFall;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.VanillaLeafPhase;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The falling leaf particles by the season on the client (step V13 of docs/04-pionowy-wycinek.md, the user's request of
 * 2026-10-26; {@code FallingParticlesLeavesBlockMixin} asks it for the chance of every animation tick of a leaf block
 * with falling particles): the chance of {@link LeafFall} for the leaves of the slice species everywhere, and for the
 * vanilla leaves in the "Polska" world only (decision V13-g: the biome at the leaf is one of the mod's biomes, which only
 * the Polska chunk generator places; the other worlds keep the vanilla chance). A vanilla leaf block falls as its species
 * ({@code oak_leaves} and {@code dark_oak_leaves} as the oak, {@code birch_leaves} as the birch, {@code spruce_leaves} as
 * the spruce) or, for the leaves the slice has no curve for, as the hornbeam (a broadleaf of the middle dates), scaled
 * by its vanilla chance against vanilla oak (the cherry petals stay ten times denser), with a phase from its position
 * ({@link VanillaLeafPhase}: a low-frequency noise, one phase per crown). The particle keeps the tint of the block: our
 * leaves fall in the color of their curve, vanilla leaves in their own (with Serene Seasons its autumn colors).
 */
public final class ClientLeafFall {
	private static final Map<Block, Species> VANILLA = new IdentityHashMap<>();

	static {
		VANILLA.put(Blocks.OAK_LEAVES, Species.OAK);
		VANILLA.put(Blocks.DARK_OAK_LEAVES, Species.OAK);
		VANILLA.put(Blocks.BIRCH_LEAVES, Species.BIRCH);
		VANILLA.put(Blocks.SPRUCE_LEAVES, Species.SPRUCE);
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
		float c = LeafFall.chance(vanillaSpecies(block), step, vanillaPhase(pos), false) * (original / LeafFall.VANILLA);
		askedVanilla++;
		expectedVanilla += c;
		return c;
	}

	/** The species whose curve a vanilla leaf block follows in the Polska world (the hornbeam where the slice has none). */
	static Species vanillaSpecies(Block block) {
		return VANILLA.getOrDefault(block, Species.HORNBEAM);
	}

	/**
	 * The phase (0-3) of a vanilla leaf block in the Polska world: a low-frequency noise of its column, so a crown keeps one
	 * phase and the rare edge between two phases is an irregular curve ({@link VanillaLeafPhase}, review round 1 of V13).
	 */
	static int vanillaPhase(BlockPos pos) {
		return VanillaLeafPhase.phase(pos.getX(), pos.getZ());
	}

	/** Whether a position of a level lies in the "Polska" world (a biome of the mod). */
	static boolean polish(Level level, BlockPos pos) {
		return SeasonPhase.polishWorld(level, pos);
	}

	/** {asked of our leaves, expected particles of our leaves, asked of vanilla leaves in Polska, expected of those}. */
	public static double[] counters() {
		return new double[] {askedOurs, expectedOurs, askedVanilla, expectedVanilla};
	}
}
