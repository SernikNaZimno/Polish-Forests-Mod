package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import net.minecraft.world.level.block.Blocks;
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
		// Step F1: + sweet woodruff, hairy wood-rush, wood sorrel and coralroot, flower beds of 16 states each.
		// Step F2: + the marsh marigold, a flower bed of 16 states, and the iris, the tussock sedge, the marsh fern and the
		// nettle, herbs of one state each.
		// Step F3: + the glandular toothwort and the white wood-rush, flower beds of 16 states each, and the perennial
		// honesty, a herb of one state.
		int ground = 4 + 16 + 16 + 32 + 2 + 4 * 16 + 16 + 4 + 2 * 16 + 1;
		// Round 1 of the V12 review: + the forest moss carpet and the mossy podzol, one state each.
		int review = 2;
		// Step F4: + the bog bilberry 4 (berries × mossy), the heather and the crowberry, flower beds of 16 states each, the
		// reindeer lichen 32 (facing 4 × segments 4 × mossy), the Labrador tea and the purple moor-grass, herbs of one state
		// each, and the sphagnum carpet and block, one state each.
		int pineForests = 4 + 2 * 16 + 32 + 2 + 2;
		// Step F5: + the reed canary grass, a herb of one state, the lesser celandine and the wild garlic, flower beds of 16
		// states each, and the hop, a climber of 4 states (facing).
		int floodplain = 1 + 2 * 16 + 4;
		// Step F6: + the alpine coltsfoot, a flower bed of 16 states, and the hairy small-reed, the alpine lady fern and the
		// butterbur, herbs of one state each (the trunks, leaves, saplings, flares, stripped trunks and wood sets of the gray
		// alder and the rowan, the leaves and sapling of the dwarf mountain pine, are counted by their tables above).
		int montane = 16 + 3;
		// Step P1: + 9 stripped trunks (one per species), thin trunks of 84 states each, with the properties of the trunks.
		assertEquals(StrippedTrunk.values().length, ModBlocks.strippedTrunks().size());
		for (StrippedTrunk t : StrippedTrunk.values()) {
			TrunkBlock b = ModBlocks.stripped(t);
			assertEquals(t.id(), BuiltInRegistries.BLOCK.getKey(b).getPath());
			assertEquals(ModBlocks.trunk(t.trunk()).getStateDefinition().getProperties(), b.getStateDefinition().getProperties(), t.id());
			assertEquals(b, ((net.minecraft.world.item.BlockItem) b.asItem()).getBlock(), t.id());
		}
		// Step P2: + the products of the pine planks (stairs 80 = facing 4 × half 2 × shape 5 × waterlogged; slab 6 = type 3 ×
		// waterlogged; fence 32 = 4 sides × waterlogged; fence gate 32 = facing 4 × in wall × open × powered; pressure plate 2;
		// button 24 = face 3 × facing 4 × powered) and 5 whole wood sets (+ log, wood, stripped log and wood of 3 axes, planks).
		int products = 80 + 6 + 32 + 32 + 2 + 24;
		int woods = products + (WoodSet.values().length - 1) * (4 * 3 + 1 + products);
		assertEquals(WoodSet.values().length, ModBlocks.woodSets().size());
		// Step TT1: + the 4 Tatra rocks (granite, gneiss, limestone, dolomite), one state each.
		// Step TT2: + the 3 Tatra surface blocks (granite and limestone scree, rendzina), one state each.
		int tatra = ModBlocks.TATRA_ROCKS.size() + ModBlocks.TATRA_SURFACE.size();
		// Step TT3: + the plants of the Tatras: edelweiss, Clusius's gentian and the least willow, flower beds of 16 states each,
		// the rock lichens, a cover of 32 states (facing 4 x segments 4 x mossy), and the three-leaved rush, the two-rowed
		// Oreochloa, the Tatra moor grass and the variegated fescue, herbs of one state each.
		int tatraPlants = 3 * 16 + 32 + 4;
		// Step C3: + the tall nettle and the tall yellow iris, double plants of 2 states each (half).
		int tallHerbs = 2 * 2;
		// Step FA7: + the door, trapdoor, standing and wall sign, hanging and wall hanging sign and shelf of every wood set,
		// with the states of vanilla's oak ones (door 64, trapdoor 64, sign 32, wall sign 8, hanging sign 64, wall hanging
		// sign 8, shelf 64).
		int partStates = 0;
		for (net.minecraft.world.level.block.Block b : List.of(Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_SIGN,
				Blocks.OAK_WALL_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN, Blocks.OAK_SHELF)) {
			partStates += b.getStateDefinition().getPossibleStates().size();
		}
		int parts = WoodSet.values().length * partStates;
		assertEquals(WoodSet.values().length, ModBlocks.woodPartSets().size());
		assertEquals(4, ModBlocks.tatraRocks().size());
		assertEquals(3, ModBlocks.tatraSurface().size());
		// Step FA8: + the anthill, 8 states (layers 1-8).
		int anthill = pl.polishforests.block.AnthillBlock.MAX_LAYERS;
		assertEquals(anthill, ModBlocks.anthill().getStateDefinition().getPossibleStates().size());
		assertEquals(84 * TreeTrunk.values().length + foliage * (112 + 2 + 1) + marcescent * 112 + 4 * 3 + 1
				+ 84 * (RootFlare.values().length + 1) + ground + review + pineForests + floodplain + montane + 84 * StrippedTrunk.values().length + woods
				+ tatra + tatraPlants + tallHerbs + parts + anthill,
				RegistrySnapshot.stateCount());
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
				case HERB -> pl.polishforests.block.SeasonalHerbBlock.class;
				case CLIMBER -> ClimberBlock.class;
				case TALL_HERB -> pl.polishforests.block.SeasonalTallHerbBlock.class;
			};
			assertTrue(family.isInstance(b), g.id() + " is a " + family.getSimpleName());
			if (g.mossy()) {
				assertTrue(!b.defaultBlockState().getValue(BerryShrubBlock.MOSSY), g.id() + ": mossy by default");
			}
		}
		assertTrue(!ModBlocks.ground(GroundPlant.BILBERRY).defaultBlockState().getValue(BerryShrubBlock.BERRIES));
		assertEquals(ModBlocks.BILBERRIES, BuiltInRegistries.ITEM.getKey(ModBlocks.bilberries()).getPath());
	}

	/**
	 * Step C3 (CP-D.10): the tall nettle and the tall yellow iris are double plants of two states (half, lower by default,
	 * no water property: never waterlogged) with one item that sets both halves, the phases of their one-block forms, and
	 * an outline of a little under two blocks (out of a level: no season, never withdrawn). The one-block nettle and iris
	 * stay as they were (old worlds).
	 */
	@Test
	void tallHerbsOfStepC3() {
		java.util.Map<GroundPlant, GroundPlant> forms = java.util.Map.of(GroundPlant.TALL_NETTLE, GroundPlant.NETTLE,
				GroundPlant.TALL_YELLOW_IRIS, GroundPlant.YELLOW_IRIS);
		for (var e : forms.entrySet()) {
			net.minecraft.world.level.block.Block b = ModBlocks.ground(e.getKey());
			SeasonalTallHerbBlock tall = assertInstanceOf(SeasonalTallHerbBlock.class, b, e.getKey().id());
			assertEquals(e.getKey(), tall.plant());
			assertEquals(e.getValue(), e.getKey().phases());
			assertTrue(e.getKey().doubleHigh());
			assertEquals(2, b.getStateDefinition().getPossibleStates().size(), e.getKey().id());
			assertFalse(b.defaultBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
			assertEquals(net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER,
					b.defaultBlockState().getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF));
			assertInstanceOf(net.minecraft.world.item.DoubleHighBlockItem.class, b.asItem(), e.getKey().id());
			BlockState upper = b.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
			double lowTop = b.defaultBlockState().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).max(Direction.Axis.Y);
			double highTop = upper.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).max(Direction.Axis.Y);
			assertEquals(1.0, lowTop, 1e-9, e.getKey().id());
			assertEquals(14.0 / 16, highTop, 1e-9, e.getKey().id());
			// The loot of the one-block herbs: itself with shears only (the broken half drops it once).
			String loot;
			try {
				loot = Files.readString(Path.of("src/main/generated/data/polishforests/loot_table/blocks/" + e.getKey().id() + ".json"));
			} catch (IOException ex) {
				throw new AssertionError(ex);
			}
			assertTrue(loot.contains("minecraft:tool/can_shear") && loot.contains("\"polishforests:" + e.getKey().id() + "\""), loot);
			assertInstanceOf(SeasonalHerbBlock.class, ModBlocks.ground(e.getValue()), "the one-block form stays");
			assertEquals(1, ModBlocks.ground(e.getValue()).getStateDefinition().getPossibleStates().size());
		}
	}

	/**
	 * Step V13 (ZW-5): the seasonal properties have one place, {@code SeasonalProperties}: the fruit of the bilberry is
	 * neutralized (off) for the determinism hash, every other property stays; the bilberry ticks at random (the fruit in its
	 * season) and the flower beds know their plant (their phases and outline).
	 */
	@Test
	void seasonalPropertiesOfStepV13() {
		net.minecraft.world.level.block.state.BlockState ripe = ModBlocks.ground(GroundPlant.BILBERRY).defaultBlockState()
				.setValue(BerryShrubBlock.BERRIES, true).setValue(BerryShrubBlock.MOSSY, true);
		net.minecraft.world.level.block.state.BlockState neutral = pl.polishforests.season.SeasonalProperties.neutral(ripe);
		assertTrue(!neutral.getValue(BerryShrubBlock.BERRIES) && neutral.getValue(BerryShrubBlock.MOSSY), neutral.toString());
		assertTrue(pl.polishforests.season.SeasonalProperties.seasonal(BerryShrubBlock.BERRIES));
		assertTrue(!pl.polishforests.season.SeasonalProperties.seasonal(BerryShrubBlock.MOSSY));
		net.minecraft.world.level.block.state.BlockState leaf = ModBlocks.leaves(TreeFoliage.OAK).defaultBlockState();
		assertEquals(leaf, pl.polishforests.season.SeasonalProperties.neutral(leaf));
		assertTrue(ripe.isRandomlyTicking(), "the bilberry ticks for its fruit");
		for (GroundPlant g : GroundPlant.values()) {
			if (ModBlocks.ground(g) instanceof SeasonalPlantBlock bed) {
				assertEquals(g, bed.plant());
			}
		}
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
