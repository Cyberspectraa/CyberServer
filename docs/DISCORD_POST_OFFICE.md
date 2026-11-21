# Fractured Island Discord Post Office (CyberServer 0.2.5)

This version hosts a Discord bot **inside CyberServer itself**. There is no
Node.js program, outside hosting service, open Minecraft port or bot plugin
to install. A dedicated Forge 1.20.1 / Java 17 server with both **CyberServer
0.2.5** and **CyberNpc 0.45.6** is required.

CyberServer runs the Discord Gateway connection, embed, buttons and form.
CyberNpc already implements the actual postal database, physical Drop Boxes,
courier pickup, player delivery and letterboxes. It consumes the local
server-only mail spool. CyberServer does **not** duplicate the NPC mail data.

## Installation

1. Build CyberServer from the `development-no-actions` branch using
   `build-local.bat` on Windows with JDK 17. This creates `CyberServer.jar`
   only after a successful Gradle build. On a dedicated server, replace the
   old **CyberServer** mod jar with it. Do not run GitHub Actions or merge
   into `main`.
2. The Minecraft **server and clients** also need matching CyberNpc 0.45.6.
   CyberServer is a **server-only** mod and does not go in the client modpack.
3. Start the dedicated server once. CyberServer creates:

       <server>/config/cyberserver-discord-mail.properties

   It defaults to disabled. Stop the server before editing.
4. Create a bot application in the Discord Developer Portal. Install it
   into your Discord server using the bot and applications.commands scopes.
   Give the bot View Channel, Send Messages and Embed Links permission in the
   desired mail channel. It needs only the Guilds gateway intent; **Message
   Content intent is not needed**. Leave the application's **Interactions
   Endpoint URL empty** so events arrive through the Gateway.
5. In the generated properties file, set:

       enabled=true
       bot_token=YOUR_DISCORD_BOT_TOKEN
       guild_id=YOUR_DISCORD_SERVER_ID
       application_id=YOUR_DISCORD_APPLICATION_ID

   The token is sensitive: never upload, commit, post in chat or include it
   in a client modpack. The config is only on the **dedicated server**. For
   hosting panels that support environment variables, use these instead:

       CYBERSERVER_DISCORD_ENABLED=true
       CYBERSERVER_DISCORD_TOKEN=...
       CYBERSERVER_DISCORD_GUILD_ID=...
       CYBERSERVER_DISCORD_APPLICATION_ID=...

   Environment variables take precedence over properties. All three IDs and
   the token are validated before connecting.
6. Restart Minecraft. The server log should say the post office is starting
   and later that the bot has connected. Operators can also run
   `/cyberserver discordmail status` for safe, token-free connection status.
   The bot registers a guild-only
   `/mailpanel` command without replacing other Discord commands.
7. In Discord, an administrator with Manage Server permission uses
   `/mailpanel` in the desired channel. It posts the **Fractured Island Post
   Office** embed with a persistent **Write a Letter** button.
8. In Minecraft, an operator looks directly at the physical Drop Box that
   should receive Discord letters, within 7 blocks, and runs:

       /cybernpc discordmail bind
       /cybernpc discordmail status

   To remove the fixed assignment and allow any loaded registered Drop Box:

       /cybernpc discordmail unbind

   These commands are implemented in CyberNpc 0.45.6. The designated box
   must be loaded, registered and not full. Courier NPCs must already be
   configured to collect from it.
9. Test using a Discord member who is not a Minecraft player. Click the
   embed's button, type an exact Minecraft username of someone who has
   joined this server, and write up to 256 characters.

## What the recipient sees

- The sender is the Discord member's actual **Discord username**, taken
  from their authenticated interaction (not a user-supplied form field).
  Discord members do **not** need Minecraft accounts or linking.
- The bot writes an atomic request file into the Minecraft server's
  `config/cybernpc-discord-mail/incoming` directory. It cannot access
  other servers via this interface.
- CyberNpc validates the recipient against players that have joined the
  world, selects the bound Drop Box and creates a normal PENDING mail
  record. The Courier NPC collects that record and delivers a sealed letter
  to the player or their registered letterbox.
- The sender's name remains visible when the recipient opens the letter.
- If a box is full or unloaded, the submission waits in the spool until
  the box is ready. Mail is never silently rerouted from a specifically
  bound box. If no recipient is found, CyberNpc rejects the request into
  its private results/rejected folders.
- The bot acknowledges **submission to the queue**, not completed delivery.
  No recipient/player data or letter message is posted to Discord channels.
- Discord users are limited to one letter every two minutes, six per hour;
  the pending spool is capped at 128 requests. A unique interaction-derived
  ID plus CyberNpc's persistent request mapping prevent normal duplicates.

## Security, failure cases and limitations

- The bot uses outbound HTTPS + Discord Gateway WebSocket, bundled Java 17
  networking and Minecraft's existing Gson JSON reader. It does **not**
  need any new third-party runtime dependency or an inbound port.
- A private server config file or environment variable contains the bot
  token; never store it inside the mod jar. Restrict the Minecraft server
  filesystem and do not publish the saved mail directory: letter texts are
  private but **not encrypted at rest**.
- If the Discord connection drops, CyberServer retries with backoff and
  the Minecraft server remains running. The bot shuts down when the
  Minecraft dedicated server stops. **It cannot receive new Discord
  submissions while Minecraft is offline.**
- The bot is not a general Discord relay and does not read ordinary chat.
  An existing `discord-mail-bot/` Node prototype in CyberNpc is an old,
  *alternative* implementation and is **not needed or to be launched**
  when CyberServer hosts the bot.
- Discord's live Gateway and permission behaviour cannot be verified
  without the owner's real bot application; source integration alone is
  not an end-to-end test.
- Local build is also required. Neither the file bridge nor the server bot
  is represented as tested until they run together on the actual Forge
  dedicated server.

## Regression checklist

- With `enabled=false`, dedicated server starts without contacting Discord.
  Other CyberServer events and CyberNpc NPC/quests/mail are unaffected.
- With valid settings, `/mailpanel` is available to staff and posts a
  persistent embed; ordinary Discord members can open and submit its modal.
- Username is derived from Discord's authenticated user; malformed
  Minecraft names and messages are refused. No account linking.
- Verify two-minute and hourly rate limits, repeated interaction IDs,
  outgoing queue cap and client chat privacy.
- With a loaded selected Drop Box, the Minecraft Courier collects and
  delivers a letter to the intended player's mailbox or directly to them.
- With the box unloaded or full, letter remains pending. Unknown
  recipients are rejected without creating mail. After restart, no
  duplicate appears for an already imported request.
- Restart Minecraft: Discord bot reconnects; closing the world cleanly
  closes the bot. **Do not** accidentally run the Node prototype alongside
  the embedded bot.
