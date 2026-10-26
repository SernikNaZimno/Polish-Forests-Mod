package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Registry snapshot (rule ZW-4, decision PW-11 of docs/04-pionowy-wycinek.md): the blocks, properties, property values
 * and items of the mod, as registered by {@link ModBlocks}, equal {@code src/test/resources/registry_snapshot.txt}.
 * These names go into the saved worlds, so a difference fails the test until the file is regenerated on purpose
 * ({@code ./gradlew test --tests '*RegistrySnapshotTest*' -PwriteRegistrySnapshot}, then {@code git diff}); after
 * checkpoint CP-B only additions at the end of the lists are allowed.
 */
class RegistrySnapshotTest {
	static final String RESOURCE = "/registry_snapshot.txt";

	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void registryMatchesSnapshot() throws IOException {
		List<String> now = RegistrySnapshot.lines();
		String write = System.getProperty("polishforests.registry.write");
		if (write != null) {
			Path out = Path.of(write);
			Files.write(out, (String.join("\n", now) + "\n").getBytes(StandardCharsets.UTF_8));
			System.out.println("RegistrySnapshotTest: wrote " + (now.size() - 1) + " lines to " + out.toAbsolutePath());
			return;
		}
		List<String> expected = new ArrayList<>();
		try (InputStream in = RegistrySnapshotTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(in, "Missing " + RESOURCE + " (write it with -PwriteRegistrySnapshot)");
			new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).forEach(expected::add);
		}
		if (!expected.equals(now)) {
			List<String> diff = new ArrayList<>();
			expected.stream().filter(l -> !now.contains(l)).forEach(l -> diff.add("- " + l));
			now.stream().filter(l -> !expected.contains(l)).forEach(l -> diff.add("+ " + l));
			fail("The blocks or items of the mod differ from " + RESOURCE + " (saved worlds depend on them; regenerate on "
					+ "purpose with -PwriteRegistrySnapshot):\n" + String.join("\n", diff.isEmpty() ? List.of("(order differs)") : diff));
		}
	}

	/** Step V2: 10 trunk blocks of 84 states each (7 radii × 6 parents × waterlogged), each with its block item. */
	@Test
	void trunksHaveTheStatesOfVariantD() {
		assertEquals(TreeTrunk.values().length, ModBlocks.trunks().size());
		for (TreeTrunk t : TreeTrunk.values()) {
			TrunkBlock b = ModBlocks.trunk(t);
			assertEquals(t.id(), BuiltInRegistries.BLOCK.getKey(b).getPath());
			assertEquals(84, b.getStateDefinition().getPossibleStates().size(), t.id());
			assertEquals(b, ((net.minecraft.world.item.BlockItem) b.asItem()).getBlock(), t.id());
			BlockState d = b.defaultBlockState();
			assertEquals(TrunkBlock.DEFAULT_RADIUS, d.getValue(TrunkBlock.RADIUS));
			assertEquals(Direction.DOWN, d.getValue(TrunkBlock.PARENT));
			assertTrue(!d.getValue(TrunkBlock.WATERLOGGED));
		}
		// Step V7: + 9 leaf blocks of 112 states (distance 7 × persistent × waterlogged × phase 4), 9 saplings of 2 stages,
		// 9 flower pots, the 4 pillars of the pine set (3 axes) and the planks.
		// The leaves of oak, hornbeam and beech have the property marcescent too (V7 review round 1).
		int foliage = TreeFoliage.values().length;
		int marcescent = (int) java.util.Arrays.stream(TreeFoliage.values()).filter(TreeFoliage::marcescent).count();
		assertEquals(3, marcescent);
		// Step I1: + 9 root flares and the underground root, thin trunks of 84 states each.
		assertEquals(RootFlare.values().length, ModBlocks.flares().size());
		// Step V12: + the herb layer: bilberry 4 (berries × mossy), wood anemone and liverleaf 16 each (facing 4 × flowers
		// 4), needle litter 32 (facing 4 × segments 4 × mossy), bracken 2 (half).
		assertEquals(GroundPlant.values().length, ModBlocks.groundPlants().size());
		int ground = 4 + 16 + 16 + 32 + 2;
		// Round 1 of the V12 review: + the forest moss carpet and the mossy podzol, one state each.
		int review = 2;
		assertEquals(84 * TreeTrunk.values().length + foliage * (112 + 2 + 1) + marcescent * 112 + 4 * 3 + 1
				+ 84 * (RootFlare.values().length + 1) + ground + review, RegistrySnapshot.stateCount());
	}

	/**
	 * Step V12: the herb layer and litter blocks keep their families (BerryShrubBlock, SeasonalPlantBlock,
	 * GroundCoverBlock, a double plant), each with its block item; the default states are the generated ones of ZW-5 (no
	 * fruit, no moss), and the bilberry fruit is a food.
	 */
	@Test
	void groundPlantsOfStepV12() {
		for (GroundPlant g : GroundPlant.values()) {
			net.minecraft.world.level.block.Block b = ModBlocks.ground(g);
			assertEquals(g.id(), BuiltInRegistries.BLOCK.getKey(b).getPath());
			assertEquals(b, ((net.minecraft.world.item.BlockItem) b.asItem()).getBlock(), g.id());
			Class<?> family = switch (g.kind()) {
				case BERRY_SHRUB -> BerryShrubBlock.class;
				case SEASONAL -> SeasonalPlantBlock.class;
				case COVER -> GroundCoverBlock.class;
				case TALL -> net.minecraft.world.level.block.DoublePlantBlock.class;
			};
			assertTrue(family.isInstance(b), g.id() + " is a " + family.getSimpleName());
			if (g.mossy()) {
				assertTrue(!b.defaultBlockState().getValue(BerryShrubBlock.MOSSY), g.id() + ": mossy by default");
			}
		}
		assertTrue(!ModBlocks.ground(GroundPlant.BILBERRY).defaultBlockState().getValue(BerryShrubBlock.BERRIES));
		assertEquals(ModBlocks.BILBERRIES, BuiltInRegistries.ITEM.getKey(ModBlocks.bilberries()).getPath());
	}

	/** Step V7: every species leaf block is a leaves block with the phase 0-3, every sapling has an item and a pot. */
	@Test
	void leavesAndSaplingsOfTheSpecies() {
		for (TreeFoliage f : TreeFoliage.values()) {
			SpeciesLeavesBlock leaves = ModBlocks.leaves(f);
			assertEquals(f.leavesId(), BuiltInRegistries.BLOCK.getKey(leaves).getPath());
			assertEquals(java.util.List.of(0, 1, 2, 3), java.util.List.copyOf(SpeciesLeavesBlock.PHASE.getPossibleValues()));
			assertEquals(0, leaves.defaultBlockState().getValue(SpeciesLeavesBlock.PHASE));
			assertEquals(f.marcescent(), leaves instanceof MarcescentLeavesBlock, f.leavesId());
			assertEquals(f.marcescent(), pl.polishforests.season.FoliageCurve.marcescent(f.species()), f.leavesId());
			assertFalse(MarcescentLeavesBlock.marcescent(leaves.defaultBlockState()), f.leavesId());
			assertEquals(leaves, ((net.minecraft.world.item.BlockItem) leaves.asItem()).getBlock());
			SpeciesSaplingBlock sapling = ModBlocks.sapling(f);
			assertEquals(f.saplingId(), BuiltInRegistries.BLOCK.getKey(sapling).getPath());
			assertEquals(sapling, ((net.minecraft.world.item.BlockItem) sapling.asItem()).getBlock());
			assertEquals(sapling, ModBlocks.potted(f).getPotted());
			assertEquals("polishforests:" + f.featurePath(), sapling.feature().identifier().toString());
		}
	}
}
