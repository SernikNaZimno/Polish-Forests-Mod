package pl.polishforests.worldgen.cave;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.PolandBiomeSource;
import pl.polishforests.worldgen.surface.Material;

/**
 * The rock of the sulfur caves (step J2): the vanilla material rule {@code overworld/sulfur_cave_bands}, which the
 * vanilla overworld applies to the stone of the biome {@code minecraft:sulfur_caves} (rule {@code overworld/underground},
 * before the deepslate): the 3D noise {@code minecraft:sulfur_cave_gradient} at the block gives cinnabar from -0.4 to
 * -0.1, sulfur from 0 to 0.4 and cinnabar above 0.4; elsewhere the rock stays. The "Poland" generator has no material
 * rules, so {@code fill()} applies this one to the rock of the plan (stone, deepslate, flysch andesite) where the biome
 * of the block is the sulfur caves, read as vanilla reads it ({@link BiomeManager#getBiome}, the fuzzy zoom of the quart
 * cells), from the biomes the chunk already holds (cells beyond the chunk count as the nearest cell of the chunk).
 */
public final class SulfurBands {
	private final Holder<Biome> sulfur;
	private final Noise noise;
	private final long zoomSeed;

	private SulfurBands(Holder<Biome> sulfur, Noise noise, long zoomSeed) {
		this.sulfur = sulfur;
		this.noise = noise;
		this.zoomSeed = zoomSeed;
	}

	/** The bands of a world: its sulfur caves biome and the noise of its random state. */
	public static SulfurBands of(PolandBiomeSource source, RandomState randomState) {
		return new SulfurBands(source.caveHolder(CaveBiomes.SULFUR), randomState.getOrCreateNoise(Noises.SULFUR_CAVE_GRADIENT),
				BiomeManager.obfuscateSeed(randomState.seed()));
	}

	/** The band of the vanilla rule at the noise value {@code v}, or null where the rock stays. */
	public static @Nullable Material band(double v) {
		if (v >= -0.4 && v <= -0.1) {
			return Material.CINNABAR;
		}
		if (v >= 0.0 && v <= 0.4) {
			return Material.SULFUR;
		}
		return v >= 0.4 ? Material.CINNABAR : null;
	}

	/** The rock the bands replace (the vanilla default stone and the rock the "Poland" plan puts in its place). */
	static boolean rock(Material m) {
		return m == Material.STONE || m == Material.DEEPSLATE || m == Material.ANDESITE;
	}

	/** The bands of one chunk, or null when no section of the chunk holds the sulfur caves. */
	public @Nullable Chunk chunk(ChunkAccess chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		boolean[] has = new boolean[sections.length];
		boolean any = false;
		for (int i = 0; i < sections.length; i++) {
			if (sections[i].getBiomes().maybeHas(b -> b == sulfur)) {
				has[i] = true;
				any = true;
			}
		}
		return any ? new Chunk(chunk, has) : null;
	}

	/** The bands of one chunk: which sections they may touch and the material of a block. */
	public final class Chunk {
		private final ChunkAccess chunk;
		private final boolean[] near;
		private final BiomeManager biomes;
		private final int minQuartX;
		private final int minQuartZ;

		private Chunk(ChunkAccess chunk, boolean[] has) {
			this.chunk = chunk;
			// The fuzzy zoom reaches the quart cells next to a block, so a section next to one with sulfur may hold banded rock.
			this.near = new boolean[has.length];
			for (int i = 0; i < has.length; i++) {
				near[i] = has[i] || i > 0 && has[i - 1] || i + 1 < has.length && has[i + 1];
			}
			this.minQuartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
			this.minQuartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
			this.biomes = new BiomeManager((qx, qy, qz) -> chunk.getNoiseBiome(Mth.clamp(qx, minQuartX, minQuartX + 3), qy,
					Mth.clamp(qz, minQuartZ, minQuartZ + 3)), zoomSeed);
		}

		/** Whether section {@code index} of the chunk may hold banded rock. */
		public boolean section(int index) {
			return near[index];
		}

		/**
		 * The material ordinal at block (x, y, z) of the chunk (x and z in the chunk) for the material ordinal of the plan
		 * {@code material}: a band in the rock of the sulfur caves, else {@code material}.
		 */
		public int material(int x, int y, int z, int material) {
			if (!rock(Material.of(material))) {
				return material;
			}
			int bx = chunk.getPos().getMinBlockX() + x;
			int bz = chunk.getPos().getMinBlockZ() + z;
			if (biomes.getBiome(bx, y, bz) != sulfur) {
				return material;
			}
			Material band = band(noise.get(bx, y, bz));
			return band != null ? band.ordinal() : material;
		}
	}
}
