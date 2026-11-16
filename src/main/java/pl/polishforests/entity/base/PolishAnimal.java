package pl.polishforests.entity.base;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import org.jspecify.annotations.Nullable;

/**
 * The common base of the animals of the mod (step FA1, docs/04-pionowy-wycinek.md "Projekt FA1"): a vanilla
 * {@link Animal} with a GeckoLib model ({@link GeoEntity}) and a SmartBrainLib brain ({@link SmartBrainOwner}; SBL 2.0
 * makes and ticks the brain through its own mixins on {@code makeBrain} and {@code serverAiStep}), described by an
 * {@link AnimalProfile}.
 *
 * <p><b>What it does for every species.</b>
 * <ul>
 * <li>An {@link Action} synchronized to the client (not saved): what the animal does when it stands, so the model plays the
 * grazing, resting or alert animation; the brain's behaviours set it.
 * <li>A texture variant ({@link #variant()}) that the renderer passes to the model through a GeckoLib {@code DataTicket}
 * ({@code client/entity/PolishAnimalRenderer}), so each species only says which texture it wears.
 * <li>No breeding (C10) and no food.
 * <li><b>Spawn caps and despawn (R15, KFA-4, KFA-5).</b> The animals keep their vanilla category ({@code CREATURE}), but
 * {@link #requiresCustomPersistence()} is true, which keeps them out of the mob counts of vanilla's {@code NaturalSpawner}
 * ({@code createState} skips such mobs), so they take no place of the wolves, foxes and rabbits; the mod's own
 * {@code FaunaSpawner} keeps its own limits. Since that flag also stops vanilla's {@code checkDespawn}, this class does
 * the despawn itself: {@link #removeWhenFarAway} is true only beyond {@value #DESPAWN_DISTANCE} blocks from the nearest
 * player and only for an animal without a name, a leash or {@code persistenceRequired}; nothing despawns closer (the
 * random despawn of vanilla's mobs after 30 s without action is left out). In 26.3 the {@code CREATURE} category is
 * persistent (vanilla spawns it only on chunk spawn every 400 ticks) and {@link Animal#removeWhenFarAway} is false: a
 * vanilla animal never despawns, so without this rule the population would only grow with exploration. The animals of
 * the spawn at generation are {@code persistenceRequired} (they belong to their chunk, as vanilla's animals do), so only
 * those of the fauna spawner despawn.
 * </ul>
 *
 * @param <T> the species class itself (SmartBrainLib's self type)
 */
public abstract class PolishAnimal<T extends PolishAnimal<T>> extends Animal implements GeoEntity, SmartBrainOwner<T> {
	/** Distance (blocks) beyond which an animal of the mod despawns (and closer than which it never does). */
	public static final int DESPAWN_DISTANCE = 128;

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(PolishAnimal.class, EntityDataSerializers.INT);

	/** What a standing animal does (for the animation and {@code /polishforests fauna}). Not saved. */
	public enum Action {
		NONE, EAT, REST, ALERT
	}

	/** The state the fauna command reports. */
	public enum State {
		FLEEING, MOVING, GRAZING, RESTING, ALERT, IDLE;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Game time until which the animal is fleeing (set by the flight behaviour), for the state only. */
	private long fleeingUntil;

	protected PolishAnimal(EntityType<? extends T> type, Level level) {
		super(type, level);
	}

	/** The profile of the species. */
	public abstract AnimalProfile profile();

	/**
	 * The texture variant: the file name without {@code .png} under {@code textures/entity/<species>/} (e.g.
	 * {@code buck_winter}); the renderer passes it to the model in a {@code DataTicket}.
	 */
	public abstract String variant();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, Action.NONE.ordinal());
	}

	public Action action() {
		int i = entityData.get(DATA_ACTION);
		Action[] all = Action.values();
		return i >= 0 && i < all.length ? all[i] : Action.NONE;
	}

	public void setAction(Action action) {
		entityData.set(DATA_ACTION, action.ordinal());
	}

	/** Marks the animal as fleeing for the given ticks (the state of {@code /polishforests fauna}). */
	public void markFleeing(int ticks) {
		fleeingUntil = level().getGameTime() + ticks;
		setAction(Action.NONE);
	}

	public boolean isFleeing() {
		return level().getGameTime() < fleeingUntil;
	}

	/** The state for the fauna command (server side). */
	public State state() {
		boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || getNavigation().isInProgress();
		if (moving) {
			return isFleeing() ? State.FLEEING : State.MOVING;
		}
		return switch (action()) {
			case EAT -> State.GRAZING;
			case REST -> State.RESTING;
			case ALERT -> State.ALERT;
			case NONE -> State.IDLE;
		};
	}

	/**
	 * The flight distance from a player: {@link AnimalProfile#fleeMin()} for a sneaking one, {@link AnimalProfile#fleeMax()}
	 * for a sprinting one, the middle for a walking one; 0 for a spectator (a creative player is still a threat). An
	 * invisible player counts only from very close: the distance times vanilla's visibility of an invisible entity
	 * (0.7 times its armor cover, at least 0.1), so 2-3 blocks without armor.
	 */
	public float fleeDistance(Player player) {
		if (player.isSpectator()) {
			return 0;
		}
		AnimalProfile p = profile();
		float d;
		if (player.isShiftKeyDown()) {
			d = p.fleeMin();
		} else {
			d = player.isSprinting() ? p.fleeMax() : (p.fleeMin() + p.fleeMax()) / 2.0F;
		}
		return player.isInvisible() ? d * 0.7F * Math.max(0.1F, player.getArmorCoverPercentage()) : d;
	}

	/** The nearest player closer than its {@link #fleeDistance}, or null ({@link FleeFromPlayers}). */
	public @Nullable Player nearestThreat() {
		Player best = null;
		double bestDist = Double.MAX_VALUE;
		for (Player p : level().players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = distanceTo(p);
			if (d < fleeDistance(p) && d < bestDist) {
				best = p;
				bestDist = d;
			}
		}
		return best;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new ForestGroundNavigation(this, level);
	}

	// ------------------------------------------------------------------ spawn caps and despawn

	/** Keeps the animal out of vanilla's mob counts (see the class comment); the despawn is {@link #checkDespawn()}. */
	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return distSqr > (double) DESPAWN_DISTANCE * DESPAWN_DISTANCE && !hasCustomName() && !isLeashed() && !isPersistenceRequired();
	}

	/** Vanilla's despawn without the random despawn closer than {@value #DESPAWN_DISTANCE} blocks (the animals are friendly). */
	@Override
	public void checkDespawn() {
		if (level().getDifficulty() == Difficulty.PEACEFUL && !getType().isAllowedInPeaceful()) {
			discard();
			return;
		}
		Entity player = level().getNearestPlayer(this, -1.0);
		if (player != null && removeWhenFarAway(player.distanceToSqr(this))) {
			discard();
		}
	}

	// ------------------------------------------------------------------ no breeding

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
