# CyberServer

**Current development version: 0.2.8** · Minecraft 1.20.1 · Forge · Java 17

Dedicated-server companion for Fractured Island. CyberQuest is required. CyberNpc, CyberRaces, CyberClasses and Photon integrations remain conditional. Existing world policy, arrival, story state and postal mail handling are retained; the old admin command surface is retired.

## Operator commands

All approved commands share the lowercase `/cadmin` root (permission level 2).

```mcfunction
/cadmin arrival setspawn
/cadmin arrival test
/cadmin arrival test <player>
/cadmin server discordmail setup
/cadmin server discordmail status
```

There are no legacy `/arrival` or `/cyberserver` aliases. Other world settings remain saved as before, but the removed commands no longer edit them.

## Discord Post Office

Run `/cadmin server discordmail setup` or start the mod to create the private file:

```text
config/cyberserver/discord-mail.properties
```

Set enabled=true, bot_token, guild_id and application_id in the dedicated server's file, then restart. Do not share the token. Discord's /mailpanel creates the letter-writing panel. CyberNpc 0.45.13 automatically uses a registered, loaded Drop Box with free space; manual binding is no longer required. Players place their own Letter Boxes. The courier delivers using the existing physical mail queue. See [Discord setup](docs/DISCORD_POST_OFFICE.md); older command examples in that guide are superseded by this README.

## Local build

```powershell
cd "$HOME\CyberMods\CyberServer"
git switch development-no-actions
git pull --ff-only
.\build-local.bat
```

Install CyberServer.jar on the dedicated server only. Install the matching CyberQuest JAR and the other updated companion mods as required. Build locally, not with GitHub Actions. Back up worlds before testing.

[Previous README and world-policy notes](https://github.com/Cyberspectraa/CyberServer/blob/f33fd7bb86d910228e21139a5a2d2885e4b43683/README.md). Source changes require local compile and in-world testing.
