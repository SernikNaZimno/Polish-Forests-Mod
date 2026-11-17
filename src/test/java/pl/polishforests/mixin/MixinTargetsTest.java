package pl.polishforests.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * The climate mixin targets exist in {@link Biome} of this game version. Unit tests run without
 * mixins, so without this check a signature change after a Minecraft update would only show up at game start.
 */
class MixinTargetsTest {
	@Test
	void injectTargetsExistInBiome() {
		int found = 0;
		for (Class<?> mixin : List.of(BiomeTemperatureMixin.class, BiomeFreezeMixin.class)) {
			// @Mixin is not visible at runtime (CLASS retention), @Inject is.
			for (Method handler : mixin.getDeclaredMethods()) {
				Inject inject = handler.getAnnotation(Inject.class);
				if (inject == null) {
					continue;
				}
				for (String target : inject.method()) {
					assertTrue(hasMethod(target), "Missing target " + target + " in Biome (" + mixin.getSimpleName() + ")");
					found++;
				}
			}
		}
		assertEquals(3, found);
	}

	@Test
	void injectTargetsOfTheFrogMixins() {
		// Step FA4: the common frog's croak and the persistence of the frog spawner's frogs (targets by name, no descriptor).
		assertTrue(declares(net.minecraft.world.entity.animal.frog.Frog.class, "getAmbientSound", 0), "Frog.getAmbientSound()");
		assertTrue(declares(net.minecraft.world.entity.Mob.class, "requiresCustomPersistence", 0), "Mob.requiresCustomPersistence()");
		for (Class<?> mixin : List.of(FrogSoundMixin.class, MobPersistenceMixin.class)) {
			int handlers = 0;
			for (Method handler : mixin.getDeclaredMethods()) {
				if (handler.getAnnotation(Inject.class) != null) {
					handlers++;
				}
			}
			assertEquals(1, handlers, mixin.getSimpleName());
		}
	}

	private static boolean declares(Class<?> owner, String name, int parameters) {
		for (Method m : owner.getDeclaredMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == parameters) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@link Biome} declares a method with the name and descriptor from {@code name(descriptor)}. */
	private static boolean hasMethod(String target) {
		int paren = target.indexOf('(');
		assertTrue(paren > 0, "Target without a descriptor: " + target);
		String name = target.substring(0, paren);
		String descriptor = target.substring(paren);
		List<String> candidates = new ArrayList<>();
		for (Method m : Biome.class.getDeclaredMethods()) {
			if (m.getName().equals(name)) {
				String d = MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString();
				if (d.equals(descriptor)) {
					return true;
				}
				candidates.add(d);
			}
		}
		System.out.println("Biome." + name + ": " + candidates);
		return false;
	}
}
