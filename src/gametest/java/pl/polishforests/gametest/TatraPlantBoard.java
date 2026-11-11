package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;

/**
 * Step TT3 (KT-3 of the new blocks, part of {@code -Pgametest=tatra}): the board of the eight new plants of the Tatras.
 * Every block state of each has a block model that is not the missing model and a particle sprite that is not the
 * missing texture; each plant stands in a row of four on its ground (grass for the herbs and the flower beds, stone for
 * the rock lichens) on a platform high over the spawn, and one screenshot {@code tatra_plants_board} is taken of it (no
 * judgment of the look, G.36: only that the board exists and the counts).
 */
final class TatraPlantBoard {
	static final GroundPlant[] PLANTS = {GroundPlant.THREE_LEAVED_RUSH, GroundPlant.TWO_ROWED_OREOCHLOA,
			GroundPlant.TATRA_MOOR_GRASS, GroundPlant.VARIEGATED_FESCUE, GroundPlant.EDELWEISS, GroundPlant.CLUSIUS_GENTIAN,
			GroundPlant.LEAST_WILLOW, GroundPlant.ROCK_LICHEN};
	/** Height of the platform. */
	static final int Y = 300;

	private TatraPlantBoard() {
	}

	/** The model check (client) and the board with its screenshot; the problems go to {@code failures}. */
	static void run(ClientGameTestContext context, TestSingleplayerContext sp, List<String> report, List<String> failures) {
		List<String> problems = new ArrayList<>();
		int[] states = new int[1];
		context.runOnClient(mc -> {
			BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
			Identifier missing = MissingTextureAtlasSprite.getLocation();
			for (GroundPlant g : PLANTS) {
				Block b = ModBlocks.ground(g);
				Identifier key = BuiltInRegistries.BLOCK.getKey(b);
				for (BlockState s : b.getStateDefinition().getPossibleStates()) {
					states[0]++;
					if (models.get(s) == models.missingModel()) {
						problems.add(key + ": missing block model for " + s);
					} else if (models.getParticleMaterial(s).sprite().contents().name().equals(missing)) {
						problems.add(key + ": the particle sprite of " + s + " is the missing texture");
					}
				}
			}
		});
		int placed = sp.getServer().computeOnServer(s -> build(s.overworld()));
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d %d %d 0 50", 4, Y + 5, -4));
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("tatra_plants_board").disableCounterPrefix().withSize(1280, 720));
		report.add(String.format(java.util.Locale.ROOT, "TT3 plant board: %d plants, %d block states checked, %d placed, %d problems%s",
				PLANTS.length, states[0], placed, problems.size(), problems.isEmpty() ? "" : ": " + problems.subList(0, Math.min(4, problems.size()))));
		if (!problems.isEmpty() || placed < 4 * PLANTS.length) {
			failures.add("TT3 plant board: " + problems.size() + " model problems, " + placed + " of " + 4 * PLANTS.length + " placed");
		}
	}

	/** The platform with a row of four of each plant (x = 0 … 7, z = 0 … 3); returns the plants standing after placing. */
	private static int build(ServerLevel level) {
		level.getChunk(0, 0);
		int placed = 0;
		for (int x = -1; x <= PLANTS.length; x++) {
			for (int z = -1; z <= 4; z++) {
				boolean rock = x == PLANTS.length - 1;
				level.setBlock(new BlockPos(x, Y, z), (rock ? Blocks.STONE : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
			}
		}
		for (int i = 0; i < PLANTS.length; i++) {
			BlockState s = ModBlocks.ground(PLANTS[i]).defaultBlockState();
			if (s.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
				s = s.setValue(BlockStateProperties.FLOWER_AMOUNT, 4);
			}
			if (s.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) {
				s = s.setValue(BlockStateProperties.SEGMENT_AMOUNT, 4);
			}
			for (int z = 0; z < 4; z++) {
				BlockPos p = new BlockPos(i, Y + 1, z);
				if (s.canSurvive(level, p)) {
					level.setBlock(p, s, 2);
					placed += level.getBlockState(p).is(s.getBlock()) ? 1 : 0;
				}
			}
		}
		return placed;
	}
}
