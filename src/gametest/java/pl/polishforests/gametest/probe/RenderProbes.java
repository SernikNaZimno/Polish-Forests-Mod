package pl.polishforests.gametest.probe;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * Render trials of step V3 of the vertical slice (docs/04-pionowy-wycinek.md §3): two probe blocks that exist only in the
 * client game test run {@code -Pgametest=probes}. They live in the game test mod (never in the mod jar) and in their own
 * namespace {@value #NAMESPACE}, so the registry snapshot of the mod (decision PW-11) does not see them; in every other
 * run (the game, other game tests) nothing is registered.
 * <ul>
 * <li>{@code probe_leaves}: a leaves block like the leaves of a species in V7 ({@link TintedParticleLeavesBlock}, so the
 * Fast graphics mode and MoreCulling treat it as leaves) with the trial color curve on the raw biome foliage color
 * (decision B6 (a)) and, in the leafless phase, the twig model emitted by a wrapped model (T-D5);
 * <li>{@code probe_plant}: a cross plant whose wrapped model emits nothing in its "empty" phase (decision PW-8 (a)).
 * </ul>
 * The client side is {@link RenderProbesClient}, the test {@link pl.polishforests.gametest.RenderProbesClientGameTest}.
 */
public final class RenderProbes implements ModInitializer {
	public static final String NAMESPACE = "polishforests_probe";

	private static @Nullable Block leaves;
	private static @Nullable Block plant;

	/** True only in the client game test run of the probes ({@code -Pgametest=probes}). */
	public static boolean enabled() {
		return "probes".equals(System.getProperty("polishforests.gametest"));
	}

	@Override
	public void onInitialize() {
		if (!enabled()) {
			return;
		}
		leaves = register("probe_leaves", ProbeLeavesBlock::new, Blocks.leavesProperties(SoundType.GRASS));
		plant = register("probe_plant", BushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable()
				.noCollision().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
		PolishForests.LOG.info("[probes] render probe blocks registered: {}, {}", BuiltInRegistries.BLOCK.getKey(leaves),
				BuiltInRegistries.BLOCK.getKey(plant));
	}

	public static Block leaves() {
		if (leaves == null) {
			throw new IllegalStateException("The render probes are registered only with -Pgametest=probes");
		}
		return leaves;
	}

	public static Block plant() {
		if (plant == null) {
			throw new IllegalStateException("The render probes are registered only with -Pgametest=probes");
		}
		return plant;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	/**
	 * The probe leaves: vanilla leaves with tinted falling leaf particles, but none in the leafless phase (review round 1
	 * of V3: a bare crown in January must not shed leaves). The particles run only on the client, where the phase is known.
	 */
	static final class ProbeLeavesBlock extends TintedParticleLeavesBlock {
		ProbeLeavesBlock(BlockBehaviour.Properties properties) {
			super(0.01F, properties);
		}

		@Override
		protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
			if (!ProbeSeason.leafless()) {
				super.spawnFallingLeavesParticle(level, pos, random);
			}
		}
	}

	private static Block register(String path, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
