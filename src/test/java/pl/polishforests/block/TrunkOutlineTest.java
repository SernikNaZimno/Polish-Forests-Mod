package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The outline and the collision of the thin trunks in a world (step P1 of docs/04-pionowy-wycinek.md, {@link TrunkShapes}):
 * <ul>
 * <li>for a straight trunk, a tip, an elbow and a fork of every radius and parent the outline is the drawing within 1 px:
 * the generated models ({@code template_trunk_r1…r7}, read from {@code src/main/generated}) of the block and its children,
 * cut to the block, voxelized at 1 px, and the outline voxelized the same way lie within 1 px of each other both ways (the
 * prism of before P1 is 8 − r px off at every tip);</li>
 * <li>the collision drops only the twigs of radius 1 that are not upright;</li>
 * <li>outside a world (the state cache with the empty block getter, the chunk regions of world generation) the shapes are
 * the prism of before P1, so the light, the heightmaps and the generated world do not change;</li>
 * <li>the shape table returns the same shape object for the same key (no allocation on a hit).</li>
 * </ul>
 */
class TrunkOutlineTest {
	private static final Path MODELS = Path.of("src/main/generated/assets/polishforests/models/block");
	private static final Map<Integer, JsonObject> TEMPLATES = new HashMap<>();
	/** Allowed distance between the outline and the drawing, px. */
	private static final int TOLERANCE = 1;

