# CyberServer

**Current version: 0.2.7** · Minecraft **1.20.1** · Forge **47.4.10** · Java **17**

## Voice-caption diagnostics (0.2.7)

Use `/voicecaptions test` to force a harmless local proximity speech
bubble above your player. Check in third-person front/back view. This bypasses
Simple Voice Chat and Vosk, so it establishes whether CyberNpc packets and
rendering work.

Use `/voicecaptions debug` to inspect the Voice Chat plugin lifecycle,
proximity connection, incoming/eligible microphone packet counts, recognizer
model status, processed frames, output caption count and any recognition
error. The diagnostic output does not log voice recordings or transcripts.

For first-time tests: `/voicecaptions on`, `/voicecaptions test`, speak
for 5 seconds with Simple Voice Chat connected, pause, and run
`/voicecaptions debug`. The bundled Vosk model initializes on the first
eligible mic packet, which can take longer on the first attempt.

## Voice-to-bubble captions (0.2.6 startup-compatibility preview)

**0.2.6 startup hotfix:** CyberServer now embeds **only Vosk's own recognizer classes and platform binaries**, not a second copy of Java Native Access (JNA) already supplied by Minecraft/Forge. This avoids a known category of classloader collisions caused by fat-JAR Java/native dependencies. The English model remains bundled. This is a protective packaging change; further diagnostics may be needed if the client still exits during early mod discovery. Install with CyberNpc 0.44.51 (not 0.44.50), which uses the caption packet protocol.

## Voice-to-bubble captions (0.2.5 preview)

This release adds an optional **Simple Voice Chat → offline Vosk → CyberNpc speech bubble** integration. Vosk English small model 0.15 is **bundled in the CyberServer JAR**; players don't download any English model. CyberNpc **0.44.51** must be installed on both server and clients to receive captions.

Captions are **opt-in** (`/voicecaptions on`, `/voicecaptions off`, `/voicecaptions status`). Server admins have a master config toggle `voice_captions.enabled` in the world server config. Voice groups are excluded; only normal proximity chat is eligible. Whispering is limited to 6 blocks; other text follows voice-chat distance with a 32-block bubble limit. Two bounded background worker queues are used; audio and transcripts are never saved. The only persistent data is the player's opt-in setting.

The first server run extracts the bundled English model once to `config/cyberserver/voice-model`; it does not contact an external service. The release contains the Apache-2.0 Vosk recognition engine/model and their runtime libraries (JNA). This is a **preview feature** pending live stress/accuracy/privacy testing in your modpack.

CyberServer is the **server-side world director** for the CyberSpectra Season 2 modpack. It keeps this specific world's story state, progression policy, first-arrival settings and authored quest bindings separate from the reusable [CyberRaces](https://github.com/Cyberspectraa/CyberRaces), [CyberClasses](https://github.com/Cyberspectraa/CyberClasses), [CyberNpc](https://github.com/Cyberspectraa/CyberNpc) and [CyberQuest](https://github.com/Cyberspectraa/CyberQuest) mods.

CyberServer is designed to run on the **server**. Integrations with other mods are conditional; their reusable mechanics do not require CyberServer.

## Persistent world state

The world saves the following server-owned values:

- Current **story act** and arbitrary named Boolean **story flags**.
- **Unlocked regions** and **defeated boss IDs**.
- Stable NPC-ID-to-quest bindings for CyberQuest.
- **Natural Wild NPC progression policy:** maximum generated levels, world age/local difficulty modifiers, veteran chance, and whether advanced classes are available to naturally spawned NPCs. The older evolved-race toggle is now superseded by CyberRaces' base-race-only NPC policy.
- **Quest XP multiplier** for compatible CyberQuest rewards.
- Configured first-arrival spawn/yaw and first-arrival player tracking.

The world-state values form a framework for gates and scripts; recording a region as unlocked or a boss as defeated does **not** automatically construct regions, boss fights or narrative missions.

## First arrival and summoning

CyberServer retains the exact spawn location and tracks one-time arrivals. When CyberNpc is installed and **both CyberNpc introduction markers are configured**, CyberNpc owns the full player cinematic, queue, delayed summoning effects, reveal, camera restoration and Pope dialogue.

Current integrated experience:

1. CyberRaces and CyberClasses complete character setup when those mods are installed.
2. CyberNpc shows the temporary Blockbench **TESTING VERSION** title, then the summoning cinematic at the saved arrival block.
3. The cinematic camera returns to the player's view and the Pope greeting begins.

The accepted integrated intro implementation is in **CyberNpc 0.44.39**. CyberServer provides a summoning-effect bridge; without an eligible CyberNpc intro it retains a shorter arrival fallback (Photon support when available, otherwise vanilla-style particles/sounds).

### Arrival commands

All of these commands are for admins (permission level 2):

