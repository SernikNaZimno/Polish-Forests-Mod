package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Soil blocks (docs/03-m2-biomy.md §7.4): the top block and the soil layers under it for a {@link Soil}, with the
 * vanilla substitutes of the mod's own blocks (decision M2-D, own blocks in M4): peat and muck are {@code mud}, the
 * sphagnum carpet is {@code moss_block}. Below the soil layers the generator continues with the deposits of the
 * substrate ({@link ChunkSurface}). A profile is packed into an {@code int}: top, first layer and its depth, second
 * layer and its depth.
 *
 * <p>Seedling rule (§7.4): the top of every soil of a forest biome belongs to {@code #minecraft:supports_vegetation},
 * except on point bars and gravel bars (zones {@link Zone#POINT_BAR}, {@link Zone#GRAVEL_BAR}); {@code SoilTest} checks
 * it. Deviation from §7.4: no {@code mossy_cobblestone} patches on the mountain podzol (not a vegetation block; stones
 * come with the decoration).
 */
public final class SoilBlocks {
	private SoilBlocks() {
	}

	/** Share of coarse dirt on the initial podzol (60%, the rest podzol). */
	static final double INITIAL_PODZOL_COARSE = 0.60;
	/** Water deeper than this (m) has a mud bottom at sea instead of sand. */
	static final double SEA_MUD_DEPTH = 20;
	/** Below this low-shore share the beach is stony (gravel with sand patches), at the foot of a cliff. */
	static final double STONY_BEACH = 0.5;

	// ------------------------------------------------------------------ packed profile

	static int profile(Material top, Material l1, int l1Depth, Material l2, int l2Depth) {
		return top.ordinal() | l1.ordinal() << 6 | l1Depth << 12 | l2.ordinal() << 16 | l2Depth << 22;
	}

	static int profile(Material top, Material l1, int l1Depth) {
		return profile(top, l1, l1Depth, Material.AIR, 0);
	}

	static int profile(Material top) {
		return profile(top, Material.AIR, 0, Material.AIR, 0);
	}

	public static Material top(int profile) {
		return Material.of(profile & 63);
	}

	static Material layer1(int profile) {
		return Material.of(profile >>> 6 & 63);
	}

	static int layer1Depth(int profile) {
		return profile >>> 12 & 15;
	}

	static Material layer2(int profile) {
		return Material.of(profile >>> 16 & 63);
	}

	static int layer2Depth(int profile) {
		return profile >>> 22 & 15;
	}

	// ------------------------------------------------------------------ dry columns

	/**
	 * Profile of a dry column.
	 *
	 * @param q1       uniform patch quantile 0–1 (main mixture of the top, e.g. coarse dirt and podzol)
	 * @param q2       an independent uniform patch quantile 0–1 (minor patches, e.g. mud)
	 * @param lowShore low sea shore 0–1 of the column ({@code Terrain.lowShore}); a beach below {@link #STONY_BEACH} is stony
	 */
	public static int dry(Soil soil, Zone zone, HabitatBiome biome, double q1, double q2, double lowShore) {
		return switch (soil) {
			case INITIAL_PODZOL -> profile(q1 < INITIAL_PODZOL_COARSE ? Material.COARSE_DIRT : Material.PODZOL);
			case PODZOL -> profile(Material.PODZOL, Material.COARSE_DIRT, 1);
			case RUSTY_SOIL -> profile(q1 < 0.5 ? Material.PODZOL : Material.GRASS_BLOCK, Material.DIRT, 1);
			case SANDY_GLEYSOL -> profile(q2 < 0.15 ? Material.MUD : Material.PODZOL, Material.SAND, 1, Material.CLAY, 2);
			// Raised bog peat 2–5 blocks under the sphagnum carpet.
			case BOG_PEAT -> profile(Material.MOSS_BLOCK, Material.MUD, 2 + (int) (q2 * 3.999));
			case FEN_PEAT -> profile(Material.MUD, Material.MUD, 2);
			case ACID_BROWN_SOIL -> profile(q1 < 0.6 ? Material.GRASS_BLOCK : Material.PODZOL, Material.DIRT, 2);
			case BROWN_SOIL -> profile(Material.GRASS_BLOCK, Material.ROOTED_DIRT, 1, Material.DIRT, 2);
			// Beech forest: bare litter, the grass does not spread.
			case BEECH_BROWN_SOIL -> profile(q1 < 0.6 ? Material.DIRT : Material.COARSE_DIRT, Material.DIRT, 2);
			// Muck 40% (mud), rooted dirt 40%, mud 20%.
			case MUCK -> profile(q1 < 0.4 ? Material.ROOTED_DIRT : Material.MUD, Material.DIRT, 1, Material.MUD, 1);
			case LIGHT_ALLUVIAL_SOIL -> zone == Zone.POINT_BAR
					? profile(q2 < 0.25 ? Material.MUD : Material.SAND, Material.SAND, 2)
					: profile(q2 < 0.10 ? Material.MUD : q1 < 0.7 ? Material.GRASS_BLOCK : Material.COARSE_DIRT,
							Material.SAND, 1, Material.DIRT, 1);
			case HEAVY_ALLUVIAL_SOIL -> profile(Material.GRASS_BLOCK, Material.DIRT, 3, Material.CLAY, 1);
			case GRAVELLY_ALLUVIAL_SOIL -> zone == Zone.GRAVEL_BAR
					? profile(q2 < 0.2 ? Material.COARSE_DIRT : Material.GRAVEL, Material.GRAVEL, 2)
					: profile(q1 < 0.5 ? Material.COARSE_DIRT : Material.GRASS_BLOCK, Material.GRAVEL, 2);
			case MOUNTAIN_BROWN_SOIL -> profile(q1 < 0.7 ? Material.GRASS_BLOCK : Material.COARSE_DIRT, Material.COARSE_DIRT, 1);
			case MOUNTAIN_PODZOL -> profile(q2 < 0.2 ? Material.MOSS_BLOCK : Material.PODZOL, Material.COARSE_DIRT, 1);
			case RANKER -> profile(q1 < 0.5 ? Material.COARSE_DIRT : Material.GRASS_BLOCK, Material.COARSE_DIRT, 1,
					Material.GRAVEL, 1);
			case DUNE_SAND -> biome == HabitatBiome.GRAY_DUNE && q1 >= 0.6 ? profile(Material.COARSE_DIRT)
					: profile(Material.SAND);
			case BEACH_SAND -> lowShore < STONY_BEACH ? profile(q2 < 0.3 ? Material.SAND : Material.GRAVEL, Material.GRAVEL, 1)
					: profile(Material.SAND);
			// Bare till of an active cliff face.
			case TILL -> profile(Material.COARSE_DIRT, Material.DIRT, 1);
			// Dry beds (a dry channel stretch, the shore of a lake bed): the bare deposit.
			case CHANNEL_BED -> profile(Material.SAND);
			case STREAM_BED -> profile(Material.GRAVEL);
			case LAKE_BED, DYSTROPHIC_LAKE_BED, LAGOON_BED -> profile(Material.MUD);
			case SEA_BED -> profile(Material.SAND);
		};
	}

	// ------------------------------------------------------------------ water beds

	/** Kind of a water bed. */
	public enum Bed {
		CHANNEL, STREAM, LAKE, DYSTROPHIC_LAKE, SEA, LAGOON, PUDDLE
	}

	/**
	 * Profile of a bed under water (§7.4, §7.2): river sand with gravel patches, stream gravel with cobbles, lake mud with
	 * clay patches, sea sand (mud deeper than {@link #SEA_MUD_DEPTH}), lagoon mud, puddle mud. The belt of 1–2 blocks of
	 * water at the shore of channels and lakes has a mud bottom (gravel in streams), so plants needing
	 * {@code #supports_vegetation} or mud can stand there.
	 *
	 * @param shoreBelt the column lies in the belt by the shore
	 * @param depth     water depth (m)
	 */
	public static int bed(Bed bed, boolean shoreBelt, double q2, double depth) {
		return switch (bed) {
			case CHANNEL -> shoreBelt ? profile(Material.MUD, Material.SAND, 1)
					: profile(q2 < 0.35 ? Material.GRAVEL : Material.SAND, Material.SAND, 1);
			case STREAM -> profile(!shoreBelt && q2 < 0.3 ? Material.COBBLESTONE : Material.GRAVEL, Material.GRAVEL, 1);
			case LAKE -> profile(!shoreBelt && q2 < 0.3 ? Material.CLAY : Material.MUD, Material.MUD, 1);
			case DYSTROPHIC_LAKE, LAGOON, PUDDLE -> profile(Material.MUD, Material.MUD, 1);
			case SEA -> depth > SEA_MUD_DEPTH ? profile(Material.MUD, Material.MUD, 1) : profile(Material.SAND);
		};
	}
}
