# CyberServer

**Current development version: 0.2.7** · Minecraft **1.20.1** · Forge **47.4.10** · Java **17**

CyberServer is the **server-side world director** for the CyberSpectra Season 2 modpack. It keeps this specific world's story state, progression policy, first-arrival settings and authored quest bindings separate from the reusable [CyberRaces](https://github.com/Cyberspectraa/CyberRaces), [CyberClasses](https://github.com/Cyberspectraa/CyberClasses), [CyberNpc](https://github.com/Cyberspectraa/CyberNpc) and [CyberQuest](https://github.com/Cyberspectraa/CyberQuest) mods.

CyberServer is designed to run on the **server**. CyberQuest is a required server dependency. CyberNpc and CyberRaces integrations are conditional, and their reusable mechanics do not require CyberServer.

## Discord config location and setup repair (0.2.7)

The Discord bot's private settings now live in
`config/cyberserver/discord-mail.properties`, **inside the CyberServer
folder** you may already see in the Minecraft server's config directory.

CyberServer creates that file as soon as the mod loads (including local
test-world setups). If an existing
`config/cyberserver-discord-mail.properties` contains your settings,
it is moved into the new folder without overwriting an existing new file.
The bot still only connects on a **dedicated Minecraft server**.

Operators can explicitly create/check the file with
`/cyberserver discordmail setup` (or `/cyber server discordmail setup`
when using the unified CyberNpc command hub). The old
`/cyberserver discordmail config` command also works and shows the
actual path plus token/ID configuration statuses, without showing secrets.
After editing the file, restart the server to enable the bot.

## Discord Post Office — hosted in CyberServer (0.2.7)

CyberServer can host the complete **Discord bot on the dedicated Minecraft
server**. A Discord admin runs `/mailpanel` to post a permanent
**Fractured Island Post Office** embed. Discord members press
**Write a Letter** and choose a Minecraft recipient and message.
The sender uses their actual Discord username; no account linking.

The bot places the submission into CyberNpc 0.45.6+’s existing Drop Box
queue. CyberNpc couriers then deliver to the intended player or letterbox.
Discord credentials stay in the dedicated server's private
`config/cyberserver/discord-mail.properties` file or environment, never
inside the JAR. The integration is **disabled by default** and requires no
Node.js process or separate web host.

Operators can check Discord bot connectivity using
`/cyberserver discordmail status`. (The separate CyberNpc command
`/cybernpc discordmail status` shows the assigned physical Drop Box.)

See [full setup and safety instructions](docs/DISCORD_POST_OFFICE.md).
The bot needs outbound Discord connectivity and can only receive letters
while the Minecraft server is running.

### Embedded intro FX (no external resource pack)

The golden summoning effect `cyberserver:summoning` from the old
`CyberServer-Resources.zip` is now bundled in the client-installed
**CyberNpc 0.45.7** JAR. CyberServer remains server-only and continues to
trigger it through Photon. The separate ZIP is no longer needed, and normal
CyberServer builds no longer generate the resource pack automatically.

### Local Windows build

Use Java 17 and the included Gradle 8.8 launcher. Install the resulting CyberServer.jar only on the dedicated server; CyberQuest must also be present. For Discord mail, use matching CyberNpc client/server JARs (current 0.45.9).

```powershell
cd "$HOME\CyberMods\CyberServer"
git switch development-no-actions
git pull --ff-only
.\build-local.bat
```

First checkout: `git clone --branch development-no-actions https://github.com/Cyberspectraa/CyberServer.git` from the CyberMods folder. No GitHub Actions. Replace older JARs only after the build succeeds.

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

1. CyberRaces character creation completes; configured intro worlds defer class registration until the Guild Receptionist.
2. CyberNpc displays the Fractured Island title and a two-pulse summoning cinematic lasting 13 seconds, including the player reveal.
3. Camera restoration leads into the Pope's greeting and newcomer quests. CyberQuest tracks guild class registration; CyberRaces gates Cyber XP until a class is chosen.

CyberNpc 0.45.8+ owns the extended cinematic. CyberServer retains the original Photon bridge and its shorter fallback where the full intro is unavailable. The original summoning FX is packaged in CyberNpc 0.45.7+ and no separate resource pack is needed.

With CyberNpc 0.45.9+, all commands can be reached through `/cyber server` and `/cyber arrival`. The legacy prefixes below remain supported with unchanged permissions.

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
- Server mod JAR: produced by the local Gradle launcher; the mod version is 0.2.7 in both Gradle and mod metadata.
- The summoning Photon FX is now included in the client-installed **CyberNpc 0.45.7** JAR. Normal CyberServer builds require **no separate resource-pack ZIP**; its legacy Gradle exporter remains available only for effect authoring. GitHub Actions are not used.
- Required CyberQuest dependency for world quest bindings/state gates; optional integrations with CyberNpc (full intro and mail importer), CyberRaces (Cyber Level and race policy), CyberClasses (class advancement policy) and Photon FX when present.
- The server state stores gates and configuration; a complete Season 2 main questline, map/region content and boss encounters still require authored gameplay content.

**Command migration:** older examples such as `/cyberserver npcprogression`, `/cyberserver story`, `/cyberserver economy`, `/arrival beam` and `/arrival resetall` have moved under the `admin` subcommands shown above.

## Current development set

CyberServer 0.2.7, CyberNpc 0.45.9, CyberQuest 0.3.0-alpha.23, CyberRaces 0.3.2-alpha.76 and CyberClasses 0.2.1-alpha.13. Use `/cyber server discordmail setup` for config creation and `/cyber server discordmail status` for connection state with the unified hub installed. The physical mail binding remains `/cyber npc discordmail bind`. Keep bot credentials private. Build and test the current source before production use; a successful registration check alone is not a gameplay test.

[Previous README](https://github.com/Cyberspectraa/CyberServer/blob/7b2e5ed730a5019df0a1fee8ccaf2ce4b5c61e6e/README.md).
