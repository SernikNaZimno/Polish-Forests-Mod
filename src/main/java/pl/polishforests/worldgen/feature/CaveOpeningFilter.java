package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import pl.polishforests.worldgen.chunk.ChunkHabitats;

/**
 * Placement filter {@code polishforests:cave_opening} (step J2 of docs/04-pionowy-wycinek.md): no M2 fallen tree
 * ({@code deadwood_substitute/<species>}, vanilla {@code fallen_tree}) within {@value #REACH} blocks of a column whose top
 * a cave entrance took away (the mask {@code cave_opened} of the chunk habitats, step J1). The vanilla fallen log may lie
 * over gaps of up to 2 blocks, so beside a cave entrance it bridged the opening (the {@code caves} game test found such a
 * log once the forest place of the test moved); the dead wood of the planner already treats those columns as unknown
 * ground. Worlds without caves have no opened columns and do not change. It never draws a random number.
 */
public final class CaveOpeningFilter implements PlacementFilter {
	public static final CaveOpeningFilter INSTANCE = new CaveOpeningFilter();
	public static final MapCodec<CaveOpeningFilter> CODEC = MapCodec.unit(() -> INSTANCE);
	/** Reach of a fallen tree from its origin: the longest vanilla log of M2 (9 blocks) and its stump. */
	static final int REACH = 10;

	private CaveOpeningFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		WorldGenLevel level = context.getLevel();
		int cx0 = (origin.getX() - REACH) >> 4;
		int cx1 = (origin.getX() + REACH) >> 4;
		int cz0 = (origin.getZ() - REACH) >> 4;
		int cz1 = (origin.getZ() + REACH) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				ChunkAccess chunk = level.getChunk(cx, cz);
				ChunkHabitats h = chunk.getAttached(ModFeatures.CHUNK_HABITATS);
				if (h == null || !h.anyCaveOpened()) {
					continue;
				}
				int x0 = Math.max(origin.getX() - REACH, cx << 4);
				int x1 = Math.min(origin.getX() + REACH, (cx << 4) + 15);
				int z0 = Math.max(origin.getZ() - REACH, cz << 4);
				int z1 = Math.min(origin.getZ() + REACH, (cz << 4) + 15);
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (h.caveOpened(ChunkHabitats.index(x & 15, z & 15))) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public MapCodec<CaveOpeningFilter> codec() {
		return CODEC;
	}
}
