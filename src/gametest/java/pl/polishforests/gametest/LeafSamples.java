package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.SpeciesSaplingBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.client.ClientSeasonPhase;
import pl.polishforests.season.FoliageCurve;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The leaves board of step V7 of docs/04-pionowy-wycinek.md ({@code -Pgametest=slice -Psites=leaves}): in the flat test
 * world a swatch (a cube 5 × 5 × 4 of persistent leaves, phase 1) and a mature open-grown planner tree of every species
 * with a leaf block, a row of trees grown from the saplings, a blocked sapling, the potted saplings, the LITE Scots pine
 * set, and two leaf screens (species oak leaves and vanilla oak leaves) against the sky.
 * <ul>
 * <li><b>Seasons</b> (with Serene Seasons by {@code season set}, without it by {@code time set} on the built-in calendar,
 * there also the 3rd color step of MID_SPRING and the 2nd and 3rd of MID_AUTUMN): LATE_SPRING (leaf-out of the
 * species at different dates), MID_SUMMER, MID_AUTUMN, LATE_AUTUMN and MID_WINTER.
 * For every species the tint of {@code BlockColors} must equal the curve ({@link FoliageCurve}) at the client's color step,
 * the swatch on the screen must have the curve's chromaticity (median of the central third, ±0.03, as V3; since the V7
 * review also in winter, with the light twig haze), the tint of the marcescent leaves of oak, hornbeam and beech (a column
 * behind the swatch) must equal their curve, and the raw foliage color of the biome must not
 * change with the season (no double seasoning by SS). Screenshots: every swatch, the tree rows and the sapling trees.
 * <li><b>Fast</b> graphics mode (MID_SUMMER): the species leaves are leaves ({@code instanceof LeavesBlock}) and their
 * screen is as cut out or as opaque as the vanilla oak screen in the same renderer.
 * <li><b>Saplings</b>: every sapling grows (two {@code advanceTree}) into exactly the tree of its configured feature
 * {@code polishforests:sapling/<species>} (every planned wood block in place; the young oak, hornbeam and beech with all
 * leaves marcescent, the others with none); a sapling under a stone block takes no bone meal and does not grow, a sapling
 * on farmland grows and leaves dirt; leaves and saplings are compostable, the pots hold their saplings.
 * <li><b>LITE pine</b>: the tags (planks, logs that burn, leaves, saplings) are loaded, an axe strips the log keeping its
 * axis, and the planks recipe exists.
 * </ul>
 * Results in {@code build/leaves/<renderer>_<ss|noss>.txt}, screenshots {@code leaves_<renderer>_<ss|noss>_<view>}.
 */
final class LeafSamples {
	private static final int NOON = 6000;
	private static final int[] SIZE = {854, 480};
	private static final int[] WIDE = {1280, 720};
	private static final double CHROMA_TOLERANCE = 0.03;
	/** Swatches: x step, depth (z) and the z of their front face. */
	private static final int SWATCH_STEP = 8;
	private static final int SWATCH_Z = -30;
	private static final int TREE_Z = 40;
	private static final int TREE_STEP = 16;
	private static final int SAPLING_Z = 90;
	private static final int PINE_SET_Z = -45;
	private static final int SCREEN_Z = -60;
	private static final Object[][] SEQUENCE = {{SubSeason.MID_SPRING, 8}, {SubSeason.LATE_SPRING, 0}, {SubSeason.MID_SUMMER, 0},
			{SubSeason.MID_AUTUMN, 0},
			{SubSeason.MID_AUTUMN, 4}, {SubSeason.MID_AUTUMN, 8}, {SubSeason.LATE_AUTUMN, 0}, {SubSeason.MID_WINTER, 0}};

	private final List<String> results = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<TreeFoliage, Integer> raw = new LinkedHashMap<>();

	private LeafSamples() {
	}

	static void run(ClientGameTestContext context) {
		new LeafSamples().board(context);
	}

