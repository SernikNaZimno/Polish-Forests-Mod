package pl.polishforests.entity.base;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * The common base of the animals of the mod (step FA1, docs/04-pionowy-wycinek.md "Projekt FA1"; step FA2 "Projekt
 * FA2": the calendar, the groups, the alarm, the daily routine's state): a vanilla {@link Animal} with a GeckoLib model
 * ({@link GeoEntity}) and a SmartBrainLib brain ({@link SmartBrainOwner}; SBL 2.0 makes and ticks the brain through its own
 * mixins on {@code makeBrain} and {@code serverAiStep}), described by an {@link AnimalProfile}.
 *
 * <p><b>What it does for every species.</b>
 * <ul>
 * <li>An {@link Action} synchronized to the client (not saved): what the animal does when it stands, so the model plays the
 * grazing, resting or alert animation; the brain's behaviours set it. A synchronized "flared" flag (not saved) while it
 * flees or stands alert after an alarm (the roe deer's white rump patch).
 * <li>A texture variant ({@link #variant()}) and bones not drawn ({@link #hiddenBones()}) that the renderer passes to the
 * model through GeckoLib {@code DataTicket}s ({@code client/entity/PolishAnimalRenderer}).
 * <li><b>The calendar</b> (FA2): every {@value #SLOW_INTERVAL} ticks (spread over the animals by id) {@link #onCalendar}
 * gets the sub-season of the season source (Serene Seasons or the built-in calendar), so a species changes its coat and
 * antlers in the tick, not only at spawn. An animal without AI ({@code NoAI}) keeps its look.
 * <li><b>Groups</b> (FA2, {@link #updateHerd}): an animal follows a group leader ({@link #herdId()}; not saved, so a
 * loaded world forms its groups anew from the animals near each other). In the sub-seasons of the winter groups a leader
 * whose group is smaller than the winter maximum joins the nearest other group within {@value #MERGE_WINTER} blocks that
 * still fits (an animal alone looks for one within {@value #LONE_WINTER} blocks); outside them only groups within
 * {@value #MERGE_SUMMER} blocks join, up to the summer maximum, and the members of a group larger than the season's maximum
 * leave it (the last ones by id). The young of a mother follow her group: when she leaves one, they leave with her. A leader that joins another group hands its members over at
 * once, so the counts of the groups stay exact and no group overfills and sheds lone animals. Members walk, feed and rest
 * near their leader ({@link DailyRoutine}).
 * <li><b>The alarm</b> (FA2, {@link ForestAlarm.Listener}): an alarm heard at the flight intensity makes the animal flee
 * from its source ({@link FleeFromPlayers}); a weaker one makes it stand alert for a few seconds.
 * <li>No breeding (C10) and no food; the young of a mother appear with her at spawn and grow up as vanilla's young, at the
 * latest when the sub-seasons of the young end ({@link #outOfSeasonYoung}); vanilla's random young of a spawned group are
 * off.
 * <li><b>Spawn caps and despawn (R15, KFA-4, KFA-5).</b> The animals keep their vanilla category ({@code CREATURE}), but
 * {@link #requiresCustomPersistence()} is true, which keeps them out of the mob counts of vanilla's {@code NaturalSpawner}
 * ({@code createState} skips such mobs), so they take no place of the wolves, foxes and rabbits; the mod's own
 * {@code FaunaSpawner} keeps its own limits. Since that flag also stops vanilla's {@code checkDespawn}, this class does
 * the despawn itself: {@link #removeWhenFarAway} is true only for a {@linkplain #isWanderer() wanderer} (an animal of the
 * fauna spawner, {@link EntitySpawnReason#NATURAL}), only beyond {@value #DESPAWN_DISTANCE} blocks from the nearest
 * player and only without a name, a leash or {@code persistenceRequired}; nothing despawns closer (the random despawn of
 * vanilla's mobs after 30 s without action is left out). In 26.3 the {@code CREATURE} category is persistent (vanilla
 * spawns it only on chunk spawn every 400 ticks) and {@link Animal#removeWhenFarAway} is false: a vanilla animal never
 * despawns, so without this rule the population of the spawner would only grow with exploration. Every other animal
 * never despawns, as a vanilla animal: those of the spawn at generation (they belong to their chunk), and those from a
 * spawn egg, a dispenser, a spawner block or {@code /summon} (also with NBT, which skips {@code finalizeSpawn}).
 * <li><b>Timing</b> (KFA-6): with {@link #timing} on, the server ticks of the animals of the mod are timed
 * ({@link #TICK_NANOS}, {@link #TICKS}) for the MSPT scene of the fauna game test.
 * <li><b>Hooks of step FA3</b> (docs/04 "Projekt FA3"; each defaults to the behaviour of the roe deer of FA2): whether an
 * animal lives in groups at all ({@link #herds()}: the adult wild boar male lives alone), its rank in a group
 * ({@link #seniority()}: the oldest sow leads the sounder), whether it flees on its own ({@link #fleesAlone()}: a member
 * of a fleeing sounder follows in single file instead, {@link FollowHerd}), the action of a settled bout of the daily
 * routine ({@link #settle}: rooting, wallowing), the place of a rest ({@link #restTarget}), the composition of a spawned
 * group ({@link #becomeSolitary}, {@link #becomeHerdLeader}, {@link #becomeMember}) and a server tick of the species
 * ({@link #onServerTick}).
 * </ul>
 *
 * @param <T> the species class itself (SmartBrainLib's self type)
 */
public abstract class PolishAnimal<T extends PolishAnimal<T>> extends Animal implements GeoEntity, SmartBrainOwner<T>, ForestAlarm.Listener {
	/** Distance (blocks) beyond which an animal of the mod despawns (and closer than which it never does). */
	public static final int DESPAWN_DISTANCE = 128;
	/** Ticks between two updates of the calendar and the group of one animal. */
	public static final int SLOW_INTERVAL = 100;
	/** Radius (blocks) within which groups join in the sub-seasons of the winter groups. */
	public static final double MERGE_WINTER = 48;
	/** Radius (blocks) within which an animal alone looks for a group in the sub-seasons of the winter groups. */
	public static final double LONE_WINTER = 80;
	/** Radius (blocks) within which groups join in the rest of the year. */
	public static final double MERGE_SUMMER = 8;
	/** Distance (blocks) from its leader beyond which an animal leaves its group. */
	public static final double HERD_LOST = 96;
	/** Ticks an alarm below the flight intensity keeps the animal alert. */
	static final int ALERT_TICKS = 80;

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(PolishAnimal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_FLARED = SynchedEntityData.defineId(PolishAnimal.class,
			EntityDataSerializers.BOOLEAN);

	/** Timed server ticks of the animals of the mod (the MSPT scene of the fauna game test, KFA-6). */
	public static final LongAdder TICK_NANOS = new LongAdder();
	public static final LongAdder TICKS = new LongAdder();
	private static volatile boolean timing;

	/** What a standing animal does (for the animation and {@code /polishforests fauna}). Not saved. */
	public enum Action {
		/** New actions only at the end (step FA3: ROOT and WALLOW of the wild boar). */
		NONE, EAT, REST, ALERT, ROOT, WALLOW
	}

	/** The state the fauna command reports (new states only at the end). */
	public enum State {
		FLEEING, MOVING, GRAZING, RESTING, ALERT, IDLE, ROOTING, WALLOWING;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Game time until which the animal is fleeing (set by the flight behaviour), for the state only. */
	private long fleeingUntil;
	/** An animal of the fauna spawner (saved): the only kind that despawns ({@link #removeWhenFarAway}). */
	private boolean wanderer;
	/** The leader of its group, or null when it leads itself (not saved). */
	private @Nullable UUID herdLeader;
	/** The mother of a young one from a spawned group (not saved): the young follow her group. */
	private @Nullable UUID mother;
	/** The size of its group at the last count (a leader's own group, or its leader's for a member). */
	private int herdSize = 1;
	/** Where it lives: where it appeared or was loaded (not saved); the walk targets of a leader lead back within its range. */
	private @Nullable Vec3 home;
	/** The activity of the current bout of its daily routine, or null (members copy their leader's). */
	private AnimalProfile.@Nullable Activity bout;
	/** Game time until which it stands alert after an alarm, and until which its flag "flared" is on. */
	private long alertUntil;
	private long flaredUntil;
	/** The source of an alarm it flees from, until {@link #alarmFleeUntil}, and whether that alarm was a bark. */
	private @Nullable Vec3 alarmSource;
	private long alarmFleeUntil;
	private boolean alarmFromBark;
	/** The animal ahead of it in the single file of a fleeing group (step FA3, {@link FollowHerd}; not saved). */
	private @Nullable UUID fileAhead;

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

	/** The bones the renderer does not draw (comma separated, empty for none), e.g. the antlers of a doe. */
	public String hiddenBones() {
		return "";
	}

	/** Keys of the look of the animal for the fauna command ({@code polishforests.fauna.look.<key>}). */
	public List<String> looks() {
		return isBaby() ? List.of("young") : List.of();
	}

	/** Called on the server every {@value #SLOW_INTERVAL} ticks and at spawn with the sub-season (coat, antlers). */
	protected void onCalendar(SubSeason subSeason) {
	}

	/**
	 * The calendar of the animal: a young one outside the sub-seasons of the young grows up at once (FA2 review: the young
	 * of a summer chunk, whose age does not run while the chunk is unloaded, are no longer spotted young in winter), then
	 * {@link #onCalendar} sets the look of the sub-season.
	 */
	private void calendar(SubSeason subSeason) {
		if (outOfSeasonYoung(profile(), subSeason, isBaby(), isAgeLocked())) {
			setAge(0);
		}
		onCalendar(subSeason);
	}

	/**
	 * Whether an animal is a young one out of its season: young outside the sub-seasons of the young of its profile
	 * ({@link AnimalProfile#young}). Such a young one grows up in the calendar tick; one whose age a player locked (a golden
	 * dandelion) stays young.
	 */
	public static boolean outOfSeasonYoung(AnimalProfile profile, SubSeason subSeason, boolean baby, boolean ageLocked) {
		return baby && !ageLocked && !profile.young(subSeason);
	}

	/** Makes the animal the mother of a group with young (the spawners call it on the first animal of such a group). */
	public void becomeMother() {
	}

	/** Step FA3: makes the animal a lone one (a spawned "group" of one of {@link AnimalProfile#soloShare}: the boar male). */
	public void becomeSolitary() {
	}

	/** Step FA3: makes the animal the leader of a spawned group (the first animal; the wild boar: the oldest sow). */
	public void becomeHerdLeader() {
	}

	/** Step FA3: makes the animal a grown member of a spawned group (the wild boar: a sow or a yearling, no adult male). */
	public void becomeMember() {
	}

	/** Step FA3: whether the animal lives in a group (false: it leads itself and no group takes it or joins it). */
	public boolean herds() {
		return true;
	}

	/**
	 * Step FA3: the rank of the animal in its group; a member of a strictly higher rank than its leader takes the lead (the
	 * oldest sow leads the sounder). 0 for every roe deer, so their groups keep the leaders of FA2.
	 */
	public int seniority() {
		return 0;
	}

	/**
	 * Step FA3: whether the animal flees from a player or an alarm on its own ({@link FleeFromPlayers}); a member of a fleeing
	 * wild boar sounder follows the one ahead of it in single file instead ({@link FollowHerd}).
	 */
	public boolean fleesAlone() {
		return true;
	}

	/**
	 * Step FA3: called when a bout of the daily routine settles (the animal has arrived or stays); returns the action it
	 * shows: grazing for feeding, lying for resting, nothing for moving (the wild boar roots or wallows instead).
	 */
	public Action settle(AnimalProfile.Activity activity) {
		return switch (activity) {
			case FEED -> Action.EAT;
			case REST -> Action.REST;
			case MOVE -> Action.NONE;
		};
	}

	/**
	 * Step FA3: the place of a rest of a leader or a lone animal: the best {@link Thicket} within {@code radius} blocks, or
	 * null to lie down where it stands (the wild boar also goes to a wallow).
	 */
	public @Nullable Vec3 restTarget(int radius, net.minecraft.util.RandomSource random) {
		BlockPos t = Thicket.find(level(), blockPosition(), radius, random);
		return t == null ? null : Vec3.atBottomCenterOf(t);
	}

	/** Step FA3: a server tick of the species (after the calendar, the group and the leaf check); not for an animal without AI. */
	protected void onServerTick(ServerLevel level) {
	}

	/** Step FA3: the animal ahead of it in the single file of its fleeing group, or null ({@link FollowHerd}). */
	public @Nullable PolishAnimal<?> fileAhead() {
		if (fileAhead == null || !(level() instanceof ServerLevel server)) {
			return null;
		}
		Entity e = server.getEntity(fileAhead);
		return e instanceof PolishAnimal<?> a && a.isAlive() && a.getType() == getType() ? a : null;
	}

	public void setFileAhead(@Nullable PolishAnimal<?> ahead) {
		fileAhead = ahead == null ? null : ahead.getUUID();
	}

	/**
	 * Step FA3: puts the members of the group this animal leads in single file behind it: the grown ones by rank (highest
	 * first), the young last, each following the one ahead ({@link FollowHerd}).
	 */
	public void formFile(ServerLevel level) {
		List<PolishAnimal<?>> file = new ArrayList<>(members(level, this));
		file.sort(Comparator.<PolishAnimal<?>>comparingInt(a -> a.isBaby() ? 1 : 0).thenComparing(a -> -a.seniority())
				.thenComparing(Entity::getUUID));
		PolishAnimal<?> ahead = this;
		for (PolishAnimal<?> a : file) {
			a.setFileAhead(ahead);
			ahead = a;
		}
	}

	/** Called when the animal starts to flee; {@code warns} is false when it flees from the warning of another animal. */
	protected void onFlightStart(boolean warns) {
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, Action.NONE.ordinal());
		builder.define(DATA_FLARED, false);
	}

	public Action action() {
		int i = entityData.get(DATA_ACTION);
		Action[] all = Action.values();
		return i >= 0 && i < all.length ? all[i] : Action.NONE;
	}

	public void setAction(Action action) {
		entityData.set(DATA_ACTION, action.ordinal());
	}

	/** Whether the animal shows its alarm (fleeing, or alert after an alarm): synchronized to the client. */
	public boolean flared() {
		return entityData.get(DATA_FLARED);
	}

	/** Marks the animal as fleeing for the given ticks (the state of {@code /polishforests fauna}); a new flight warns. */
	public void markFleeing(int ticks) {
		markFleeing(ticks, true);
	}

	/**
	 * Marks the animal as fleeing for the given ticks; when it was not fleeing yet, {@link #onFlightStart} is called with
	 * {@code warns} (false for a flight from the warning of another animal).
	 */
	public void markFleeing(int ticks, boolean warns) {
		boolean was = isFleeing();
		long now = level().getGameTime();
		fleeingUntil = Math.max(fleeingUntil, now + ticks);
		flaredUntil = Math.max(flaredUntil, fleeingUntil);
		setAction(Action.NONE);
		if (!was) {
			onFlightStart(warns);
		}
	}

	public boolean isFleeing() {
		return level().getGameTime() < fleeingUntil;
	}

	/** Whether the animal stands alert after an alarm below the flight intensity. */
	public boolean isAlerted() {
		return level().getGameTime() < alertUntil;
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
			case ROOT -> State.ROOTING;
			case WALLOW -> State.WALLOWING;
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

	// ------------------------------------------------------------------ alarm

	@Override
	public void hearAlarm(ForestAlarm alarm, double distance) {
		if (isNoAi()) {
			return;
		}
		long now = level().getGameTime();
		if (alarm.fleesAt(distance)) {
			alarmSource = alarm.source();
			alarmFleeUntil = now + 40;
			// The warning of another animal (the roe deer's bark, the wild boar's snort): a flight from it does not warn again.
			alarmFromBark = alarm.kind() == ForestAlarm.Kind.BARK || alarm.kind() == ForestAlarm.Kind.SNORT;
		} else if (!isFleeing()) {
			alertUntil = Math.max(alertUntil, now + ALERT_TICKS);
			flaredUntil = Math.max(flaredUntil, alertUntil);
		}
	}

	/** The source of an alarm the animal should flee from now, or null. */
	public @Nullable Vec3 alarmSource() {
		return level().getGameTime() < alarmFleeUntil ? alarmSource : null;
	}

	/** Whether the alarm it flees from is the warning of another animal (a fleeing animal then does not warn again). */
	public boolean alarmFromBark() {
		return alarmFromBark;
	}

	/** Forgets the alarm it has started to flee from. */
	public void clearAlarm() {
		alarmFleeUntil = 0;
	}

	// ------------------------------------------------------------------ daily routine and home

	/** The activity of the current bout of the daily routine, or null between bouts. */
	public AnimalProfile.@Nullable Activity bout() {
		return bout;
	}

	public void setBout(AnimalProfile.@Nullable Activity bout) {
		this.bout = bout;
	}

	/** Where the animal lives (its position when it first ticked on the server, or after it left a group). */
	public Vec3 home() {
		return home == null ? position() : home;
	}

	public void setHome(Vec3 home) {
		this.home = home;
	}

	/** The time of day of the level (0-23999, 0 = 6:00). */
	public long timeOfDay() {
		return Math.floorMod(level().getOverworldClockTime(), (long) AnimalProfile.DAY);
	}

	// ------------------------------------------------------------------ groups

	/** The id of its group: its leader's id, or its own when it leads itself. */
	public UUID herdId() {
		return herdLeader == null ? getUUID() : herdLeader;
	}

	/** Whether it leads itself (alone or as the leader of a group). */
	public boolean isHerdLeader() {
		return herdLeader == null;
	}

	/** The size of its group at the last count. */
	public int herdSize() {
		return herdSize;
	}

	/** Makes it follow a leader (the spawners put the members of a spawned group under their first animal). */
	public void followLeader(@Nullable PolishAnimal<?> leader) {
		herdLeader = leader == null || leader == this ? null : leader.getUUID();
	}

	/** Whether it can mother young (grown; a species may add more, e.g. no buck). */
	protected boolean canMother() {
		return !isBaby();
	}

	/** Radius (blocks) within which a young one without a mother finds one. */
	static final double ADOPT_RADIUS = 16;

	/**
	 * A young one without a mother (the link is not saved, so the young of a loaded chunk, e.g. of the chunk generation,
	 * have none) takes the nearest animal of its species within {@value #ADOPT_RADIUS} blocks that can mother young and has
	 * fewer young than {@link AnimalProfile#maxYoung} (two for the roe deer).
	 */
	private void adoptMother(ServerLevel level) {
		PolishAnimal<?> best = null;
		double bestDist = Double.MAX_VALUE;
		List<Entity> near = level.getEntities(this, getBoundingBox().inflate(ADOPT_RADIUS + 4, 8, ADOPT_RADIUS + 4),
				e -> e.getType() == getType() && e.isAlive());
		for (Entity e : near) {
			if (!(e instanceof PolishAnimal<?> a) || !a.canMother() || a.isNoAi()) {
				continue;
			}
			double d = distanceTo(a);
			if (d > ADOPT_RADIUS || d >= bestDist) {
				continue;
			}
			UUID id = a.getUUID();
			long young = near.stream().filter(o -> o instanceof PolishAnimal<?> y && y.isBaby() && id.equals(y.mother)).count();
			if (young < profile().maxYoung()) {
				best = a;
				bestDist = d;
			}
		}
		if (best != null) {
			mother = best.getUUID();
		}
	}

	/** The id of the mother of a young one from a spawned group, or null. */
	public @Nullable UUID motherId() {
		return mother;
	}

	/** Makes a young one follow its mother's group (the spawners set it for the young of a mother). */
	public void setMother(@Nullable PolishAnimal<?> mother) {
		this.mother = mother == null ? null : mother.getUUID();
	}

	/** Its leader on the server, or null when it leads itself or the leader is gone. */
	public @Nullable PolishAnimal<?> leader() {
		if (herdLeader == null || !(level() instanceof ServerLevel server)) {
			return null;
		}
		Entity e = server.getEntity(herdLeader);
		return e instanceof PolishAnimal<?> a && a.isAlive() && a.getType() == getType() ? a : null;
	}

	/** The members of the group led by {@code leader} near it (same species, same leader id), ordered by id. */
	public static List<PolishAnimal<?>> members(ServerLevel level, PolishAnimal<?> leader) {
		AABB box = leader.getBoundingBox().inflate(HERD_LOST, 32, HERD_LOST);
		UUID id = leader.getUUID();
		List<PolishAnimal<?>> out = new ArrayList<>();
		for (Entity e : level.getEntities(leader, box, e -> e.getType() == leader.getType() && e.isAlive())) {
			if (e instanceof PolishAnimal<?> a && id.equals(a.herdLeader)) {
				out.add(a);
			}
		}
		out.sort(Comparator.comparing(Entity::getUUID));
		return out;
	}

	/**
	 * The group update (see the class comment): a member re-points to the leader of its leader, leaves a lost leader or a
	 * group too large for the season; a leader counts its group and joins a group nearby that still fits.
	 */
	void updateHerd(ServerLevel level, SubSeason subSeason) {
		AnimalProfile.Range range = profile().group(subSeason);
		if (isBaby() && mother == null) {
			adoptMother(level);
		}
		if (mother != null) {
			// A young one follows the group of its mother (so it leaves a group with her); grown up, it is on its own.
			Entity m = isBaby() ? level.getEntity(mother) : null;
			if (m instanceof PolishAnimal<?> pm && pm.isAlive() && distanceTo(pm) <= HERD_LOST) {
				UUID id = pm.herdId();
				herdLeader = id.equals(getUUID()) ? null : id;
			} else {
				mother = null;
			}
		}
		if (!herds()) {
			// Step FA3: an animal that lives alone (the boar male) leads itself, and no group takes it.
			herdLeader = null;
			herdSize = 1;
			return;
		}
		PolishAnimal<?> leader = leader();
		if (leader != null && leader.herdLeader != null) {
			// Its leader joined another group: follow that group's leader.
			herdLeader = leader.herdLeader;
			leader = leader();
		}
		if (herdLeader != null && (leader == null || distanceTo(leader) > HERD_LOST || !leader.herds())) {
			herdLeader = null;
			leader = null;
			home = position();
		}
		if (leader != null) {
			List<PolishAnimal<?>> members = members(level, leader);
			herdSize = members.size() + 1;
			if (!(isBaby() && mother != null) && herdSize > range.max()) {
				// The young of a mother go where she goes; of the other members (grown, or young without a mother) the last
				// ones by id leave.
				int young = 0;
				int rank = 0;
				for (PolishAnimal<?> m : members) {
					if (m.isBaby() && m.mother != null) {
						young++;
					} else if (m.getUUID().compareTo(getUUID()) < 0) {
						rank++;
					}
				}
				if (rank >= range.max() - 1 - young) {
					herdLeader = null;
					herdSize = 1;
					home = position();
					// A mother takes her young along at once (else she could join another group alone and her young
					// would follow her there later, overfilling it).
					UUID self = getUUID();
					for (PolishAnimal<?> m : members) {
						if (m.isBaby() && self.equals(m.mother)) {
							m.herdLeader = self;
							herdSize++;
						}
					}
				}
			}
			return;
		}
		List<PolishAnimal<?>> own = members(level, this);
		herdSize = own.size() + 1;
		if (handOverLead(own)) {
			return;
		}
		if (herdSize >= range.max()) {
			return;
		}
		boolean winter = profile().winterGroups(subSeason);
		double radius = !winter ? MERGE_SUMMER : herdSize == 1 ? LONE_WINTER : MERGE_WINTER;
		PolishAnimal<?> best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : level.getEntities(this, getBoundingBox().inflate(radius, 16, radius), e -> e.getType() == getType() && e.isAlive())) {
			if (!(e instanceof PolishAnimal<?> other) || other.herdLeader != null || other.isBaby() || other.isNoAi() || !other.herds()) {
				continue;
			}
			double d = distanceTo(other);
			if (d > radius || d >= bestDist) {
				continue;
			}
			// The group counted now, not at its leader's last update: members that came in since (young following their
			// mother, members handed over) would else let it overfill.
			int size = members(level, other).size() + 1;
			other.herdSize = size;
			// Join a group that fits; of two groups of one size the one with the smaller id leads (no two leaders join each other).
			boolean leads = size > herdSize || size == herdSize && other.getUUID().compareTo(getUUID()) < 0;
			if (leads && size + herdSize <= range.max()) {
				best = other;
				bestDist = d;
			}
		}
		if (best != null) {
			UUID id = best.getUUID();
			herdLeader = id;
			// Hand the members over at once (else the new leader would count them only after their own update and could
			// take in another group meanwhile, overfill and shed lone animals).
			for (PolishAnimal<?> m : own) {
				m.herdLeader = id;
			}
			best.herdSize += herdSize;
			herdSize = best.herdSize;
		}
	}

	/**
	 * Step FA3: a leader hands the lead to the grown member of the highest rank when that rank is strictly above its own
	 * ({@link #seniority()}); the group follows the new leader at once. Returns whether it did.
	 */
	private boolean handOverLead(List<PolishAnimal<?>> own) {
		PolishAnimal<?> best = null;
		for (PolishAnimal<?> m : own) {
			if (!m.isBaby() && !m.isNoAi() && m.seniority() > (best == null ? seniority() : best.seniority())) {
				best = m;
			}
		}
		if (best == null) {
			return false;
		}
		UUID id = best.getUUID();
		best.herdLeader = null;
		best.herdSize = herdSize;
		best.home = home();
		for (PolishAnimal<?> m : own) {
			if (m != best) {
				m.herdLeader = id;
			}
		}
		herdLeader = id;
		return true;
	}

	// ------------------------------------------------------------------ server tick: calendar, groups, flag, timing

	/** Turns the timing of the server ticks of the animals of the mod on or off (and resets it when turned on). */
	public static void setTiming(boolean on) {
		if (on) {
			TICK_NANOS.reset();
			TICKS.reset();
		}
		timing = on;
	}

	@Override
	public void tick() {
		if (level() instanceof ServerLevel server) {
			if (timing) {
				long t0 = System.nanoTime();
				super.tick();
				serverTick(server);
				TICK_NANOS.add(System.nanoTime() - t0);
				TICKS.increment();
			} else {
				super.tick();
				serverTick(server);
			}
		} else {
			super.tick();
		}
	}

	private void serverTick(ServerLevel level) {
		if (isRemoved() || isNoAi()) {
			return;
		}
		if (home == null) {
			home = position();
		}
		boolean flared = level.getGameTime() < flaredUntil;
		if (flared != flared()) {
			entityData.set(DATA_FLARED, flared);
		}
		if (Math.floorMod(level.getGameTime() + getId(), (long) SLOW_INTERVAL) == 0) {
			SubSeason subSeason = Seasons.provider().subSeason(level);
			calendar(subSeason);
			updateHerd(level, subSeason);
		}
		if (Math.floorMod(level.getGameTime() + getId(), (long) LEAF_CHECK_INTERVAL) == 0) {
			stepOffLeaves(level);
		}
		onServerTick(level);
	}

	// ------------------------------------------------------------------ never on the leaves (KFA-4)

	/** Ticks between two checks whether the animal stands on leaves. */
	static final int LEAF_CHECK_INTERVAL = 5;

	/** Largest drop (blocks) an animal takes to step off leaves; higher up it is moved down (see {@link #stepOffLeaves}). */
	static final int LEAF_DROP = 3;

	/**
	 * Step FA2: an animal that ended up on a leaf block (pushed by its group in a thicket, or lifted by a jump the walk
	 * control made at the edge of a bush; the navigation never plans a step on leaves, {@link ForestGroundNavigation})
	 * steps off it: towards the nearest column within 2 blocks that is free at its feet and head, has no leaf from its feet
	 * down to the ground, and ground at most {@value #LEAF_DROP} blocks below its feet where it can stand. An animal stranded
	 * higher (on a crown that touches a slope) with no such column is put on the nearest ground within 2 blocks where it can
	 * stand (up to 16 blocks below); it is not walked across the crown.
	 */
	private void stepOffLeaves(ServerLevel level) {
		if (!onGround() || !level.getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ())).is(BlockTags.LEAVES)) {
			return;
		}
		BlockPos feet = blockPosition();
		BlockPos step = null;
		BlockPos ground = null;
		int stepDist = Integer.MAX_VALUE;
		int groundDist = Integer.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int d = dx * dx + dz * dz;
				if (d == 0 || d >= groundDist && d >= stepDist) {
					continue;
				}
				int x = feet.getX() + dx;
				int z = feet.getZ() + dz;
				BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (at.getY() > feet.getY() || !Thicket.standable(level, at)) {
					continue;
				}
				if (d < groundDist && feet.getY() - at.getY() <= 16) {
					ground = at;
					groundDist = d;
				}
				if (d >= stepDist || feet.getY() - at.getY() > LEAF_DROP) {
					continue;
				}
				boolean clear = true;
				for (int y = at.getY(); y <= feet.getY() + 1 && clear; y++) {
					var state = level.getBlockState(p.set(x, y, z));
					clear = !state.is(BlockTags.LEAVES) && (y < feet.getY() || state.getCollisionShape(level, p).isEmpty());
				}
				if (clear) {
					step = at;
					stepDist = d;
				}
			}
		}
		if (step != null) {
			getNavigation().stop();
			Vec3 dir = new Vec3(step.getX() + 0.5 - getX(), 0, step.getZ() + 0.5 - getZ()).normalize().scale(0.3);
			setDeltaMovement(dir.x, getDeltaMovement().y, dir.z);
		} else if (ground != null && feet.getY() - ground.getY() > LEAF_DROP) {
			getNavigation().stop();
			teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		}
	}

	/** Step FA2: no jump onto a leaf block just ahead (the walk control jumps at a bush that blocks the way). */
	@Override
	public void jumpFromGround() {
		// Only the block straight ahead: a wider check (35 degrees to the sides, or no jump while on a leaf) also stopped
		// the jumps up a step of the ground next to a bush, and a fleeing deer stood still (FA2 stage 2).
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		BlockPos ahead = BlockPos.containing(getX() - Mth.sin(yaw) * 0.9, getY() + 0.5, getZ() + Mth.cos(yaw) * 0.9);
		if (!isInWater() && level().getBlockState(ahead).is(BlockTags.LEAVES)) {
			return;
		}
		super.jumpFromGround();
	}

	// ------------------------------------------------------------------ spawn caps and despawn

	/** The tag of {@link #isWanderer()} in the saved data. */
	public static final String TAG_WANDERER = "Wanderer";

	/**
	 * Marks the animals of the fauna spawner ({@link EntitySpawnReason#NATURAL}) as {@linkplain #isWanderer() wanderers};
	 * every other reason (chunk generation, spawn egg, dispenser, spawner block, command) gives an animal that never
	 * despawns, as a vanilla animal. Never makes a vanilla young one. Sets the look of the sub-season ({@link #onCalendar}).
	 */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		wanderer = reason == EntitySpawnReason.NATURAL;
		// No vanilla young (AgeableMob gives every second and later animal of a group a 5% chance in any season): the young
		// come only with a mother of a group of the spawners, in the sub-seasons of the young (FA2 review).
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData == null ? new AgeableMob.AgeableMobGroupData(false) : groupData);
		calendar(Seasons.provider().subSeason(level.getLevel()));
		return data;
	}

	/** True for an animal of the fauna spawner: it despawns beyond {@value #DESPAWN_DISTANCE} blocks. */
	public boolean isWanderer() {
		return wanderer;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_WANDERER, wanderer);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		wanderer = input.getBooleanOr(TAG_WANDERER, false);
	}

	/** Keeps the animal out of vanilla's mob counts (see the class comment); the despawn is {@link #checkDespawn()}. */
	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return wanderer && distSqr > (double) DESPAWN_DISTANCE * DESPAWN_DISTANCE && !hasCustomName() && !isLeashed() && !isPersistenceRequired();
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
