package pl.polishforests.block;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * The particle types of the mod (step FA8: {@code polishforests:ant}, the ants crawling on an anthill, {@link AnthillBlock};
 * drawn by the client's {@code AntParticle}). Particle types are not saved, so they are outside the registry snapshot.
 */
public final class ModParticles {
	private static @Nullable SimpleParticleType ant;

	private ModParticles() {
	}

	/** Registers the particle types (in the game only; the unit tests do not need them). */
	public static void register() {
		ant = Registry.register(BuiltInRegistries.PARTICLE_TYPE, PolishForests.id("ant"), FabricParticleTypes.simple());
	}

	public static SimpleParticleType ant() {
		if (ant == null) {
			throw new IllegalStateException("The particle types are not registered yet");
		}
		return ant;
	}
}