	@BeforeAll
	static void registries() throws IOException {
		GameRegistries.bootstrap();
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			TEMPLATES.put(r, JsonParser.parseString(Files.readString(MODELS.resolve("template_trunk_r" + r + ".json"))).getAsJsonObject());
		}
	}

	/** A trunk block of a scene: offset from the tested block (blocks), radius, parent. */
	private record Part(int dx, int dy, int dz, int r, Direction parent) {
	}

	@Test
	void outlineIsTheDrawing() {
		TrunkBlock trunk = ModBlocks.trunk(TreeTrunk.OAK);
		int scenes = 0;
		List<String> failures = new ArrayList<>();
		for (Direction p : Direction.values()) {
			Direction side = p.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
			Direction away = p.getOpposite();
			for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
				int thin = Math.max(1, r - 2);
				Map<String, List<Part>> children = new java.util.LinkedHashMap<>();
				children.put("tip", List.of());
				children.put("straight", List.of(child(away, r)));
				children.put("elbow", List.of(child(side, r)));
				children.put("fork", List.of(child(away, r), child(side, thin)));
				children.put("joint", List.of(child(away, thin)));
				for (Map.Entry<String, List<Part>> e : children.entrySet()) {
					String name = e.getKey() + " r" + r + " parent " + p;
					Map<BlockPos, BlockState> world = new HashMap<>();
					BlockPos o = new BlockPos(0, 64, 0);
					world.put(o, trunk.state(r, p));
					// The parent (thicker), as in a tree.
					world.put(o.relative(p), trunk.state(TrunkBlock.MAX_RADIUS, p));
					for (Part c : e.getValue()) {
						world.put(o.offset(c.dx(), c.dy(), c.dz()), trunk.state(c.r(), c.parent()));
					}
					BlockGetter level = getter(world);
					VoxelShape outline = TrunkShapes.outline(world.get(o), level, o);
					List<Part> drawn = new ArrayList<>(e.getValue());
					drawn.add(new Part(0, 0, 0, r, p));
					boolean[][][] model = drawing(drawn);
					String miss = compare(model, voxels(outline));
					if (miss != null) {
						failures.add(name + ": " + miss);
					}
					scenes++;
					if (e.getKey().equals("tip") && r < TrunkBlock.MAX_RADIUS) {
						// The prism of before P1 is off by 8 - r px at a tip.
						assertTrue(compare(model, voxels(TrunkBlock.shape(p, r))) != null, name + ": the old prism matches the drawing");
					}
				}
			}
		}
		assertEquals(6 * 7 * 5, scenes);
		assertTrue(failures.isEmpty(), failures.size() + " scenes differ from the drawing:\n" + String.join("\n", failures));
	}

	@Test
	void straightTrunkKeepsThePrismAndTipsEndPastTheCenter() {
		TrunkBlock trunk = ModBlocks.trunk(TreeTrunk.SCOTS_PINE);
		BlockPos o = new BlockPos(5, 70, -3);
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			Map<BlockPos, BlockState> world = new HashMap<>();
			world.put(o, trunk.state(r, Direction.DOWN));
			world.put(o.above(), trunk.state(r, Direction.DOWN));
			assertEquals(TrunkBlock.shape(Direction.DOWN, r).bounds(), TrunkShapes.outline(world.get(o), getter(world), o).bounds(), "r" + r);
			world.remove(o.above());
			AABB tip = TrunkShapes.outline(world.get(o), getter(world), o).bounds();
			assertEquals((8 + r) / 16.0, tip.maxY, 1e-9, "tip r" + r);
			assertEquals(0, tip.minY, 1e-9, "tip r" + r);
		}
	}

	@Test
	void twigsOfRadiusOneDoNotCollideUnlessUpright() {
		TrunkBlock trunk = ModBlocks.trunk(TreeTrunk.HORNBEAM);
		BlockPos o = new BlockPos(0, 64, 0);
		// A lying twig: outline yes, collision no.
		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(o, trunk.state(1, Direction.WEST));
		assertFalse(TrunkShapes.outline(world.get(o), getter(world), o).isEmpty());
		assertTrue(TrunkShapes.collision(world.get(o), getter(world), o).isEmpty());
		// An upright stem of radius 1 collides.
		world.put(o, trunk.state(1, Direction.DOWN));
		assertFalse(TrunkShapes.collision(world.get(o), getter(world), o).isEmpty());
		// A branch of radius 3 with a twig on its side: the twig's half arm is in the outline, not in the collision.
		world.put(o, trunk.state(3, Direction.WEST));
		world.put(o.north(), trunk.state(1, Direction.SOUTH));
		AABB outline = TrunkShapes.outline(world.get(o), getter(world), o).bounds();
		AABB collision = TrunkShapes.collision(world.get(o), getter(world), o).bounds();
		assertEquals(0, outline.minZ, 1e-9);
		assertEquals((8 - 3) / 16.0, collision.minZ, 1e-9);
		// Standing on a branch of radius 2: its top is solid.
		world.clear();
		world.put(o, trunk.state(2, Direction.WEST));
		world.put(o.east(), trunk.state(2, Direction.WEST));
		assertEquals((8 + 2) / 16.0, TrunkShapes.collision(world.get(o), getter(world), o).bounds().maxY, 1e-9);
	}

	@Test
	void outsideAWorldTheShapesAreThePrismAsBeforeP1() {
		List<TrunkBlock> blocks = new ArrayList<>(ModBlocks.trunks().values());
		blocks.addAll(ModBlocks.strippedTrunks().values());
		blocks.add(ModBlocks.treeRoot());
		for (TrunkBlock b : blocks) {
			for (BlockState s : b.getStateDefinition().getPossibleStates()) {
				VoxelShape prism = TrunkBlock.shape(s.getValue(TrunkBlock.PARENT), s.getValue(TrunkBlock.RADIUS));
				assertSame(prism, s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), s.toString());
				assertSame(prism, s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), s.toString());
				assertSame(prism, s.getOcclusionShape(), s.toString());
				// A region of world generation is a block getter, not a level: the prism, whatever the neighbors.
				Map<BlockPos, BlockState> world = new HashMap<>();
				world.put(BlockPos.ZERO, s);
				assertSame(prism, s.getShape(getter(world), BlockPos.ZERO, CollisionContext.empty()), s.toString());
				assertSame(prism, s.getBlockSupportShape(getter(world), BlockPos.ZERO), s.toString());
			}
		}
		// The light comes from the cached prism (the heightmaps of 26.3 go by the tag #blocks_motion_in_heightmap).
		BlockState thick = ModBlocks.trunk(TreeTrunk.OAK).state(TrunkBlock.MAX_RADIUS, Direction.DOWN);
		assertTrue(thick.propagatesSkylightDown());
		assertFalse(thick.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	@Test
	void flaresAndChildrenOfOtherKinds() {
		BlockPos o = new BlockPos(0, 64, 0);
		TrunkBlock trunk = ModBlocks.trunk(TreeTrunk.OAK);
		RootFlareBlock flare = ModBlocks.flare(RootFlare.OAK);
		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(o, trunk.state(5, Direction.DOWN));
		world.put(o.above(), trunk.state(5, Direction.DOWN));
		// A lying flare whose parent is the trunk base draws its fin, not an arm: no part of the outline.
		world.put(o.east(), flare.state(3, Direction.WEST));
		assertEquals(TrunkBlock.shape(Direction.DOWN, 5).bounds(), TrunkShapes.outline(world.get(o), getter(world), o).bounds());
		// A stripped child is a trunk as any other.
		world.put(o.east(), ModBlocks.stripped(StrippedTrunk.OAK).state(2, Direction.WEST));
		assertEquals(1.0, TrunkShapes.outline(world.get(o), getter(world), o).bounds().maxX, 1e-9);
		// The same key gives the same shape object.
		int key = TrunkShapes.key(world.get(o), getter(world), o);
		assertSame(TrunkShapes.outline(key), TrunkShapes.outline(key));
		assertEquals(key, TrunkShapes.key(5, Direction.DOWN, new int[] {0, 5, 0, 0, 0, 2}));
		// A waterlogged trunk keeps its shape.
		world.put(o, trunk.state(5, Direction.DOWN).setValue(BlockStateProperties.WATERLOGGED, true));
		assertEquals(key, TrunkShapes.key(world.get(o), getter(world), o));
	}

	// ------------------------------------------------------------------ helpers

	/** A child on side d of the tested block, its parent pointing back. */
	private static Part child(Direction d, int r) {
		return new Part(d.getStepX(), d.getStepY(), d.getStepZ(), r, d.getOpposite());
	}

	/** The model elements of the parts that fall into the tested block, voxelized at 1 px (voxel centers inside a box). */
	private static boolean[][][] drawing(List<Part> parts) {
		boolean[][][] v = new boolean[16][16][16];
		for (Part part : parts) {
			JsonArray elements = TEMPLATES.get(part.r()).getAsJsonArray("elements");
			for (int i = 0; i < elements.size(); i++) {
				JsonObject e = elements.get(i).getAsJsonObject();
				double[] from = vec(e.getAsJsonArray("from"));
				double[] to = vec(e.getAsJsonArray("to"));
				double[] p = rotatePoint(part.parent(), from);
				double[] q = rotatePoint(part.parent(), to);
				double[] lo = new double[3];
				double[] hi = new double[3];
				int[] off = {16 * part.dx(), 16 * part.dy(), 16 * part.dz()};
				for (int k = 0; k < 3; k++) {
					lo[k] = Math.min(p[k], q[k]) + off[k];
					hi[k] = Math.max(p[k], q[k]) + off[k];
				}
				for (int x = 0; x < 16; x++) {
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							if (x + 0.5 > lo[0] && x + 0.5 < hi[0] && y + 0.5 > lo[1] && y + 0.5 < hi[1] && z + 0.5 > lo[2] && z + 0.5 < hi[2]) {
								v[x][y][z] = true;
							}
						}
					}
				}
			}
		}
		return v;
	}

	private static boolean[][][] voxels(VoxelShape shape) {
		boolean[][][] v = new boolean[16][16][16];
		for (AABB a : shape.toAabbs()) {
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						double cx = (x + 0.5) / 16;
						double cy = (y + 0.5) / 16;
						double cz = (z + 0.5) / 16;
						if (cx > a.minX && cx < a.maxX && cy > a.minY && cy < a.maxY && cz > a.minZ && cz < a.maxZ) {
							v[x][y][z] = true;
						}
					}
				}
			}
		}
		return v;
	}

	/** Null when each set lies within {@link #TOLERANCE} px of the other, else the first voxel that does not. */
	private static @Nullable String compare(boolean[][][] drawn, boolean[][][] outline) {
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					if (drawn[x][y][z] && !near(outline, x, y, z)) {
						return "drawn voxel " + x + "," + y + "," + z + " outside the outline";
					}
					if (outline[x][y][z] && !near(drawn, x, y, z)) {
						return "outline voxel " + x + "," + y + "," + z + " not drawn";
					}
				}
			}
		}
		return null;
	}

	private static boolean near(boolean[][][] v, int x, int y, int z) {
		for (int dx = -TOLERANCE; dx <= TOLERANCE; dx++) {
			for (int dy = -TOLERANCE; dy <= TOLERANCE; dy++) {
				for (int dz = -TOLERANCE; dz <= TOLERANCE; dz++) {
					int a = x + dx;
					int b = y + dy;
					int c = z + dz;
					if (a >= 0 && a < 16 && b >= 0 && b < 16 && c >= 0 && c < 16 && v[a][b][c]) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static double[] vec(JsonArray a) {
		return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
	}

	/** The template (arm down) turned to the parent around the block center, as {@code TrunkModelRayTest}. */
	private static double[] rotatePoint(Direction parent, double[] p) {
		double x = p[0] - 8;
		double y = p[1] - 8;
		double z = p[2] - 8;
		double[] r = switch (parent) {
			case DOWN -> new double[] {x, y, z};
			case UP -> new double[] {x, -y, -z};
			case NORTH -> new double[] {x, -z, y};
			case SOUTH -> new double[] {x, z, -y};
			case WEST -> new double[] {y, -x, z};
			case EAST -> new double[] {-y, x, z};
		};
		return new double[] {r[0] + 8, r[1] + 8, r[2] + 8};
	}

	/** A block getter over a map of states (air elsewhere), not a level: as a region of world generation. */
	private static BlockGetter getter(Map<BlockPos, BlockState> states) {
		return new BlockGetter() {
			@Override
			public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return states.getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState());
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return getBlockState(pos).getFluidState();
			}

			@Override
			public int getHeight() {
				return 384;
			}

			@Override
			public int getMinY() {
				return -64;
			}
		};
	}
}
