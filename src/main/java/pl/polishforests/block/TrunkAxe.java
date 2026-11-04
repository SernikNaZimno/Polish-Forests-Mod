package pl.polishforests.block;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Thinning a thin trunk with an axe (step P1 of docs/04-pionowy-wycinek.md, decision U-2 of docs/00 G.36): a player who
 * uses an axe on a trunk of the mod (with or without bark, {@link #thinnable}) <b>while sneaking</b> shaves it 1 px
 * thinner, down to radius 1; the parent and the water stay. Without sneaking the axe strips the trunk as vanilla strips a
 * log (the stripping registered in {@link ModBlocks}), default choice P1-a. Like vanilla's stripping: the axe sound, one
 * point of durability, the block change game event and the "item used on block" trigger; in adventure mode only an axe
 * that may be placed on the block ({@code can_place_on}) works, as vanilla's axe; spectators do nothing.
 *
 * <p>Only the server changes the block (the client returns success without a change, so it swings the arm and sends the
 * use to the server, which plays the sound to everyone, the user included). On a trunk of radius 1 the sneaking use does
 * nothing (it does not fall through to stripping, so a row of clicks never strips by surprise; default choice P1-c).
 * Sneaking takes the use before the block and the item: vanilla skips the block's own use when the player sneaks with an
 * item, so the hook is Fabric's {@link UseBlockCallback}.
 */
public final class TrunkAxe {
	/** Durability an axe loses per thinning: as one stripping. */
	public static final int DAMAGE = 1;

	private TrunkAxe() {
	}

	/** Registers the use hook (at start-up, with the blocks). */
	public static void register() {
		UseBlockCallback.EVENT.register(TrunkAxe::use);
	}

	/** Whether a stack is an axe: in {@code #minecraft:axes} or with vanilla's axe block transformer. */
	public static boolean axe(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.AXES)) {
			return true;
		}
		Holder<BlockTransformer> t = stack.get(DataComponents.BLOCK_TRANSFORMER);
		return t != null && t.is(BlockTransformers.AXE);
	}

	/** Whether a block is a trunk the axe thins: a trunk or stripped trunk (not a root flare, not the underground root). */
	public static boolean thinnable(BlockState state) {
		Block b = state.getBlock();
		return b instanceof TrunkBlock && !(b instanceof RootFlareBlock) && b != ModBlocks.treeRoot();
	}

	/** The state an axe makes of a thinnable trunk: 1 px thinner, or null at radius 1. */
	public static BlockState thinned(BlockState state) {
		int r = state.getValue(TrunkBlock.RADIUS);
		return r > TrunkBlock.MIN_RADIUS ? state.setValue(TrunkBlock.RADIUS, r - 1) : null;
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!axe(stack)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!thinnable(state)) {
			return InteractionResult.PASS;
		}
		// Adventure mode as vanilla's ItemStack#useOn: only with an axe that may be placed on the block.
		if (!player.getAbilities().mayBuild && !stack.canPlaceOnBlockInAdventureMode(new BlockInWorld(level, pos, false))) {
			return InteractionResult.PASS;
		}
		BlockState thinner = thinned(state);
		if (thinner == null) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp) {
			CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(sp, pos, stack.copy());
		}
		level.setBlock(pos, thinner, Block.UPDATE_ALL_IMMEDIATE);
		level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, thinner));
		stack.hurtAndBreak(DAMAGE, player, hand.asEquipmentSlot());
		return InteractionResult.SUCCESS;
	}
}
