package gg.xp.xivsupport.events.triggers.duties.vc;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@ScanMe
@CalloutRepo(name = "Another Aloala Island (Criterion/Savage)", duty = KnownDuty.AAI_Criterion)
public class AAI_Crit extends AutoChildEventHandler implements FilteredEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(AAI_Crit.class);
	private final XivState state;
	private StatusEffectRepository buffs;

	public AAI_Crit(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

//	@AutoFeed
//	private final SequentialTrigger<BaseEvent> combatEndHack = SqtTemplates.sq(10_000,
//			InCombatChangeEvent.class, event -> !event.isInCombat(),
//			(event, controller) -> {
//				controller.waitMs(3000);
//				controller.accept(new ForceCombatEnd());
//			}
//	);

	// Ketuduke
	@NpcCastCallout(0x8AD4)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide + Bleed");
	@NpcCastCallout(0x8ACC)
	private final ModifiableCallout<AbilityCastStart> recedingTwinTides = ModifiableCallout.durationBasedCall("Receding Twin Tides", "Out then In + Pairs");
	@NpcCastCallout(0x8ACE)
	private final ModifiableCallout<AbilityCastStart> encroachingTwinTides = ModifiableCallout.durationBasedCall("Encroaching Twin Tides", "In then Out + Pairs");

	// TODO spreads / stacks?

	// Lala
	@NpcCastCallout(0x88AE)
	private final ModifiableCallout<AbilityCastStart> infernoTheorem = ModifiableCallout.durationBasedCall("Inferno Theorem", "Raidwide");
	private final ModifiableCallout<AbilityCastStart> arcaneBlight = ModifiableCallout.durationBasedCall("Arcane Blight", "{safeSpot}");
	private final ModifiableCallout<AbilityCastStart> analysisSafeSide = ModifiableCallout.durationBasedCall("Analysis Safe Side", "{safeSide}");
	private final ModifiableCallout<BuffApplied> planarTacticsForcedMarch = ModifiableCallout.durationBasedCall("Planar Tactics Forced March Direction", "{direction} March");
	private final ModifiableCallout<BaseEvent> planarTacticsEnums = new ModifiableCallout<>("Planar Tactics Enums", "{p1} ({p1Stacks}) {p2} {p2Stacks}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> angularAddition = SqtTemplates.sq(
			20_000,
			AbilityCastStart.class,
			cast -> cast.abilityIdMatches(0x8889, 0x8D2E),
			(event, controller) -> {
				List<ArenaSector> initialSafeSpots = List.of(ArenaSector.SOUTH, ArenaSector.NORTH, ArenaSector.EAST, ArenaSector.WEST);

				BuffApplied numberBuff = controller.waitEvent(BuffApplied.class, buff -> buff.buffIdMatches(0xF62, 0xF63));
				HeadMarkerEvent headMarker = controller.waitEvent(HeadMarkerEvent.class, marker -> List.of(0x1E4L, 0x1E5).contains(marker.getMarkerId()));
				AbilityCastStart arcaneBlightCast = controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x888B, 0x888C, 0x888D, 0x888E));

				int numberOfRotations = numberBuff.buffIdMatches(0xF62) ? 3 : 5;
				if (headMarker.getMarkerId() == 0x1E5) {
					numberOfRotations = -numberOfRotations;
				}

				ArenaSector safeSpot = initialSafeSpots.get((int) (arcaneBlightCast.getAbility().getId() % 0x888B)).plusQuads(numberOfRotations);

				controller.setParam("safeSpot", safeSpot);
				controller.updateCall(arcaneBlight, arcaneBlightCast);
			}
	);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> analysis = SqtTemplates.sq(
			20_000,
			BuffApplied.class,
			buff -> buff.buffIdMatches(0xE8E, 0xE8F, 0xE90, 0xE91) && buff.getTarget().isThePlayer(),
			(event, controller) -> {
				BuffApplied numberBuff = controller.waitEvent(BuffApplied.class, buff -> buff.buffIdMatches(0xE89, 0xECE) && buff.getTarget().isThePlayer());
				HeadMarkerEvent headMarker = controller.waitEvent(HeadMarkerEvent.class, marker -> marker.getTarget().isThePlayer() && List.of(0x1EDL, 0x1EE).contains(marker.getMarkerId()));

				int numberOfRotations = numberBuff.buffIdMatches(0xE89) ? 3 : 5;
				if (headMarker.getMarkerId() == 0x1EE) {
					numberOfRotations = -numberOfRotations;
				}

				List<ArenaSector> safeLookup = List.of(ArenaSector.NORTH, ArenaSector.SOUTH, ArenaSector.EAST, ArenaSector.WEST);
				ArenaSector personalSafe = safeLookup.get((int) (event.getBuff().getId() % 0xE8E)).plusQuads(numberOfRotations);
				String safeSide = switch (personalSafe) {
					case NORTH -> "Front";
					case EAST -> "Right";
					case SOUTH -> "Behind";
					case WEST -> "Left";
					default -> "Error";
				};

				AbilityCastStart targetedLightCast = controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x8CDF));
				controller.setParam("safeSide", safeSide);
				controller.updateCall(analysisSafeSide, targetedLightCast);
			}
	);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> planarTactics = SqtTemplates.sq(
			20_000,
			AbilityCastStart.class,
			cast -> cast.abilityIdMatches(0x8898),
			(event, controller) -> {
				List<BuffApplied> enums = controller.waitEvents(2, BuffApplied.class, buff -> buff.buffIdMatches(0xE8B));
				List<XivCombatant> enumPlayers = new ArrayList<>();
				List<Long> enumFireStacks = new ArrayList<>();
				enums.forEach(buff -> {
					enumPlayers.add(buff.getTarget());
					enumFireStacks.add(buffs.findStatusOnTarget(buff.getTarget(), 0xE8C).getStacks());
				});

				controller.setParam("p1", enumPlayers.get(0));
				controller.setParam("p1Stacks", enumFireStacks.get(0));
				controller.setParam("p2", enumPlayers.get(1));
				controller.setParam("p2Stacks", enumFireStacks.get(1));
				controller.updateCall(planarTacticsEnums);

				BuffApplied marchBuff = controller.findOrWaitForBuff(buffs, buff -> buff.buffIdMatches(0xE83) && buff.getTarget().isThePlayer());
				BuffApplied personalNumberBuff = controller.findOrWaitForBuff(buffs, buff -> buff.buffIdMatches(0xE89, 0xECE) && buff.getTarget().isThePlayer());
				HeadMarkerEvent headMarker = controller.waitEvent(HeadMarkerEvent.class, marker -> marker.getTarget().isThePlayer() && List.of(0x1EDL, 0x1EE).contains(marker.getMarkerId()));

				int numberOfRotations = personalNumberBuff.buffIdMatches(0xE89) ? 3 : 5;
				if (headMarker.getMarkerId() == 0x1EE) {
					numberOfRotations = -numberOfRotations;
				}

				String direction = switch(ArenaSector.NORTH.plusQuads(numberOfRotations)) {
					case EAST -> "Right";
					case WEST -> "Left";
					default -> "Error";
				};

				controller.setParam("direction", direction);
				controller.updateCall(planarTacticsForcedMarch, marchBuff);
			}
	);

	// Fairy
	@NpcCastCallout(0x8949)
	private final ModifiableCallout<AbilityCastStart> aeroIV = ModifiableCallout.durationBasedCall("Aero IV", "Raidwide");
	private final ModifiableCallout<BaseEvent> trapShootingSpreadEarly = new ModifiableCallout<>("Trapshooting Spread Early", "Spread");
	private final ModifiableCallout<BaseEvent> trapShootingStackEarly = new ModifiableCallout<>("Trapshooting Stack Early", "Stack");
	private final ModifiableCallout<BaseEvent> trapShootingSafeSpotEarly = new ModifiableCallout<>("Trapshooting Safe Spot Early", "{safeSpot}");
	private final ModifiableCallout<AbilityCastStart> trapShootingSpread = ModifiableCallout.durationBasedCall("Trapshooting Spread", "Spread");
	private final ModifiableCallout<AbilityCastStart> trapShootingStack = ModifiableCallout.durationBasedCall("Trapshooting Stack", "Stack");
	private final ModifiableCallout<AbilityCastStart> trapShootingSafeSpot = ModifiableCallout.durationBasedCall("Trapshooting Safe Spot", "{safeSpot}");
	private final ModifiableCallout<BaseEvent> surpriseBalloonKnockback = new ModifiableCallout<>("Surprise Balloon Knockback", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> trickReload = SqtTemplates.sq(
			60_000,
			AbilityUsedEvent.class,
			cast -> cast.abilityIdMatches(0x894A),
			(event, controller) -> {
				AbilityUsedEvent firstBullet = controller.waitEvent(AbilityUsedEvent.class, cast -> cast.abilityIdMatches(0x8925, 0x8926));
				boolean isStackFirst = firstBullet.abilityIdMatches(0x8926);
				controller.updateCall(isStackFirst ? trapShootingStackEarly : trapShootingSpreadEarly);

				List<AbilityUsedEvent> bullets = controller.waitEventsUntil(6, AbilityUsedEvent.class, cast -> cast.abilityIdMatches(0x8925), AbilityUsedEvent.class, cast -> cast.abilityIdMatches(0x8926));
				int safeSpot = bullets.size() + 1;
				controller.setParam("safeSpot", safeSpot);
				controller.updateCall(trapShootingSafeSpotEarly);

				AbilityCastStart trapShootingCastOne = controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x8D1A, 0x8959));
				controller.updateCall(isStackFirst ? trapShootingStack : trapShootingSpread, trapShootingCastOne);

				AbilityCastStart triggerHappyCast = controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x894B));
				controller.setParam("safeSpot", safeSpot);
				controller.updateCall(trapShootingSafeSpot, triggerHappyCast);

				AbilityCastStart trapShootingCastTwo = controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x8D1A, 0x8959));
				controller.updateCall(isStackFirst ? trapShootingSpread : trapShootingStack, trapShootingCastTwo);
			}
	);

	private final SequentialTrigger<BaseEvent> surpriseBalloon = SqtTemplates.sq(
			20_000,
			AbilityUsedEvent.class,
			cast -> cast.abilityIdMatches(0x894D),
			(event, controller) -> {
				controller.waitEvent(AbilityCastStart.class, cast -> cast.abilityIdMatches(0x894B));
				controller.updateCall(surpriseBalloonKnockback);
			}
	);

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AAI_Criterion) || state.dutyIs(KnownDuty.AAI_Savage);
	}
}
