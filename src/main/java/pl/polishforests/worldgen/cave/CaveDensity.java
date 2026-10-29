package pl.polishforests.worldgen.cave;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The vanilla noise caves of the overworld (cheese, spaghetti, noodles, pillars and the cave entrances) as one density
 * function over the "Poland" terrain (step J1 of docs/04-pionowy-wycinek.md). It is the vanilla final density of
 * {@code NoiseRouterData.registerTerrainNoises} built from the vanilla registered functions and noises by id, with two
 * changes forced by the 2.5D terrain:
 * <ul>
 * <li>the vanilla "sloped cheese" (terrain density) is replaced by {@link ColumnField#DEPTH}, a linear depth below the
 * top of the model column, which keeps the vanilla choice of cave kinds by depth: only the entrances (spaghetti 3D and
 * the big entrances) within about 10 blocks of the surface, below them the cheese with its top slide, the spaghetti 2D
 * layer, the entrances and the pillars; and the vanilla {@code min(slopedCheese, entrances × 5)} near the surface is
 * the entrances alone, since the ground itself comes from the surface plan (a density of the terrain would carve every
 * slope that the interpolation between cells sees from above);</li>
 * <li>no top slide (the vanilla world ends at Y 320, the Poland worlds at Y 767 and 2031), and the noodles reach the
 * top of the world instead of Y 320; the bottom slide, the lava level and the deepslate stay at the vanilla Y, since
 * both Poland worlds start at the vanilla Y -64.</li>
 * </ul>
 * The result is in the units of the vanilla final density (interpolated in cells of 4 × 8 × 4 blocks, squeezed), so the
 * vanilla aquifer and its barriers work on it unchanged: a block is a cave where it is at most 0.
 */
public final class CaveDensity {
	/**
	 * Vanilla sloped cheese per block below the surface: the depth gradient (3 per 384 blocks) times 4 times the terrain
	 * factor 5, a typical vanilla value of inland plains and forests (spline points 4.69–6.25). With it the vanilla
	 * threshold 1.5625 of the near-surface caves lies 10 blocks below the surface and the cheese top slide ends about 15
	 * blocks below it, as under vanilla plains.
	 */
	public static final float SLOPE_PER_BLOCK = 4.0F * 5.0F / 128.0F;
	/** The vanilla lower limit of the noodle caves, 4 blocks above the vanilla world bottom. */
	private static final int NOODLE_ABOVE_BOTTOM = 4;

	private CaveDensity() {
	}

	/**
	 * The cave density of a world whose blocks span {@code minY} to {@code maxY}.
	 *
	 * @param functions the density functions of the world (vanilla registry {@code worldgen/density_function})
	 * @param noises    the noises of the world (vanilla registry {@code worldgen/noise})
	 */
	public static DensityFunction build(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises, int minY,
			int maxY) {
		DensityFunction depth = ColumnField.DEPTH;
		DensityFunction caves = belowSurface(DensityFunctions.rangeChoice(depth, -1000000.0F,
				NoiseRouterData.SURFACE_DENSITY_THRESHOLD, NoiseRouterData.getFunction(functions, NoiseRouterData.ENTRANCES).mul(5.0F),
				NoiseRouterData.underground(functions, noises, depth)));
		// The vanilla bottom slide of the overworld (NoiseRouterData.slideOverworld): solid toward the bottom 24 blocks.
		DensityFunction slid = DensityFunctions.lerp(DensityFunctions.yClampedGradient(minY, minY + 24, 0.0F, 1.0F),
				0.1171875F, caves);
		return DensityFunctions.add(DensityFunctions.min(NoiseRouterData.postProcess(slid, 4, 8), noodle(functions, noises,
				minY + NOODLE_ABOVE_BOTTOM, maxY)), DensityFunctions.beardifier());
	}

	/**
	 * Typical Y of the top block of a vanilla plain or forest, the reference of the vanilla Y limits that follow the
	 * surface rather than the world bottom.
	 */
	static final int VANILLA_SURFACE_Y = 70;
	/** The vanilla gradient of the big cave entrances: none above Y 30, smaller toward Y -10 (NoiseRouterData.entrances). */
	private static final DensityFunction BIG_ENTRANCE_GRADIENT = DensityFunctions.yClampedGradient(-10, 30, 0.3F, 0.0F);

	/**
	 * The function with the gradient of the vanilla big entrances by the depth below the Poland surface instead of by Y:
	 * from {@value #VANILLA_SURFACE_Y} − 30 to {@value #VANILLA_SURFACE_Y} + 10 blocks deep, as under a vanilla plain. In
	 * vanilla the big entrances shrink below Y 30, i.e. 40 to 80 blocks under its plains; by Y they would fill the whole
	 * underground of the Poland lowlands, which lie at Y 130–250 (step J1, comparison with vanilla in
	 * {@code CaveProfileTest}).
	 */
	static DensityFunction belowSurface(DensityFunction function) {
		DensityFunction byDepth = DensityFunctions.clampedMap(ColumnField.DEPTH, (VANILLA_SURFACE_Y - 30) * SLOPE_PER_BLOCK,
				(VANILLA_SURFACE_Y + 10) * SLOPE_PER_BLOCK, 0.0F, 0.3F);
		return function.rewriteChildren(new DfRewriteRule() {
			@Override
			public DensityFunction rewrite(DensityFunction f) {
				f = DfRewriteRule.INLINE_REFERENCE.rewrite(f);
				return f.equals(BIG_ENTRANCE_GRADIENT) ? byDepth : f.rewriteChildren(this);
			}
		});
	}

	/**
	 * The vanilla noodle caves ({@code NoiseRouterData.noodle}) between {@code minY} and {@code maxY}: the same noises,
	 * scales, cells and thickness, only the vanilla upper limit Y 320 moves to the top of the world.
	 */
	static DensityFunction noodle(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises, int minY, int maxY) {
		DensityFunction y = NoiseRouterData.getFunction(functions, NoiseRouterData.Y);
		DensityFunction toggle = NoiseRouterData.yLimitedInterpolatable(y,
				DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE), 1.0, 1.0), minY, maxY, -1, 4, 8);
		DensityFunction thickness = NoiseRouterData.yLimitedInterpolatable(y,
				DensityFunctions.mappedNoise(noises.getOrThrow(Noises.NOODLE_THICKNESS), 1.0, 1.0, -0.05F, -0.1F), minY, maxY, 0, 4, 8);
		double ridgeFrequency = 2.6666666666666665;
		DensityFunction ridgeA = NoiseRouterData.yLimitedInterpolatable(y,
				DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE_RIDGE_A), ridgeFrequency, ridgeFrequency), minY, maxY, 0, 4, 8);
		DensityFunction ridgeB = NoiseRouterData.yLimitedInterpolatable(y,
				DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE_RIDGE_B), ridgeFrequency, ridgeFrequency), minY, maxY, 0, 4, 8);
		DensityFunction ridged = DensityFunctions.max(ridgeA.abs(), ridgeB.abs()).mul(1.5F);
		return DensityFunctions.rangeChoice(toggle, -1000000.0F, 0.0F, DensityFunctions.constant(64.0F),
				DensityFunctions.add(thickness, ridged));
	}

	/**
	 * The vanilla overworld aquifers ({@code NoiseRouterData.overworldAquifers}: barrier, floodedness, spread and lava
	 * noises by id) over the Poland surface: the preliminary surface level is the model top ({@link ColumnField#LEVEL}).
	 * The exclusion is the vanilla one ({@code OverworldBiomeBuilder.deepDarkRegion}: no water or lava where the erosion is
	 * below -0.225 and the depth above 0.9, so the deep dark stays dry; step J2) with the depth of the cave biomes
	 * ({@link ColumnField#BIOME_DEPTH}) instead of the vanilla terrain depth; without the cave biomes
	 * ({@code deepDarkDry} false, the diagnostic switch of {@link CaveBiomes#enabledFromSystem}) nothing is excluded, as in J1.
	 */
	public static Aquifer.Config aquifers(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises,
			boolean deepDarkDry) {
		Aquifer.Config vanilla = NoiseRouterData.overworldAquifers(functions, noises, NoiseRouterData.OVERWORLD_FUNCTIONS);
		if (!deepDarkDry) {
			return new Aquifer.Config(vanilla.barrierNoise(), vanilla.fluidLevelFloodednessNoise(), vanilla.fluidLevelSpreadNoise(),
					vanilla.lavaNoise(), DensityFunctions.constant(-1.0F), ColumnField.LEVEL);
		}
		DensityFunction exclusion = OverworldBiomeBuilder.deepDarkRegion(
				NoiseRouterData.getFunction(functions, NoiseRouterData.OVERWORLD_FUNCTIONS.erosion()), ColumnField.BIOME_DEPTH);
		return new Aquifer.Config(vanilla.barrierNoise(), vanilla.fluidLevelFloodednessNoise(), vanilla.fluidLevelSpreadNoise(),
				vanilla.lavaNoise(), exclusion, ColumnField.LEVEL);
	}
}
