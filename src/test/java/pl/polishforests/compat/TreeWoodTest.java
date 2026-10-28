package pl.polishforests.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.RootFlare;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.DeadwoodPlan;
import pl.polishforests.worldgen.tree.DeadwoodVoxels;
import pl.polishforests.worldgen.tree.RootPlan;
import pl.polishforests.worldgen.tree.RootVoxels;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Step G1 (tree-felling mods): the wood of one tree found through the parent graph ({@link TreeWood}) is exactly the wood
 * the tree planner wrote for it, with its root flares and all barks, and nothing of a neighbor of the same species, a
 * shrub stem at its base, dead wood beside it or the underground roots; the recommended FallingTree limits cover the
 * largest tree.
 */
class TreeWoodTest {
	private static final long SEED = 20260927L;
	private static final int GROUND = 64;
	/** The wood rule of the test: the game's {@code #minecraft:logs} without bound tags. */
	private static TreeWood wood;

	@BeforeAll
	static void bootstrap() {
		GameRegistries.bootstrap();
		wood = new TreeWood(s -> s.getBlock() instanceof TrunkBlock && s.getBlock() != ModBlocks.treeRoot()
				|| s.getBlock() instanceof RotatedPillarBlock && s.getBlock() != Blocks.QUARTZ_PILLAR);
	}

	/** A world of a map: air where nothing was written, grass below {@link #GROUND}. */
	private static final class World implements TreeWood.Blocks {
		final Map<BlockPos, BlockState> blocks = new HashMap<>();

		@Override
		public BlockState state(BlockPos pos) {
			BlockState s = blocks.get(pos);
			return s != null ? s : pos.getY() < GROUND ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		boolean free(BlockPos pos) {
			BlockState s = state(pos);
			return s.isAir() || s.getBlock() instanceof LeavesBlock;
		}

		/** Writes a planned tree as the feature does (a block whose parent was not written is left out); its wood. */
		Set<BlockPos> tree(TreeVoxels v, Species species) {
			Set<BlockPos> out = new HashSet<>();
			boolean[] written = new boolean[v.woodCount()];
			for (int i = 0; i < v.woodCount(); i++) {
				int p = v.parentIndex(i);
				BlockPos at = new BlockPos(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i));
				if (p >= 0 && !written[p] || !free(at)) {
					continue;
				}
				blocks.put(at, ProceduralTreeFeature.wood(species, v.bark(i), v.radius(i), Direction.from3DDataValue(v.parent(i))));
				written[i] = true;
				out.add(at);
			}
			BlockState leaf = ProceduralTreeFeature.leaves(species, 0);
			for (int i = 0; i < v.leafCount(); i++) {
				BlockPos at = new BlockPos(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i));
				if (state(at).isAir() && v.leafDistance(i) >= 1) {
					blocks.put(at, leaf.setValue(LeavesBlock.DISTANCE, v.leafDistance(i)));
				}
			}
			return out;
		}

