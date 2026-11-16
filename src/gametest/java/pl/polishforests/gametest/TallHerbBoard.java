package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SeasonalTallHerbBlock;

/**
 * Step C3 (CP-D.10, part of {@code -Pgametest=vegetation}, realistic scale): the block test and the board of the tall
 * nettle and the tall yellow iris. Every block state has a block model that is not the missing model and a particle sprite
 * that is not the missing texture. On a platform high over the spawn, for each plant: the item placed on grass sets both
 * halves; breaking the lower half removes the upper one and breaking the upper half the lower one; the plant cannot stand
 * on stone and its upper half cannot stand alone; the plant is not withdrawn in the summer date of the test (its outline
 * is there). Four plants of each stay in a row for one screenshot {@code tall_herbs_board} (no judgment of the look, G.36
 * U-1: only that the board exists and the counts).
 */
final class TallHerbBoard {
	static final GroundPlant[] PLANTS = {GroundPlant.TALL_NETTLE, GroundPlant.TALL_YELLOW_IRIS};
	/** Height of the platform. */
	static final int Y = 300;
	/** West edge of the platform (away from the Tatra plant board at x 0 … 7). */
	static final int X0 = 20;

	private TallHerbBoard() {
	}

	/** The model check (client), the block test and the board with its screenshot; the problems go to {@code failures}. */
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
		List<String> serverProblems = sp.getServer().computeOnServer(s -> build(s.overworld()));
		problems.addAll(serverProblems);
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 40", X0 + 3, Y + 4, -5));
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("tall_herbs_board").disableCounterPrefix().withSize(1280, 720));
		report.add(String.format(Locale.ROOT, "C3 tall herb board: %d plants, %d block states checked, %d problems%s", PLANTS.length,
				states[0], problems.size(), problems.isEmpty() ? "" : ": " + problems.subList(0, Math.min(6, problems.size()))));
		if (!problems.isEmpty()) {
			failures.add("C3 tall herb board: " + problems);
		}
	}

	/** The platform, the block test of each plant and its row of four for the screenshot; returns the problems. */
	private static List<String> build(ServerLevel level) {
		List<String> problems = new ArrayList<>();
		level.getChunk(X0 >> 4, 0);
		for (int x = X0 - 1; x <= X0 + 8; x++) {
			for (int z = -1; z <= 2 * PLANTS.length + 1; z++) {
				level.setBlock(new BlockPos(x, Y, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				for (int dy = 1; dy <= 3; dy++) {
					level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int i = 0; i < PLANTS.length; i++) {
			GroundPlant g = PLANTS[i];
			SeasonalTallHerbBlock block = (SeasonalTallHerbBlock) ModBlocks.ground(g);
			int z = 2 * i;
			// Placing with the item: both halves.
			for (int k = 0; k < 6; k++) {
				BlockPos pos = new BlockPos(X0 + k, Y + 1, z);
				if (!placeWithItem(level, block, pos)) {
					problems.add(g.id() + ": the item did not place the plant at " + pos.toShortString());
					continue;
				}
				if (!paired(level, block, pos)) {
					problems.add(g.id() + ": the item left " + level.getBlockState(pos) + " / " + level.getBlockState(pos.above()));
				}
			}
			// Breaking either half removes both.
			BlockPos lowerBroken = new BlockPos(X0 + 4, Y + 1, z);
			level.destroyBlock(lowerBroken, false);
			if (!level.getBlockState(lowerBroken).isAir() || !level.getBlockState(lowerBroken.above()).isAir()) {
				problems.add(g.id() + ": breaking the lower half left " + level.getBlockState(lowerBroken.above()));
			}
			BlockPos upperBroken = new BlockPos(X0 + 5, Y + 2, z);
			level.destroyBlock(upperBroken, false);
			if (!level.getBlockState(upperBroken).isAir() || !level.getBlockState(upperBroken.below()).isAir()) {
				problems.add(g.id() + ": breaking the upper half left " + level.getBlockState(upperBroken.below()));
			}
			// Survival: not on stone, the upper half not alone.
			BlockPos stone = new BlockPos(X0 + 7, Y, z);
			level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			if (block.defaultBlockState().canSurvive(level, stone.above())) {
				problems.add(g.id() + ": survives on stone");
			}
			BlockState upper = block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
			if (upper.canSurvive(level, new BlockPos(X0 + 6, Y + 1, z))) {
				problems.add(g.id() + ": the upper half survives without its lower half");
			}
			// In the summer of the test the plant has its outline (the phases: GroundPhasesTest).
			BlockPos standing = new BlockPos(X0, Y + 1, z);
			if (level.getBlockState(standing).getShape(level, standing).isEmpty() && !block.withdrawn(level, standing)) {
				problems.add(g.id() + ": no outline while not withdrawn");
			}
		}
		return problems;
	}

	/** Places the plant as a player's item does (no player: the item is used up as in survival). */
	private static boolean placeWithItem(ServerLevel level, SeasonalTallHerbBlock block, BlockPos pos) {
		BlockItem item = (BlockItem) block.asItem();
		BlockPos ground = pos.below();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		InteractionResult result = item.place(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(item), hit));
		return result.consumesAction();
	}

	private static boolean paired(ServerLevel level, SeasonalTallHerbBlock block, BlockPos pos) {
		BlockState low = level.getBlockState(pos);
		BlockState high = level.getBlockState(pos.above());
		return low.is(block) && low.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER && high.is(block)
				&& high.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
	}
}
