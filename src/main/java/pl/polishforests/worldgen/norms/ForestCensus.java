package pl.polishforests.worldgen.norms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The census of a sample plot for the norms card (step V4, docs/04-pionowy-wycinek.md §6): counts what a block snapshot
 * ({@link CensusGrid}) holds, the way a forest inventory counts a sample plot, under the keys of {@link ForestNorms}.
 * Pure Java, so the counting is checked piece by piece on hand-built grids ({@link CensusTestPlot}); the game test
 * {@code slice} fills the grid from the world.
 *
 * <p><b>Objects.</b> Wood cells form objects: cells touching by a face, an edge or a corner belong together, except that
 * wood lying on the ground (on its side, 1 block above the ground) never joins wood standing up, so a fallen log next
 * to a stump or a trunk stays an object of its own. An object with standing wood right on the ground (its base) stands;
 * one without a base made of lying wood is a lying log; other wood (branches of a tree rooted outside the grid) is left
 * out. Leaves belong to the standing object whose wood they reach first through leaves (faces only, at most
 * {@value #LEAF_REACH} steps, as vanilla leaves stay alive); a leaf reached by two objects in the same step goes to the
 * one with more wood, with as much wood to the one of a known species (a recorded hazel, not the stump next to it).
 * Standing wood of at most {@value #STUMP_MAX_HEIGHT} block without a known species (no species in its blocks, none
 * recorded at its base) is a stump by its shape and takes no leaves. A standing object with leaves lives: a shrub when its species is a shrub, else a tree. One without
 * leaves is dead: a stump up to {@value #STUMP_MAX_HEIGHT} block of wood, else a snag.
 *
 * <p><b>Where an object counts.</b> A standing object counts for the area that holds its base (the base column with the
 * lowest x, then z), a lying log for the area that holds its middle (the mean of its cell centers). The grid should
 * reach beyond the plot by the widest crown, so crowns of trees at the edge are whole; columns without ground are
 * outside the grid.
 *
 * <p><b>Height and layers.</b> The height of an object is the top of its highest cell (wood or leaves) above the ground
 * of its base, in blocks (m, trees are 1:1 in both scales, PW-12). The layers follow the IUFRO tree classes from the top
 * height of the area, the mean height of its {@value #TOP_TREES_PER_HA} tallest trees per ha (the forester's
 * "wysokość górna"): the main canopy a1 from 2/3 of it, the lower tree layer a2 from 1/3, below that the tree is
 * regeneration and belongs, with the shrubs, to the shrub layer b (understory). The trees per chunk (N1) are those of a1
 * and a2.
 *
 * <p><b>Columns.</b> Every column of the area with ground counts once for the cover metrics: canopy closure is the share
 * of columns with leaves {@value #CANOPY_FROM} or more blocks above the ground (more than 2 blocks, §3 V4); a canopy gap
 * is a group of open columns (touching by a side) of at least {@value #GAP_MIN_AREA} columns, its size as the
 * diameter of a circle of the same area; the understory cover the share of columns that a cell of a shrub or of
 * regeneration covers; the herb layer, bilberry, mosses and leaf litter are read from the block standing on the ground
 * and the ground block ({@link #herb}, {@link #moss}).
 */
public final class ForestCensus {
	private ForestCensus() {
	}

	/** Suffix of a plant or ground id whose block is in its mossy state (the mossy underside of PW-7, from V12). */
	public static final String MOSSY = "[mossy]";
	/** Leaves this many blocks above the ground or higher close the canopy of the column (more than 2 blocks). */
	public static final int CANOPY_FROM = 3;
	/** The main canopy (a1) from this share of the top height, the lower tree layer (a2) from {@link #LOWER}. */
	public static final double UPPER = 2 / 3.0;
	public static final double LOWER = 1 / 3.0;
	/** Trees per ha whose mean height is the top height. */
	public static final int TOP_TREES_PER_HA = 100;
	/** Steps through leaves from wood to a leaf that still belongs to it (vanilla leaves live up to 6). */
	public static final int LEAF_REACH = 6;
	/** Most blocks of wood of a stump; a dead standing object of more is a snag. */
	public static final int STUMP_MAX_HEIGHT = 1;
	/** Least area of a canopy gap, columns (m²): about the crown of one tree, a diameter of 8 m. */
	public static final int GAP_MIN_AREA = 50;
	/** A coarse piece of dead wood (GIOŚ 9170): longer than this, m ... */
	public static final double COARSE_LENGTH = 3;
	/** ... and at least this thick, m. */
	public static final double COARSE_DIAMETER = 0.5;
	/**
	 * Least change of the transect score between the stretches of two forests for a transition width, percentage points:
	 * with less, the score of a step (17 columns, noise about 15 points) cannot place the transition.
	 */
	public static final double MIN_CONTRAST = 10;

	/**
	 * Plants of the herb layer (c): herbs, grasses, ferns and dwarf shrubs, as the M2 vanilla placeholders stand for them
	 * (docs/03-m2-biomy.md §8.6: {@code bush} the dwarf shrubs, wildflowers the spring geophytes, pink petals the
	 * heather, dripleaves cattail and butterbur, sugar cane the reed).
	 */
	public static final Set<String> HERB_PLANTS = Set.of("minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
			"minecraft:large_fern", "minecraft:bush", "minecraft:wildflowers", "minecraft:pink_petals",
			"minecraft:lily_of_the_valley", "minecraft:dandelion", "minecraft:poppy", "minecraft:oxeye_daisy", "minecraft:cornflower",
			"minecraft:azure_bluet", "minecraft:allium", "minecraft:blue_orchid", "minecraft:red_tulip", "minecraft:orange_tulip",
			"minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:sunflower", "minecraft:lilac", "minecraft:peony",
			"minecraft:firefly_bush", "minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:small_dripleaf",
			"minecraft:big_dripleaf", "minecraft:big_dripleaf_stem", "minecraft:sugar_cane", "minecraft:sweet_berry_bush");
	/** The bilberry (a part of the herb layer): in M2 the {@code bush} of the pine forests (decision M2-13). */
	public static final Set<String> BILBERRY_PLANTS = Set.of("minecraft:bush");
	/** Mosses and lichens (layer d) standing on the ground. */
	public static final Set<String> MOSS_PLANTS = Set.of("minecraft:moss_carpet", "minecraft:pale_moss_carpet");
	/** Ground blocks that are moss. */
	public static final Set<String> MOSS_GROUNDS = Set.of("minecraft:moss_block");
	/** Leaf litter (not a plant layer; an indicator of the broadleaved forests on the transect). */
	public static final Set<String> LITTER_PLANTS = Set.of("minecraft:leaf_litter");
	/** Known blocks on the ground that are none of the layers (mushrooms, snow, a shrub of the fringe). */
	public static final Set<String> OTHER_PLANTS = Set.of("minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:snow",
			"minecraft:rose_bush", "minecraft:dead_bush", "minecraft:vine");
	/** Conifers among the tree species (ids). */
	public static final Set<String> CONIFERS = Set.of("scots_pine", "spruce", "fir", "larch", "yew");

	/** What an object is. */
	public enum Kind {
		/** A living tree (layers a1, a2 or, as regeneration, b). */
		TREE,
		/** A living shrub (layer b). */
		SHRUB,
		/** Dead standing wood up to {@link #STUMP_MAX_HEIGHT} block. */
		STUMP,
		/** Dead standing wood above that. */
		SNAG,
		/** Dead wood lying on the ground. */
		LOG
	}

	/** Layer of a tree in an area. */
	public enum Layer {
		A1, A2, B
	}

	/**
	 * An object of the census.
	 *
	 * @param kind       what it is
	 * @param x          x of the column it counts in (the base of a standing object, the middle of a log)
	 * @param z          z of that column
	 * @param y          ground under the base (standing), lowest wood (log)
	 * @param height     top of its highest cell above {@code y}, blocks
	 * @param woodHeight top of its highest wood above {@code y}, blocks
	 * @param length     longest extent of a log, or the wood height of a standing object, m
	 * @param volume     wood volume, m³
	 * @param diameter   thickness: of the base of a standing object, the thickest cell of a log, m
	 * @param leaves     leaves that belong to it
	 * @param species    species (of the wood, else recorded at the base), or null
	 * @param block      block of the base (standing) or of the first cell (log)
	 */
	public record WoodObject(Kind kind, int x, int z, int y, int height, int woodHeight, double length, double volume,
			double diameter, int leaves, @Nullable Species species, String block) {
		/** A conifer (Scots pine, spruce, fir, larch, yew). */
		public boolean conifer() {
			return species != null && CONIFERS.contains(species.id());
		}
	}

	// ------------------------------------------------------------------ the ground

	private static String strip(@Nullable String id) {
		return id == null ? "" : id.endsWith(MOSSY) ? id.substring(0, id.length() - MOSSY.length()) : id;
	}

	/** A plant of the herb layer (dwarf shrubs included). */
	public static boolean herb(@Nullable String plant) {
		return HERB_PLANTS.contains(strip(plant));
	}

	/** The bilberry. */
	public static boolean bilberry(@Nullable String plant) {
		return BILBERRY_PLANTS.contains(strip(plant));
	}

	/** A column with moss: a moss plant, a moss ground block or a block in its mossy state. */
	public static boolean moss(@Nullable String plant, @Nullable String ground) {
		return plant != null && (MOSS_PLANTS.contains(plant) || plant.endsWith(MOSSY))
				|| ground != null && (MOSS_GROUNDS.contains(ground) || ground.endsWith(MOSSY));
	}

	/** Leaf litter. */
	public static boolean litter(@Nullable String plant) {
		return LITTER_PLANTS.contains(strip(plant));
	}

	/** A plant id the census does not know (listed in the report, so it can be classified). */
	public static boolean unclassified(@Nullable String plant) {
		String p = strip(plant);
		return plant != null && !HERB_PLANTS.contains(p) && !MOSS_PLANTS.contains(p) && !LITTER_PLANTS.contains(p)
				&& !OTHER_PLANTS.contains(p) && !p.endsWith("_sapling");
	}

	// ------------------------------------------------------------------ areas

	/** The columns of a rectangle of the grid (world coordinates; parts outside the grid are left out). */
	public static boolean[] rectangle(CensusGrid grid, int minX, int minZ, int sizeX, int sizeZ) {
		boolean[] area = new boolean[grid.sizeX() * grid.sizeZ()];
		for (int x = minX; x < minX + sizeX; x++) {
			for (int z = minZ; z < minZ + sizeZ; z++) {
				if (grid.contains(x, z)) {
					area[grid.column(x, z)] = true;
				}
			}
		}
		return area;
	}

	/** The census of a grid: its objects, measured over any area by {@link Census#measure}. */
	public static Census of(CensusGrid grid) {
		return new Census(grid);
	}

	/** Shortcut: the metrics of a rectangle of the grid. */
	public static Map<String, Double> plot(CensusGrid grid, int minX, int minZ, int sizeX, int sizeZ) {
		return of(grid).measure(rectangle(grid, minX, minZ, sizeX, sizeZ));
	}

	// ------------------------------------------------------------------ the census

	/** Objects of a grid and the cells they own. */
	public static final class Census {
		private final CensusGrid grid;
		private final int height;
		/** Owner object of each cell (wood and leaves), -1 for none. */
		private final int[] owner;
		private final List<WoodObject> objects = new ArrayList<>();

		Census(CensusGrid grid) {
			this.grid = grid;
			this.height = grid.height();
			int cells = grid.sizeX() * grid.sizeZ() * height;
			this.owner = new int[cells];
			Arrays.fill(owner, -1);
			int[] component = new int[cells];
			Arrays.fill(component, -1);
			List<int[]> members = new ArrayList<>();
			// Wood objects (26-neighborhood; lying wood does not join standing wood).
			int[] queue = new int[cells];
			for (int c = 0; c < grid.sizeX() * grid.sizeZ(); c++) {
				if (grid.groundAt(c) == CensusGrid.NO_GROUND) {
					continue;
				}
				for (int dy = 1; dy <= height; dy++) {
					int i = c * height + dy - 1;
					if (!wood(i) || component[i] >= 0) {
						continue;
					}
					int id = members.size();
					int head = 0;
					int tail = 0;
					queue[tail++] = i;
					component[i] = id;
					while (head < tail) {
						int a = queue[head++];
						int ax = x(a);
						int az = z(a);
						int ay = y(a);
						for (int ox = -1; ox <= 1; ox++) {
							for (int oz = -1; oz <= 1; oz++) {
								for (int oy = -1; oy <= 1; oy++) {
									int b = index(ax + ox, ay + oy, az + oz);
									if (b < 0 || component[b] >= 0 || !wood(b) || !joined(a, b)) {
										continue;
									}
									component[b] = id;
									queue[tail++] = b;
								}
							}
						}
					}
					members.add(Arrays.copyOf(queue, tail));
				}
			}
			// Classify the components: standing (with a base), lying, or left out.
			int n = members.size();
			boolean[] standing = new boolean[n];
			boolean[] lying = new boolean[n];
			int[] woodCount = new int[n];
			boolean[] known = new boolean[n];
			boolean[] stump = new boolean[n];
			for (int id = 0; id < n; id++) {
				woodCount[id] = members.get(id).length;
				boolean low = true;
				for (int i : members.get(id)) {
					boolean base = grid.cellAt(i / height, dy(i)) == CensusGrid.WOOD_UP && dy(i) == 1;
					standing[id] |= base;
					lying[id] |= lyingCell(i);
					known[id] |= woodOf(i).species() != null || base && grid.recordAt(i / height) != null;
					low &= dy(i) <= STUMP_MAX_HEIGHT;
				}
				// Wood only on the ground and no species known: a stump by its shape, which takes no leaves of the crowns
				// around it (the stumps of the M2 fallen trees under low crowns).
				stump[id] = standing[id] && low && !known[id];
			}
			// Leaves: breadth-first from the wood of the standing objects, level by level; ties to the object with more wood.
			byte[] dist = new byte[cells];
			List<Integer> frontier = new ArrayList<>();
			for (int id = 0; id < n; id++) {
				if (standing[id] && !stump[id]) {
					for (int i : members.get(id)) {
						owner[i] = id;
						frontier.add(i);
					}
				}
			}
			int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			for (int d = 1; d <= LEAF_REACH && !frontier.isEmpty(); d++) {
				List<Integer> next = new ArrayList<>();
				for (int a : frontier) {
					int ax = x(a);
					int az = z(a);
					int ay = y(a);
					for (int[] f : faces) {
						int b = index(ax + f[0], ay + f[1], az + f[2]);
						if (b < 0 || grid.cellAt(b / height, dy(b)) != CensusGrid.LEAVES) {
							continue;
						}
						if (dist[b] == 0) {
							dist[b] = (byte) d;
							owner[b] = owner[a];
							next.add(b);
						} else if (dist[b] == d && better(owner[a], owner[b], woodCount, known)) {
							owner[b] = owner[a];
						}
					}
				}
				frontier = next;
			}
			int[] leaves = new int[n];
			int[] top = new int[n];
			Arrays.fill(top, Integer.MIN_VALUE);
			for (int i = 0; i < cells; i++) {
				if (owner[i] >= 0) {
					top[owner[i]] = Math.max(top[owner[i]], y(i));
					if (!wood(i)) {
						leaves[owner[i]]++;
					}
				}
			}
			int[] objectOf = new int[n];
			Arrays.fill(objectOf, -1);
			for (int id = 0; id < n; id++) {
				WoodObject o = standing[id] ? standing(members.get(id), leaves[id], top[id]) : lying[id] ? lying(members.get(id)) : null;
				if (o != null) {
					objectOf[id] = objects.size();
					objects.add(o);
				}
			}
			for (int i = 0; i < cells; i++) {
				if (owner[i] >= 0) {
					owner[i] = objectOf[owner[i]];
				} else if (component[i] >= 0) {
					owner[i] = objectOf[component[i]];
				}
			}
		}

		/** All objects of the grid. */
		public List<WoodObject> objects() {
			return List.copyOf(objects);
		}

		/** A leaf reached in the same step: more wood first, then a known species (a recorded shrub before a stump). */
		private static boolean better(int candidate, int current, int[] woodCount, boolean[] known) {
			if (woodCount[candidate] != woodCount[current]) {
				return woodCount[candidate] > woodCount[current];
			}
			if (known[candidate] != known[current]) {
				return known[candidate];
			}
			return candidate < current;
		}

		private WoodObject standing(int[] cells, int leaves, int top) {
			int base = -1;
			for (int i : cells) {
				if (dy(i) == 1 && grid.cellAt(i / height, 1) == CensusGrid.WOOD_UP
						&& (base < 0 || x(i) < x(base) || x(i) == x(base) && z(i) < z(base))) {
					base = i;
				}
			}
			int ground = grid.groundAt(base / height);
			int woodTop = Integer.MIN_VALUE;
			double volume = 0;
			Species species = null;
			for (int i : cells) {
				CensusGrid.Wood w = woodOf(i);
				woodTop = Math.max(woodTop, y(i));
				volume += w.volume();
				if (species == null) {
					species = w.species();
				}
			}
			CensusGrid.Wood baseWood = woodOf(base);
			if (baseWood.species() != null) {
				species = baseWood.species();
			}
			if (species == null) {
				species = grid.recordAt(base / height);
			}
			if (species == null) {
				for (int i : cells) {
					if (dy(i) == 1 && grid.cellAt(i / height, 1) == CensusGrid.WOOD_UP && grid.recordAt(i / height) != null) {
						species = grid.recordAt(i / height);
						break;
					}
				}
			}
			int woodHeight = woodTop - ground;
			Kind kind;
			if (leaves == 0) {
				kind = woodHeight <= STUMP_MAX_HEIGHT ? Kind.STUMP : Kind.SNAG;
			} else {
				kind = species != null && species.path().startsWith("shrub/") ? Kind.SHRUB : Kind.TREE;
			}
			return new WoodObject(kind, x(base), z(base), ground, Math.max(top, woodTop) - ground, woodHeight, woodHeight, volume, baseWood.diameter(),
					leaves, species, baseWood.block());
		}

		private WoodObject lying(int[] cells) {
			double sx = 0;
			double sz = 0;
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			double volume = 0;
			double diameter = 0;
			Species species = null;
			int first = cells[0];
			for (int i : cells) {
				int x = x(i);
				int y = y(i);
				int z = z(i);
				sx += x + 0.5;
				sz += z + 0.5;
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
				CensusGrid.Wood w = woodOf(i);
				volume += w.volume();
				diameter = Math.max(diameter, w.diameter());
				if (species == null) {
					species = w.species();
				}
				if (x < x(first) || x == x(first) && (z < z(first) || z == z(first) && y < y(first))) {
					first = i;
				}
			}
			int length = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1;
			return new WoodObject(Kind.LOG, (int) Math.floor(sx / cells.length), (int) Math.floor(sz / cells.length), minY, maxY - minY + 1,
					maxY - minY + 1, length, volume, diameter, 0, species, woodOf(first).block());
		}

		/**
		 * The metrics of {@link ForestNorms#METRICS} for an area of the grid ({@link #rectangle}); NaN where nothing was
		 * measured (no trees for the composition, no gap for its diameter).
		 */
		public Map<String, Double> measure(boolean[] area) {
			int columns = 0;
			int groundColumns = 0;
			for (int c = 0; c < area.length; c++) {
				if (area[c]) {
					columns++;
					if (grid.groundAt(c) != CensusGrid.NO_GROUND) {
						groundColumns++;
					}
				}
			}
			double chunks = columns / ForestNorms.CHUNK_AREA;
			double ha = columns / ForestNorms.HECTARE;
			// Layers from the top height of the area.
			List<WoodObject> in = new ArrayList<>();
			for (WoodObject o : objects) {
				if (grid.contains(o.x(), o.z()) && area[grid.column(o.x(), o.z())]) {
					in.add(o);
				}
			}
			List<WoodObject> trees = in.stream().filter(o -> o.kind() == Kind.TREE)
					.sorted(Comparator.comparingInt(WoodObject::height).reversed()).toList();
			int topCount = Math.max(1, (int) Math.round(TOP_TREES_PER_HA * ha));
			double topHeight = trees.isEmpty() ? Double.NaN
					: trees.subList(0, Math.min(topCount, trees.size())).stream().mapToInt(WoodObject::height).average().orElseThrow();
			List<WoodObject> a1 = new ArrayList<>();
			List<WoodObject> a2 = new ArrayList<>();
			List<WoodObject> regeneration = new ArrayList<>();
			for (WoodObject t : trees) {
				switch (layer(t, topHeight)) {
					case A1 -> a1.add(t);
					case A2 -> a2.add(t);
					case B -> regeneration.add(t);
				}
			}
			// Canopy and gaps.
			boolean[] open = new boolean[area.length];
			int canopy = 0;
			for (int c = 0; c < area.length; c++) {
				if (!area[c] || grid.groundAt(c) == CensusGrid.NO_GROUND) {
					continue;
				}
				boolean closed = false;
				for (int dy = CANOPY_FROM; dy <= height && !closed; dy++) {
					closed = grid.cellAt(c, dy) == CensusGrid.LEAVES;
				}
				if (closed) {
					canopy++;
				} else {
					open[c] = true;
				}
			}
			boolean[] gap = new boolean[area.length];
			int gapColumns = 0;
			List<Integer> gapSizes = new ArrayList<>();
			boolean[] seen = new boolean[area.length];
			int[] queue = new int[area.length];
			for (int c = 0; c < area.length; c++) {
				if (!open[c] || seen[c]) {
					continue;
				}
				int head = 0;
				int tail = 0;
				queue[tail++] = c;
				seen[c] = true;
				while (head < tail) {
					int a = queue[head++];
					int ax = grid.columnX(a);
					int az = grid.columnZ(a);
					int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] s : sides) {
						int bx = ax + s[0];
						int bz = az + s[1];
						if (!grid.contains(bx, bz)) {
							continue;
						}
						int b = grid.column(bx, bz);
						if (open[b] && !seen[b]) {
							seen[b] = true;
							queue[tail++] = b;
						}
					}
				}
				if (tail >= GAP_MIN_AREA) {
					gapSizes.add(tail);
					gapColumns += tail;
					for (int k = 0; k < tail; k++) {
						gap[queue[k]] = true;
					}
				}
			}
			// The shrub layer: shrubs and regeneration, and the columns their cells cover.
			boolean[] shrubLayer = new boolean[objects.size()];
			int shrubs = 0;
			for (WoodObject o : in) {
				if (o.kind() == Kind.SHRUB) {
					shrubs++;
				}
			}
			for (int k = 0; k < objects.size(); k++) {
				WoodObject o = objects.get(k);
				// Trees around the area are layered by the top height of the area; an area without trees has no regeneration.
				shrubLayer[k] = o.kind() == Kind.SHRUB || o.kind() == Kind.TREE && !trees.isEmpty() && layer(o, topHeight) == Layer.B;
			}
			int understory = 0;
			for (int c = 0; c < area.length; c++) {
				if (!area[c] || grid.groundAt(c) == CensusGrid.NO_GROUND) {
					continue;
				}
				for (int dy = 1; dy <= height; dy++) {
					int o = owner[c * height + dy - 1];
					if (o >= 0 && shrubLayer[o]) {
						understory++;
						break;
					}
				}
			}
			// Regeneration in gaps and under the canopy.
			int regenerationInGaps = 0;
			int oaks = 0;
			int pinesUnderCanopy = 0;
			for (WoodObject r : regeneration) {
				boolean inGap = gap[grid.column(r.x(), r.z())];
				regenerationInGaps += inGap ? 1 : 0;
				String id = r.species() == null ? "" : r.species().id();
				if (ForestNorms.GROUPS.get("oak").contains(id)) {
					oaks++;
				}
				if (id.equals("scots_pine") && !inGap) {
					pinesUnderCanopy++;
				}
			}
			double ratio = Double.NaN;
			if (gapColumns > 0 && !regeneration.isEmpty()) {
				double inGaps = (double) regenerationInGaps / gapColumns;
				int closedColumns = groundColumns - gapColumns;
				double under = closedColumns == 0 ? 0 : (double) (regeneration.size() - regenerationInGaps) / closedColumns;
				ratio = under == 0 ? Double.POSITIVE_INFINITY : inGaps / under;
			}
			// The ground.
			int herbs = 0;
			int bilberry = 0;
			int moss = 0;
			int litter = 0;
			for (int c = 0; c < area.length; c++) {
				if (!area[c] || grid.groundAt(c) == CensusGrid.NO_GROUND) {
					continue;
				}
				String plant = grid.plantAt(c);
				herbs += herb(plant) ? 1 : 0;
				bilberry += bilberry(plant) ? 1 : 0;
				moss += moss(plant, grid.groundBlockAt(c)) ? 1 : 0;
				litter += litter(plant) ? 1 : 0;
			}
			// Dead wood.
			int logs = 0;
			int stumps = 0;
			int snags = 0;
			double volume = 0;
			int coarse = 0;
			for (WoodObject o : in) {
				switch (o.kind()) {
					case LOG -> logs++;
					case STUMP -> stumps++;
					case SNAG -> snags++;
					default -> {
						continue;
					}
				}
				if (o.kind() != Kind.STUMP) {
					volume += o.volume();
					if (o.length() > COARSE_LENGTH && o.diameter() >= COARSE_DIAMETER) {
						coarse++;
					}
				}
			}
			Map<String, Double> m = new LinkedHashMap<>();
			int layered = a1.size() + a2.size();
			m.put(ForestNorms.TREES_PER_CHUNK, layered / chunks);
			m.put(ForestNorms.A1_PER_CHUNK, a1.size() / chunks);
			m.put(ForestNorms.A1_HEIGHT, meanHeight(a1));
			m.put(ForestNorms.A2_PER_CHUNK, a2.size() / chunks);
			m.put(ForestNorms.A2_HEIGHT, meanHeight(a2));
			m.put(ForestNorms.A2_SHARE, layered == 0 ? Double.NaN : 100.0 * a2.size() / layered);
			m.put(ForestNorms.TOP_HEIGHT, topHeight);
			Map<String, Integer> composition = new LinkedHashMap<>();
			for (String key : ForestNorms.COMPOSITION_KEYS) {
				composition.put(key, 0);
			}
			for (WoodObject t : a1) {
				composition.merge(compositionKey(t.species()), 1, Integer::sum);
			}
			for (String key : ForestNorms.COMPOSITION_KEYS) {
				m.put(ForestNorms.COMPOSITION + key, a1.isEmpty() ? Double.NaN : 100.0 * composition.get(key) / a1.size());
			}
			m.put(ForestNorms.CANOPY, percent(canopy, groundColumns));
			m.put(ForestNorms.GAP_SHARE, percent(gapColumns, groundColumns));
			m.put(ForestNorms.GAP_DIAMETER, gapSizes.isEmpty() ? Double.NaN
					: gapSizes.stream().mapToDouble(a -> 2 * Math.sqrt(a / Math.PI)).average().orElseThrow());
			m.put(ForestNorms.REGENERATION, regeneration.size() / ha);
			m.put(ForestNorms.REGENERATION_RATIO, ratio);
			m.put(ForestNorms.OAK_REGENERATION, oaks / ha);
			m.put(ForestNorms.PINE_REGENERATION_UNDER_CANOPY, pinesUnderCanopy / ha);
			m.put(ForestNorms.UNDERSTORY, percent(understory, groundColumns));
			m.put(ForestNorms.SHRUBS_PER_CHUNK, shrubs / chunks);
			m.put(ForestNorms.HERBS, percent(herbs, groundColumns));
			m.put(ForestNorms.BILBERRY, percent(bilberry, groundColumns));
			m.put(ForestNorms.LITTER, percent(litter, groundColumns));
			m.put(ForestNorms.MOSS, percent(moss, groundColumns));
			m.put(ForestNorms.DEADWOOD_PER_CHUNK, (logs + stumps + snags) / chunks);
			m.put(ForestNorms.LOGS_PER_CHUNK, logs / chunks);
			m.put(ForestNorms.STUMPS_PER_CHUNK, stumps / chunks);
			m.put(ForestNorms.SNAGS_PER_CHUNK, snags / chunks);
			m.put(ForestNorms.DEADWOOD_VOLUME, volume / ha);
			m.put(ForestNorms.COARSE_PIECES, coarse / ha);
			long conifers = a1.stream().filter(WoodObject::conifer).count() + a2.stream().filter(WoodObject::conifer).count();
			m.put(ForestNorms.CONIFERS, layered == 0 ? Double.NaN : 100.0 * conifers / layered);
			return m;
		}

		/**
		 * Plants the census does not know, with the number of columns of the area they stand on (sorted by id), so a new
		 * block of the ground layer shows up in the report.
		 */
		public Map<String, Integer> unclassified(boolean[] area) {
			Map<String, Integer> out = new TreeMap<>();
			for (int c = 0; c < area.length; c++) {
				if (area[c] && grid.groundAt(c) != CensusGrid.NO_GROUND && ForestCensus.unclassified(grid.plantAt(c))) {
					out.merge(grid.plantAt(c), 1, Integer::sum);
				}
			}
			return out;
		}

		// ---------------------------------------------------------------- cells

		private int x(int cell) {
			return grid.columnX(cell / height);
		}

		private int z(int cell) {
			return grid.columnZ(cell / height);
		}

		private int dy(int cell) {
			return cell % height + 1;
		}

		/** World y of a cell. */
		private int y(int cell) {
			return grid.groundAt(cell / height) + dy(cell);
		}

		/** Cell at a world position, or -1 outside the grid, below the ground or above the cells of the column. */
		private int index(int x, int y, int z) {
			if (!grid.contains(x, z)) {
				return -1;
			}
			int c = grid.column(x, z);
			int g = grid.groundAt(c);
			if (g == CensusGrid.NO_GROUND) {
				return -1;
			}
			int dy = y - g;
			return dy < 1 || dy > height ? -1 : c * height + dy - 1;
		}

		private boolean wood(int cell) {
			byte k = grid.cellAt(cell / height, dy(cell));
			return k == CensusGrid.WOOD_UP || k == CensusGrid.WOOD_SIDE;
		}

		private boolean lyingCell(int cell) {
			return dy(cell) == 1 && grid.cellAt(cell / height, 1) == CensusGrid.WOOD_SIDE;
		}

		/** Two wood cells next to each other belong together, unless one lies on the ground and the other stands. */
		private boolean joined(int a, int b) {
			boolean upA = grid.cellAt(a / height, dy(a)) == CensusGrid.WOOD_UP;
			boolean upB = grid.cellAt(b / height, dy(b)) == CensusGrid.WOOD_UP;
			return !(lyingCell(a) && upB || lyingCell(b) && upA);
		}

		private CensusGrid.Wood woodOf(int cell) {
			CensusGrid.Wood w = grid.woodAt(cell / height, dy(cell));
			if (w == null) {
				throw new IllegalStateException("wood cell without wood at " + x(cell) + " " + y(cell) + " " + z(cell));
			}
			return w;
		}
	}

	/** Layer of a tree by its height and the top height of its area. */
	public static Layer layer(WoodObject tree, double topHeight) {
		if (tree.height() >= UPPER * topHeight) {
			return Layer.A1;
		}
		return tree.height() >= LOWER * topHeight ? Layer.A2 : Layer.B;
	}

	/** The composition key of a species ({@link ForestNorms#COMPOSITION_KEYS}). */
	public static String compositionKey(@Nullable Species species) {
		if (species == null) {
			return "other";
		}
		for (Map.Entry<String, List<String>> g : ForestNorms.GROUPS.entrySet()) {
			if (g.getValue().contains(species.id())) {
				return g.getKey();
			}
		}
		return ForestNorms.COMPOSITION_KEYS.contains(species.id()) ? species.id() : "other";
	}

	private static double meanHeight(List<WoodObject> trees) {
		return trees.isEmpty() ? Double.NaN : trees.stream().mapToInt(WoodObject::height).average().orElseThrow();
	}

	private static double percent(int part, int whole) {
		return whole == 0 ? Double.NaN : 100.0 * part / whole;
	}

	// ------------------------------------------------------------------ the transect (N15)

	/**
	 * The census of a transect.
	 *
	 * @param stretches metrics of each forest of the sequence, in its order ({@link Census#measure} over the band
	 *                  columns of its steps)
	 * @param score     score of each step: moss cover minus leaf litter cover of its band columns, percentage points (NaN
	 *                  without ground)
	 * @param widths    width of the transition between forest r and r + 1 (10–90% of the logistic curve from the mean
	 *                  score of stretch r to that of stretch r + 1 that fits the steps between their middles best), m·k:
	 *                  blocks along the line, divided by k; NaN when the two means differ by less than
	 *                  {@link #MIN_CONTRAST}
	 * @param contrasts change of the mean score from one stretch to the next, percentage points
	 * @param order     the indicators keep the order of the sequence: conifer share and moss cover do not grow, leaf
	 *                  litter cover does not shrink from one forest to the next by more than the sampling noise
	 *                  ({@link #reversed})
	 */
	public record Transect(List<Map<String, Double>> stretches, double[] score, double[] widths, double[] contrasts, boolean order) {
	}

	/**
	 * The census of a transect from (x0, z0) to (x1, z1) (block centers, world coordinates) in {@code steps} steps: each
	 * column of the grid within {@code band} blocks of the line belongs to the step nearest to its projection on the line.
	 * {@code rank[i]} is the forest of step i in the sequence (0–3) or -1 for another biome; k divides the widths (the
	 * local scale factor of the world, 1 in the realistic scale).
	 */
	public static Transect transect(CensusGrid grid, double x0, double z0, double x1, double z1, int steps, int[] rank, double band,
			double k) {
		double len = Math.hypot(x1 - x0, z1 - z0);
		double ux = (x1 - x0) / len;
		double uz = (z1 - z0) / len;
		double stepLength = len / steps;
		int forests = 0;
		for (int r : rank) {
			forests = Math.max(forests, r + 1);
		}
		int[] stepOf = new int[grid.sizeX() * grid.sizeZ()];
		Arrays.fill(stepOf, -1);
		int[] withGround = new int[steps + 1];
		double[] mossOf = new double[steps + 1];
		double[] litterOf = new double[steps + 1];
		for (int c = 0; c < stepOf.length; c++) {
			if (grid.groundAt(c) == CensusGrid.NO_GROUND) {
				continue;
			}
			double dx = grid.columnX(c) + 0.5 - x0;
			double dz = grid.columnZ(c) + 0.5 - z0;
			int i = (int) Math.round((dx * ux + dz * uz) / stepLength);
			if (i < 0 || i > steps || Math.abs(-dx * uz + dz * ux) > band) {
				continue;
			}
			stepOf[c] = i;
			withGround[i]++;
			mossOf[i] += moss(grid.plantAt(c), grid.groundBlockAt(c)) ? 1 : 0;
			litterOf[i] += litter(grid.plantAt(c)) ? 1 : 0;
		}
		double[] score = new double[steps + 1];
		for (int i = 0; i <= steps; i++) {
			score[i] = withGround[i] == 0 ? Double.NaN : 100.0 * (mossOf[i] - litterOf[i]) / withGround[i];
		}
		Census census = of(grid);
		List<Map<String, Double>> stretches = new ArrayList<>();
		int[] columns = new int[forests];
		int[] ground = new int[forests];
		int[] first = new int[forests];
		int[] last = new int[forests];
		Arrays.fill(first, -1);
		for (int r = 0; r < forests; r++) {
			boolean[] area = new boolean[stepOf.length];
			for (int c = 0; c < stepOf.length; c++) {
				area[c] = stepOf[c] >= 0 && rank[stepOf[c]] == r;
			}
			stretches.add(census.measure(area));
			for (int c = 0; c < stepOf.length; c++) {
				if (area[c]) {
					columns[r]++;
					ground[r] += grid.groundAt(c) == CensusGrid.NO_GROUND ? 0 : 1;
				}
			}
			for (int i = 0; i <= steps; i++) {
				if (rank[i] == r) {
					if (first[r] < 0) {
						first[r] = i;
					}
					last[r] = i;
				}
			}
		}
		boolean order = true;
		for (int r = 0; r + 1 < forests; r++) {
			Map<String, Double> a = stretches.get(r);
			Map<String, Double> b = stretches.get(r + 1);
			// Conifers out of the trees (a1 + a2), moss and litter out of the columns with ground of each stretch.
			double treesA = a.get(ForestNorms.TREES_PER_CHUNK) * columns[r] / ForestNorms.CHUNK_AREA;
			double treesB = b.get(ForestNorms.TREES_PER_CHUNK) * columns[r + 1] / ForestNorms.CHUNK_AREA;
			order &= !reversed(a.get(ForestNorms.CONIFERS), treesA, b.get(ForestNorms.CONIFERS), treesB)
					&& !reversed(a.get(ForestNorms.MOSS), ground[r], b.get(ForestNorms.MOSS), ground[r + 1])
					&& !reversed(-a.get(ForestNorms.LITTER), ground[r], -b.get(ForestNorms.LITTER), ground[r + 1]);
		}
		double[] widths = new double[Math.max(0, forests - 1)];
		double[] contrasts = new double[widths.length];
		for (int r = 0; r + 1 < forests; r++) {
			if (first[r] < 0 || first[r + 1] < 0) {
				widths[r] = Double.NaN;
				contrasts[r] = Double.NaN;
				continue;
			}
			double a = mean(score, rank, r);
			double b = mean(score, rank, r + 1);
			contrasts[r] = Math.abs(b - a);
			double[] fit = fitLogistic(score, (first[r] + last[r]) / 2, (first[r + 1] + last[r + 1]) / 2, a, b);
			widths[r] = !(contrasts[r] >= MIN_CONTRAST) ? Double.NaN : 2 * Math.log(9) * fit[0] * stepLength / k;
		}
		return new Transect(stretches, score, widths, contrasts, order);
	}

	/**
	 * An indicator that should not grow from one forest to the next grows by more than {@link #ORDER_ERRORS} standard
	 * errors: the shares a and b (percent, negated for an indicator that should not shrink) out of na and nb samples, with
	 * the pooled share for the error. A difference within the sampling noise (e.g. leaf litter 35.6% and 35.0% over
	 * thousands of columns) keeps the order; NaN (nothing to count) breaks it.
	 */
	static boolean reversed(double a, double na, double b, double nb) {
		if (Double.isNaN(a) || Double.isNaN(b) || na <= 0 || nb <= 0) {
			return true;
		}
		double pa = Math.abs(a) / 100;
		double pb = Math.abs(b) / 100;
		double p = (pa * na + pb * nb) / (na + nb);
		double se = Math.sqrt(Math.max(p * (1 - p), 1e-12) * (1 / na + 1 / nb));
		return (b - a) / 100 > ORDER_ERRORS * se;
	}

	/** Standard errors by which an indicator must go the wrong way to break the order of a transect. */
	public static final double ORDER_ERRORS = 2;

	/** Scales of the logistic curve tried by {@link #fitLogistic}, steps: 0.25 to 2 by 0.25, to 10 by 0.5, to 60 by 1. */
	private static final double[] SCALES;

	static {
		List<Double> s = new ArrayList<>();
		for (double w = 0.25; w <= 2; w += 0.25) {
			s.add(w);
		}
		for (double w = 2.5; w <= 10; w += 0.5) {
			s.add(w);
		}
		for (double w = 11; w <= 60; w++) {
			s.add(w);
		}
		SCALES = s.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/** Mean score of the steps of a forest (NaN without any). */
	private static double mean(double[] score, int[] rank, int r) {
		double sum = 0;
		int n = 0;
		for (int i = 0; i < score.length; i++) {
			if (rank[i] == r && !Double.isNaN(score[i])) {
				sum += score[i];
				n++;
			}
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	/**
	 * Least-squares fit of a + (b − a)·σ((i − c) / w) to the scores of the steps from {@code from} to {@code to}, with the
	 * levels a and b given (the mean scores of the two forests, so noise within a stretch cannot pose as a step): a grid
	 * search over the center c (half steps) and the scale w ({@link #SCALES}). Returns {w, c}.
	 */
	static double[] fitLogistic(double[] score, int from, int to, double a, double b) {
		double best = Double.MAX_VALUE;
		double[] out = {Double.NaN, Double.NaN};
		for (double c = from; c <= to; c += 0.5) {
			for (double w : SCALES) {
				double sse = 0;
				for (int i = from; i <= to; i++) {
					if (Double.isNaN(score[i])) {
						continue;
					}
					double g = 1 / (1 + Math.exp(-(i - c) / w));
					double e = score[i] - (a + (b - a) * g);
					sse += e * e;
				}
				if (sse < best - 1e-9) {
					best = sse;
					out = new double[] {w, c};
				}
			}
		}
		return out;
	}
}
