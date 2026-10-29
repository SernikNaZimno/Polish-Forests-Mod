package pl.polishforests.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.compat.FellingCompat;

/**
 * Loot condition {@code polishforests:trunk_base} (step S1 of docs/04-pionowy-wycinek.md, variant 3 of decision G1-j
 * chosen at CP-C.1): the block broken is the base of a thin stem, a thin trunk (or root flare) of the smallest radius whose
 * parent is below ({@link #isBase}) standing on the ground ({@code #minecraft:supports_vegetation}: soil, grass, podzol,
 * moss, farmland). The trunk tables give 1–2 logs for it, so the small tree grown from a sapling (the pole stage, a trunk of
 * 1 px that otherwise gives only sticks) yields renewable wood. The block state alone cannot tell the base from the other
 * upright segments of the stem (the frozen registry, PW-11, rules out a new block property), so the condition looks at the
 * block below the broken one.
 *
 * <p>The position of the broken block is the loot origin, except while a felling mod breaks a tree part by part and drops
 * everything where the tree was hit (FallingTree's {@code spawnItemsAtBreakPoint}, recommended in the modpack): then the
 * hook of {@link FellingCompat#fellingPart} gives the part's own position.
 */
public final class TrunkBaseCondition implements LootItemCondition {
	public static final TrunkBaseCondition INSTANCE = new TrunkBaseCondition();
	public static final MapCodec<TrunkBaseCondition> MAP_CODEC = MapCodec.unit(INSTANCE);

	private TrunkBaseCondition() {
	}

	/** Registers the condition type (at start-up, with the blocks). */
	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, PolishForests.id("trunk_base"), MAP_CODEC);
	}

	/** The condition for the loot tables of the data generator. */
	public static LootItemCondition.Builder trunkBase() {
		return () -> INSTANCE;
	}

	@Override
	public MapCodec<TrunkBaseCondition> codec() {
		return MAP_CODEC;
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.BLOCK_STATE, LootContextParams.ORIGIN);
	}

	@Override
	public boolean test(LootContext context) {
		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (state == null || origin == null || !stem(state)) {
			return false;
		}
		@Nullable BlockPos part = FellingCompat.fellingPart();
		BlockPos pos = part != null ? part : BlockPos.containing(origin);
		return ground(context.getLevel().getBlockState(pos.below()));
	}

	/** Whether a block is the lowest segment shape of a thin stem: a trunk of the smallest radius with its parent below. */
	public static boolean stem(BlockState state) {
		return state.getBlock() instanceof TrunkBlock && state.getBlock() != ModBlocks.treeRoot()
				&& state.getValue(TrunkBlock.RADIUS) == TrunkBlock.MIN_RADIUS && state.getValue(TrunkBlock.PARENT) == Direction.DOWN;
	}

	/** Whether a block is ground for a stem base: {@code #minecraft:supports_vegetation}. */
	public static boolean ground(BlockState below) {
		return below.is(BlockTags.SUPPORTS_VEGETATION);
	}

	/** Whether a block on a block below is the base of a thin stem (the condition without the loot context). */
	public static boolean isBase(BlockState state, BlockState below) {
		return stem(state) && ground(below);
	}
}
