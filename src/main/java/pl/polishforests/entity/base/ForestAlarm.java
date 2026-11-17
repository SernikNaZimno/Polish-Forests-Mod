package pl.polishforests.entity.base;

import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An alarm in the forest (step FA2, docs/04-pionowy-wycinek.md "Projekt FA2"): an event with a source, a radius and a
 * strength that the animals of the mod within the radius hear ({@link Listener}). The intensity falls linearly from the
 * strength at the source to 0 at the radius ({@link #intensityAt}); an animal flees from the source when it hears at least
 * {@link #FLEE_INTENSITY}, otherwise it stands alert for a while (the roe deer with its rump patch flared).
 *
 * <p>Sources in step FA2: the fauna game test ({@link Kind#TEST}), a running player ({@link Kind#RUNNING_PLAYER}: every
 * {@value #PLAYER_INTERVAL} ticks around each sprinting player that is not a spectator, radius {@value #PLAYER_RADIUS}
 * blocks, strength 1), and the warning bark of a fleeing roe deer ({@link Kind#BARK}: its group and neighbours hear it, an
 * animal that reacts to a bark does not bark itself, so the alarm does not run on through the forest). The screech of the
 * jay joins in step FA4 and the jay as an entity in FA6 (new kinds only at the end of the enum). Step FA3: the snort of a
 * wild boar at the start of a flight ({@link Kind#SNORT}), a warning like the bark (an animal that flees from it does not
 * warn again). Step FA4: the screech of a jay a running player startles ({@link Kind#JAY}, {@code sound/ForestAmbience}, radius
 * 32 blocks), a warning like the bark.
 *
 * @param source   where the alarm comes from
 * @param radius   how far it is heard (blocks)
 * @param strength its intensity at the source (0-1)
 * @param kind     what raised it
 * @param cause    the entity that raised it (a player, an animal), or null
 */
public record ForestAlarm(Vec3 source, double radius, float strength, Kind kind, @Nullable Entity cause) {
	/** Least intensity that makes an animal flee instead of standing alert. */
	public static final float FLEE_INTENSITY = 0.5F;
	/** Ticks between two alarms of a running player. */
	public static final int PLAYER_INTERVAL = 10;
	/** Radius of the alarm of a running player (blocks). */
	public static final double PLAYER_RADIUS = 64;
	/** Alarms raised and animals that heard one (for the census of the fauna game test). */
	public static final LongAdder RAISED = new LongAdder();
	public static final LongAdder HEARD = new LongAdder();

	/** What raised an alarm. New kinds only at the end. */
	public enum Kind {
		TEST, RUNNING_PLAYER, BARK, SNORT, JAY
	}

	/** Animals that heard an alarm, by its kind (step FA4: the census counts those of the jay apart). */
	public static final LongAdder[] HEARD_BY_KIND = heardByKind();

	private static LongAdder[] heardByKind() {
		LongAdder[] a = new LongAdder[Kind.values().length];
		for (int i = 0; i < a.length; i++) {
			a[i] = new LongAdder();
		}
		return a;
	}

	/** Whether the alarm is the warning of another animal (an animal that flees from it does not warn again). */
	public boolean warning() {
		return kind == Kind.BARK || kind == Kind.SNORT || kind == Kind.JAY;
	}

	/** An animal that hears the alarms of the forest. */
	public interface Listener {
		/** Called on the server for an alarm heard at {@code distance} blocks from its source (intensity above 0). */
		void hearAlarm(ForestAlarm alarm, double distance);
	}

	public ForestAlarm {
		if (radius <= 0 || strength < 0 || strength > 1) {
			throw new IllegalArgumentException("Bad alarm radius " + radius + " or strength " + strength);
		}
	}

	/** The intensity at a distance from the source: the strength falling linearly to 0 at the radius. */
	public float intensityAt(double distance) {
		return distance >= radius ? 0 : (float) (strength * (1 - distance / radius));
	}

	/** Whether an animal at a distance flees (else it stands alert, when it hears it at all). */
	public boolean fleesAt(double distance) {
		return intensityAt(distance) >= FLEE_INTENSITY;
	}

	/** Raises an alarm: every listener within the radius (and 32 blocks up and down) hears it. Returns how many heard it. */
	public static int raise(ServerLevel level, ForestAlarm alarm) {
		RAISED.increment();
		Vec3 s = alarm.source();
		double r = alarm.radius();
		AABB box = new AABB(s.x - r, s.y - 32, s.z - r, s.x + r, s.y + 32, s.z + r);
		int n = 0;
		for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof Listener && e.isAlive() && e != alarm.cause())) {
			double d = e.position().distanceTo(s);
			if (alarm.intensityAt(d) > 0) {
				((Listener) e).hearAlarm(alarm, d);
				n++;
			}
		}
		HEARD.add(n);
		HEARD_BY_KIND[alarm.kind().ordinal()].add(n);
		return n;
	}

	/** Registers the alarm of running players. */
	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(ForestAlarm::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % PLAYER_INTERVAL != 3) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (runningThreat(p)) {
				raise(level, new ForestAlarm(p.position(), PLAYER_RADIUS, 1.0F, Kind.RUNNING_PLAYER, p));
			}
		}
	}

	/** A player whose running the animals hear: sprinting, alive, not a spectator, not invisible. */
	public static boolean runningThreat(@Nullable Entity e) {
		return e instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && p.isSprinting() && !p.isInvisible();
	}
}
