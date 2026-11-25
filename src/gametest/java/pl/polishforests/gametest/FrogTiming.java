package pl.polishforests.gametest;

import java.util.concurrent.atomic.LongAdder;

/**
 * Step FA10 (KFA-6, ZW-8): the timing of the server ticks of frogs for the MSPT scenes of the fauna game test. The common
 * frog of step FA4 is a vanilla {@code Frog} with a variant of the mod, so the timing of the animals of the mod
 * ({@code PolishAnimal#setTiming}) does not see it; the game test mixin {@code FrogTimingMixin} times {@code Frog#tick} on
 * the server while this timing is on. Off outside the MSPT scenes.
 */
public final class FrogTiming {
	public static final LongAdder TICK_NANOS = new LongAdder();
	public static final LongAdder TICKS = new LongAdder();
	private static volatile boolean timing;

	private FrogTiming() {
	}

	/** Turns the timing on (and resets it) or off. */
	public static void set(boolean on) {
		if (on) {
			TICK_NANOS.reset();
			TICKS.reset();
		}
		timing = on;
	}

	public static boolean on() {
		return timing;
	}

	/** Adds one timed server tick of a frog. */
	public static void add(long nanos) {
		TICK_NANOS.add(nanos);
		TICKS.increment();
	}
}
