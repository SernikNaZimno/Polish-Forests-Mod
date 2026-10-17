package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The game side of the tree planner (step V6): the configuration codec of {@code polishforests:procedural_tree} and the
 * block states the feature writes for the planner's wood and leaves.
 */
class ProceduralTreeStatesTest {
	@BeforeAll
	static void bootstrap() {
		GameRegistries.bootstrap();
	}

	@Test
	void codecReadsTheConfigurationWithDefaults() {
		JsonObject full = JsonParser.parseString("{\"species\": \"oak\", \"layer\": \"subcanopy\", \"age_class\": \"old_growth\"}")
				.getAsJsonObject();
		ProceduralTreeFeature f = ProceduralTreeFeature.CODEC.codec().parse(JsonOps.INSTANCE, full).getOrThrow();
		assertEquals(new ProceduralTreeFeature(Species.OAK, CrownLayer.SUBCANOPY, AgeClass.OLD_GROWTH), f);
		assertEquals(full, ProceduralTreeFeature.CODEC.codec().encodeStart(JsonOps.INSTANCE, f).getOrThrow());
		ProceduralTreeFeature d = ProceduralTreeFeature.CODEC.codec().parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"species\": \"scots_pine\"}")).getOrThrow();
		assertEquals(new ProceduralTreeFeature(Species.SCOTS_PINE, CrownLayer.CANOPY, AgeClass.MATURE), d);
		// A species without a planner shape and an unknown layer are errors.
		assertTrue(ProceduralTreeFeature.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"species\": \"elm\"}"))
				.isError());
		assertTrue(ProceduralTreeFeature.CODEC.codec().parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"species\": \"oak\", \"layer\": \"emergent\"}")).isError());
	}

	@Test
	void woodAndLeavesStates() {
		for (Species s : SpeciesShape.species()) {
			for (int r = 1; r <= 7; r++) {
				for (Direction parent : Direction.values()) {
					BlockState state = ProceduralTreeFeature.wood(s, TreeVoxels.LOWER, r, parent);
					assertTrue(state.getBlock() instanceof TrunkBlock, s + " r" + r);
					assertEquals(r, state.getValue(TrunkBlock.RADIUS));
					assertEquals(parent, state.getValue(TrunkBlock.PARENT));
					assertEquals(s, ProceduralTreeFeature.trunk(s, TreeVoxels.LOWER).species());
				}
			}
			BlockState log = ProceduralTreeFeature.wood(s, TreeVoxels.LOWER, TreeVoxels.FULL, Direction.WEST);
			assertSame(ProceduralTreeFeature.trunk(s, TreeVoxels.LOWER).log(), log.getBlock());
			assertEquals(Direction.Axis.X, log.getValue(RotatedPillarBlock.AXIS));
			for (int phase = 0; phase < 4; phase++) {
				BlockState leaf = ProceduralTreeFeature.leaves(s, phase);
				assertTrue(leaf.getBlock() instanceof LeavesBlock);
				assertSame(ModBlocks.leaves(TreeFoliage.of(s)), leaf.getBlock(), s.id());
				assertTrue(!leaf.getValue(LeavesBlock.PERSISTENT));
				assertEquals(phase, leaf.getValue(SpeciesLeavesBlock.PHASE));
			}
		}
		// The pine's barks: lower bark, the two blocks of the transition zone, the "mirror".
		assertEquals(TreeTrunk.SCOTS_PINE, ProceduralTreeFeature.trunk(Species.SCOTS_PINE, TreeVoxels.LOWER));
		assertEquals(TreeTrunk.SCOTS_PINE_TRANSITION, ProceduralTreeFeature.trunk(Species.SCOTS_PINE, TreeVoxels.TRANSITION));
		assertEquals(TreeTrunk.SCOTS_PINE_TRANSITION_UPPER, ProceduralTreeFeature.trunk(Species.SCOTS_PINE,
				TreeVoxels.TRANSITION_UPPER));
		assertEquals(TreeTrunk.SCOTS_PINE_UPPER, ProceduralTreeFeature.trunk(Species.SCOTS_PINE, TreeVoxels.UPPER));
		// Step V7: the pine trunks drop the pine log of the LITE set; spruce, birch and oak the vanilla logs (D2).
		assertSame(ModBlocks.pineLog(), TreeTrunk.SCOTS_PINE.log());
		assertSame(ModBlocks.pineLog(), TreeTrunk.SCOTS_PINE_UPPER.log());
		assertSame(Blocks.SPRUCE_LOG, TreeTrunk.SPRUCE.log());
		assertSame(Blocks.BIRCH_LOG, TreeTrunk.BIRCH.log());
	}

	/**
	 * V7 review round 1: only oak, hornbeam and beech keep dead leaves, on the whole crown of a young tree, the lower half of
	 * a subcanopy tree and the lowest quarter of a canopy tree; a player's leaves are not marcescent.
	 */
	@Test
	void marcescentLeavesOnYoungTreesAndLowerCrowns() {
		for (Species s : pl.polishforests.worldgen.tree.SpeciesShape.species()) {
			boolean dry = s == Species.OAK || s == Species.HORNBEAM || s == Species.BEECH;
			assertEquals(dry ? 1.0 : 0.0, ProceduralTreeFeature.marcescentShare(s, CrownLayer.CANOPY, AgeClass.POLE_STAGE), s.id());
			assertEquals(dry ? 0.5 : 0.0, ProceduralTreeFeature.marcescentShare(s, CrownLayer.SUBCANOPY, AgeClass.MATURE), s.id());
			assertEquals(dry ? 0.25 : 0.0, ProceduralTreeFeature.marcescentShare(s, CrownLayer.CANOPY, AgeClass.OLD_GROWTH), s.id());
			BlockState leaf = ProceduralTreeFeature.leaves(s, 2);
			assertEquals(dry, leaf.hasProperty(MarcescentLeavesBlock.MARCESCENT), s.id());
			assertTrue(!MarcescentLeavesBlock.marcescent(leaf), s.id());
		}
	}

	/** Step V7: the phase of a tree is a pure function of the seed and the trunk base, and every phase occurs. */
	@Test
	void treePhaseIsDeterministicAndSpread() {
		int[] counts = new int[4];
		for (int x = 0; x < 64; x++) {
			for (int z = 0; z < 64; z++) {
				int p = ProceduralTreeFeature.phase(12345L, x, z);
				assertEquals(p, ProceduralTreeFeature.phase(12345L, x, z));
				counts[p]++;
			}
		}
		for (int c : counts) {
			assertTrue(c > 900 && c < 1150, java.util.Arrays.toString(counts));
		}
	}
}
