package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import pl.polishforests.PolishForests;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.norms.CensusGrid;
import pl.polishforests.worldgen.norms.CensusTestPlot;
import pl.polishforests.worldgen.norms.ForestCensus;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.norms.ForestNorms.Forest;
import pl.polishforests.worldgen.norms.ForestNorms.Norm;
import pl.polishforests.worldgen.tree.AgeClass;

/**
 * The census of the norms card in the game (step V4, docs/04-pionowy-wycinek.md §6): reads the blocks of a sample plot
 * or a transect of the world into a {@link CensusGrid}, counts it with {@link ForestCensus} and writes the rows of the
 * report ({@link #write}: {@code build/norms/karta-norm.csv}, one row per metric with its norm and verdict). Before the
 * plots of a run, the hand-built test plot ({@link CensusTestPlot}) is built from blocks in the sky, read back and
 * counted: the grid must equal the plot cell by cell and every value its value worked out by hand, which checks the
 * world reader as the unit test checks the census.
 *
 * <p>The species of the vanilla placeholder trees of M2 come from {@code ModFeatures.WOODY_PLACEMENTS}: the test records
 * the species of every tree and shrub feature placed while its worlds generate, by column.
 */
final class NormsCensus {
	/** Side of a sample plot (1 ha), cells above the ground, margin of the grid for whole crowns (PW-9: radius ≤ 16). */
	static final int PLOT = ForestNorms.PLOT_SIDE;
	static final int HEIGHT = 48;
	static final int MARGIN = 16;
	/** Half-width of the band along a transect, blocks (17 columns per step). */
	static final int BAND = 8;
	static final String HEADER = "scale,mode,place,forest,season,metric,part,value,norm,low,high,verdict,source";

	private NormsCensus() {
	}

	static long key(int x, int z) {
		return (long) x << 32 | z & 0xFFFFFFFFL;
	}

	// ------------------------------------------------------------------ reading the world

