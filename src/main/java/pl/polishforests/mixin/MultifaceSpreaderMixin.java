package pl.polishforests.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.worldgen.cave.SculkFastPath;

/**
 * Step O2: {@code spreadAll} as two loops in the order of vanilla's streams ({@code Direction.stream()} is
 * {@code Direction.values()} in order; the filter, the map to the per-face count and the sum run element by element, so
 * the spreads happen in the same order with the same arguments). See {@link SculkFastPath}.
 */
@Mixin(MultifaceSpreader.class)
abstract class MultifaceSpreaderMixin {
	@Unique
	private static final Direction[] POLISHFORESTS$DIRECTIONS = Direction.values();

	@Shadow
	@Final
	private MultifaceSpreader.SpreadConfig config;

	@Shadow
	public abstract Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(BlockState state, LevelAccessor level,
			BlockPos pos, Direction fromFace, Direction spreadDirection, boolean postProcess);

	@Inject(method = "spreadAll", at = @At("HEAD"), cancellable = true)
	private void polishforests$spreadAllLoops(BlockState state, LevelAccessor level, BlockPos pos, boolean postProcess,
			CallbackInfoReturnable<Long> cir) {
		if (!SculkFastPath.ENABLED) {
			return;
		}
		long sum = 0;
		for (Direction face : POLISHFORESTS$DIRECTIONS) {
			if (!config.canSpreadFrom(state, face)) {
				continue;
			}
			long count = 0;
			for (Direction direction : POLISHFORESTS$DIRECTIONS) {
				if (spreadFromFaceTowardDirection(state, level, pos, face, direction, postProcess).isPresent()) {
					count++;
				}
			}
			sum += count;
		}
		cir.setReturnValue(sum);
	}
}
