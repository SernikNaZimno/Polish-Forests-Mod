package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * Placement filter {@code polishforests:lake_clearance} (step J2 of docs/04-pionowy-wycinek.md): the vanilla rule of the
 * underground lava lake (its origin at least {@value #DEPTH} blocks below {@code OCEAN_FLOOR_WG}) checked over the whole
 * footprint of the lake instead of only at its origin. The vanilla lake ({@code LakeFeature}) spans x and z from
 * {@value #FROM} to {@value #TO} around the origin and opens air up to 2 blocks above it, so on a slope or a bank the
 * lower ground beside the origin broke open, under trees and logs placed before (the {@code caves} game test found a log
 * over such a hole). The filter never draws a random number, so the features after it do not change.
 */
public final class LakeClearanceFilter implements PlacementFilter {
	public static final LakeClearanceFilter INSTANCE = new LakeClearanceFilter();
	public static final MapCodec<LakeClearanceFilter> CODEC = MapCodec.unit(() -> INSTANCE);
	/** Least depth of the origin below the ground of every column of the footprint (the vanilla threshold). */
	static final int DEPTH = 5;
	/** Footprint of the lake around its origin (vanilla: grid cells 1 … 14 from the origin offset by -8). */
	static final int FROM = -7;
	static final int TO = 6;

	private LakeClearanceFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		int limit = origin.getY() + DEPTH;
		for (int dx = FROM; dx <= TO; dx++) {
			for (int dz = FROM; dz <= TO; dz++) {
				if (context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz) < limit) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public MapCodec<LakeClearanceFilter> codec() {
		return CODEC;
	}
}
