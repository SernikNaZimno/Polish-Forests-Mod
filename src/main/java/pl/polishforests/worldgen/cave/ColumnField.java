package pl.polishforests.worldgen.cave;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;

/**
 * Density functions of the caves (step J1) that read the terrain of the "Poland" generator instead of vanilla terrain
 * noise: the vanilla cave router asks how deep a point lies below the surface, and the Poland surface is the landscape
 * model, not a density. Each function is bound to a sampler in the user fields of the {@link SamplerContext}, like the
 * vanilla beardifier; the sampler of a chunk reads the model tops of the columns ({@link PolandCaves.Columns}).
 * Never encoded: the functions live only in code, built by {@link CaveDensity}.
 */
public enum ColumnField implements DensityFunction {
	/**
	 * The vanilla "sloped cheese" below the Poland surface: {@link CaveDensity#SLOPE_PER_BLOCK} per block below the top of the
	 * model column (0 on the top face of the top block), negative above it. Vanilla chooses its cave kinds by this value
	 * (entrances only near the surface, cheese and spaghetti deeper), so the caves keep the vanilla depth profile under
	 * the Poland terrain.
	 */
	DEPTH("surface_depth"),
	/** The vanilla aquifer's preliminary surface level: Y of the top block of the model column. */
	LEVEL("surface_level");

	private final ContextKey<DensitySampler> key;
	private final MapCodec<ColumnField> codec = MapCodec.unit(this);

	ColumnField(String name) {
		this.key = new ContextKey<>(Identifier.fromNamespaceAndPath("polishforests", name));
	}

	/** The key of the sampler of this function in the sampler context. */
	public ContextKey<DensitySampler> key() {
		return key;
	}

	@Override
	public DensitySampler compileSampler(DensityFunction.CompileContext context) {
		// Without a bound sampler the point counts as deep underground (never used by the generator, which always binds).
		return new ContextBoundSampler(key, new ConstantFunction.Sampler(this == DEPTH ? 1000.0F : 0.0F));
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.INFINITE;
	}

	@Override
	public @DensityFunction.Axes int domainAxes() {
		return this == DEPTH ? ALL_AXES : AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<ColumnField> codec() {
		return codec;
	}

	/** The sampler of {@link #DEPTH} for the model tops of {@code columns}. */
	static DensitySampler depth(PolandCaves.Columns columns) {
		return new Sampler(columns, true);
	}

	/** The sampler of {@link #LEVEL} for the model tops of {@code columns}. */
	static DensitySampler level(PolandCaves.Columns columns) {
		return new Sampler(columns, false);
	}

	private record Sampler(PolandCaves.Columns columns, boolean depth) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
			for (int z = 0; z < volume.sizeZ(); z++) {
				int blockZ = volume.blockZ(z);
				for (int x = 0; x < volume.sizeX(); x++) {
					int blockX = volume.blockX(x);
					int top = columns.top(blockX, blockZ);
					int base = volume.indexUnchecked(x, 0, z);
					for (int y = 0; y < volume.sizeY(); y++) {
						outputBuffer.set(base + y, value(top, volume.blockY(y)));
					}
				}
			}
		}

		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			return value(columns.top(blockX, blockZ), blockY);
		}

		private float value(int top, int y) {
			return depth ? CaveDensity.SLOPE_PER_BLOCK * (top + 1 - y) : top;
		}
	}
}