	/**
	 * The blocks of the columns of a rectangle for which {@code columns} holds: the ground is the highest block that is not
	 * air, leaves, wood or liquid and fills its block (or a dirt path, farmland), found from the top of the column down;
	 * the cells above it are leaves, wood (vanilla logs by axis, thin trunks by the side of their parent), the plant on
	 * the ground at dy 1 (its id, with {@link ForestCensus#MOSSY} when a {@code mossy} property is on) or other blocks.
	 * Loads the chunks of the rectangle and those around them, so the features of the neighbors are in.
	 */
	static CensusGrid read(ServerLevel level, int minX, int minZ, int sizeX, int sizeZ, int height, Map<Long, Species> records,
			BiPredicate<Integer, Integer> columns) {
		for (int cx = (minX >> 4) - 1; cx <= (minX + sizeX - 1 >> 4) + 1; cx++) {
			for (int cz = (minZ >> 4) - 1; cz <= (minZ + sizeZ - 1 >> 4) + 1; cz++) {
				if (near(cx, cz, minX, minZ, sizeX, sizeZ, columns)) {
					level.getChunk(cx, cz);
				}
			}
		}
		CensusGrid g = new CensusGrid(minX, minZ, sizeX, sizeZ, height);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int truncated = 0;
		for (int x = minX; x < minX + sizeX; x++) {
			for (int z = minZ; z < minZ + sizeZ; z++) {
				if (!columns.test(x, z)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockState s = null;
				for (; y >= level.getMinY(); y--) {
					s = level.getBlockState(pos.set(x, y, z));
					if (ground(s)) {
						break;
					}
				}
				if (y < level.getMinY()) {
					continue;
				}
				g.setGround(x, z, y, id(s));
				for (int dy = 1; dy <= height; dy++) {
					BlockState c = level.getBlockState(pos.set(x, y + dy, z));
					if (c.isAir()) {
						continue;
					}
					if (c.is(BlockTags.LEAVES)) {
						g.set(x, dy, z, CensusGrid.LEAVES);
					} else if (c.getBlock() instanceof TrunkBlock) {
						Direction parent = c.getValue(TrunkBlock.PARENT);
						g.setWood(x, dy, z, parent.getAxis() == Direction.Axis.Y, CensusGrid.trunk(c.getValue(TrunkBlock.RADIUS), species(c),
								name(c), parent.get3DDataValue()));
					} else if (c.is(BlockTags.LOGS)) {
						boolean up = !c.hasProperty(BlockStateProperties.AXIS) || c.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
						g.setWood(x, dy, z, up, CensusGrid.log(name(c)));
					} else if (dy == 1 && !(c.getBlock() instanceof LiquidBlock)) {
						g.setPlant(x, z, id(c));
					} else {
						g.set(x, dy, z, CensusGrid.OTHER);
					}
				}
				BlockState above = level.getBlockState(pos.set(x, y + height + 1, z));
				if (above.is(BlockTags.LEAVES) || above.is(BlockTags.LOGS)) {
					truncated++;
				}
				g.setRecord(x, z, records.get(key(x, z)));
			}
		}
		if (truncated > 0) {
			throw new AssertionError(truncated + " columns from (" + minX + ", " + minZ + ") have leaves or wood above the " + height
					+ " cells of the census");
		}
		return g;
	}

	private static boolean near(int cx, int cz, int minX, int minZ, int sizeX, int sizeZ, BiPredicate<Integer, Integer> columns) {
		// A chunk is needed when a column within one chunk of it is read (every 4th block is enough for the corridors).
		for (int x = Math.max(minX, (cx - 1) << 4); x <= Math.min(minX + sizeX - 1, ((cx + 2) << 4) - 1); x += 4) {
			for (int z = Math.max(minZ, (cz - 1) << 4); z <= Math.min(minZ + sizeZ - 1, ((cz + 2) << 4) - 1); z += 4) {
				if (columns.test(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean ground(BlockState s) {
		if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.getBlock() instanceof TrunkBlock
				|| s.getBlock() instanceof LiquidBlock) {
			return false;
		}
		return s.isSolidRender() || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND);
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}

	/** Block id, with {@link ForestCensus#MOSSY} when the block has a {@code mossy} property that is on. */
	private static String id(BlockState s) {
		for (Property<?> p : s.getProperties()) {
			if (p instanceof BooleanProperty b && b.getName().equals("mossy") && s.getValue(b)) {
				return name(s) + ForestCensus.MOSSY;
			}
		}
		return name(s);
	}

	private static Species species(BlockState s) {
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		for (TreeTrunk t : TreeTrunk.values()) {
			if (t.id().equals(path)) {
				return t.species();
			}
		}
		throw new AssertionError("A trunk block without a species: " + path);
	}

	// ------------------------------------------------------------------ the hand-built test plot

	/**
	 * Builds the test plot in the sky above (x, z), reads it back, compares the grid and the census with the plot and the
	 * values worked out by hand, then removes it. Returns a line for the log.
	 */
	static String checkTestPlot(ServerLevel level, int x, int z) {
		int groundY = level.getMaxY() - CensusTestPlot.HEIGHT - 8;
		CensusGrid plot = CensusTestPlot.grid(x, z, groundY);
		Map<Long, Species> records = new HashMap<>();
		int minX = plot.minX();
		int minZ = plot.minZ();
		for (int cx = minX; cx < minX + plot.sizeX(); cx++) {
			for (int cz = minZ; cz < minZ + plot.sizeZ(); cz++) {
				if (plot.record(cx, cz) != null) {
					records.put(key(cx, cz), plot.record(cx, cz));
				}
			}
		}
		build(level, plot);
		try {
			CensusGrid read = read(level, minX, minZ, plot.sizeX(), plot.sizeZ(), plot.height(), records, (a, b) -> true);
			List<String> differences = plot.differences(read, 20);
			if (!differences.isEmpty()) {
				throw new AssertionError("The test plot read from the world differs from the plot (expected vs read):\n"
						+ String.join("\n", differences));
			}
			boolean[] area = ForestCensus.rectangle(read, x, z, CensusTestPlot.SIZE, CensusTestPlot.SIZE);
			ForestCensus.Census census = ForestCensus.of(read);
			Map<String, Double> values = census.measure(area);
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<String, Double> e : CensusTestPlot.expected().entrySet()) {
				double v = values.get(e.getKey());
				double w = e.getValue();
				if (!(Double.isInfinite(w) ? v == w : Math.abs(v - w) < 1e-9)) {
					wrong.add(e.getKey() + ": expected " + w + ", counted " + v);
				}
			}
			if (!census.unclassified(area).equals(CensusTestPlot.expectedUnclassified())) {
				wrong.add("unclassified plants " + census.unclassified(area));
			}
			if (!wrong.isEmpty()) {
				throw new AssertionError("The census of the test plot in the world is wrong:\n" + String.join("\n", wrong));
			}
			return String.format(Locale.ROOT, "census test plot at (%d, %d, %d): grid read back cell by cell, %d values as counted by hand, "
					+ "%d objects", x, groundY, z, values.size(), census.objects().size());
		} finally {
			clear(level, plot);
		}
	}

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Places the blocks of a grid: ground, plants (double plants with their upper half), leaves and wood. */
	private static void build(ServerLevel level, CensusGrid g) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int x = g.minX(); x < g.minX() + g.sizeX(); x++) {
			for (int z = g.minZ(); z < g.minZ() + g.sizeZ(); z++) {
				int y = g.ground(x, z);
				level.getChunk(x >> 4, z >> 4);
				level.setBlock(pos.set(x, y, z), block(g.groundBlock(x, z)).defaultBlockState(), FLAGS);
				String plant = g.plant(x, z);
				if (plant != null) {
					Block b = block(plant);
					level.setBlock(pos.set(x, y + 1, z), b.defaultBlockState(), FLAGS);
					if (b instanceof DoublePlantBlock) {
						level.setBlock(pos.set(x, y + 2, z), b.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
					}
				}
				for (int dy = 1; dy <= g.height(); dy++) {
					byte kind = g.cell(x, dy, z);
					if (kind == CensusGrid.LEAVES) {
						level.setBlock(pos.set(x, y + dy, z), leaves, FLAGS);
					} else if (kind == CensusGrid.WOOD_UP || kind == CensusGrid.WOOD_SIDE) {
						CensusGrid.Wood w = g.wood(x, dy, z);
						Block b = block(w.block());
						boolean up = kind == CensusGrid.WOOD_UP;
						BlockState s = b instanceof TrunkBlock t ? t.state((int) Math.round(w.diameter() * 8), up ? Direction.DOWN : Direction.WEST)
								: b.defaultBlockState().setValue(BlockStateProperties.AXIS, up ? Direction.Axis.Y : Direction.Axis.X);
						level.setBlock(pos.set(x, y + dy, z), s, FLAGS);
					}
				}
			}
		}
	}

	private static void clear(ServerLevel level, CensusGrid g) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = g.minX(); x < g.minX() + g.sizeX(); x++) {
			for (int z = g.minZ(); z < g.minZ() + g.sizeZ(); z++) {
				int y = g.ground(x, z);
				for (int dy = 0; dy <= g.height() + 1; dy++) {
					level.setBlock(pos.set(x, y + dy, z), Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
	}

	private static Block block(String id) {
		Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
		return b.orElseThrow(() -> new AssertionError("Unknown block " + id));
	}

	// ------------------------------------------------------------------ plots and transects

	/** Context of the rows: scale, mode, place, season (sub-season name) and the aspect of the year. */
	record Context(String scale, String mode, String place, String season, ForestNorms.Aspect aspect) {
	}

	/**
	 * The column of the card for a place: its forest type, natural or managed by the stand of the stand map at its center
	 * (step V9; until V9 by the vegetation mode, decision V4-g).
	 */
	static Forest forest(String biome, boolean natural) {
		return switch (biome) {
			case "oak_hornbeam_forest" -> natural ? Forest.OAK_HORNBEAM_NATURAL : Forest.OAK_HORNBEAM_MANAGED;
			case "fresh_pine_forest" -> natural ? Forest.FRESH_PINE_NATURAL : Forest.FRESH_PINE_MANAGED;
			default -> throw new AssertionError("No column of the card for " + biome);
		};
	}

	/**
	 * The census of the 100 × 100 plot centered at (x, z) in a stand of the age class {@code age} (the norms by age class):
	 * rows of the report and a line for the log.
	 */
	static List<String> plot(ServerLevel level, Context c, Forest forest, AgeClass age, int x, int z, Map<Long, Species> records,
			StringBuilder log) {
		int minX = x - PLOT / 2;
		int minZ = z - PLOT / 2;
		CensusGrid g = read(level, minX - MARGIN, minZ - MARGIN, PLOT + 2 * MARGIN, PLOT + 2 * MARGIN, HEIGHT, records, (a, b) -> true);
		boolean[] area = ForestCensus.rectangle(g, minX, minZ, PLOT, PLOT);
		// Step V9: the plot is its forest columns (a forest biome without a zone of the model's habitat code): a meadow, a
		// field, a herb fringe or a shore of up to 5% of the square (the places allow it) is no canopy gap of the forest,
		// nor is an opening at its edge.
		boolean[] nonForestColumns = nonForest(level, g);
		int nonForest = 0;
		for (int col = 0; col < area.length; col++) {
			if (area[col] && nonForestColumns[col]) {
				area[col] = false;
				nonForest++;
			}
		}
		ForestCensus.Census census = ForestCensus.of(g);
		Map<String, Double> values = census.measure(area, nonForestColumns);
		writeMap(g, census.canopyMap(), c.scale() + "_" + c.mode() + "_" + c.place());
		// The canopy by the distance from the nearest chunk border (step V9, V5-i: no band of weaker cover along the borders).
		byte[] map = census.canopyMap();
		long[] closedBy = new long[8];
		long[] allBy = new long[8];
		for (int col = 0; col < map.length; col++) {
			byte m = map[col];
			if (m == ForestCensus.MAP_CLOSED || m == ForestCensus.MAP_OPEN) {
				int bx = g.columnX(col) & 15;
				int bz = g.columnZ(col) & 15;
				int d = Math.min(Math.min(bx, 15 - bx), Math.min(bz, 15 - bz));
				allBy[d]++;
				closedBy[d] += m == ForestCensus.MAP_CLOSED ? 1 : 0;
			}
		}
		StringBuilder band = new StringBuilder();
		for (int d = 0; d < 8; d++) {
			band.append(String.format(Locale.ROOT, " %d: %.1f%%", d, 100.0 * closedBy[d] / Math.max(1, allBy[d])));
		}
		PolishForests.LOG.info("[slice] {}: canopy outside gaps by distance from a chunk border:{}", c.place(), band);
		List<String> rows = new ArrayList<>();
		int in = 0;
		int tolerance = 0;
		int out = 0;
		for (Map.Entry<String, Double> e : values.entrySet()) {
			Optional<Norm> norm = ForestNorms.of(e.getKey(), forest, c.aspect(), age);
			ForestNorms.Verdict v = norm.map(n -> ForestNorms.verdict(e.getValue(), n)).orElse(ForestNorms.Verdict.NONE);
			in += v == ForestNorms.Verdict.IN ? 1 : 0;
			tolerance += v == ForestNorms.Verdict.TOLERANCE ? 1 : 0;
			out += v == ForestNorms.Verdict.OUT ? 1 : 0;
			rows.add(row(c, forest, e.getKey(), "", e.getValue(), norm.orElse(null), v));
		}
		Map<String, Integer> unknown = census.unclassified(area);
		// The young trees below a third of the top height, for a look in the world (M2 has no regeneration of its own).
		double top = values.get(ForestNorms.TOP_HEIGHT);
		List<String> young = census.objects().stream().filter(o -> o.kind() == ForestCensus.Kind.TREE && area[g.column(o.x(), o.z())]
				&& ForestCensus.layer(o, top) == ForestCensus.Layer.B).limit(8).map(o -> String.format(Locale.ROOT, "%s at (%d, %d, %d) "
						+ "height %d, %d leaves, %s", o.block(), o.x(), o.y() + 1, o.z(), o.height(), o.leaves(), o.species())).toList();
		if (!young.isEmpty()) {
			PolishForests.LOG.info("[slice] {}: regeneration {}", c.place(), young);
		}
		// The young Scots pines (N6: only in gaps), for a look where they stand (step V9).
		List<String> pines = census.objects().stream().filter(o -> o.kind() == ForestCensus.Kind.TREE && area[g.column(o.x(), o.z())]
				&& o.species() == Species.SCOTS_PINE && ForestCensus.layer(o, top) == ForestCensus.Layer.B).limit(12)
				.map(o -> String.format(Locale.ROOT, "%s at (%d, %d, %d) height %d", o.block(), o.x(), o.y() + 1, o.z(), o.height())).toList();
		if (!pines.isEmpty()) {
			PolishForests.LOG.info("[slice] {}: young pines {}", c.place(), pines);
		}
		// The tallest trees, for a look at the canopy (step V9).
		List<String> tallest = census.objects().stream().filter(o -> o.kind() == ForestCensus.Kind.TREE && area[g.column(o.x(), o.z())])
				.sorted(java.util.Comparator.comparingInt(ForestCensus.WoodObject::height).reversed()).limit(6)
				.map(o -> String.format(Locale.ROOT, "%s at (%d, %d, %d) height %d, wood %d, %d leaves", o.species(), o.x(), o.y() + 1, o.z(),
						o.height(), o.woodHeight(), o.leaves())).toList();
		PolishForests.LOG.info("[slice] {}: tallest trees {}", c.place(), tallest);
		List<String> outside = new ArrayList<>();
		for (Map.Entry<String, Double> e : values.entrySet()) {
			Optional<Norm> norm = ForestNorms.of(e.getKey(), forest, c.aspect(), age);
			if (norm.isPresent() && ForestNorms.verdict(e.getValue(), norm.get()) == ForestNorms.Verdict.OUT) {
				outside.add(e.getKey() + " " + ForestNorms.number(e.getValue()) + " (" + norm.get().range() + ")");
			}
		}
		if (nonForest > 0) {
			log.append(String.format(Locale.ROOT, "[%d columns of the square outside the forest] ", nonForest));
		}
		log.append(String.format(Locale.ROOT, "%s (%s): %s trees/chunk (a1 %s, a2 %s, top height %s), canopy %s%%, gaps %s%%, understory %s%%, "
				+ "herbs %s%%, moss %s%%, dead wood %s/chunk; vs %s: %d in, %d within tolerance, %d out%s%s", c.place(),
				age.name().toLowerCase(Locale.ROOT), n(values,
						ForestNorms.TREES_PER_CHUNK), n(values, ForestNorms.A1_PER_CHUNK), n(values, ForestNorms.A2_PER_CHUNK), n(values,
								ForestNorms.TOP_HEIGHT), n(values, ForestNorms.CANOPY), n(values, ForestNorms.GAP_SHARE), n(values,
										ForestNorms.UNDERSTORY), n(values, ForestNorms.HERBS), n(values, ForestNorms.MOSS), n(values,
												ForestNorms.DEADWOOD_PER_CHUNK), forest.polishName(), in, tolerance, out,
				outside.isEmpty() ? "" : " " + outside, unknown.isEmpty() ? "" : "; plants unknown to the census " + unknown));
		if (values.get(ForestNorms.TREES_PER_CHUNK) == 0) {
			throw new AssertionError(c.place() + ": no trees counted in the plot of a forest");
		}
		return rows;
	}

	/**
	 * The census of a transect: the band of {@link #BAND} blocks each side of the line from (x0, z0) to (x1, z1) (block
	 * coordinates), steps as in the check of the transect, {@code rank} the forest of each step; k the local scale factor.
	 */
	static List<String> transect(ServerLevel level, Context c, Forest forest, int x0, int z0, int x1, int z1, int steps, int[] rank,
			double k, Map<Long, Species> records, StringBuilder log) {
		double ax = x0 + 0.5;
		double az = z0 + 0.5;
		double bx = x1 + 0.5;
		double bz = z1 + 0.5;
		double reach = BAND + MARGIN;
		int minX = (int) Math.floor(Math.min(ax, bx) - reach);
		int minZ = (int) Math.floor(Math.min(az, bz) - reach);
		int maxX = (int) Math.ceil(Math.max(ax, bx) + reach);
		int maxZ = (int) Math.ceil(Math.max(az, bz) + reach);
		double len = Math.hypot(bx - ax, bz - az);
		BiPredicate<Integer, Integer> corridor = (x, z) -> {
			double t = ((x + 0.5 - ax) * (bx - ax) + (z + 0.5 - az) * (bz - az)) / (len * len);
			t = Math.clamp(t, 0, 1);
			return Math.hypot(x + 0.5 - (ax + t * (bx - ax)), z + 0.5 - (az + t * (bz - az))) <= reach;
		};
		CensusGrid g = read(level, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, HEIGHT, records, corridor);
		ForestCensus.Transect t = ForestCensus.transect(g, ax, az, bx, bz, steps, rank, BAND, k);
		List<String> rows = new ArrayList<>();
		String[] metrics = {ForestNorms.TREES_PER_CHUNK, ForestNorms.CONIFERS, ForestNorms.CANOPY, ForestNorms.UNDERSTORY, ForestNorms.HERBS,
				ForestNorms.MOSS, ForestNorms.LITTER, ForestNorms.DEADWOOD_PER_CHUNK};
		StringBuilder stretches = new StringBuilder();
		for (int r = 0; r < t.stretches().size(); r++) {
			Map<String, Double> s = t.stretches().get(r);
			String part = SliceSites.SEQUENCE.get(r);
			for (String m : metrics) {
				rows.add(row(c, forest, m, part, s.get(m), null, ForestNorms.Verdict.NONE));
			}
			stretches.append(String.format(Locale.ROOT, " %s: conifers %s%%, moss %s%%, litter %s%%, trees %s/chunk;", part,
					n(s, ForestNorms.CONIFERS), n(s, ForestNorms.MOSS), n(s, ForestNorms.LITTER), n(s, ForestNorms.TREES_PER_CHUNK)));
		}
		Norm order = ForestNorms.of(ForestNorms.TRANSECT_ORDER, forest, c.aspect(), null).orElseThrow();
		double ordered = t.order() ? 1 : 0;
		rows.add(row(c, forest, ForestNorms.TRANSECT_ORDER, "", ordered, order, ForestNorms.verdict(ordered, order)));
		Norm width = ForestNorms.of(ForestNorms.TRANSITION_WIDTH, forest, c.aspect(), null).orElseThrow();
		StringBuilder widths = new StringBuilder();
		for (int r = 0; r < t.widths().length; r++) {
			String part = SliceSites.SEQUENCE.get(r) + ">" + SliceSites.SEQUENCE.get(r + 1);
			rows.add(row(c, forest, ForestNorms.TRANSITION_WIDTH, part, t.widths()[r], width, ForestNorms.verdict(t.widths()[r], width)));
			widths.append(String.format(Locale.ROOT, " %s %s m·k (score change %s);", part, ForestNorms.number(t.widths()[r]),
					ForestNorms.number(t.contrasts()[r])));
		}
		log.append(String.format(Locale.ROOT, "%s: order %s;%s transitions:%s", c.place(), t.order() ? "kept" : "BROKEN", stretches, widths));
		// The score of each step, for a look at the transitions (build/norms/transect_<scale>_<mode>.csv).
		List<String> profile = new ArrayList<>();
		profile.add("step,x,z,forest,score");
		for (int i = 0; i <= steps; i++) {
			double f = (double) i / steps;
			profile.add(String.format(Locale.ROOT, "%d,%d,%d,%s,%s", i, (int) Math.floor(ax + (bx - ax) * f), (int) Math.floor(az + (bz - az) * f),
					rank[i] < 0 ? "other" : SliceSites.SEQUENCE.get(rank[i]), ForestNorms.number(t.score()[i])));
		}
		writeLines("transect_" + c.scale() + "_" + c.mode() + ".csv", profile);
		return rows;
	}

	/** The columns of the grid that are not forest by the habitat code of the world's model (a forest biome without a zone). */
	private static boolean[] nonForest(ServerLevel level, CensusGrid g) {
		boolean[] out = new boolean[g.sizeX() * g.sizeZ()];
		if (!(level.getChunkSource().getGenerator() instanceof pl.polishforests.worldgen.chunk.PolandChunkGenerator gen)) {
			return out;
		}
		pl.polishforests.worldgen.landscape.LandscapeModel m = gen.model(level.getSeed());
		pl.polishforests.worldgen.habitat.HabitatClassifier k = gen.classifier(level.getSeed());
		for (int c = 0; c < out.length; c++) {
			int x = g.columnX(c);
			int z = g.columnZ(c);
			int code = k.classify(m.sample(x, z), x, z);
			out[c] = pl.polishforests.worldgen.habitat.Habitat.biome(code).group() != pl.polishforests.worldgen.habitat.HabitatBiome.Group.FOREST
					|| pl.polishforests.worldgen.habitat.Habitat.zone(code) != pl.polishforests.worldgen.habitat.Zone.NONE;
		}
		return out;
	}

	/**
	 * A PNG of the canopy of a plot (step V9, {@code build/norms/plots/<scale>_<mode>_<place>.png}, 3 px per column):
	 * closed canopy green, open columns light, gaps yellow, tree bases dark (a1), blue (a2) and red (regeneration).
	 */
	private static void writeMap(CensusGrid g, byte[] map, String name) {
		int px = 3;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(g.sizeX() * px, g.sizeZ() * px,
				java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (int a = 0; a < g.sizeX(); a++) {
			for (int b = 0; b < g.sizeZ(); b++) {
				int rgb = switch (map[g.column(g.minX() + a, g.minZ() + b)]) {
					case ForestCensus.MAP_CLOSED -> 0x3E7A3A;
					case ForestCensus.MAP_OPEN -> 0xC8D8B0;
					case ForestCensus.MAP_GAP -> 0xF0D040;
					case ForestCensus.MAP_A1 -> 0x202020;
					case ForestCensus.MAP_A2 -> 0x2050D0;
					case ForestCensus.MAP_B -> 0xD03020;
					default -> 0x808080;
				};
				for (int i = 0; i < px; i++) {
					for (int j = 0; j < px; j++) {
						img.setRGB(a * px + i, b * px + j, rgb);
					}
				}
			}
		}
		String dir = System.getProperty("polishforests.norms.out", "");
		if (dir.isBlank()) {
			return;
		}
		try {
			Path file = Path.of(dir, "plots", name + ".png");
			Files.createDirectories(file.getParent());
			javax.imageio.ImageIO.write(img, "png", file.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String n(Map<String, Double> values, String metric) {
		return ForestNorms.number(values.get(metric));
	}

	private static String row(Context c, Forest forest, String metric, String part, double value, Norm norm, ForestNorms.Verdict v) {
		List<String> f = List.of(c.scale(), c.mode(), c.place(), forest.name().toLowerCase(Locale.ROOT), c.season(), metric, part,
				ForestNorms.number(value), norm == null ? "" : norm.range(), norm == null ? "" : ForestNorms.number(norm.low()),
				norm == null ? "" : ForestNorms.number(norm.high()), v.name().toLowerCase(Locale.ROOT), norm == null ? "" : norm.source());
		return String.join(",", f.stream().map(NormsCensus::csv).toList());
	}

	private static String csv(String s) {
		return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
	}

	/**
	 * Writes the rows to {@code karta-norm.csv} in the directory of {@code -Dpolishforests.norms.out} (the profiles of the
	 * transects went there as {@code transect_<scale>_<mode>.csv}).
	 */
	static Path write(List<String> rows) {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		lines.addAll(rows);
		Path file = writeLines("karta-norm.csv", lines);
		PolishForests.LOG.info("[slice] norms card: {} rows written to {}", rows.size(), file);
		return file;
	}

	/** Writes a file into the directory of {@code -Dpolishforests.norms.out}. */
	private static Path writeLines(String name, List<String> lines) {
		String dir = System.getProperty("polishforests.norms.out", "");
		if (dir.isBlank()) {
			throw new AssertionError("No -Dpolishforests.norms.out (the build sets it to build/norms)");
		}
		Path file = Path.of(dir, name);
		try {
			Files.createDirectories(file.getParent());
			// LF on every system, as the files in docs/.
			Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return file;
	}
}
