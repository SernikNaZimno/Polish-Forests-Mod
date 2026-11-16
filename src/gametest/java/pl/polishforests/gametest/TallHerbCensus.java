package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.SeasonalHerbBlock;
import pl.polishforests.block.SeasonalTallHerbBlock;

/**
 * Step C3 (CP-D.10): the census of the tall nettle and the tall yellow iris on the dry columns a game test walks, by biome
 * and zone ({@code biome} or {@code biome/zone}), with the one-block nettle and iris beside them: the lower halves standing
 * on the top ground block with their upper half above them, and the faults, which must be 0 — a lower half without its
 * upper half (of them "under wood": leaves or a log in the place of the upper half, a crown or a branch written over it),
 * an upper half without its lower half, and a half holding water. Used by {@code habitats} (the tree census sites) and
 * {@code vegetation} / {@code present_day} (the transects of §4.6).
 */
final class TallHerbCensus {
	/** Per place: tall nettle, tall iris, one-block nettle, one-block iris. */
	private final Map<String, long[]> byPlace = new TreeMap<>();
	private long columns;
	private long orphanLower;
	private long underWood;
	private long orphanUpper;
	private long wet;
	private long woodAbove;
	private final List<String> examples = new ArrayList<>();
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	/** One dry column with its top ground block at {@code top}. */
	void column(ServerLevel level, int x, int top, int z, String place) {
		columns++;
		BlockState low = level.getBlockState(at.set(x, top + 1, z));
		BlockState high = level.getBlockState(at.set(x, top + 2, z));
		if (low.getBlock() instanceof SeasonalTallHerbBlock tall) {
			if (low.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
				fault("upper half on the ground", x, top + 1, z);
				orphanUpper++;
			} else if (!high.is(tall) || high.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER) {
				orphanLower++;
				if (high.is(BlockTags.LEAVES) || high.is(BlockTags.LOGS)) {
					underWood++;
				}
				fault("lower half without its upper half (" + high.getBlock().getDescriptionId() + " above)", x, top + 1, z);
			} else {
				byPlace.computeIfAbsent(place, k -> new long[4])[tall.plant() == GroundPlant.TALL_NETTLE ? 0 : 1]++;
				BlockState over = level.getBlockState(at.set(x, top + 3, z));
				woodAbove += over.is(BlockTags.LEAVES) || over.is(BlockTags.LOGS) ? 1 : 0;
			}
			if (!level.getFluidState(at.set(x, top + 1, z)).isEmpty() || !level.getFluidState(at.set(x, top + 2, z)).isEmpty()) {
				wet++;
				fault("a half in water", x, top + 1, z);
			}
			return;
		}
		if (high.getBlock() instanceof SeasonalTallHerbBlock && high.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			orphanUpper++;
			fault("upper half without its lower half", x, top + 2, z);
		}
		if (low.getBlock() instanceof SeasonalHerbBlock herb && (herb.plant() == GroundPlant.NETTLE || herb.plant() == GroundPlant.YELLOW_IRIS)) {
			byPlace.computeIfAbsent(place, k -> new long[4])[herb.plant() == GroundPlant.NETTLE ? 2 : 3]++;
		}
	}

	private void fault(String what, int x, int y, int z) {
		if (examples.size() < 8) {
			examples.add(String.format(Locale.ROOT, "%s at (%d, %d, %d)", what, x, y, z));
		}
	}

	/** Lower halves of the tall nettle (0) or the tall iris (1) with their upper half, over all places. */
	long tall(int which) {
		return byPlace.values().stream().mapToLong(c -> c[which]).sum();
	}

	/** The faults: halves without their pair or in water (0 expected). */
	long faults() {
		return orphanLower + orphanUpper + wet;
	}

	String report(String label) {
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%s tall herbs (C3) on %d dry columns: tall nettle %d, tall "
				+ "yellow iris %d, unpaired lower halves %d (%d under leaves or a log), unpaired upper halves %d, in water %d, leaves "
				+ "or a log right above the plant %d; by place (tall nettle / tall iris / nettle / iris):", label, columns, tall(0),
				tall(1), orphanLower, underWood, orphanUpper, wet, woodAbove));
		for (Map.Entry<String, long[]> e : byPlace.entrySet()) {
			long[] c = e.getValue();
			sb.append(String.format(Locale.ROOT, " %s %d/%d/%d/%d;", e.getKey(), c[0], c[1], c[2], c[3]));
		}
		if (!examples.isEmpty()) {
			sb.append(" faults: ").append(examples);
		}
		return sb.toString();
	}
}