		/** Writes the root flares (into air only) and the underground roots of a tree; the flares. */
		Set<BlockPos> roots(RootVoxels v, Species species) {
			Set<BlockPos> out = new HashSet<>();
			RootFlare flare = RootFlare.of(species);
			for (int i = 0; i < v.flareCount() && flare != null; i++) {
				BlockPos at = new BlockPos(v.x() + v.flareX(i), v.y() + v.flareY(i), v.z() + v.flareZ(i));
				int p = v.flareParentIndex(i);
				if (p >= 0 && !out.contains(new BlockPos(v.x() + v.flareX(p), v.y() + v.flareY(p), v.z() + v.flareZ(p)))
						|| !state(at).isAir()) {
					continue;
				}
				blocks.put(at, ModBlocks.flare(flare).state(v.flareRadius(i), Direction.from3DDataValue(v.flareParent(i))));
				out.add(at);
			}
			for (int i = 0; i < v.rootCount(); i++) {
				if (v.rootKind(i) == RootVoxels.ROOT) {
					blocks.put(new BlockPos(v.x() + v.rootX(i), v.y() + v.rootY(i), v.z() + v.rootZ(i)),
							ModBlocks.treeRoot().state(v.rootRadius(i), Direction.from3DDataValue(v.rootParent(i))));
				}
			}
			return out;
		}
	}

	private static TreeVoxels plan(Species s, CrownLayer layer, AgeClass age, int x, int z) {
		return TreeShapePlan.plan(new TreeRequest(s, layer, age, SEED, x, GROUND, z));
	}

	private static RootVoxels rootPlan(Species s, CrownLayer layer, AgeClass age, TreeVoxels tree) {
		return RootPlan.plan(new RootPlan.Request(s, layer, age, SEED, tree.x(), GROUND, tree.z(), tree.radius(0),
				(wx, wz) -> GROUND - 1, null));
	}

	private static Set<BlockPos> component(World w, BlockPos start) {
		TreeWood.Tree t = wood.of(w, start);
		assertNotNull(t, "no tree at " + start);
		Set<BlockPos> set = new HashSet<>(t.wood());
		assertEquals(t.wood().size(), set.size(), "a block found twice");
		return set;
	}

	/**
	 * Every species, layer and age class alone: the tree found from the trunk base, from the topmost wood block and from a
	 * flare is all the wood written for it, flares included; the log drops are its blocks of 3 px and more and full logs.
	 */
	@Test
	void everyPlannedTreeIsOneTree() {
		int largest = 0;
		String largestName = "";
		StringBuilder report = new StringBuilder();
		for (Species s : SpeciesShape.species()) {
			for (CrownLayer layer : List.of(CrownLayer.CANOPY, CrownLayer.SUBCANOPY)) {
				for (AgeClass age : AgeClass.values()) {
					World w = new World();
					TreeVoxels v = plan(s, layer, age, 0, 0);
					Set<BlockPos> expected = new HashSet<>(w.tree(v, s));
					expected.addAll(w.roots(rootPlan(s, layer, age, v), s));
					BlockPos base = new BlockPos(v.x(), v.y(), v.z());
					BlockPos top = expected.stream().max((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow();
					String name = s.id() + " " + layer + " " + age;
					assertEquals(expected, component(w, base), name);
					assertEquals(expected, component(w, top), name + " from the top");
					int drops = 0;
					int full = 0;
					Set<Block> barks = new HashSet<>();
					for (BlockPos p : expected) {
						BlockState b = w.state(p);
						assertTrue(wood.wood(b), name + ": " + b);
						drops += TreeWood.dropsLog(b) ? 1 : 0;
						full += b.getBlock() instanceof TrunkBlock ? 0 : 1;
						barks.add(b.getBlock());
					}
					TreeWood.Tree t = wood.of(w, base);
					assertEquals(drops, t.logDrops(), name);
					assertEquals(expected.size() - full, t.trunks(), name);
					if (s == Species.SCOTS_PINE && age == AgeClass.OLD_GROWTH && layer == CrownLayer.CANOPY) {
						for (TreeTrunk bark : List.of(TreeTrunk.SCOTS_PINE, TreeTrunk.SCOTS_PINE_TRANSITION,
								TreeTrunk.SCOTS_PINE_TRANSITION_UPPER, TreeTrunk.SCOTS_PINE_UPPER)) {
							assertTrue(barks.contains(ModBlocks.trunk(bark)), "old pine without " + bark.id() + ": " + barks);
						}
					}
					if (expected.size() > largest) {
						largest = expected.size();
						largestName = name;
					}
					if (layer == CrownLayer.CANOPY) {
						report.append(String.format(Locale.ROOT, "%n  %-26s wood %4d, log drops %4d (%.0f%%), full logs %3d",
								s.id() + " " + age.name().toLowerCase(Locale.ROOT), expected.size(), drops,
								100.0 * drops / expected.size(), full));
					}
				}
			}
		}
		System.out.println("[G1] wood of the planner trees (canopy), largest " + largest + " (" + largestName + "):" + report);
	}

	/**
	 * FallingTree fells a tree only with a leaf next to its topmost wood block ({@code minimumLeavesAroundRequired} 1):
	 * every wood block at the top of every planned tree (species, layer, age class, 8 places) has a leaf on a side.
	 */
	@Test
	void topmostWoodHasLeaves() {
		int trees = 0;
		List<String> bare = new ArrayList<>();
		for (Species s : SpeciesShape.species()) {
			for (CrownLayer layer : List.of(CrownLayer.CANOPY, CrownLayer.SUBCANOPY)) {
				for (AgeClass age : AgeClass.values()) {
					for (int k = 0; k < 8; k++) {
						World w = new World();
						Set<BlockPos> wood = w.tree(plan(s, layer, age, 53 * k, 31 * k), s);
						int top = wood.stream().mapToInt(BlockPos::getY).max().orElseThrow();
						trees++;
						for (BlockPos p : wood) {
							if (p.getY() == top && TreeWood.leavesAround(w, List.of(p), 1, false) == 0) {
								bare.add(s.id() + " " + layer + " " + age + " #" + k + " at " + p.toShortString());
							}
						}
					}
				}
			}
		}
		System.out.println("[G1] topmost wood without a leaf: " + bare.size() + " of " + trees + " trees " + bare);
		assertTrue(bare.isEmpty(), "topmost wood without a leaf: " + bare);
	}

	/**
	 * Two trees of a species and age close enough for their crowns to overlap and their wood to come within a block, with
	 * a shrub stem of the same trunk block at the trunk base and a lying log and a windthrow beside it: the trees stay
	 * apart, the stem and the dead wood are not part of either, the underground roots never are.
	 */
	@Test
	void neighborsShrubsAndDeadWoodStayApart() {
		for (Species s : SpeciesShape.species()) {
			for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
				for (int gap : new int[] {2, 3, 5}) {
					World w = new World();
					TreeVoxels a = plan(s, CrownLayer.CANOPY, age, 0, 0);
					TreeVoxels b = plan(s, CrownLayer.CANOPY, age, gap, 1);
					Set<BlockPos> wa = new HashSet<>(w.tree(a, s));
					Set<BlockPos> wb = new HashSet<>(w.tree(b, s));
					wa.addAll(w.roots(rootPlan(s, CrownLayer.CANOPY, age, a), s));
					wb.addAll(w.roots(rootPlan(s, CrownLayer.CANOPY, age, b), s));
					// A shrub stem of the same block one block diagonally from the trunk base (DP-34).
					Set<BlockPos> stem = new HashSet<>();
					TrunkBlock trunk = ModBlocks.trunk(ProceduralTreeFeature.trunk(s, TreeVoxels.LOWER));
					for (int y = 0; y < 3; y++) {
						stem.add(new BlockPos(-1, GROUND + y, -1));
					}
					if (stem.stream().allMatch(p -> w.state(p).isAir() || w.state(p).getBlock() instanceof LeavesBlock)) {
						stem.forEach(p -> w.blocks.put(p, trunk.state(1, Direction.DOWN)));
					} else {
						stem.clear();
					}
					// Dead wood beside the trees.
					List<Set<BlockPos>> dead = new ArrayList<>();
					for (DeadwoodPlan.Kind kind : List.of(DeadwoodPlan.Kind.LOG, DeadwoodPlan.Kind.WINDTHROW)) {
						int dx = kind == DeadwoodPlan.Kind.LOG ? -3 : 3;
						DeadwoodVoxels d = DeadwoodPlan.plan(new DeadwoodPlan.Request(s, kind, CrownLayer.CANOPY, AgeClass.OLD_GROWTH, true,
								SEED, dx, GROUND, -4, (x, z) -> GROUND - 1));
						if (!d.empty()) {
							Set<BlockPos> written = w.tree(d.wood(), s);
							written.removeIf(p -> !wood.wood(w.state(p)));
							dead.add(written);
						}
					}
					String name = s.id() + " " + age + " gap " + gap;
					Set<BlockPos> ca = component(w, new BlockPos(a.x(), a.y(), a.z()));
					assertEquals(wa, ca, name + ": first tree");
					BlockPos baseB = new BlockPos(b.x(), b.y(), b.z());
					Set<BlockPos> cb = wb.contains(baseB) ? component(w, baseB) : Set.of();
					if (wb.contains(baseB)) {
						assertEquals(wb, cb, name + ": second tree");
					}
					for (BlockPos p : stem) {
						assertFalse(ca.contains(p) || cb.contains(p), name + ": the shrub stem joined a tree");
					}
					for (Set<BlockPos> d : dead) {
						if (d.isEmpty()) {
							continue;
						}
						Set<BlockPos> cd = component(w, d.iterator().next());
						assertEquals(d, cd, name + ": dead wood");
						for (BlockPos p : d) {
							assertFalse(ca.contains(p) || cb.contains(p), name + ": dead wood joined a tree");
						}
					}
					for (BlockPos p : ca) {
						assertFalse(w.state(p).is(ModBlocks.treeRoot()), name + ": an underground root in the tree");
					}
				}
			}
		}
	}

	/** The link rules of single block pairs. */
	@Test
	void linkRules() {
		TrunkBlock oak = ModBlocks.trunk(TreeTrunk.OAK);
		BlockState up = oak.state(3, Direction.DOWN);
		// A thin trunk is linked to its parent, from either side, and to nothing else.
		assertTrue(wood.linked(up, Direction.DOWN, oak.state(4, Direction.DOWN)));
		assertTrue(wood.linked(oak.state(4, Direction.DOWN), Direction.UP, up));
		assertFalse(wood.linked(up, Direction.EAST, oak.state(3, Direction.DOWN)));
		assertTrue(wood.linked(oak.state(2, Direction.WEST), Direction.WEST, up));
		assertTrue(wood.linked(up, Direction.EAST, oak.state(2, Direction.WEST)));
		// Full logs: the same block along the axis of either; a thin trunk only through its parent.
		BlockState logY = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState logX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		assertTrue(wood.linked(logY, Direction.UP, logY));
		assertTrue(wood.linked(logY, Direction.EAST, logX));
		assertFalse(wood.linked(logY, Direction.NORTH, logX));
		assertFalse(wood.linked(logY, Direction.UP, Blocks.BIRCH_LOG.defaultBlockState()));
		assertTrue(wood.linked(logY, Direction.UP, up));
		assertFalse(wood.linked(logY, Direction.EAST, up));
		assertNull(TreeWood.side(BlockPos.ZERO, new BlockPos(1, 1, 0)));
		assertEquals(Direction.SOUTH, TreeWood.side(BlockPos.ZERO, new BlockPos(0, 0, 1)));
	}

	/** Wood without a thin trunk (a vanilla tree, a log cabin) is not a tree of this mod: the felling mods keep their rules. */
	@Test
	void vanillaWoodIsLeftToTheMods() {
		World w = new World();
		for (int y = 0; y < 6; y++) {
			w.blocks.put(new BlockPos(0, GROUND + y, 0), Blocks.OAK_LOG.defaultBlockState());
		}
		assertNull(wood.of(w, new BlockPos(0, GROUND + 2, 0)));
		assertNull(wood.of(w, new BlockPos(5, GROUND, 5)));
		// A thin trunk on the vanilla log makes it one of ours: the log is its parent.
		w.blocks.put(new BlockPos(0, GROUND + 6, 0), ModBlocks.trunk(TreeTrunk.OAK).state(3, Direction.DOWN));
		assertEquals(7, wood.of(w, new BlockPos(0, GROUND, 0)).size());
		// The underground root is not wood.
		w.blocks.put(new BlockPos(0, GROUND - 1, 0), ModBlocks.treeRoot().state(3, Direction.UP));
		assertEquals(7, wood.of(w, new BlockPos(0, GROUND + 3, 0)).size());
		assertNull(wood.of(w, new BlockPos(0, GROUND - 1, 0)));
	}

	/** Leaves next to the wood: counted up to the limit, a persistent one aborts when asked. */
	@Test
	void leavesAround() {
		World w = new World();
		BlockPos trunk = new BlockPos(0, GROUND, 0);
		w.blocks.put(trunk, ModBlocks.trunk(TreeTrunk.OAK).state(3, Direction.DOWN));
		BlockState leaf = ProceduralTreeFeature.leaves(Species.OAK, 0).setValue(LeavesBlock.DISTANCE, 1);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			w.blocks.put(trunk.relative(d), leaf);
		}
		assertEquals(4, TreeWood.leavesAround(w, List.of(trunk), 9, true));
		assertEquals(2, TreeWood.leavesAround(w, List.of(trunk), 2, true));
		w.blocks.put(trunk.above(), leaf.setValue(LeavesBlock.PERSISTENT, true));
		assertEquals(-1, TreeWood.leavesAround(w, List.of(trunk), 9, true));
		assertEquals(5, TreeWood.leavesAround(w, List.of(trunk), 9, false));
	}

	/**
	 * {@link TreeWood#dropsLog} (Tree Harvester's log count) follows the generated loot tables of every trunk and root
	 * flare: for each radius and parent the first entry after silk touch whose condition holds is the log exactly when
	 * {@code dropsLog} says so (G1-e), and a twig gives at most one stick (G1-f).
	 */
	@Test
	void logDropsFollowTheLootTables() throws IOException {
		Path dir = Path.of("src/main/generated/data/polishforests/loot_table/blocks");
		int tables = 0;
		for (TreeTrunk t : TreeTrunk.values()) {
			tables += checkLoot(dir.resolve(t.id() + ".json"), ModBlocks.trunk(t));
		}
		for (RootFlare f : RootFlare.values()) {
			tables += checkLoot(dir.resolve(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ModBlocks.flare(f)).getPath() + ".json"), ModBlocks.flare(f));
		}
		assertEquals(TreeTrunk.values().length + RootFlare.values().length, tables);
	}

	private static int checkLoot(Path file, TrunkBlock block) throws IOException {
		JsonObject table = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		com.google.gson.JsonArray children = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
				.getAsJsonObject().getAsJsonArray("children");
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			for (Direction d : Direction.values()) {
				BlockState state = block.state(r, d);
				JsonObject hit = null;
				for (int i = 1; i < children.size() && hit == null; i++) {
					JsonObject c = children.get(i).getAsJsonObject();
					if (!c.has("condition") || holds(c.get("condition").getAsJsonObject(), r, d)) {
						hit = c;
					}
				}
				assertNotNull(hit, file + " r" + r + " " + d);
				boolean log = !hit.get("name").getAsString().equals("minecraft:stick");
				assertEquals(TreeWood.dropsLog(state), log, file.getFileName() + " r" + r + " parent " + d);
				if (r == TrunkBlock.MIN_RADIUS) {
					assertTrue(hit.toString().contains("\"max\":1"), file.getFileName() + ": a twig gives more than one stick");
				}
			}
		}
		return 1;
	}

	/** A loot condition of the trunk tables for a radius and parent: match_block on state, any_of, survives_explosion. */
	private static boolean holds(JsonObject c, int r, Direction d) {
		return switch (c.get("type").getAsString()) {
			case "minecraft:any_of" -> {
				for (var term : c.getAsJsonArray("terms")) {
					if (holds(term.getAsJsonObject(), r, d)) {
						yield true;
					}
				}
				yield false;
			}
			case "minecraft:match_block" -> {
				JsonObject st = c.getAsJsonObject("state");
				yield (!st.has("radius") || st.get("radius").getAsString().equals(Integer.toString(r)))
						&& (!st.has("parent") || st.get("parent").getAsString().equals(d.getSerializedName()));
			}
			case "minecraft:survives_explosion" -> true;
			default -> throw new AssertionError("unexpected loot condition " + c);
		};
	}

	/**
	 * The recommended FallingTree configuration of the modpack ({@code tools/modpack/config/fallingtree.json}) fells the
	 * largest tree of the planner: its limits of the search and of the felled tree are above the wood of every tree with
	 * its flares (decision G1-c).
	 */
	@Test
	void recommendedFallingTreeLimitsCoverTheLargestTree() throws IOException {
		int largest = 0;
		for (Species s : SpeciesShape.species()) {
			for (AgeClass age : AgeClass.values()) {
				for (int k = 0; k < 4; k++) {
					World w = new World();
					TreeVoxels v = plan(s, CrownLayer.CANOPY, age, 97 * k, -61 * k);
					Set<BlockPos> all = new HashSet<>(w.tree(v, s));
					all.addAll(w.roots(rootPlan(s, CrownLayer.CANOPY, age, v), s));
					largest = Math.max(largest, all.size());
				}
			}
		}
		JsonObject config = JsonParser.parseString(Files.readString(Path.of("tools/modpack/config/fallingtree.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject trees = config.getAsJsonObject("trees");
		int maxSize = trees.get("maxSize").getAsInt();
		int maxScan = trees.get("maxScanSize").getAsInt();
		System.out.println("[G1] largest tree of 4 per species and age class: " + largest + " wood blocks; FallingTree maxSize "
				+ maxSize + ", maxScanSize " + maxScan);
		assertTrue(maxSize >= 2 * largest, "maxSize " + maxSize + " below twice the largest tree, " + largest);
		assertTrue(maxScan >= maxSize, "maxScanSize " + maxScan + " below maxSize " + maxSize);
	}
}
