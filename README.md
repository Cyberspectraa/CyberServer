# CyberServer

Server-side world director for the CyberSpectra Minecraft 1.20.1 Forge server.

CyberServer owns rules and content that belong to this specific world. Reusable mechanics remain in CyberRaces, CyberClasses, CyberNpc and CyberQuest.

## Current responsibilities

- Exact first-arrival church spawn and one-time summoning effect.
- Persistent `WorldState` stored with the world.
- Story act and arbitrary story flags.
- Unlocked regions and defeated-boss state.
- Natural Wild NPC progression policy.
- Natural NPC class-advancement and race-evolution gates.
- Quest XP reward multiplier.
- Server-owned quest bindings for stable CyberQuest NPC IDs.

## Architecture

CyberServer may integrate with the reusable gameplay mods, but those mods do not require CyberServer.

```text
CyberRaces -> CyberClasses -> CyberNpc

CyberQuest   reusable quest engine
     \
      CyberServer   world-specific director/content
```

## World-state commands

```text
/cyberserver state
/cyberserver story act
/cyberserver story act 2
/cyberserver flag get magic_awakened
/cyberserver flag set magic_awakened true
/cyberserver region unlock capital
/cyberserver region lock capital
/cyberserver boss mark dragon
/cyberserver boss clear dragon
```

## Natural NPC policy

CyberNpc only marks that a Wild NPC spawned naturally. CyberServer decides the starting Cyber Level and whether natural advancements/evolutions are allowed.

Defaults preserve the previous behaviour:

```text
/cyberserver npcprogression
/cyberserver npcprogression levelcap 35
/cyberserver npcprogression advancedclasses true
/cyberserver npcprogression evolvedraces true
```

Changes apply to newly generated natural Wild NPCs. Existing NPC progression is not rewritten.

## Quest/economy policy

```text
/cyberserver economy questmultiplier
/cyberserver economy questmultiplier 1.25
```

The multiplier affects Cyber XP and vanilla XP quest rewards. Item rewards are not multiplied.

CyberServer can own Season 2 quest bindings for a stable NPC ID:

```text
/cyberserver npcquests bind hunter cyberserver:first_hunt
/cyberserver npcquests list hunter
/cyberserver npcquests unbind hunter cyberserver:first_hunt
```

The CyberNpc itself only needs the matching `CyberQuestNpcId`, allowing quest content/bindings to remain world-specific.

## Arrival commands

The existing arrival commands remain compatible:

```text
/arrival setspawn
/arrival test
/arrival beam
/arrival reset <player>
/arrival status
```
