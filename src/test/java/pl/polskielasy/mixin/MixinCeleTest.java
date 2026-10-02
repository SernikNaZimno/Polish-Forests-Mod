package pl.polskielasy.mixin;

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
 * Cele mixinów klimatu istnieją w {@link Biome} tej wersji gry. Testy jednostkowe działają bez
 * mixinów, więc bez tego zmiana sygnatury po aktualizacji Minecrafta wyszłaby dopiero przy starcie gry.
 */
class MixinCeleTest {
	@Test
	void injectTargetsExistInBiome() {
		int found = 0;
		for (Class<?> mixin : List.of(BiomeTemperatureMixin.class, BiomeFreezeMixin.class)) {
			// @Mixin nie jest widoczne w czasie działania (retencja CLASS), @Inject jest.
			for (Method handler : mixin.getDeclaredMethods()) {
				Inject inject = handler.getAnnotation(Inject.class);
				if (inject == null) {
					continue;
				}
				for (String target : inject.method()) {
					assertTrue(hasMethod(target), "Brak celu " + target + " w Biome (" + mixin.getSimpleName() + ")");
					found++;
				}
			}
		}
		assertEquals(2, found);
	}

	/** Czy {@link Biome} deklaruje metodę o nazwie i deskryptorze z {@code nazwa(deskryptor)}. */
	private static boolean hasMethod(String target) {
		int paren = target.indexOf('(');
		assertTrue(paren > 0, "Cel bez deskryptora: " + target);
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
