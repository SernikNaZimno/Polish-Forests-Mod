package pl.polishforests.compat.mixin;

import fr.rakambda.fallingtree.common.tree.TreePartType;
import fr.rakambda.fallingtree.common.tree.builder.ToAnalyzePos;
import fr.rakambda.fallingtree.common.tree.builder.TreeBuilder;
import fr.rakambda.fallingtree.common.wrapper.IBlock;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.compat.FellingCompat;

/**
 * Step G1: FallingTree's search through a tree of the tree planner follows the parent graph of the thin trunks
 * ({@link FellingCompat#fallingTreeStep}); other trees keep the mod's rule.
 */
@Mixin(value = TreeBuilder.class, remap = false)
abstract class FallingTreeBuilderMixin {
	@Inject(method = "shouldIncludeInChain", at = @At("HEAD"), cancellable = true, require = 0)
	private void polishforests$parentGraph(Predicate<IBlockPos> boundingBoxSearch, IBlockPos originPos, IBlock originBlock,
			ToAnalyzePos parent, ToAnalyzePos check, CallbackInfoReturnable<Boolean> cir) {
		if (!parent.treePartType().isLog()) {
			return;
		}
		Boolean step = FellingCompat.fallingTreeStep(parent.checkState().getRaw(), parent.checkPos().getRaw(),
				check.checkState().getRaw(), check.checkPos().getRaw(), check.treePartType() == TreePartType.LEAF);
		if (step != null) {
			cir.setReturnValue(step && boundingBoxSearch.test(check.checkPos()));
		}
	}
}