	private void board(ClientGameTestContext context) {
		boolean seasons = FabricLoader.getInstance().isModLoaded("sereneseasons");
		boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
		boolean nvidium = context.computeOnClient(mc -> RenderProbesClientGameTest.nvidiumEnabled());
		String tag = (!sodium ? "vanilla" : nvidium ? "sodium_nvidium" : "sodium") + (seasons ? "_ss" : "_noss");
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.cutoutLeaves().set(true);
			mc.options.renderDistance().set(10);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
					"gamerule send_command_feedback false", "time set " + NOON, "weather clear", "difficulty peaceful"}) {
				sp.getServer().runCommand(c);
			}
			int y0 = sp.getServer().computeOnServer(s -> build(s));
			result("%s: calendar %s, board at y %d", tag, Seasons.provider().name(), y0);
			sp.getServer().computeOnServer(s -> {
				saplingsAndSet(s, y0);
				return 0;
			});
			sp.getServer().runCommand("gamemode spectator @a");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(40);
			waitCompiled(context);
			String p = "leaves_" + tag + "_";
			shot(context, sp, p + "pine_set", 34, y0 + 1.5, PINE_SET_Z - 5, 0, 15, SIZE);
			shot(context, sp, p + "potted", 8, y0 + 1.2, PINE_SET_Z - 3, 0, 30, SIZE);
			for (Object[] s : SEQUENCE) {
				SubSeason sub = (SubSeason) s[0];
				int day = (Integer) s[1];
				if (seasons && day != 0) {
					continue;
				}
				int expected = setSeason(context, sp, sub, day, seasons);
				String name = sub.name().toLowerCase(Locale.ROOT) + (day == 0 ? "" : "_d" + day);
				check(context, sp, y0, p, name, expected);
			}
			fast(context, sp, y0, p, seasons);
			fastWinter(context, sp, y0, p, seasons);
			sp.getServer().runCommand("gamemode creative @a");
		}
		write(tag);
		if (!failures.isEmpty()) {
			throw new AssertionError("[leaves] " + failures.size() + " failures:\n" + String.join("\n", failures));
		}
	}

	// ------------------------------------------------------------------ board

	/** Builds the swatches, the mature trees, the phase strips and the leaf screens; returns the y of the ground. */
	private int build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		TreeFoliage[] all = TreeFoliage.values();
		for (int i = 0; i < all.length; i++) {
			BlockState leaf = ModBlocks.leaves(all[i]).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
					.setValue(SpeciesLeavesBlock.PHASE, 1);
			int x0 = i * SWATCH_STEP;
			for (int x = 0; x < 5; x++) {
				for (int y = 0; y < 5; y++) {
					for (int z = 0; z < 4; z++) {
						level.setBlock(new BlockPos(x0 + x, y0 + y, SWATCH_Z + z), leaf, 2);
					}
				}
			}
			// The four phases side by side behind the swatch (a column of 3 blocks each), then the marcescent leaves (phase 1).
			for (int phase = 0; phase < 4; phase++) {
				for (int y = 0; y < 3; y++) {
					level.setBlock(new BlockPos(x0 + phase, y0 + y, SWATCH_Z + 8), leaf.setValue(SpeciesLeavesBlock.PHASE, phase), 2);
				}
			}
			if (all[i].marcescent()) {
				for (int y = 0; y < 3; y++) {
					level.setBlock(new BlockPos(x0 + 4, y0 + y, SWATCH_Z + 8), leaf.setValue(MarcescentLeavesBlock.MARCESCENT, true), 2);
				}
			}
			ProceduralTreeFeature f = new ProceduralTreeFeature(all[i].species(), CrownLayer.CANOPY, AgeClass.MATURE);
			if (!f.place(level, level.getChunkSource().getGenerator(), level.getRandom(), new BlockPos(i * TREE_STEP, y0, TREE_Z))) {
				fail("mature %s tree not placed", all[i].species().id());
			}
		}
		// Leaf screens one block thick against the sky: the species oak leaves and vanilla oak leaves.
		BlockState ours = ModBlocks.leaves(TreeFoliage.OAK).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState vanilla = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int x = 0; x < 7; x++) {
			for (int y = 3; y < 8; y++) {
				level.setBlock(new BlockPos(x, y0 + y, SCREEN_Z), ours, 2);
				level.setBlock(new BlockPos(x + 12, y0 + y, SCREEN_Z), vanilla, 2);
			}
		}
		return y0;
	}

	/** Saplings (growth, room check, bone meal, compost, pots) and the LITE pine set (tags, stripping, recipe). */
	private void saplingsAndSet(MinecraftServer server, int y0) {
		ServerLevel level = server.overworld();
		TreeFoliage[] all = TreeFoliage.values();
		int grown = 0;
		for (int i = 0; i < all.length; i++) {
			SpeciesSaplingBlock sapling = ModBlocks.sapling(all[i]);
			BlockPos pos = new BlockPos(i * TREE_STEP, y0, SAPLING_Z);
			level.setBlock(pos, sapling.defaultBlockState(), 3);
			if (!sapling.isValidBonemealTarget(level, pos, level.getBlockState(pos), BonemealSource.INTERACTION)) {
				fail("%s sapling on free ground takes no bone meal", all[i].species().id());
			}
			sapling.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
			if (level.getBlockState(pos).getValue(SaplingBlock.STAGE) != 1) {
				fail("%s sapling did not reach stage 1", all[i].species().id());
			}
			sapling.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
			Feature feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValueOrThrow(sapling.feature());
			if (!(feature instanceof ProceduralTreeFeature tree)) {
				fail("%s: %s is not a procedural tree", all[i].species().id(), sapling.feature().identifier());
				continue;
			}
			TreeVoxels v = tree.plan(level.getSeed(), pos);
			int missing = 0;
			int leaves = 0;
			int dry = 0;
			for (int k = 0; k < v.woodCount(); k++) {
				BlockPos w = new BlockPos(v.x() + v.woodX(k), v.y() + v.woodY(k), v.z() + v.woodZ(k));
				if (level.getBlockState(w) != ProceduralTreeFeature.wood(all[i].species(), v.bark(k), v.radius(k),
						Direction.from3DDataValue(v.parent(k)))) {
					missing++;
				}
			}
			for (int k = 0; k < v.leafCount(); k++) {
				BlockState s = level.getBlockState(new BlockPos(v.x() + v.leafX(k), v.y() + v.leafY(k), v.z() + v.leafZ(k)));
				if (s.is(ModBlocks.leaves(all[i]))) {
					leaves++;
					if (MarcescentLeavesBlock.marcescent(s)) {
						dry++;
					}
				}
			}
			int height = 0;
			for (int k = 0; k < v.woodCount(); k++) {
				height = Math.max(height, v.woodY(k) + 1);
			}
			result("sapling %s grew into %s: %d wood blocks (%d missing), %d of %d leaves (%d marcescent), %d blocks tall, phase %d",
					all[i].species().id(), sapling.feature().identifier(), v.woodCount(), missing, leaves, v.leafCount(), dry, height,
					ProceduralTreeFeature.phase(level.getSeed(), pos.getX(), pos.getZ()));
			// A young tree (pole stage) of oak, hornbeam or beech keeps all its dead leaves over the winter; others none.
			if (dry != (all[i].marcescent() ? leaves : 0)) {
				fail("sapling %s: %d of %d leaves marcescent", all[i].species().id(), dry, leaves);
			}
			if (missing > 0 || leaves < v.leafCount() * 0.95 || v.woodCount() == 0) {
				fail("sapling %s: the grown tree differs from the planner (%d wood missing, %d of %d leaves)", all[i].species().id(),
						missing, leaves, v.leafCount());
			} else {
				grown++;
			}
			// Compost and the pot.
			if (!new ItemStack(sapling).has(DataComponents.COMPOSTABLE) || !new ItemStack(ModBlocks.leaves(all[i])).has(
					DataComponents.COMPOSTABLE)) {
				fail("%s sapling or leaves not compostable", all[i].species().id());
			}
			if (ModBlocks.potted(all[i]).getPotted() != sapling) {
				fail("%s pot holds %s", all[i].species().id(), ModBlocks.potted(all[i]).getPotted());
			}
			level.setBlock(new BlockPos(i * 2, y0, PINE_SET_Z + 2), ModBlocks.potted(all[i]).defaultBlockState(), 3);
		}
		result("saplings: %d of %d grew into the trees of their features", grown, all.length);
		// A sapling with a stone block 4 above it: no room for the trunk.
		SpeciesSaplingBlock pine = ModBlocks.sapling(TreeFoliage.SCOTS_PINE);
		BlockPos blocked = new BlockPos(all.length * TREE_STEP + 8, y0, SAPLING_Z);
		level.setBlock(blocked.above(4), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(blocked, pine.defaultBlockState(), 3);
		boolean meal = pine.isValidBonemealTarget(level, blocked, level.getBlockState(blocked), BonemealSource.INTERACTION);
		for (int k = 0; k < 3; k++) {
			pine.advanceTree(level, blocked, level.getBlockState(blocked), level.getRandom());
		}
		boolean still = level.getBlockState(blocked).is(pine);
		result("blocked sapling: takes bone meal %s, still a sapling after 3 advances %s", meal, still);
		if (meal || !still) {
			fail("a sapling under a stone block took bone meal (%s) or grew (%s)", meal, !still);
		}
		// A birch sapling on farmland grows (as vanilla saplings do) and the farmland turns into dirt.
		SpeciesSaplingBlock birch = ModBlocks.sapling(TreeFoliage.BIRCH);
		BlockPos farm = new BlockPos(all.length * TREE_STEP + 24, y0, SAPLING_Z);
		level.setBlock(farm.below(), Blocks.FARMLAND.defaultBlockState(), 3);
		level.setBlock(farm, birch.defaultBlockState(), 3);
		boolean farmMeal = birch.isValidBonemealTarget(level, farm, level.getBlockState(farm), BonemealSource.INTERACTION);
		birch.advanceTree(level, farm, level.getBlockState(farm), level.getRandom());
		birch.advanceTree(level, farm, level.getBlockState(farm), level.getRandom());
		boolean farmTree = !level.getBlockState(farm).is(birch) && !level.getBlockState(farm).isAir();
		boolean dirt = level.getBlockState(farm.below()).is(Blocks.DIRT);
		result("sapling on farmland: takes bone meal %s, grew %s, farmland turned into dirt %s", farmMeal, farmTree, dirt);
		if (!farmMeal || !farmTree || !dirt) {
			fail("a sapling on farmland: bone meal %s, grew %s, dirt %s", farmMeal, farmTree, dirt);
		}
		// The LITE pine set: tags, stripping with an axe (the axis kept), the planks recipe.
		boolean tags = ModBlocks.pinePlanks().defaultBlockState().is(BlockTags.PLANKS)
				&& ModBlocks.pineLog().defaultBlockState().is(BlockTags.LOGS)
				&& ModBlocks.strippedPineWood().defaultBlockState().is(BlockTags.LOGS)
				&& ModBlocks.leaves(TreeFoliage.OAK).defaultBlockState().is(BlockTags.LEAVES)
				&& ModBlocks.sapling(TreeFoliage.LINDEN).defaultBlockState().is(BlockTags.SAPLINGS)
				&& ModBlocks.potted(TreeFoliage.BEECH).defaultBlockState().is(BlockTags.FLOWER_POTS)
				&& new ItemStack(ModBlocks.pinePlanks()).is(ItemTags.PLANKS) && new ItemStack(ModBlocks.pineLog()).is(ItemTags.LOGS_THAT_BURN)
				&& new ItemStack(ModBlocks.leaves(TreeFoliage.SPRUCE)).is(ItemTags.LEAVES)
				&& new ItemStack(ModBlocks.sapling(TreeFoliage.ASH)).is(ItemTags.SAPLINGS);
		result("LITE pine and foliage tags loaded: %s", tags);
		if (!tags) {
			fail("tags of the LITE pine set, leaves or saplings missing");
		}
		int x = 30;
		List<BlockState> set = List.of(ModBlocks.pineLog().defaultBlockState(),
				ModBlocks.pineLog().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), ModBlocks.pineWood().defaultBlockState(),
				ModBlocks.strippedPineLog().defaultBlockState(), ModBlocks.strippedPineWood().defaultBlockState(),
				ModBlocks.pinePlanks().defaultBlockState());
		for (BlockState s : set) {
			level.setBlock(new BlockPos(x, y0, PINE_SET_Z), s, 3);
			level.setBlock(new BlockPos(x, y0 + 1, PINE_SET_Z), s, 3);
			x += 2;
		}
		BlockPos strip = new BlockPos(x, y0, PINE_SET_Z);
		level.setBlock(strip, ModBlocks.pineLog().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), 3);
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		player.setItemInHand(InteractionHand.MAIN_HAND, axe);
		InteractionResult r = axe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(strip), Direction.UP, strip, false)));
		BlockState after = level.getBlockState(strip);
		boolean stripped = after.is(ModBlocks.strippedPineLog()) && after.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z;
		result("axe on a pine log (axis z): %s, now %s", r, after);
		if (!stripped) {
			fail("the axe did not strip the pine log keeping its axis: %s", after);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		boolean recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
				PolishForests.id("scots_pine_planks"))).isPresent();
		result("planks recipe polishforests:scots_pine_planks present: %s", recipe);
		if (!recipe) {
			fail("the planks recipe is missing");
		}
	}

	// ------------------------------------------------------------------ seasons

	private int setSeason(ClientGameTestContext context, TestSingleplayerContext sp, SubSeason sub, int day, boolean seasons) {
		if (seasons) {
			sp.getServer().runCommand("season set " + sub.name().toLowerCase(Locale.ROOT));
		} else {
			long ticks = Seasons.BUILT_IN.dayTimeOf(sub, day) + NOON;
			sp.getServer().runCommand("time set " + ticks);
		}
		int expected = SeasonPhase.firstStep(sub) + (seasons ? 0 : day / 4);
		int waited = context.waitFor(mc -> ClientSeasonPhase.step() == expected, 400);
		context.waitFor(mc -> !ClientSeasonPhase.rebuilding(), 600);
		waitCompiled(context);
		context.waitTicks(20);
		result("%s day %d: color step %d after %d ticks, %d rebuilds", sub, day, ClientSeasonPhase.step(), waited,
				ClientSeasonPhase.rebuilds());
		return expected;
	}

	private void check(ClientGameTestContext context, TestSingleplayerContext sp, int y0, String p, String name, int step) {
		TreeFoliage[] all = TreeFoliage.values();
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < all.length; i++) {
			TreeFoliage f = all[i];
			BlockPos pos = new BlockPos(i * SWATCH_STEP + 2, y0 + 2, SWATCH_Z);
			int[] t = context.computeOnClient(mc -> {
				BlockState s = mc.level.getBlockState(pos);
				if (!(s.getBlock() instanceof SpeciesLeavesBlock)) {
					throw new AssertionError("The client has " + s + " at the swatch of " + f);
				}
				int rawColor = mc.level.getBlockTint(pos, ClientSeasonPhase.RAW_FOLIAGE);
				// Step V13: a leafless crown shows the bare twigs, tinted by the twig color of the species (tint index 1).
				boolean bare = FoliageCurve.leafless(f.species(), step, 1, false);
				int curve = 0xFF000000 | (bare ? FoliageCurve.twigs(f.species(), rawColor) : FoliageCurve.color(f.species(), step, 1, rawColor));
				int tint = mc.getBlockColors().getTintSources(s).get(bare ? 1 : 0).colorInWorld(s, mc.level, pos);
				int dryCurve = curve;
				int dryTint = tint;
				if (f.marcescent()) {
					BlockPos dryPos = new BlockPos(pos.getX() + 2, pos.getY() - 1, SWATCH_Z + 8);
					BlockState d = mc.level.getBlockState(dryPos);
					if (!MarcescentLeavesBlock.marcescent(d)) {
						throw new AssertionError("The client has " + d + " at the marcescent leaves of " + f);
					}
					dryCurve = 0xFF000000 | FoliageCurve.color(f.species(), step, 1, true, mc.level.getBlockTint(dryPos,
							ClientSeasonPhase.RAW_FOLIAGE));
					dryTint = mc.getBlockColors().getTintSources(d).getFirst().colorInWorld(d, mc.level, dryPos);
				}
				return new int[] {rawColor, curve, tint, dryCurve, dryTint, bare ? 1 : 0};
			});
			if (t[3] != t[4]) {
				fail("%s %s marcescent: BlockColors gives %s, the curve %s", name, f.species().id(), RenderProbesClientGameTest.hex(t[4]),
						RenderProbesClientGameTest.hex(t[3]));
			}
			Path shot = shot(context, sp, p + "swatch_" + f.species().id() + "_" + name, i * SWATCH_STEP + 2.5, y0 + 2.5 - 1.62,
					SWATCH_Z - 3, 0, 0, SIZE);
			double[] m = RenderProbesClientGameTest.median(shot);
			double d = RenderProbesClientGameTest.chromaDistance(m, t[1]);
			line.append(String.format(Locale.ROOT, " %s %s%s (screen %s, %.3f);", f.species().id(), t[5] == 1 ? "twigs " : "",
					RenderProbesClientGameTest.hex(t[1]), RenderProbesClientGameTest.rgb(m), d));
			if (t[2] != t[1]) {
				fail("%s %s: BlockColors gives %s, the curve %s", name, f.species().id(), RenderProbesClientGameTest.hex(t[2]),
						RenderProbesClientGameTest.hex(t[1]));
			}
			Integer before = raw.putIfAbsent(f, t[0]);
			if (before != null && before != t[0]) {
				fail("%s %s: the raw foliage color changed with the season: %s -> %s", name, f.species().id(),
						RenderProbesClientGameTest.hex(before), RenderProbesClientGameTest.hex(t[0]));
			}
			// Step V13: through a swatch of bare twigs the sky and the columns behind it show, so its median is only logged
			// (the twig color is checked by BlockColors above, the twigs themselves by fastWinter).
			if (d > CHROMA_TOLERANCE && t[5] == 0) {
				fail("%s %s: the swatch %s is not the curve %s (chroma distance %.3f)", name, f.species().id(),
						RenderProbesClientGameTest.rgb(m), RenderProbesClientGameTest.hex(t[1]), d);
			}
		}
		result("%s step %d:%s", name, step, line);
		shot(context, sp, p + "trees_left_" + name, 2 * TREE_STEP, y0 + 9, TREE_Z - 34, 0, -8, WIDE);
		shot(context, sp, p + "trees_right_" + name, 6 * TREE_STEP, y0 + 9, TREE_Z - 34, 0, -8, WIDE);
		shot(context, sp, p + "saplings_" + name, 4 * TREE_STEP, y0 + 10, SAPLING_Z - 45, 0, -5, WIDE);
		shot(context, sp, p + "swatches_" + name, 4 * SWATCH_STEP + 2, y0 + 6, SWATCH_Z - 26, 0, 8, WIDE);
	}

	/** The Fast graphics mode in MID_SUMMER: species leaves behave like vanilla leaves in the same renderer. */
	private void fast(ClientGameTestContext context, TestSingleplayerContext sp, int y0, String p, boolean seasons) {
		setSeason(context, sp, SubSeason.MID_SUMMER, 0, seasons);
		double fancy = RenderProbesClientGameTest.sky(shot(context, sp, p + "screen_fancy", 3.5, y0 + 2.2 - 1.62 + 1, SCREEN_Z - 3, 0,
				-30, SIZE));
		double fancyOak = RenderProbesClientGameTest.sky(shot(context, sp, p + "oak_screen_fancy", 15.5, y0 + 2.2 - 1.62 + 1,
				SCREEN_Z - 3, 0, -30, SIZE));
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(false));
		context.waitTicks(60);
		waitCompiled(context);
		boolean leaves = context.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(3, y0 + 4, SCREEN_Z))
				.getBlock() instanceof LeavesBlock);
		double sky = RenderProbesClientGameTest.sky(shot(context, sp, p + "screen_fast", 3.5, y0 + 2.2 - 1.62 + 1, SCREEN_Z - 3, 0, -30,
				SIZE));
		double oakSky = RenderProbesClientGameTest.sky(shot(context, sp, p + "oak_screen_fast", 15.5, y0 + 2.2 - 1.62 + 1,
				SCREEN_Z - 3, 0, -30, SIZE));
		shot(context, sp, p + "trees_left_fast", 2 * TREE_STEP, y0 + 9, TREE_Z - 34, 0, -8, WIDE);
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(true));
		result("fast: leaves block %s; screen sky Fancy %.1f%% / Fast %.1f%%, vanilla oak screen Fancy %.1f%% / Fast %.1f%%", leaves,
				100 * fancy, 100 * sky, 100 * fancyOak, 100 * oakSky);
		if (!leaves || (sky > 0.05) != (oakSky > 0.05) || Math.abs(fancy - fancyOak) > 0.08) {
			fail("fast: species leaves behave unlike vanilla leaves (screen sky %.1f%% / vanilla %.1f%%, Fancy %.1f%% / %.1f%%)",
					100 * sky, 100 * oakSky, 100 * fancy, 100 * fancyOak);
		}
	}

	/**
	 * Step V13 (T-D5, V3-b): in MID_WINTER the species oak screen shows bare twigs against the sky in the Fancy mode, and in
	 * the Fast mode the twigs stay cut out (the forceOpaque mixin) with as much sky as in the Fancy mode (the faces towards
	 * other leaves kept); the vanilla oak screen keeps its leaves.
	 */
	private void fastWinter(ClientGameTestContext context, TestSingleplayerContext sp, int y0, String p, boolean seasons) {
		setSeason(context, sp, SubSeason.MID_WINTER, 0, seasons);
		double fancy = RenderProbesClientGameTest.sky(shot(context, sp, p + "screen_fancy_mid_winter", 3.5, y0 + 2.2 - 1.62 + 1,
				SCREEN_Z - 3, 0, -30, SIZE));
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(false));
		context.waitTicks(60);
		waitCompiled(context);
		double sky = RenderProbesClientGameTest.sky(shot(context, sp, p + "screen_fast_mid_winter", 3.5, y0 + 2.2 - 1.62 + 1,
				SCREEN_Z - 3, 0, -30, SIZE));
		shot(context, sp, p + "trees_left_fast_mid_winter", 2 * TREE_STEP, y0 + 9, TREE_Z - 34, 0, -8, WIDE);
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(true));
		context.waitTicks(60);
		waitCompiled(context);
		result("winter twigs: screen sky Fancy %.1f%% / Fast %.1f%%", 100 * fancy, 100 * sky);
		if (fancy < 0.35 || sky < 0.35 || Math.abs(fancy - sky) > 0.08) {
			fail("winter twigs: screen sky Fancy %.1f%% / Fast %.1f%% (bare twigs must let the sky through, also in Fast mode)",
					100 * fancy, 100 * sky);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void waitCompiled(ClientGameTestContext context) {
		context.waitFor(LeafSamples::compiled, 1200);
	}

	private static boolean compiled(Minecraft mc) {
		try {
			return mc.levelRenderer.hasRenderedAllSections();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double y, double z,
			double yaw, double pitch, int[] size) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		context.waitTicks(10);
		waitCompiled(context);
		context.waitTicks(5);
		return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(size[0], size[1]));
	}

	private void result(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		results.add(line);
		PolishForests.LOG.info("[leaves] {}", line);
	}

	private void fail(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		failures.add(line);
		results.add("FAIL " + line);
		PolishForests.LOG.error("[leaves] FAIL {}", line);
	}

	private void write(String tag) {
		String dir = System.getProperty("polishforests.leaves.out");
		if (dir == null || dir.isBlank()) {
			return;
		}
		try {
			Path file = Path.of(dir, tag + ".txt");
			Files.createDirectories(file.getParent());
			Files.write(file, results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("Cannot write the leaves results", e);
		}
	}
}
