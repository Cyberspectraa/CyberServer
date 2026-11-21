package com.cyberspectraa.cyberserver.discord;

import com.cyberspectraa.cyberserver.CyberServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Discord app hosted INSIDE the dedicated CyberServer Forge mod. Uses only
 * Java 17's HTTP/WebSocket APIs and Minecraft's existing Gson library:
 * no Node process, external service, bot library, or public inbound port.
 *
 * Discord Gateway events never touch Minecraft world data. The server-only
 * bot writes authenticated, rate-limited letters into CyberNpc's existing
 * on-disk import queue, so NPC couriers handle the rest.
 */
@Mod.EventBusSubscriber(modid = CyberServer.MOD_ID)
public final class DiscordMailBot {
    private static final URI GATEWAY =
            URI.create("wss://gateway.discord.gg/?v=10&encoding=json");
    private static final String API = "https://discord.com/api/v10";
    private static final String COMPOSE = "cyberserver:mail:compose";
    private static final String SUBMIT = "cyberserver:mail:send";
    private static final long MIN_INTERVAL = 120_000L;
    private static final long HOUR = 3_600_000L;
    private static final int MAX_PER_HOUR = 6;
    private static DiscordMailBot instance;

    private final DiscordMailConfig config;
    private final DiscordMailSpool spool;
    private final HttpClient http;
    private final ScheduledExecutorService tasks;
    // Accessed on the one bot scheduler thread, never the MC server thread.
    private final Map<String, ArrayDeque<Long>> sentByUser = new HashMap<>();

    private volatile boolean running;
    private volatile boolean online;
    private boolean connecting;
    private volatile WebSocket socket;
    private ScheduledFuture<?> heartbeatTask;
    private boolean awaitingHeartbeatAck;
    private long sequence = -1L;
    private int consecutiveFailures;

