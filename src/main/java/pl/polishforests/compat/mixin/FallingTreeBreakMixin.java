package pl.polishforests.compat.mixin;

import fr.rakambda.fallingtree.common.tree.Tree;
import fr.rakambda.fallingtree.common.tree.breaking.InstantaneousTreeBreakingHandler;
import fr.rakambda.fallingtree.common.tree.breaking.LootHandler;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import fr.rakambda.fallingtree.common.wrapper.IItemStack;
import fr.rakambda.fallingtree.common.wrapper.ILevel;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.compat.FellingCompat;

/**
 * Step S1: while FallingTree's instantaneous break (its default mode) breaks one part of a tree, the loot conditions know
 * the part's own position ({@link FellingCompat#fellingPart}); with {@code spawnItemsAtBreakPoint} the mod drops the loot
 * of every part at the hit block, and the base of a thin stem gives its logs only on the ground
 * ({@link pl.polishforests.block.TrunkBaseCondition}). The target is the lambda of {@code breakTree} that breaks one part
 * (FallingTree for 26.3); without it the conditions use the loot origin.
 */
@Mixin(value = InstantaneousTreeBreakingHandler.class, remap = false)
abstract class FallingTreeBreakMixin {
	@Inject(method = "lambda$breakTree$0", at = @At("HEAD"), require = 0)
	private void polishforests$partStart(ILevel level, Tree tree, IPlayer player, IItemStack tool, LootHandler loot, IBlockPos pos,
			CallbackInfoReturnable<Integer> cir) {
		FellingCompat.fellingPart(pos.getRaw());
	}

	@Inject(method = "lambda$breakTree$0", at = @At("RETURN"), require = 0)
	private void polishforests$partEnd(ILevel level, Tree tree, IPlayer player, IItemStack tool, LootHandler loot, IBlockPos pos,
			CallbackInfoReturnable<Integer> cir) {
		FellingCompat.fellingPart(null);
	}
}