```mcfunction
/arrival setspawn
/arrival test
/arrival test <player>
/arrival reset <player>
/arrival admin beam
/arrival admin beam <player>
/arrival admin resetall
/arrival admin mark <player>
/arrival admin info <player>
```

`/arrival setspawn` configures CyberServer's spawn. For the complete **CyberNpc** intro, also set:

```mcfunction
/cybernpc intro setarrival
/cybernpc intro setpopewait
```

`/arrival test` uses the CyberNpc full intro when that mod is installed and properly configured; it is not merely a particle preview. The old duplicate `/cyberserver arrival` prefix and `/arrival status` no longer exist; use `/cyberserver status` for general status.

## Natural Wild NPC progression

CyberNpc identifies naturally generated Wild NPCs. CyberServer then makes a one-time decision about their starting Cyber Level and class advancement eligibility. Since **CyberRaces v0.3.2-alpha.44**, Wild NPCs receive **base races only**, regardless of Cyber Level or world-age difficulty.

The starting level calculation takes account of **world age**, **local difficulty**, some random variation and a configurable chance to become a higher-level veteran. Defaults and limits live in `CyberServerSavedData` and `CyberServerNpcPolicy`. Policies affect **new naturally generated Wild NPCs** rather than rewriting every existing resident.

Examples:

```mcfunction
/cyberserver admin npcprogression
/cyberserver admin npcprogression levelcap 35
/cyberserver admin npcprogression advancedclasses true
/cyberserver admin npcprogression evolvedraces false
```

Wild NPC classes and gear come from CyberClasses/CyberNpc; **base-race** attributes, appearances and racial AI come from CyberRaces. The `evolvedraces` command remains for compatibility but **cannot grant NPC evolutions** when CyberRaces v0.3.2-alpha.44 or later is installed. Previously evolved Wild NPCs have their stored evolution removed by CyberRaces as they load, without losing their class, Cyber Level, inventory or identity.

## World state and story controls

Current command paths are grouped under `/cyberserver admin`. Examples:

```mcfunction
/cyberserver status
/cyberserver state
/cyberserver admin story act
/cyberserver admin story act 2
/cyberserver admin flag get magic_awakened
/cyberserver admin flag set magic_awakened true
/cyberserver admin region unlock capital
/cyberserver admin region lock capital
/cyberserver admin boss mark dragon
/cyberserver admin boss clear dragon
```

These commands manage saved world state; they are not player-facing story quests by themselves.

## CyberQuest and economy policy

CyberServer can associate a **stable NPC ID** with one or more world-specific quest IDs, avoiding hard-coded per-entity quest content:

```mcfunction
/cyberserver admin npcquests list mason
/cyberserver admin npcquests bind hunter cyberquest:first_hunt
/cyberserver admin npcquests unbind hunter cyberquest:first_hunt
```

CyberQuest merges these server-owned bindings with compatible NPC-local quest bindings. NPCs use their matching `CyberQuestNpcId`.

Server-owned quest XP policy:

```mcfunction
/cyberserver admin economy questmultiplier
/cyberserver admin economy questmultiplier 1.25
```

The multiplier affects **Cyber XP and vanilla XP** quest rewards, **not** quest items or guild reputation. CyberNpc owns the separate physical coin/shop/bank gameplay.

### Built-in Mason story content

The world seeds two authored CyberQuest definitions and their stable binding to `mason`:

- `cyberserver:mason_choso_plush` — **A Gift for Mason**: hand Mason one `cybernpc:choso_plush`.
- `cyberserver:mason_secret` — **Mason's Secret**: unlocked after the plush quest; speak with Mason again.

The current physical plush item is registered by **CyberNpc** as `cybernpc:choso_plush`. The earlier README's `cybercontent:choso_plush` reference is obsolete.

Authored quests are JSON under `cyberserver/src/main/resources/data/cyberserver/cyberquests/`; world-specific story content can expand there without adding more quest logic to CyberQuest.

## Installation, builds and integration notes

- Target: **Minecraft 1.20.1 / Forge 47.4.10 / Java 17**.
- Server mod JAR: produced by the repository's Gradle/GitHub Actions build; its version is read from `gradle.properties`.
- The GitHub Actions build also packages a separately named **CyberServer resource-pack ZIP** when needed for optional visuals. A resource pack is not the server mod JAR.
- Optional integration with CyberNpc (full intro), CyberQuest (world quest bindings/state gates), CyberRaces (Cyber Level and race policy), CyberClasses (class advancement policy) and Photon FX when present.
- The server state stores gates and configuration; a complete Season 2 main questline, map/region content and boss encounters still require authored gameplay content.

**Command migration:** older examples such as `/cyberserver npcprogression`, `/cyberserver story`, `/cyberserver economy`, `/arrival beam` and `/arrival resetall` have moved under the `admin` subcommands shown above.
