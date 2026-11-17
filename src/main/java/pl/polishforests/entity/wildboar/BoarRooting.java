package pl.polishforests.entity.wildboar;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.Thicket;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.spawn.FaunaRules;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * What the wild boar does to the ground (step FA3, C9, docs/04-pionowy-wycinek.md "Projekt FA3"), only from the tick of a
 * living boar, never at generation (the determinism of the terrain stays: the world generation does not know the boars).
 *
 * <ul>
 * <li><b>Rooting</b> (buchtowisko): a boar that roots in a feeding bout ({@code Action.ROOT}) turns once per bout, with the
 * chance {@value #PATCH_CHANCE}, a patch of {@value #PATCH_MIN}-{@value #PATCH_MAX} neighbouring grass blocks into coarse
 * dirt ({@value #COARSE_SHARE} of them) or rooted dirt, at most one block above or below its feet, in the habitats of
 * {@link #ROOTING} (the fertile broadleaf and riparian forests of report 07: oak-hornbeam and beech forests, the mixed
 * forest, the riparian forests). The plant on such a block (grass, a fern, a herb, a flower, a snow layer) is removed
 * without drops (a double plant whole: its upper half goes with the lower one); a block with a sapling, leaves, a climber,
 * a fluid or a solid block (a trunk, a root, a root flare) on it stays.
 * <li><b>Wallowing</b> (kąpielisko): in the wet forests of {@link #WALLOWING} a rest of a leader or a lone boar goes with
 * the chance {@value #WALLOW_CHANCE} to the ground next to water within {@value #WALLOW_RADIUS} blocks; there, with the
 * chance {@value #MUD_CHANCE}, one grass or dirt block next to the water becomes mud.
 * </ul>
 * Every change needs the game rule {@code polishforests:boar_rooting} and vanilla's {@code mob_griefing}
 * ({@link FaunaRules#rooting}); with either off a boar still roots and wallows, but no block changes ({@link #REFUSED}
 * counts the attempts).
 */
public final class BoarRooting {
	public static final float PATCH_CHANCE = 0.5F;
	public static final int PATCH_MIN = 2;
	public static final int PATCH_MAX = 4;
	public static final float COARSE_SHARE = 0.6F;
	/** Ticks of a rooting bout before the patch is dug (drawn between these). */
	public static final int DIG_MIN = 40;
	public static final int DIG_MAX = 120;
	public static final float WALLOW_CHANCE = 0.4F;
	public static final int WALLOW_RADIUS = 16;
	public static final float MUD_CHANCE = 0.25F;
	/** The habitats where a feeding boar roots up the ground. */
	public static final Set<HabitatBiome> ROOTING = EnumSet.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.LOWLAND_BEECH_FOREST,
			HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.UPLAND_FIR_FOREST, HabitatBiome.MIXED_FOREST, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.ELM_ASH_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST);
	/** The habitats where a resting boar may go to a wallow. */
	public static final Set<HabitatBiome> WALLOWING = EnumSet.of(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.ELM_ASH_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.GRAY_ALDER_FOREST);

	/** Counters for the census of the fauna game test: patches and blocks rooted, attempts the rules refused, wallows, mud. */
	public static final LongAdder PATCHES = new LongAdder();
	public static final LongAdder BLOCKS = new LongAdder();
	public static final LongAdder REFUSED = new LongAdder();
	/** Attempts under the rules that found no grass block to root (bare ground, litter, a plant that stays). */
	public static final LongAdder BARE = new LongAdder();
	public static final LongAdder WALLOWS = new LongAdder();
	public static final LongAdder MUD = new LongAdder();

	private BoarRooting() {
	}

	/** Resets the counters (the fauna game test). */
	public static void resetCounters() {
		PATCHES.reset();
		BLOCKS.reset();
		REFUSED.reset();
		BARE.reset();
		WALLOWS.reset();
		MUD.reset();
	}

	/** Whether a boar roots (rather than grazes) where it stands: the habitat at its column is one of {@link #ROOTING}. */
	public static boolean rootsAt(Level level, BlockPos feet) {
		return ROOTING.contains(FaunaHabitat.surface(level, feet.getX(), feet.getZ()));
	}

	/** Whether a resting boar may look for a wallow where it stands. */
	public static boolean wallowsAt(Level level, BlockPos feet) {
		return WALLOWING.contains(FaunaHabitat.surface(level, feet.getX(), feet.getZ()));
	}

	/**
	 * The patch of a rooting bout around the feet of a boar (see the class comment). Returns the ground blocks changed
	 * (empty when the rules refuse it or no grass block is at hand).
	 */
	public static List<BlockPos> dig(ServerLevel level, BlockPos feet, RandomSource random) {
		if (!rootsAt(level, feet)) {
			return List.of();
		}
		if (!FaunaRules.rooting(level)) {
			REFUSED.increment();
			return List.of();
		}
		int want = PATCH_MIN + random.nextInt(PATCH_MAX - PATCH_MIN + 1);
		List<BlockPos> patch = new ArrayList<>();
		List<BlockPos> open = new ArrayList<>();
		open.add(feet);
		// Grow the patch from the feet over neighbouring grass blocks (4-neighbours, one block up or down).
		while (!open.isEmpty() && patch.size() < want) {
			BlockPos at = open.remove(random.nextInt(open.size()));
			BlockPos ground = groundAt(level, at);
			if (ground == null || patch.contains(ground)) {
				continue;
			}
			patch.add(ground);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				open.add(ground.above().relative(d));
			}
		}
		if (patch.size() < PATCH_MIN) {
			BARE.increment();
			return List.of();
		}
		for (BlockPos ground : patch) {
			BlockPos above = ground.above();
			if (!level.getBlockState(above).isAir()) {
				level.destroyBlock(above, false);
			}
			Block dirt = random.nextFloat() < COARSE_SHARE ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT;
			level.setBlock(ground, dirt.defaultBlockState(), Block.UPDATE_ALL);
		}
		PATCHES.increment();
		BLOCKS.add(patch.size());
		return List.copyOf(patch);
	}

	/** Whether a boar standing at these feet finds a grass block to root (the fauna game test picks its place by it). */
	public static boolean grassAt(Level level, BlockPos feet) {
		return groundAt(level, feet) != null;
	}

	/**
	 * The grass block a boar can root at a feet position (the block under it, or one block up or down), or null: grass with
	 * air or a plant that a boar uproots above it.
	 */
	static @Nullable BlockPos groundAt(Level level, BlockPos feet) {
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos ground = feet.below().above(dy);
			if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && uprootable(level, ground.above())) {
				return ground;
			}
		}
		return null;
	}

	/** Whether the block on a grass block lets a boar root there: air, or a plant or a snow layer without a fluid. */
	static boolean uprootable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return true;
		}
		// A climber (the hop, a vine) hangs on a trunk: the boar leaves it and the trunk alone.
		if (!level.getFluidState(pos).isEmpty() || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.CLIMBABLE)) {
			return false;
		}
		return state.canBeReplaced() || state.is(Blocks.SNOW);
	}

	/**
	 * The place of a wallow near a boar: a column within {@value #WALLOW_RADIUS} blocks where it can lie on dry ground next to
	 * water (12 tries), or null.
	 */
	public static @Nullable Vec3 wallowSpot(Level level, BlockPos around, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			int x = around.getX() + random.nextInt(2 * WALLOW_RADIUS + 1) - WALLOW_RADIUS;
			int z = around.getZ() + random.nextInt(2 * WALLOW_RADIUS + 1) - WALLOW_RADIUS;
			if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(feet.getY() - around.getY()) > 6 || !Thicket.standable(level, feet)) {
				continue;
			}
			if (waterNext(level, feet.below()) != null) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}

	/** A water block next to a ground block (4-neighbours at its height or one block up), or null. */
	static @Nullable BlockPos waterNext(Level level, BlockPos ground) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos p = ground.relative(d).above(dy);
				if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
					return p;
				}
			}
		}
		return null;
	}

	/**
	 * A boar lies down in a wallow at its feet: counted, and with the chance {@value #MUD_CHANCE} (under the rules) the grass
	 * or dirt block under it, when it is next to water, becomes mud. Returns whether a block changed.
	 */
	public static boolean wallow(ServerLevel level, BlockPos feet, RandomSource random) {
		WALLOWS.increment();
		BlockPos ground = feet.below();
		if (waterNext(level, ground) == null || random.nextFloat() >= MUD_CHANCE) {
			return false;
		}
		BlockState state = level.getBlockState(ground);
		BlockPos above = ground.above();
		// As at rooting: only under air or a plant the boar uproots (never under a sapling, a climber or a trunk).
		if (!uprootable(level, above)) {
			return false;
		}
		if (!(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT))) {
			return false;
		}
		if (!FaunaRules.rooting(level)) {
			REFUSED.increment();
			return false;
		}
		if (!level.getBlockState(above).isAir()) {
			level.destroyBlock(above, false);
		}
		level.setBlock(ground, Blocks.MUD.defaultBlockState(), Block.UPDATE_ALL);
		MUD.increment();
		return true;
	}
}
