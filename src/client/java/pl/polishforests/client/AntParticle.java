package pl.polishforests.client;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import pl.polishforests.block.ModParticles;

/**
 * An ant on an anthill (step FA8 of docs/04-pionowy-wycinek.md, {@code polishforests:ant}, spawned by
 * {@code AnthillBlock#animateTick}): a tiny dark red-brown quad (the sprite is vanilla's smallest generic particle by id,
 * {@code minecraft:generic_0}, tinted: no texture of the mod) that runs over the top of its block in short straight
 * stretches with sudden turns, as the workers of the red wood ant do, and turns back at the edge of the block. No gravity,
 * no collision; it lives 1.5–4 seconds.
 */
public final class AntParticle extends SingleQuadParticle {
	/** Speed of a running ant, blocks per tick (about 3 cm/s, Formica rufa runs 2–5 cm/s). */
	static final double SPEED = 0.0015;
	private final double minX;
	private final double minZ;
	private double heading;

	private AntParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.minX = Math.floor(x);
		this.minZ = Math.floor(z);
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 1;
		this.quadSize = 0.025F + 0.01F * random.nextFloat();
		float shade = 0.85F + 0.15F * random.nextFloat();
		setColor(0.32F * shade, 0.12F * shade, 0.06F * shade);
		this.heading = random.nextDouble() * Mth.TWO_PI;
		this.lifetime = 30 + random.nextInt(50);
		steer();
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		if (random.nextInt(8) == 0) {
			heading += (random.nextDouble() - 0.5) * 2.4;
			steer();
		}
		double nx = x + xd;
		double nz = z + zd;
		if (nx < minX + 0.05 || nx > minX + 0.95 || nz < minZ + 0.05 || nz > minZ + 0.95) {
			heading += Math.PI;
			steer();
			nx = x + xd;
			nz = z + zd;
		}
		setPos(nx, y, nz);
	}

	private void steer() {
		double v = SPEED * (8 + 4 * random.nextDouble());
		xd = Math.cos(heading) * v;
		zd = Math.sin(heading) * v;
		yd = 0;
	}

	/** Registers the provider of the particle type (client initializer). */
	public static void register() {
		ParticleProviderRegistry.getInstance().register(ModParticles.ant(), sprites -> new Provider(sprites::get));
	}

	private record Provider(java.util.function.Function<RandomSource, TextureAtlasSprite> sprites)
			implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xa, double ya,
				double za, RandomSource random) {
			return new AntParticle(level, x, y, z, sprites.apply(random));
		}
	}
}
