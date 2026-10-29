package pl.polishforests.worldgen.cave;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.WaterKind;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.Material;

/**
 * Rules of the caves of step J1 on the plans of 4 × 4 chunk blocks in both scales, around a lowland river, around the
 * sea cliff of the golden terrain test and on plain lowland: no cave touches the water of the surface or comes within
 * {@link PolandCaves#WATER_ROOF} blocks of a water bed, no sand or gravel hangs over a cave, lava only below the vanilla
 * lava level, the same caves whatever the order of the chunks, and the base column of the generator matches the noise
 * caves of the chunk.
 */
@Tag("slow")
class PolandCavesTest {
	private static final int BLOCK = 4;

	private record Area(String name, int cx, int cz) {
	}

	private static List<Area> areas(CaveFixture f) {
		List<Area> areas = new ArrayList<>();
		int[] river = CaveFixture.find(-30_000, 20_000, 48, 4_000, p -> {
			ColumnSample s = f.model.sample(p[0], p[1]);
			return s.waterKind() == WaterKind.RIVER && s.type().isLowland();
		});
		assertTrue(river != null, f.scale + ": no lowland river");
		areas.add(new Area("river", (river[0] >> 4) - 2, (river[1] >> 4) - 2));
		// The center of the golden terrain patch "cliff" of the seed (golden_terrain_m2.txt), a sea cliff.
		int[] cliff = f.scale == PolandScale.REALISTIC ? new int[] {-181_586, -207_822} : new int[] {-3_216, 6_103};
		int[] sea = CaveFixture.find(cliff[0], cliff[1], 16, 400, p -> f.model.sample(p[0], p[1]).waterKind() == WaterKind.SEA);
		assertTrue(sea != null, f.scale + ": no sea by the cliff");
		areas.add(new Area("sea", (sea[0] >> 4) - 2, (sea[1] >> 4) - 2));
		areas.add(new Area("lowland", 200, -300));
		return areas;
	}

	private static Material material(CaveFixture.Chunk c, int i, int y) {
		Material m = c.caves().get(i, y);
		return m != null ? m : c.surface().material(i, y);
	}

