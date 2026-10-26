package pl.polishforests.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dwarf shrub with fruit (step V12, {@link GroundPlant.Kind#BERRY_SHRUB}; the bilberry): low, without collision,
 * thorns or slowing down (decision M2-13: unlike the vanilla sweet berry bush). {@link #BERRIES} is the server state of
 * the fruit (PW-8 (a)): the generation leaves it off (ZW-5: no season in the generated state), the season sets it in
 * step V13; a player picks the fruit with an empty hand (1–2 of the fruit item) and bone meal ripens it. {@link #MOSSY}
 * is the mossy underside (PW-7: a moss carpet under the shrub, the model's second layer), set by the generation in the
 * pine forests.
 */
public class BerryShrubBlock extends VegetationBlock implements BonemealableBlock {
	public static final BooleanProperty BERRIES = BooleanProperty.create("berries");
	public static final BooleanProperty MOSSY = BooleanProperty.create("mossy");
	/** Bilberry is 15–50 cm: the outline of a low shrub. */
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 9.0);

	private final Supplier<Item> fruit;

	public BerryShrubBlock(Supplier<Item> fruit, BlockBehaviour.Properties properties) {
		super(properties);
		this.fruit = fruit;
		registerDefaultState(stateDefinition.any().setValue(BERRIES, false).setValue(MOSSY, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hitResult) {
		return !state.getValue(BERRIES) && itemStack.is(Items.BONE_MEAL) ? InteractionResult.PASS
				: super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!state.getValue(BERRIES)) {
			return super.useWithoutItem(state, level, pos, player, hitResult);
		}
		if (level instanceof ServerLevel server) {
			popResource(server, pos, new ItemStack(fruit.get(), 1 + server.getRandom().nextInt(2)));
			server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
					0.8F + server.getRandom().nextFloat() * 0.4F);
			BlockState picked = state.setValue(BERRIES, false);
			server.setBlock(pos, picked, Block.UPDATE_CLIENTS);
			server.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BERRIES, MOSSY);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return !state.getValue(BERRIES);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		level.setBlock(pos, state.setValue(BERRIES, true), Block.UPDATE_CLIENTS);
	}
}
