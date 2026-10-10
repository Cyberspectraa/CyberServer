package com.cyberspectraa.cyberserver;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectiveOperationException;

public final class CyberServerCommands {
    private static final String VERSION = "0.2.0";

    private CyberServerCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        // Keep the old convenient command for existing worlds/admin habits.
        dispatcher.register(buildArrivalCommands());

        dispatcher.register(
                Commands.literal("cyberserver")
                        .requires(source -> source.hasPermission(2))
                        .then(buildArrivalCommands())
                        .then(Commands.literal("status")
                                .executes(ctx ->
                                        status(ctx.getSource())))
                        .then(Commands.literal("state")
                                .executes(ctx ->
                                        worldState(ctx.getSource())))
                        .then(storyCommands())
                        .then(flagCommands())
                        .then(regionCommands())
                        .then(bossCommands())
                        .then(npcQuestCommands())
                        .then(npcProgressionCommands())
                        .then(economyCommands())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    buildArrivalCommands() {
        return Commands.literal("arrival")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("setspawn")
                        .executes(ctx ->
                                setSpawn(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx ->
                                status(ctx.getSource())))
                .then(Commands.literal("test")
                        .executes(ctx -> test(
                                ctx.getSource(),
                                ctx.getSource()
                                        .getPlayerOrException()
                        ))
                        .then(Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(ctx -> test(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(
                                                ctx,
                                                "player"
                                        )
                                ))))
                .then(Commands.literal("beam")
                        .executes(ctx -> beam(
                                ctx.getSource(),
                                ctx.getSource()
                                        .getPlayerOrException()
                        ))
                        .then(Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(ctx -> beam(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(
                                                ctx,
                                                "player"
                                        )
                                ))))
                .then(Commands.literal("reset")
                        .then(Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(ctx -> reset(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(
                                                ctx,
                                                "player"
                                        )
                                ))))
                .then(Commands.literal("resetall")
                        .executes(ctx ->
                                resetAll(ctx.getSource())))
                .then(Commands.literal("mark")
                        .then(Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(ctx -> mark(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(
                                                ctx,
                                                "player"
                                        )
                                ))))
                .then(Commands.literal("info")
                        .then(Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(ctx -> info(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(
                                                ctx,
                                                "player"
                                        )
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    storyCommands() {
        return Commands.literal("story")
                .then(Commands.literal("act")
                        .executes(ctx -> storyActStatus(
                                ctx.getSource()
                        ))
                        .then(Commands.argument(
                                        "value",
                                        IntegerArgumentType.integer(
                                                1,
                                                1000
                                        )
                                )
                                .executes(ctx -> setStoryAct(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(
                                                ctx,
                                                "value"
                                        )
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    flagCommands() {
        return Commands.literal("flag")
                .then(Commands.literal("get")
                        .then(Commands.argument(
                                        "flag",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> flagGet(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "flag"
                                        )
                                ))))
                .then(Commands.literal("set")
                        .then(Commands.argument(
                                        "flag",
                                        StringArgumentType.word()
                                )
                                .then(Commands.argument(
                                                "value",
                                                BoolArgumentType.bool()
                                        )
                                        .executes(ctx -> flagSet(
                                                ctx.getSource(),
                                                StringArgumentType.getString(
                                                        ctx,
                                                        "flag"
                                                ),
                                                BoolArgumentType.getBool(
                                                        ctx,
                                                        "value"
                                                )
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    regionCommands() {
        return Commands.literal("region")
                .then(Commands.literal("unlock")
                        .then(Commands.argument(
                                        "region",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> regionSet(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "region"
                                        ),
                                        true
                                ))))
                .then(Commands.literal("lock")
                        .then(Commands.argument(
                                        "region",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> regionSet(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "region"
                                        ),
                                        false
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    bossCommands() {
        return Commands.literal("boss")
                .then(Commands.literal("mark")
                        .then(Commands.argument(
                                        "boss",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> bossSet(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "boss"
                                        ),
                                        true
                                ))))
                .then(Commands.literal("clear")
                        .then(Commands.argument(
                                        "boss",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> bossSet(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "boss"
                                        ),
                                        false
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    npcQuestCommands() {
        return Commands.literal("npcquests")
                .then(Commands.literal("list")
                        .then(Commands.argument(
                                        "npc",
                                        StringArgumentType.word()
                                )
                                .executes(ctx -> npcQuestList(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                                ctx,
                                                "npc"
                                        )
                                ))))
                .then(Commands.literal("bind")
                        .then(Commands.argument(
                                        "npc",
                                        StringArgumentType.word()
                                )
                                .then(Commands.argument(
                                                "quest",
                                                StringArgumentType.string()
                                        )
                                        .executes(ctx -> npcQuestBind(
                                                ctx.getSource(),
                                                StringArgumentType.getString(
                                                        ctx,
                                                        "npc"
                                                ),
                                                StringArgumentType.getString(
                                                        ctx,
                                                        "quest"
                                                )
                                        )))))
                .then(Commands.literal("unbind")
                        .then(Commands.argument(
                                        "npc",
                                        StringArgumentType.word()
                                )
                                .then(Commands.argument(
                                                "quest",
                                                StringArgumentType.string()
                                        )
                                        .executes(ctx -> npcQuestUnbind(
                                                ctx.getSource(),
                                                StringArgumentType.getString(
                                                        ctx,
                                                        "npc"
                                                ),
                                                StringArgumentType.getString(
                                                        ctx,
                                                        "quest"
                                                )
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    npcProgressionCommands() {
        return Commands.literal("npcprogression")
                .executes(ctx ->
                        npcProgressionStatus(ctx.getSource()))
                .then(Commands.literal("levelcap")
                        .then(Commands.argument(
                                        "level",
                                        IntegerArgumentType.integer(
                                                1,
                                                1000
                                        )
                                )
                                .executes(ctx -> setNpcLevelCap(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(
                                                ctx,
                                                "level"
                                        )
                                ))))
                .then(Commands.literal("advancedclasses")
                        .then(Commands.argument(
                                        "enabled",
                                        BoolArgumentType.bool()
                                )
                                .executes(ctx -> setAdvancedClasses(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(
                                                ctx,
                                                "enabled"
                                        )
                                ))))
                .then(Commands.literal("evolvedraces")
                        .then(Commands.argument(
                                        "enabled",
                                        BoolArgumentType.bool()
                                )
                                .executes(ctx -> setEvolvedRaces(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(
                                                ctx,
                                                "enabled"
                                        )
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    economyCommands() {
        return Commands.literal("economy")
                .then(Commands.literal("questmultiplier")
                        .executes(ctx ->
                                questMultiplierStatus(
                                        ctx.getSource()
                                ))
                        .then(Commands.argument(
                                        "value",
                                        DoubleArgumentType.doubleArg(
                                                0.0D,
                                                100.0D
                                        )
                                )
                                .executes(ctx -> setQuestMultiplier(
                                        ctx.getSource(),
                                        DoubleArgumentType.getDouble(
                                                ctx,
                                                "value"
                                        )
                                ))));
    }

    private static int setSpawn(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();
        MinecraftServer server =
                source.getServer();

        if (player.serverLevel() != server.overworld()) {
            source.sendFailure(Component.literal(
                    "CyberServer: the church arrival spawn must be set in the Overworld."
            ));
            return 0;
        }

        BlockPos pos = BlockPos.containing(
                player.getX(),
                player.getY(),
                player.getZ()
        );

        CyberServerSavedData data =
                CyberServerSavedData.get(server);

        data.setSpawn(pos, player.getYRot());
        CyberServerEvents.enforceExactWorldSpawn(
                server,
                pos,
                player.getYRot()
        );

        source.sendSuccess(() -> Component.literal(
                "CyberServer arrival spawn set exactly to "
                        + pos.getX() + " "
                        + pos.getY() + " "
                        + pos.getZ()
                        + ". Vanilla spawnRadius is now 0."
        ), true);

        return 1;
    }

    private static int status(
            CommandSourceStack source
    ) {
        MinecraftServer server =
                source.getServer();
        CyberServerSavedData data =
                CyberServerSavedData.get(server);

        String photon = PhotonBridge.isPhotonLoaded()
                ? "loaded; effect="
                    + PhotonBridge.EFFECT_ID
                : "not loaded; vanilla fallback active";

        String spawn;

        if (!data.isSpawnConfigured()) {
            spawn = "not configured";
        } else {
            BlockPos pos = data.getSpawnPos();
            int radius = server.overworld()
                    .getGameRules()
                    .getRule(GameRules.RULE_SPAWN_RADIUS)
                    .get();

            spawn = pos.getX() + " "
                    + pos.getY() + " "
                    + pos.getZ()
                    + " (radius=" + radius + ")";
        }

        source.sendSuccess(() -> Component.literal(
                "CyberServer " + VERSION
                        + " | spawn=" + spawn
                        + " | arrivedPlayers="
                        + data.arrivedCount()
                        + " | storyAct="
                        + data.getStoryAct()
                        + " | Photon: " + photon
        ), false);

        return 1;
    }

    private static int worldState(
            CommandSourceStack source
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        source.sendSuccess(() -> Component.literal(
                "WorldState v"
                        + CyberServerSavedData.DATA_VERSION
                        + " | act=" + data.getStoryAct()
                        + " | flags="
                        + data.getEnabledFlags().size()
                        + " | regions="
                        + data.getUnlockedRegions().size()
                        + " | bosses="
                        + data.getDefeatedBosses().size()
                        + " | NPC cap="
                        + data.getNaturalNpcLevelCap()
                        + " | advancedClasses="
                        + data.areAdvancedNpcClassesUnlocked()
                        + " | evolvedRaces="
                        + data.areEvolvedNpcRacesUnlocked()
                        + " | questMultiplier="
                        + data.getQuestRewardMultiplier()
        ), false);

        return 1;
    }

    private static int storyActStatus(
            CommandSourceStack source
    ) {
        int act = CyberServerSavedData
                .get(source.getServer())
                .getStoryAct();

        source.sendSuccess(
                () -> Component.literal(
                        "CyberServer story act: " + act
                ),
                false
        );

        return act;
    }

    private static int setStoryAct(
            CommandSourceStack source,
            int act
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.setStoryAct(act);

        source.sendSuccess(
                () -> Component.literal(
                        "CyberServer story act set to "
                                + data.getStoryAct() + "."
                ),
                true
        );

        return data.getStoryAct();
    }

    private static int flagGet(
            CommandSourceStack source,
            String flag
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(flag);
        boolean value = data.getFlag(key);

        source.sendSuccess(
                () -> Component.literal(
                        "World flag " + key
                                + " = " + value
                ),
                false
        );

        return value ? 1 : 0;
    }

    private static int flagSet(
            CommandSourceStack source,
            String flag,
            boolean value
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(flag);

        data.setFlag(key, value);

        source.sendSuccess(
                () -> Component.literal(
                        "World flag " + key
                                + " set to " + value + "."
                ),
                true
        );

        return 1;
    }

    private static int regionSet(
            CommandSourceStack source,
            String region,
            boolean unlocked
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(region);

        data.setRegionUnlocked(key, unlocked);

        source.sendSuccess(
                () -> Component.literal(
                        "Region " + key
                                + (unlocked
                                ? " unlocked."
                                : " locked.")
                ),
                true
        );

        return 1;
    }

    private static int bossSet(
            CommandSourceStack source,
            String boss,
            boolean defeated
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(boss);

        data.setBossDefeated(key, defeated);

        source.sendSuccess(
                () -> Component.literal(
                        "Boss " + key
                                + (defeated
                                ? " marked defeated."
                                : " cleared.")
                ),
                true
        );

        return 1;
    }

    private static int npcQuestList(
            CommandSourceStack source,
            String npcId
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(
                        npcId
                );

        var quests = data.getNpcQuestBindings(key);

        source.sendSuccess(
                () -> Component.literal(
                        "Server quest bindings for "
                                + key + ": "
                                + (quests.isEmpty()
                                ? "<none>"
                                : String.join(", ", quests))
                ),
                false
        );

        return quests.size();
    }

    private static int npcQuestBind(
            CommandSourceStack source,
            String npcId,
            String questId
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(
                        npcId
                );

        boolean changed = data.bindNpcQuest(
                key,
                questId
        );

        source.sendSuccess(
                () -> Component.literal(
                        (changed
                        ? "Bound "
                        : "Already bound ")
                                + questId
                                + " to server NPC id "
                                + key + "."
                ),
                true
        );

        return changed ? 1 : 0;
    }

    private static int npcQuestUnbind(
            CommandSourceStack source,
            String npcId,
            String questId
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        String key =
                CyberServerSavedData.normalizeKey(
                        npcId
                );

        boolean changed = data.unbindNpcQuest(
                key,
                questId
        );

        if (!changed) {
            source.sendFailure(
                    Component.literal(
                            "No binding for "
                                    + questId
                                    + " on server NPC id "
                                    + key + "."
                    )
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Unbound "
                                + questId
                                + " from server NPC id "
                                + key + "."
                ),
                true
        );

        return 1;
    }

    private static int npcProgressionStatus(
            CommandSourceStack source
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Natural NPC policy: levelCap="
                                + data.getNaturalNpcLevelCap()
                                + ", advancedClasses="
                                + data.areAdvancedNpcClassesUnlocked()
                                + ", evolvedRaces="
                                + data.areEvolvedNpcRacesUnlocked()
                                + ", worldDaysPerLevel="
                                + data.getNpcWorldDaysPerLevel()
                ),
                false
        );

        return 1;
    }

    private static int setNpcLevelCap(
            CommandSourceStack source,
            int value
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.setNaturalNpcLevelCap(value);

        source.sendSuccess(
                () -> Component.literal(
                        "Natural Wild NPC level cap set to "
                                + data.getNaturalNpcLevelCap()
                                + ". New natural spawns use this policy."
                ),
                true
        );

        return data.getNaturalNpcLevelCap();
    }

    private static int setAdvancedClasses(
            CommandSourceStack source,
            boolean enabled
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.setAdvancedNpcClassesUnlocked(enabled);

        source.sendSuccess(
                () -> Component.literal(
                        "Natural NPC class advancements "
                                + (enabled
                                ? "enabled."
                                : "locked.")
                                + " New natural spawns use this policy."
                ),
                true
        );

        return 1;
    }

    private static int setEvolvedRaces(
            CommandSourceStack source,
            boolean enabled
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.setEvolvedNpcRacesUnlocked(enabled);

        source.sendSuccess(
                () -> Component.literal(
                        "Natural NPC race evolutions "
                                + (enabled
                                ? "enabled."
                                : "locked.")
                                + " New natural spawns use this policy."
                ),
                true
        );

        return 1;
    }

    private static int questMultiplierStatus(
            CommandSourceStack source
    ) {
        double value = CyberServerSavedData
                .get(source.getServer())
                .getQuestRewardMultiplier();

        source.sendSuccess(
                () -> Component.literal(
                        "Quest reward multiplier: "
                                + value
                ),
                false
        );

        return (int) Math.round(value * 100.0D);
    }

    private static int setQuestMultiplier(
            CommandSourceStack source,
            double value
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.setQuestRewardMultiplier(value);

        source.sendSuccess(
                () -> Component.literal(
                        "Quest reward multiplier set to "
                                + data.getQuestRewardMultiplier()
                                + "."
                ),
                true
        );

        return 1;
    }

    private static int test(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        MinecraftServer server =
                source.getServer();

        // CyberNpc owns the complete 6.5-second camera, summon/reveal,
        // queue and Pope conversation. A testing command must invoke the
        // actual controller, not CyberServer's old particle-only effect.
        if (ModList.get().isLoaded("cybernpc")) {
            final String result;
            try {
                Class<?> intro = Class.forName(
                        "com.cyberspectraa.cybernpc.intro.CyberIntroService");
                Method entry = intro.getMethod("testFullIntro", ServerPlayer.class);
                Object response = entry.invoke(null, target);
                result = response instanceof String ? (String) response : "INTEGRATION_ERROR";
            } catch (ReflectiveOperationException | RuntimeException error) {
                source.sendFailure(Component.literal(
                        "CyberServer: cannot start the CyberNpc intro. "
                        + "Update CyberNpc to v0.44.30 or later. Check server logs."));
                CyberServer.LOGGER.error("CyberNpc full arrival test failed", error);
                return 0;
            }

            if ("STARTED".equals(result) || "QUEUED".equals(result)) {
                source.sendSuccess(() -> Component.literal(
                        "CyberServer: " + target.getGameProfile().getName()
                        + ("QUEUED".equals(result)
                           ? " joined the summoning queue. The complete intro starts when the Pope is free."
                           : " will play the complete cinematic and Pope greeting.")
                        + " Character race, class and levels are unchanged."), true);
                return 1;
            }

            String problem = switch (result) {
                case "SETUP_REQUIRED" -> "Set both locations first: /cyberintro setarrival and /cyberintro setpopewait (in the same dimension).";
                case "CUSTOMIZATION_INCOMPLETE" -> "Finish both character race and class customisation before testing the introduction.";
                case "ALREADY_IN_INTRO" -> "That player is already customising, queued, or in the introduction.";
                default -> "CyberNpc rejected the intro test (" + result + ").";
            };
            source.sendFailure(Component.literal("CyberServer: " + problem));
            return 0;
        }

        // Legacy particle-only test remains available without CyberNpc.
        CyberServerSavedData data =
                CyberServerSavedData.get(server);

        if (!data.isSpawnConfigured()) {
            source.sendFailure(Component.literal(
                    "CyberServer: set the church spawn first with /arrival setspawn."
            ));
            return 0;
        }

        BlockPos spawn = data.getSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;

        target.teleportTo(
                server.overworld(),
                x,
                y,
                z,
                data.getSpawnYaw(),
                0.0F
        );
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;

        CyberServerEvents.startArrival(
                server.overworld(),
                x,
                y,
                z
        );

        source.sendSuccess(() -> Component.literal(
                "Played the full CyberServer arrival test for "
                        + target.getGameProfile().getName()
                        + ". First-join state was not changed."
        ), true);

        return 1;
    }

    private static int beam(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        CyberServerEvents.startArrival(
                target.serverLevel(),
                target.getX(),
                target.getY(),
                target.getZ()
        );

        source.sendSuccess(() -> Component.literal(
                "Played the CyberServer summoning VFX at "
                        + target.getGameProfile().getName()
                        + "'s current position."
        ), false);

        return 1;
    }

    private static int reset(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.reset(target.getUUID());

        source.sendSuccess(() -> Component.literal(
                "Reset first-arrival state for "
                        + target.getGameProfile().getName()
                        + ". Their next login will trigger the real arrival."
        ), true);

        return 1;
    }

    private static int resetAll(
            CommandSourceStack source
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        int count = data.resetAll();

        source.sendSuccess(() -> Component.literal(
                "Reset first-arrival state for "
                        + count + " stored player(s)."
        ), true);

        return 1;
    }

    private static int mark(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        data.markArrived(target.getUUID());

        source.sendSuccess(() -> Component.literal(
                "Marked "
                        + target.getGameProfile().getName()
                        + " as already summoned."
        ), true);

        return 1;
    }

    private static int info(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        CyberServerSavedData data =
                CyberServerSavedData.get(
                        source.getServer()
                );

        boolean arrived =
                data.hasArrived(target.getUUID());

        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName()
                        + " first-arrival state: "
                        + (arrived
                        ? "already summoned"
                        : "not yet summoned")
        ), false);

        return 1;
    }
}