	@Test
	void cavesKeepOffSurfaceWaterAndHaveNoHangingSand() {
		for (PolandScale scale : PolandScale.values()) {
			CaveFixture f = new CaveFixture(scale);
			long carved = 0;
			long fluid = 0;
			long lava = 0;
			long wetColumns = 0;
			for (Area a : areas(f)) {
				CaveFixture.Chunk[][] block = new CaveFixture.Chunk[BLOCK][BLOCK];
				for (int dx = 0; dx < BLOCK; dx++) {
					for (int dz = 0; dz < BLOCK; dz++) {
						block[dx][dz] = f.chunk(a.cx() + dx, a.cz() + dz, true);
					}
				}
				for (int dx = 0; dx < BLOCK; dx++) {
					for (int dz = 0; dz < BLOCK; dz++) {
						CaveFixture.Chunk c = block[dx][dz];
						ChunkSurface s = c.surface();
						for (int i = 0; i < 256; i++) {
							if (s.wet(i)) {
								wetColumns++;
							}
							int lo = c.caves().lowest(i);
							if (lo == Integer.MAX_VALUE) {
								continue;
							}
							int bx = dx * 16 + (i >> 4);
							int bz = dz * 16 + (i & 15);
							for (int y = lo; y <= c.caves().highest(i); y++) {
								if (!c.caves().carved(i, y)) {
									continue;
								}
								carved++;
								Material m = c.caves().get(i, y);
								if (m == Material.WATER) {
									fluid++;
								}
								if (m == Material.LAVA) {
									lava++;
									// Vanilla: the global lava below Y -54 and the lava aquifers with a level at or below Y -10.
									assertTrue(y < -10, scale + " " + a.name() + ": lava at Y " + y);
								}
								String at = String.format(Locale.ROOT, "%s %s: cave at (%d, %d, %d)", scale, a.name(), c.cx() * 16 + (i >> 4),
										y, c.cz() * 16 + (i & 15));
								if (y + 1 <= s.top(i) && !c.caves().carved(i, y + 1)) {
									Material above = material(c, i, y + 1);
									assertTrue(above != Material.SAND && above != Material.GRAVEL, at + " under " + above);
								}
								assertTrue(y + 1 > s.waterTop(i) || y + 1 <= s.top(i), at + " under the surface water");
								// Neighbors within the 4 x 4 block: no surface water beside the cave, and the roof of the water beds.
								for (int nx = bx - 1; nx <= bx + 1; nx++) {
									for (int nz = bz - 1; nz <= bz + 1; nz++) {
										if (nx < 0 || nz < 0 || nx >= BLOCK * 16 || nz >= BLOCK * 16) {
											continue;
										}
										CaveFixture.Chunk n = block[nx >> 4][nz >> 4];
										int ni = (nx & 15) << 4 | (nz & 15);
										if (!n.surface().wet(ni)) {
											continue;
										}
										int bed = n.surface().top(ni);
										boolean side = nx == bx || nz == bz;
										// The leak itself: surface water on a side of the cave (in the chunk and across its border).
										assertTrue(!side || y <= bed || y > n.surface().waterTop(ni), at + " beside the surface water of ("
												+ (n.cx() * 16 + (ni >> 4)) + ", " + (n.cz() * 16 + (ni & 15)) + ") above Y " + bed);
										// The roof of the water beds, which the chunk knows exactly in its own columns and from the
										// model water beyond them (a puddle of a neighbor chunk is not model water).
										boolean model = f.columns().water(n.cx() * 16 + (ni >> 4), n.cz() * 16 + (ni & 15))
												!= ChunkSurface.NO_WATER;
										if (n == c || model) {
											assertTrue(y <= bed - PolandCaves.WATER_ROOF, at + " within " + PolandCaves.WATER_ROOF
													+ " blocks of the water bed at Y " + bed);
										}
									}
								}
							}
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s: %d carved blocks (%d water, %d lava) in %d chunks with %d wet columns%n", scale,
					carved, fluid, lava, 3 * BLOCK * BLOCK, wetColumns);
			assertTrue(carved > 10_000, scale + ": almost no caves (" + carved + ")");
			assertTrue(wetColumns > 500, scale + ": the areas have almost no water (" + wetColumns + ")");
		}
	}

	@Test
	void cavesDoNotDependOnTheOrderOfChunks() {
		for (PolandScale scale : PolandScale.values()) {
			CaveFixture a = new CaveFixture(scale);
			CaveFixture b = new CaveFixture(scale);
			CaveFixture.Chunk a1 = a.chunk(200, -300, true);
			CaveFixture.Chunk a2 = a.chunk(201, -300, true);
			CaveFixture.Chunk b2 = b.chunk(201, -300, true);
			CaveFixture.Chunk b1 = b.chunk(200, -300, true);
			assertSame(a1.caves(), b1.caves(), a.vertical.height());
			assertSame(a2.caves(), b2.caves(), a.vertical.height());
			assertTrue(a1.caves().carvedBlocks() + a2.caves().carvedBlocks() > 0, scale + ": no caves in the test chunks");
		}
	}

	private static void assertSame(CaveCarving a, CaveCarving b, int height) {
		assertEquals(a.carvedBlocks(), b.carvedBlocks());
		for (int s = 0; s < (height + 15) >> 4; s++) {
			byte[] x = a.section(s);
			byte[] y = b.section(s);
			if (x == null || y == null) {
				assertTrue(x == y, "section " + s + " present in one plan only");
			} else {
				assertArrayEquals(x, y, "section " + s);
			}
		}
		for (int i = 0; i < 256; i++) {
			assertEquals(a.solidTop(i), b.solidTop(i));
		}
	}

	/**
	 * The base column and base height of the generator (structures, spawn) have the noise caves and aquifers of the chunk
	 * (without the carvers, as vanilla) in dry columns whose plan top is the model top.
	 */
	@Test
	void baseColumnsMatchTheNoiseCaves() {
		PolandCaves.bindServer(CaveFixture.registries());
		try {
			for (PolandScale scale : PolandScale.values()) {
				CaveFixture f = new CaveFixture(scale);
				LevelHeightAccessor height = LevelHeightAccessor.create(f.minY(), f.vertical.height());
				int columns = 0;
				int blocks = 0;
				int mismatched = 0;
				int opened = 0;
				for (int k = 0; k < 4; k++) {
					CaveFixture.Chunk c = f.chunk(200 + k, -300, false);
					for (int i = 0; i < 256; i += 3) {
						int x = c.cx() * 16 + (i >> 4);
						int z = c.cz() * 16 + (i & 15);
						int modelTop = f.vertical.topBlockY(f.model.sample(x, z).surface());
						if (c.surface().top(i) != modelTop || waterNear(f, x, z)) {
							continue;
						}
						columns++;
						NoiseColumn base = f.generator.getBaseColumn(x, z, height, f.randomState);
						for (int y = f.minY() + 8; y <= modelTop; y++) {
							BlockState st = base.getBlock(y);
							Material m = c.caves().get(i, y);
							boolean cave = m != null && CaveCarving.isCave(m);
							boolean baseCave = !st.is(Blocks.STONE);
							blocks++;
							if (cave != baseCave) {
								mismatched++;
							}
						}
						int baseHeight = f.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, f.randomState);
						if (c.caves().solidTop(i) < modelTop) {
							opened++;
						}
						assertTrue(Math.abs(baseHeight - (c.caves().solidTop(i) + 1)) <= 1 || c.caves().fluidTop(i) != Integer.MIN_VALUE,
								scale + ": base height " + baseHeight + " at (" + x + ", " + z + "), caves give "
										+ (c.caves().solidTop(i) + 1));
					}
				}
				System.out.printf(Locale.ROOT, "%s: %d columns, %d blocks, %d differ from the chunk caves, %d opened tops%n", scale,
						columns, blocks, mismatched, opened);
				assertTrue(columns > 200, scale + ": too few dry columns");
				assertTrue(mismatched <= blocks / 200, scale + ": base column differs from the chunk caves in " + mismatched
						+ " of " + blocks + " blocks");
			}
		} finally {
			PolandCaves.bindServer(null);
		}
	}

	private static boolean waterNear(CaveFixture f, int x, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (f.model.sample(x + dx, z + dz).hasWater()) {
					return true;
				}
			}
		}
		return false;
	}
}
