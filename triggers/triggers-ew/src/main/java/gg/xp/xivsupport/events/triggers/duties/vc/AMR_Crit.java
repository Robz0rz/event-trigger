package gg.xp.xivsupport.events.triggers.duties.vc;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HasDuration;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.ForceCombatEnd;
import gg.xp.xivsupport.events.state.InCombatChangeEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// TODO: can duties support multiple zones?
@CalloutRepo(name = "Another Mount Rokkon", duty = KnownDuty.AMR)
public class AMR_Crit extends AutoChildEventHandler implements FilteredEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(AMR_Crit.class);
	private final XivState state;
	private StatusEffectRepository buffs;

	public AMR_Crit(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> combatEndHack = SqtTemplates.sq(10_000,
			InCombatChangeEvent.class, event -> !event.isInCombat(),
			(event, controller) -> {
				controller.waitMs(3000);
				controller.accept(new ForceCombatEnd());
			}
	);

	// TODO: Trash packs

	// Shishio
	@NpcCastCallout({ 0x841A, 0x8441 })
	private final ModifiableCallout<AbilityCastStart> enkyo = ModifiableCallout.durationBasedCall("Enkyo", "Raidwide");
	@NpcCastCallout({0x841B, 0x8442})
	private final ModifiableCallout<AbilityCastStart> splittingCry = ModifiableCallout.durationBasedCall("Splitting Cry", "Tank Buster");
	private final ModifiableCallout<BuffApplied> hauntingCryFirst = ModifiableCallout.durationBasedCall("Haunting Cry First", "{safeSpots[0]} {spreads[0] ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<BuffApplied> hauntingCrySecond = ModifiableCallout.durationBasedCall("Haunting Cry Second", "{safeSpots[1]} {spreads[1] ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<AbilityCastStart> hauntingCrySafeSpots = new ModifiableCallout<>("Haunting Cry Safe Spots", "Then {safeSpots[1]} {spreads[1] ? 'Spread' : 'Buddies'}", "{safeSpots[0]} {spreads[0] ? 'Spread' : 'Buddies'}, {safeSpots[1]} {spreads[1] ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<AbilityCastStart> vengefulSoulsTower = ModifiableCallout.durationBasedCall("Vengeful Souls Tower", "Soak Tower");
	private final ModifiableCallout<AbilityCastStart> vengefulSoulsDefamation = ModifiableCallout.durationBasedCall("Vengeful Souls Defamation", "Drop Defamation");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hauntingCryCaller = SqtTemplates.multiInvocation(
			40_000,
			AbilityCastStart.class,
			ability -> ability.abilityIdMatches(0x840A, 0x8431),
			(event, controller) -> {
				BuffApplied spreadBuff = buffs.findBuffById(0xDEB);
				BuffApplied buddyBuff = buffs.findBuffById(0xDEC);

				if (spreadBuff == null || buddyBuff == null) {
					logger.error("[Haunting Cry] Missing spread or buddy buff");
					return;
				}

				ArenaPos center = new ArenaPos(0, -100, 0, 0);
				boolean spreadFirst = spreadBuff.getInitialDuration().toSeconds() < buddyBuff.getInitialDuration().getSeconds();
				List<Boolean> spreads = List.of(spreadFirst, !spreadFirst);
				List<ArenaSector> safeSpots = new ArrayList<>();

				// Find which ArenaSector the two inward looking thralls are facing
				List<ArenaSector> firstSectors = controller.waitEvents(4, AbilityCastStart.class, ability -> ability.abilityIdMatches(0x840B, 0x840C, 0x8432, 0x8433)).stream()
						.map(AbilityCastStart::getSource)
						.filter(combatant -> center.forCombatant(combatant).opposite() == ArenaPos.combatantFacing(combatant))
						.map(ArenaPos::combatantFacing)
						.toList();

				if (firstSectors.size() != 2) {
					logger.error(String.format("[Haunting Cry] Expected to find 2 thralls facing inwards in the first set but found %d", firstSectors.size()));
					return;
				}

				boolean isCardinals = ArenaSector.cardinals.contains(firstSectors.get(0));
				// The safe spot will be between the two ArenaSectors found out above
				safeSpots.add(isCardinals ?
						ArenaSector.tryCombineTwoCardinals(firstSectors) :
						ArenaSector.tryCombineTwoQuadrants(firstSectors)
				);

				controller.setParam("safeSpots", safeSpots);
				controller.setParam("spreads", spreads);
				controller.updateCall(hauntingCryFirst, spreadFirst ? spreadBuff : buddyBuff);

				List<ArenaSector> secondSectors = controller.waitEvents(4, AbilityCastStart.class, ability -> ability.abilityIdMatches(0x840B, 0x840C, 0x8432, 0x8433)).stream()
						.map(AbilityCastStart::getSource)
						.filter(combatant -> center.forCombatant(combatant).opposite() == ArenaPos.combatantFacing(combatant))
						.map(ArenaPos::combatantFacing)
						.toList();

				if (secondSectors.size() != 2) {
					logger.error(String.format("[Haunting Cry] Expected to find 2 thralls in the second set but found %d", secondSectors.size()));
					return;
				}

				safeSpots.add(isCardinals ?
						ArenaSector.tryCombineTwoQuadrants(secondSectors) :
						ArenaSector.tryCombineTwoCardinals(secondSectors)
				);
				controller.setParam("safeSpots", safeSpots);
				controller.setParam("spreads", spreads);
				controller.updateCall(hauntingCrySafeSpots);

				controller.waitEvent(BuffRemoved.class, buff -> buff.buffIdMatches(0xDEB, 0xDEC));
				controller.setParam("safeSpots", safeSpots);
				controller.setParam("spreads", spreads);
				controller.updateCall(hauntingCrySecond, spreadFirst ? buddyBuff : spreadBuff);

			},
			(event, controller) -> {
				AbilityCastStart cast = controller.waitEvent(AbilityCastStart.class, c -> c.abilityIdMatches(0x840E, 0x8435));
				List<HeadMarkerEvent> headMarkers = controller.waitEvents(2, HeadMarkerEvent.class, mark -> true);
				boolean hasHeadMarker = headMarkers.stream().anyMatch(marker -> marker.getTarget().isThePlayer());
				controller.updateCall(hasHeadMarker ? vengefulSoulsDefamation : vengefulSoulsTower, cast);
			}
	);

	private final ModifiableCallout<BuffApplied> eyeOfTheThunderVortexOut = ModifiableCallout.<BuffApplied>durationBasedCall("Eye of the Thunder Vortex Out", "Out {spread ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<BuffApplied> eyeOfTheThunderVortexIn = ModifiableCallout.<BuffApplied>durationBasedCall("Eye of the Thunder Vortex In", "In {spread ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<BuffApplied> vortexOfTheThunderEyeIn = ModifiableCallout.<BuffApplied>durationBasedCall("Vortex of the Thunder Eye In", "In {spread ? 'Spread' : 'Buddies'}");
	private final ModifiableCallout<BuffApplied> vortexOfTheThunderEyeOut = ModifiableCallout.<BuffApplied>durationBasedCall("Vortex of the Thunder Eye Out", "Out {spread ? 'Spread' : 'Buddies'}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> eyeVortexCaller = SqtTemplates.sq(
			20_000,
			AbilityCastStart.class, cast -> cast.abilityIdMatches(0x8413, 0x8415, 0x843A, 0x843C),
			(event, controller) -> {
				BuffApplied spreadBuff = buffs.findBuffById(0xDEB);
				BuffApplied buddyBuff = buffs.findBuffById(0xDEC);

				if (spreadBuff == null || buddyBuff == null) {
					logger.error(String.format("[%s] Missing spread or buddy buff", event.getAbility().getName()));
					return;
				}

				boolean eyeFirst = List.of(0x8413L, 0x843AL).contains(event.getAbility().getId());
				boolean spreadFirst = spreadBuff.getInitialDuration().toSeconds() < buddyBuff.getInitialDuration().getSeconds();

				controller.setParam("spread", spreadFirst);
				controller.updateCall(eyeFirst ? eyeOfTheThunderVortexOut : vortexOfTheThunderEyeIn, spreadFirst ? spreadBuff : buddyBuff);
				controller.waitEvent(AbilityUsedEvent.class, ability -> ability.abilityIdMatches(0x8413, 0x8415, 0x843A, 0x843C));
				controller.setParam("spread", !spreadFirst);
				controller.updateCall(eyeFirst ? eyeOfTheThunderVortexIn : vortexOfTheThunderEyeOut, spreadFirst ? buddyBuff : spreadBuff);
			}
	);

	// Gorai the Uncaged
	@NpcCastCallout({ 0x8534, 0x8555 })
	private final ModifiableCallout<AbilityCastStart> unenlightenment = ModifiableCallout.durationBasedCall("Unenlightenment", "Raidwide");
	@NpcCastCallout({ 0x852F, 8552 })
	private final ModifiableCallout<AbilityCastStart> impurePurgation = ModifiableCallout.durationBasedCall("Impure Purgation", "Double Proteans");
	@NpcCastCallout({ 0x8532, 8554 })
	private final ModifiableCallout<AbilityCastStart> torchingTorment = ModifiableCallout.durationBasedCall("Torching Torment", "Tank Buster");
	private final ModifiableCallout<HasDuration> sealOfScurryingSparksStacks = new ModifiableCallout<>("Seal Of Scurrying Sparks Stacks", "{stacks[0]}, {stacks[1]}");
	private final ModifiableCallout<BuffApplied> cloudToGroundStack = ModifiableCallout.durationBasedCall("Cloud To Ground Stack", "Stack");
	private final ModifiableCallout<BuffApplied> cloudToGroundSpread = ModifiableCallout.durationBasedCall("Cloud To Ground Spread", "Spread");

	// TODO: add callouts
	@AutoFeed
	private final SequentialTrigger<BaseEvent> sealOfScurryingSparksCaller = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, cast -> cast.abilityIdMatches(0x8503),
			(event, controller) -> {
				List<BuffApplied> stacks = controller.waitEvents(2, BuffApplied.class, buffApplied -> buffApplied.buffIdMatches(0xE17));
				BuffApplied spread = buffs.findBuffById(0xE18);

				controller.setParam("stacks", stacks.stream().map(BuffApplied::getTarget).toList());
				controller.updateCall(sealOfScurryingSparksStacks);
				if (spread == null) {
					return;
				}

				// Exaflares
				BuffApplied stack = stacks.stream().findAny().orElseThrow();
				boolean spreadFirst = stack.getInitialDuration().toSeconds() < spread.getInitialDuration().toSeconds();
				List<BuffApplied> buffOrder = spreadFirst ? List.of(spread, stack) : List.of(stack, spread);
				List<ModifiableCallout<BuffApplied>> calloutOrder = spreadFirst ? List.of(cloudToGroundSpread, cloudToGroundStack) : List.of(cloudToGroundStack, cloudToGroundSpread);


				controller.updateCall(calloutOrder.get(0), buffOrder.get(0));
				controller.waitEvent(BuffRemoved.class, buffRemoved -> buffRemoved.buffIdMatches(0xE17, 0xE18));
				controller.updateCall(calloutOrder.get(1), buffOrder.get(1));
			}
	);

	@PlayerHeadmarker(0x150)
	private final ModifiableCallout<?> fightingSpiritsOne = new ModifiableCallout<>("Fighting Spirits #1", "One");
	@PlayerHeadmarker(0x151)
	private final ModifiableCallout<?> fightingSpiritsTwo = new ModifiableCallout<>("Fighting Spirits #2", "Two");
	@PlayerHeadmarker(0x152)
	private final ModifiableCallout<?> fightingSpiritsThree = new ModifiableCallout<>("Fighting Spirits #3", "Three");
	@PlayerHeadmarker(0x153)
	private final ModifiableCallout<?> fightingSpiritsFour = new ModifiableCallout<>("Fighting Spirits #4", "Four");
	@NpcCastCallout({ 0x852B, 0x854F })
	private final ModifiableCallout<AbilityCastStart> fightingSpiritsKnockback = ModifiableCallout.durationBasedCall("Fighting Spirits Knockback", "Knockback");

	// Moko the Restless
	@NpcCastCallout({ 0x85E0, 0x860C })
	private final ModifiableCallout<AbilityCastStart> kenkiRelease = ModifiableCallout.durationBasedCall("Kenki Release", "Raidwide");

	private final ModifiableCallout<?> kasumiGiriFirst = new ModifiableCallout<>("Triple Kasumi-giri Initial", "{isFire[0] ? 'Out' : 'In'} {safeSpots[0]}")
			.extendedDescription("""
                You can configure this callout in multiple ways with the provided parameters.
                safeSpots is a list which contains the true north safe spots,
                relativeSafeSpots is a list which contains the safe spots using the boss his facing direction as relative north
                and isFire is a list of booleans that tell you if it is the fire variant or not, this can be used to tell if it is an in or out.""");
	private final ModifiableCallout<?> kasumiGiriSecond = new ModifiableCallout<>("Triple Kasumi-giri Second", "then {isFire[1] ? 'Out' : 'In'} {safeSpots[1]}", "{isFire[0] ? 'Out' : 'In'} {safeSpots[0]} then {isFire[1] ? 'Out' : 'In'} {safeSpots[1]}");
	private final ModifiableCallout<?> kasumiGiriSafespots = new ModifiableCallout<>("Triple Kasumi-giri Safe Spots", "then {isFire[2] ? 'Out' : 'In'} {safeSpots[2]}", "{isFire[0] ? 'Out' : 'In'} {safeSpots[0]}, {isFire[1] ? 'Out' : 'In'} {safeSpots[1]}, {isFire[2] ? 'Out' : 'In'} {safeSpots[2]}");

	private void kasumiGiriHelper(SequentialTriggerController<BaseEvent> controller, ArenaSector facing) {
		/*
			Boss will receive buff 0xB9A for every telegraph with the amount stacks indicating which attack he will perform

			Possible variations:
			South Safe Fire: 0x24C
			West Safe Fire: 0x24D
			North Safe Fire: 0x24E
			East Safe Fire: 0x24F

			South Safe Water: 0x250
			West Safe Water: 0x251
			North Safe Water: 0x252
			East Safe Water: 0x253
		 */
		List<ArenaSector> safeSpots = new ArrayList<>(3);
		List<ArenaSector> relativeSafeSpots = new ArrayList<>(3);
		List<Boolean> isFire = new ArrayList<>(3);

		ArenaSector relativeNorth = facing;

		for (ModifiableCallout<?> callout : List.of(kasumiGiriFirst, kasumiGiriSecond, kasumiGiriSafespots)) {
			BuffApplied current = controller.findOrWaitForBuff(buffs, (buffApplied) -> buffApplied.buffIdMatches(0xB9A));
			long stacks = current.getRawStacks();

			// Look at it like it's a 270 degree attack in the cardinal direction, so the opposite of that cardinal will be safe
			ArenaSector relativeSafe = ArenaSector.cardinals.get((int) ((stacks - 0x24C) % ArenaSector.cardinals.size())).opposite();
			ArenaSector trueSafe = relativeSafe.plusQuads(ArenaSector.cardinals.indexOf(relativeNorth));

			safeSpots.add(trueSafe);
			relativeSafeSpots.add(relativeSafe);
			isFire.add(stacks < 0x250);

			controller.setParam("safeSpots", safeSpots);
			controller.setParam("relativeSafe", relativeSafeSpots);
			controller.setParam("isFire", isFire);
			controller.updateCall(callout);

			relativeNorth = trueSafe.opposite();
			controller.waitBuffRemoved(buffs, current);
		}
	}

	private static boolean isKasumiGiri(AbilityCastStart cast) {
		return cast.abilityIdMatches(
				0x85B0, 0x85B1, 0x85B2, 0x85B3,
				0x85BA, 0x85BB, 0x85BC, 0x85BD,
				0x85E4, 0x85E5, 0x85E6, 0x85E7,
				0x85EE, 0x85EF, 0x85F0, 0x85F1
		);
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> kasumiGiriSq = SqtTemplates.sq(
			45_000,
			AbilityCastStart.class,
			AMR_Crit::isKasumiGiri,
			(event, controller) -> {
				kasumiGiriHelper(controller, ArenaSector.NORTH);

				// This is a bit of a hack for the 2nd call, it will fire immediately after the first
				// as it is the only instance where the boss will not reset to face north
				// just let it time out otherwise
				AbilityCastStart cast = controller.waitEvent(AbilityCastStart.class, AMR_Crit::isKasumiGiri);
				ArenaSector facing = ArenaPos.combatantFacing(cast.getTarget());
				kasumiGiriHelper(controller, facing);
			}
	);

	private final ModifiableCallout<AbilityCastStart> farEdge = new ModifiableCallout<>("Far Edge No Tether", "Go Far");
	private final ModifiableCallout<AbilityCastStart> farEdgeTether = new ModifiableCallout<>("Far Edge Tether", "Stay Close, then Behind And {safeSpot}");
	private final ModifiableCallout<AbilityCastStart> nearEdge = new ModifiableCallout<>("Near Edge No Tether", "Stay Close");
	private final ModifiableCallout<AbilityCastStart> nearEdgeTether = new ModifiableCallout<>("Near Edge No Tether", "Go Far, then Behind And {safeSpot}");

	private void updateNearFarEdgeCallout(SequentialTriggerController<BaseEvent> controller) {
		List<TetherEvent> tethers = controller.waitEvents(2, TetherEvent.class, (tether) -> true);
		AbilityCastStart cast = controller.waitEvent(AbilityCastStart.class, c -> c.abilityIdMatches(0x85D8, 0x85D9));
		Optional<TetherEvent> tether = tethers.stream().filter(t -> t.eitherTargetMatches(XivCombatant::isThePlayer)).findFirst();
		boolean isFarEdge = cast.abilityIdMatches(0x85D8);

		if (tether.isEmpty()) {
			controller.updateCall(isFarEdge ? farEdge : nearEdge);
			return;
		}


		// Get the second 0xB9A buff on the clone that is tethered to you, the first one will always be behind so we ignore that one
		BuffApplied buff = controller.findOrWaitForBuff(buffs, buffApplied -> buffApplied.getTarget().equals(tether.get().getSource()) && buffApplied.buffIdMatches(0xB9A) && buffApplied.getRawStacks() != 0x248);
		String safeSpot = switch ((int) buff.getRawStacks()) {
			case 0x248 -> "Behind";
			case 0x249 -> "Left";
			case 0x24A -> "Front";
			case 0x24B -> "Right";
			default -> "Unknown";
		};

		controller.setParam("safeSpot", safeSpot);
		controller.updateCall(isFarEdge ? farEdgeTether : nearEdgeTether);
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> nearFarEdgeSq = SqtTemplates.sq(
			60_000,
			AbilityUsedEvent.class,
			cast -> cast.abilityIdMatches(0x85C7),
			(event, controller) -> {
				updateNearFarEdgeCallout(controller);
				updateNearFarEdgeCallout(controller);
			}
	);

	private final ModifiableCallout<AbilityCastStart> soldiersOfDeath = new ModifiableCallout<>("Soldiers Of Death Safe Spots", "{safeSpots[0]} and {safeSpots[1]}");
	private final ModifiableCallout<AbilityCastStart> shadowTwinTwoLeft = new ModifiableCallout<>("Shadow-Twin 2 Left Inwards", "Face Left Side Inwards");
	private final ModifiableCallout<AbilityCastStart> shadowTwinTwoRight = new ModifiableCallout<>("Shadow-Twin 2 Right Inwards", "Face Right Side Inwards");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> soldiersOfDeathCaller = SqtTemplates.sq(
			60_000,
			BuffApplied.class,
			buff -> buff.buffIdMatches(0x808) && buff.getRawStacks() == 0x5E,
			(event, controller) -> {
				ArenaPos center = new ArenaPos(-200,0 ,0 ,0);
				ArenaSector sector = center.forCombatant(event.getTarget());
				ArenaSector facing = ArenaPos.combatantFacing(event.getTarget());
				List<ArenaSector> safeSpots;

				// If blue add faces East Or West then he will shoot far, otherwise close
				if (List.of(ArenaSector.EAST, ArenaSector.WEST).contains(facing)) {
					safeSpots = ArenaSector.cardinals.stream().filter(s -> s == facing.opposite() || (!s.isStrictlyAdjacentTo(sector) && s != facing)).toList();
				} else {
					safeSpots = ArenaSector.cardinals.stream().filter(cardinal -> !cardinal.isStrictlyAdjacentTo(sector)).toList();
				}

				controller.setParam("safeSpots", safeSpots);
				controller.updateCall(soldiersOfDeath);

				// Shadow-Twin
				TetherEvent tether = controller.waitEvent(TetherEvent.class, t -> t.eitherTargetMatches(XivCombatant::isThePlayer));
				// First safe will always be south, so we can ignore the first set
				controller.waitEvents(4, BuffRemoved.class, buff -> buff.buffIdMatches(0xB9A));
				BuffApplied buff =  controller.waitEvent(BuffApplied.class, b -> b.buffIdMatches(0xB9A) && tether.getSource().getPos().equals(b.getTarget().getPos()));
				controller.updateCall(buff.getRawStacks() == 0x249 ? shadowTwinTwoLeft : shadowTwinTwoRight);
			}
	);

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AMR);
	}
}