    private DiscordMailBot(DiscordMailConfig config) {
        this.config = config;
        this.spool = new DiscordMailSpool(config.spoolDir());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
        this.tasks = Executors.newSingleThreadScheduledExecutor(job -> {
            Thread thread = new Thread(job, "CyberServer-Discord-Mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    @SubscribeEvent
    public static synchronized void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()
                || !ModList.get().isLoaded("cybernpc") || instance != null) {
            return;
        }
        DiscordMailConfig config = DiscordMailConfig.load();
        if (config == null) return;
        String npcVersion = ModList.get().getModContainerById("cybernpc")
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("");
        if (!hasPostalImporter(npcVersion)) {
            CyberServer.LOGGER.warn("Discord mail requires CyberNpc 0.45.6 or later (found {})",
                    npcVersion);
            return;
        }
        instance = new DiscordMailBot(config);
        instance.start();
    }

    private static boolean hasPostalImporter(String version) {
        // Mod versions may carry a suffix such as -alpha or +build; compare
        // the three leading numeric parts without blocking unrelated mods.
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)")
                        .matcher(version);
        if (!m.find()) return false;
        try {
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            int patch = Integer.parseInt(m.group(3));
            return major > 0 || minor > 45 || (minor == 45 && patch >= 6);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Safe read-only status for Minecraft operators; never returns secrets. */
    public static synchronized String status() {
        if (instance == null) {
            return "Discord post office is disabled, unconfigured, or CyberNpc is unavailable.";
        }
        return instance.online
                ? "Discord post office is connected. Staff can use /mailpanel in Discord."
                : "Discord post office is configured but still connecting or reconnecting.";
    }

    @SubscribeEvent
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    private void start() {
        running = true;
        tasks.execute(() -> {
            CyberServer.LOGGER.info("CyberServer Discord post office starting (server-only)");
            registerPanelCommand();
            connect();
        });
    }

    private void stop() {
        running = false;
        online = false;
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        WebSocket ws = socket;
        socket = null;
        if (ws != null) ws.abort();
        tasks.shutdownNow();
    }

    /**
     * Register ONLY our one guild slash command: never bulk-overwrite the
     * server's unrelated Discord commands.
     */
    private void registerPanelCommand() {
        JsonObject data = new JsonObject();
        data.addProperty("name", "mailpanel");
        data.addProperty("type", 1);
        data.addProperty("description", "Post the Fractured Island letter-writing panel");
        data.addProperty("default_member_permissions", "32"); // Manage Guild
        String endpoint = API + "/applications/" + config.applicationId()
                + "/guilds/" + config.guildId() + "/commands";
        post(endpoint, data, true, "register /mailpanel");
    }

    private void connect() {
        if (!running || connecting || socket != null) return;
        connecting = true;
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(GATEWAY, new GatewayListener())
                .whenComplete((ws, error) -> {
                    if (!running) {
                        if (ws != null) ws.abort();
                        return;
                    }
                    tasks.execute(() -> {
                    connecting = false;
                    if (!running) {
                        if (ws != null) ws.abort();
                        return;
                    }
                    if (error != null) {
                        CyberServer.LOGGER.warn("Discord Gateway connection failed: {}",
                                error.getClass().getSimpleName());
                        scheduleConnect();
                    }
                    });
                });
    }

    private void scheduleConnect() {
        if (!running) return;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        long wait = Math.min(60L, 2L << Math.min(5, consecutiveFailures++));
        tasks.schedule(this::connect, wait, TimeUnit.SECONDS);
    }

    private void disconnect(WebSocket candidate) {
        if (candidate != socket) return;
        socket = null;
        online = false;
        awaitingHeartbeatAck = false;
        sequence = -1L; // No resume token: identify a fresh session.
        candidate.abort();
        scheduleConnect();
    }

    private void handleGateway(WebSocket from, String raw) {
        if (!running || from != socket) return;
        try {
            JsonObject envelope = JsonParser.parseString(raw).getAsJsonObject();
            int op = integer(envelope, "op");
            if (op == 0) {
                JsonElement seq = envelope.get("s");
                if (seq != null && !seq.isJsonNull()) sequence = seq.getAsLong();
                if ("READY".equals(string(envelope, "t"))) {
                    online = true;
                    consecutiveFailures = 0;
                    CyberServer.LOGGER.info("Discord Post Office connected; /mailpanel is available");
                } else if ("INTERACTION_CREATE".equals(string(envelope, "t"))) {
                    JsonObject interaction = object(envelope, "d");
                    if (interaction != null) interaction(interaction);
                }
            } else if (op == 10) {
                JsonObject hello = object(envelope, "d");
                if (hello != null) identifyAndHeartbeat(from, integer(hello, "heartbeat_interval"));
            } else if (op == 11) {
                awaitingHeartbeatAck = false;
            } else if (op == 1) {
                sendHeartbeat(from);
            } else if (op == 7 || op == 9) {
                disconnect(from);
            }
        } catch (RuntimeException ex) {
            // Discord interaction payloads can include personal letter text.
            // Never log full JSON, message contents, or bot credentials.
            CyberServer.LOGGER.warn("Could not handle Discord event: {}",
                    ex.getClass().getSimpleName());
        }
    }

    private void identifyAndHeartbeat(WebSocket from, int intervalMs) {
        if (intervalMs < 1_000 || intervalMs > 120_000) {
            disconnect(from);
            return;
        }
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        awaitingHeartbeatAck = false;
        heartbeatTask = tasks.scheduleAtFixedRate(
                () -> sendHeartbeat(from), intervalMs / 2L, intervalMs,
                TimeUnit.MILLISECONDS);

        JsonObject properties = new JsonObject();
        properties.addProperty("os", "linux");
        properties.addProperty("browser", "cyberserver");
        properties.addProperty("device", "cyberserver");
        JsonObject identify = new JsonObject();
        identify.addProperty("token", config.token());
        identify.addProperty("intents", 1); // GUILDS only; no message-content intent.
        identify.add("properties", properties);
        sendGateway(from, 2, identify);
    }

    private void sendHeartbeat(WebSocket from) {
        if (!running || socket != from) return;
        if (awaitingHeartbeatAck) {
            CyberServer.LOGGER.warn("Discord heartbeat timed out; reconnecting");
            disconnect(from);
            return;
        }
        awaitingHeartbeatAck = true;
        sendGateway(from, 1, sequence < 0 ? JsonNull.INSTANCE
                : new com.google.gson.JsonPrimitive(sequence));
    }

    private void sendGateway(WebSocket from, int op, JsonElement data) {
        JsonObject packet = new JsonObject();
        packet.addProperty("op", op);
        packet.add("d", data);
        from.sendText(packet.toString(), true).exceptionally(error -> {
            tasks.execute(() -> disconnect(from));
            return null;
        });
    }

    private void interaction(JsonObject body) {
        // An interaction in the wrong guild is never allowed to write mail.
        if (!config.guildId().equals(string(body, "guild_id"))) return;
        int type = integer(body, "type");
        JsonObject data = object(body, "data");
        if (data == null) return;

        if (type == 2 && "mailpanel".equals(string(data, "name"))) {
            JsonObject member = object(body, "member");
            boolean permitted = member != null && canManageGuild(string(member, "permissions"));
            callback(body, permitted ? panelResponse()
                    : messageResponse("Only server staff can place the postal panel.", true));
        } else if (type == 3 && COMPOSE.equals(string(data, "custom_id"))) {
            callback(body, modalResponse());
        } else if (type == 5 && SUBMIT.equals(string(data, "custom_id"))) {
            submitLetter(body, data);
        }
    }

    private static boolean canManageGuild(String permissions) {
        try {
            return new BigInteger(permissions).testBit(5); // 1 << 5
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void submitLetter(JsonObject body, JsonObject data) {
        JsonObject member = object(body, "member");
        JsonObject author = member == null ? null : object(member, "user");
        if (author == null) author = object(body, "user");
        String discordId = author == null ? "" : string(author, "id");
        String sender = author == null ? "" : string(author, "username");
        Map<String, String> fields = modalFields(data);

        String recipient = fields.getOrDefault("recipient", "").trim();
        String message = fields.getOrDefault("message", "").trim();
        if (!discordId.matches("[0-9]{15,22}") || !validText(sender, 32)
                || !recipient.matches("[A-Za-z0-9_]{3,16}")
                || !validText(message, 256)) {
            callback(body, messageResponse(
                    "Use a valid Minecraft username and a letter of 1–256 characters.", true));
            return;
        }
        if (!allowLetter(discordId)) {
            callback(body, messageResponse(
                    "Please wait two minutes between letters (maximum six per hour).", true));
            return;
        }
        String id = string(body, "id");
        if (!id.matches("[0-9]{15,22}")) return;
        UUID requestId = UUID.nameUUIDFromBytes(
                ("cyberserver:discord-interaction:" + id).getBytes(StandardCharsets.UTF_8));

        try {
            DiscordMailSpool.QueueResult result = spool.submit(
                    requestId, discordId, sender, recipient, message);
            if (result == DiscordMailSpool.QueueResult.BUSY) {
                callback(body, messageResponse(
                        "The island post office is busy. Please try again later.", true));
                return;
            }
            if (result == DiscordMailSpool.QueueResult.QUEUED) {
                recordLetter(discordId);
            }
            callback(body, messageResponse(
                    "Your letter to **" + recipient + "** has been queued for the island post office. "
                            + "A Courier will collect it from the Minecraft Drop Box when available. "
                            + "This confirms submission, not delivery.", true));
        } catch (Exception ex) {
            CyberServer.LOGGER.warn("Could not queue a Discord letter: {}",
                    ex.getClass().getSimpleName());
            callback(body, messageResponse(
                    "The island post office could not accept the letter. Try again later.", true));
        }
    }

    private boolean allowLetter(String discordId) {
        ArrayDeque<Long> timestamps = sentByUser.get(discordId);
        if (timestamps == null) return true;
        long now = System.currentTimeMillis();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= HOUR) {
            timestamps.removeFirst();
        }
        if (timestamps.isEmpty()) {
            sentByUser.remove(discordId);
            return true;
        }
        return timestamps.size() < MAX_PER_HOUR
                && now - timestamps.peekLast() >= MIN_INTERVAL;
    }

    private void recordLetter(String discordId) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> timestamps =
                sentByUser.computeIfAbsent(discordId, id -> new ArrayDeque<>());
        timestamps.addLast(now);
        // Cap memory in a long-lived public server with many unique visitors.
        if (sentByUser.size() > 10_000) {
            sentByUser.entrySet().removeIf(e ->
                    e.getValue().isEmpty() || now - e.getValue().peekLast() >= HOUR);
        }
    }

    private static boolean validText(String text, int max) {
        if (text.isBlank() || text.length() > max) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 || Character.isISOControl(c)) return false;
        }
        return true;
    }

    private static Map<String, String> modalFields(JsonObject data) {
        Map<String, String> result = new HashMap<>();
        JsonArray rows = array(data, "components");
        for (JsonElement row : rows) {
            if (!row.isJsonObject()) continue;
            JsonObject container = row.getAsJsonObject();
            // Standard Discord modal Text Inputs are nested in Action Rows.
            JsonArray inputs = array(container, "components");
            for (JsonElement input : inputs) {
                if (!input.isJsonObject()) continue;
                JsonObject field = input.getAsJsonObject();
                String id = string(field, "custom_id");
                if (("recipient".equals(id) || "message".equals(id))
                        && !result.containsKey(id)) {
                    result.put(id, string(field, "value"));
                }
            }
        }
        return result;
    }

    private static JsonObject panelResponse() {
        JsonObject button = new JsonObject();
        button.addProperty("type", 2);
        button.addProperty("style", 2);
        button.addProperty("custom_id", COMPOSE);
        button.addProperty("label", "Write a Letter");
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        JsonArray buttons = new JsonArray();
        buttons.add(button);
        row.add("components", buttons);

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Fractured Island Post Office");
        embed.addProperty("color", 0x795938);
        embed.addProperty("description",
                "Send a letter to someone on the island.\n\n"
                        + "Press **Write a Letter**, enter their Minecraft username and your "
                        + "message. Your Discord username will appear as the sender. "
                        + "A Minecraft Courier collects it from the Drop Box and delivers "
                        + "it to the player or their letterbox.\n\n"
                        + "Letters are private and may take time to arrive.");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonArray components = new JsonArray();
        components.add(row);
        JsonObject data = new JsonObject();
        data.add("embeds", embeds);
        data.add("components", components);
        data.add("allowed_mentions", noMentions());
        return response(4, data);
    }

    private static JsonObject modalResponse() {
        JsonArray rows = new JsonArray();
        rows.add(input("recipient", "Minecraft recipient username", 1, 3, 16));
        rows.add(input("message", "Your letter (max 256 characters)", 2, 1, 256));
        JsonObject data = new JsonObject();
        data.addProperty("custom_id", SUBMIT);
        data.addProperty("title", "Write a Letter");
        data.add("components", rows);
        return response(9, data);
    }

    private static JsonObject input(String id, String label, int style, int min, int max) {
        JsonObject field = new JsonObject();
        field.addProperty("type", 4);
        field.addProperty("custom_id", id);
        field.addProperty("style", style);
        field.addProperty("label", label);
        field.addProperty("min_length", min);
        field.addProperty("max_length", max);
        field.addProperty("required", true);
        JsonArray fields = new JsonArray();
        fields.add(field);
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        row.add("components", fields);
        return row;
    }

    private static JsonObject messageResponse(String message, boolean ephemeral) {
        JsonObject data = new JsonObject();
        data.addProperty("content", message);
        data.add("allowed_mentions", noMentions());
        if (ephemeral) data.addProperty("flags", 64);
        return response(4, data);
    }

    private static JsonObject response(int type, JsonObject data) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        response.add("data", data);
        return response;
    }

