package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The stripped trunks and the axe's thinning (step P1 of docs/04-pionowy-wycinek.md): every trunk block strips into the
 * stripped trunk of its species (the four pine barks into one), which has the same properties, so the copy of the axe
 * keeps radius, parent and water; the axe thins every trunk and stripped trunk of radius 2-7 by 1 px and nothing at
 * radius 1, never a root flare or the underground root; the stripped trunks are thin trunks for the stem-base loot (S1).
 */
class StrippedTrunkTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void everyTrunkStripsIntoItsSpecies() {
		Set<StrippedTrunk> used = new HashSet<>();
		for (TreeTrunk t : TreeTrunk.values()) {
			StrippedTrunk s = StrippedTrunk.of(t);
			assertEquals(t.species(), s.species(), t.id());
			used.add(s);
			TrunkBlock bark = ModBlocks.trunk(t);
			TrunkBlock bare = ModBlocks.stripped(s);
			for (BlockState st : bark.getStateDefinition().getPossibleStates()) {
				BlockState copy = bare.defaultBlockState();
				for (var p : st.getProperties()) {
					copy = copyValue(copy, st, p);
				}
				assertEquals(st.getValue(TrunkBlock.RADIUS), copy.getValue(TrunkBlock.RADIUS));
				assertEquals(st.getValue(TrunkBlock.PARENT), copy.getValue(TrunkBlock.PARENT));
				assertEquals(st.getValue(TrunkBlock.WATERLOGGED), copy.getValue(TrunkBlock.WATERLOGGED));
			}
		}
		assertEquals(StrippedTrunk.values().length, used.size(), "a stripped trunk no trunk strips into");
		assertEquals("stripped_scots_pine_trunk", StrippedTrunk.of(TreeTrunk.SCOTS_PINE_TRANSITION_UPPER).id());
		assertEquals("Okorowany pień dębu szypułkowego", StrippedTrunk.OAK.polishName());
		assertEquals("Stripped Norway Maple Trunk", StrippedTrunk.NORWAY_MAPLE.englishName());
	}

	@Test
	void axeThinsTrunksDownToRadiusOne() {
		for (TrunkBlock b : java.util.List.of(ModBlocks.trunk(TreeTrunk.BEECH), ModBlocks.stripped(StrippedTrunk.BEECH))) {
			for (Direction p : Direction.values()) {
				for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
					BlockState st = b.state(r, p).setValue(TrunkBlock.WATERLOGGED, r % 2 == 0);
					assertTrue(TrunkAxe.thinnable(st));
					BlockState thin = TrunkAxe.thinned(st);
					if (r == TrunkBlock.MIN_RADIUS) {
						assertNull(thin);
						continue;
					}
					assertEquals(b, thin.getBlock());
					assertEquals(r - 1, thin.getValue(TrunkBlock.RADIUS));
					assertEquals(p, thin.getValue(TrunkBlock.PARENT));
					assertEquals(r % 2 == 0, thin.getValue(TrunkBlock.WATERLOGGED));
				}
			}
		}
		assertFalse(TrunkAxe.thinnable(ModBlocks.flare(RootFlare.OAK).state(3, Direction.WEST)));
		assertFalse(TrunkAxe.thinnable(ModBlocks.treeRoot().state(3, Direction.UP)));
		assertFalse(TrunkAxe.thinnable(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()));
	}

	@Test
	void strippedStemIsAStemBase() {
		BlockState stem = ModBlocks.stripped(StrippedTrunk.LINDEN).state(1, Direction.DOWN);
		assertTrue(TrunkBaseCondition.stem(stem));
	}

	private static <T extends Comparable<T>> BlockState copyValue(BlockState to, BlockState from,
			net.minecraft.world.level.block.state.properties.Property<T> p) {
		return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
	}
}