    private static JsonObject noMentions() {
        JsonObject obj = new JsonObject();
        obj.add("parse", new JsonArray());
        return obj;
    }

    private void callback(JsonObject interaction, JsonObject response) {
        String id = string(interaction, "id");
        String token = string(interaction, "token");
        if (!id.matches("[0-9]{15,22}") || token.isBlank()) return;
        // Gateway-delivered interactions MUST be answered by Discord's HTTPS
        // callback endpoint, within the interaction's three-second deadline.
        post(API + "/interactions/" + id + "/" + token + "/callback",
                response, false, "Discord interaction");
    }

    private void post(String endpoint, JsonObject data, boolean authenticate,
                      String description) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        data.toString(), StandardCharsets.UTF_8));
        if (authenticate) {
            request.header("Authorization", "Bot " + config.token());
        }
        http.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (!running) return;
                    if (error != null) {
                        CyberServer.LOGGER.warn("{} request failed: {}",
                                description, error.getClass().getSimpleName());
                    } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        CyberServer.LOGGER.warn("{} request returned HTTP {}",
                                description, response.statusCode());
                    }
                });
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement v = parent.get(key);
        return v != null && v.isJsonObject() ? v.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement v = parent.get(key);
        return v != null && v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key) {
        if (parent == null) return "";
        JsonElement v = parent.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    private static int integer(JsonObject parent, String key) {
        JsonElement v = parent.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsInt() : -1;
    }

    private final class GatewayListener implements WebSocket.Listener {
        private final StringBuilder chunks = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            if (!running) {
                ws.abort();
                return;
            }
            tasks.execute(() -> {
                if (!running) {
                    ws.abort();
                    return;
                }
                socket = ws;
            });
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (!running) return CompletableFuture.completedFuture(null);
            chunks.append(data);
            if (chunks.length() > 256_000) {
                ws.abort();
            } else if (last) {
                String packet = chunks.toString();
                chunks.setLength(0);
                tasks.execute(() -> handleGateway(ws, packet));
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            if (running) tasks.execute(() -> disconnect(ws));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (running) tasks.execute(() -> disconnect(ws));
        }
    }
}
