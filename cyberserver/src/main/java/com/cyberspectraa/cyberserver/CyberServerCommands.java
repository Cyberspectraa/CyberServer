package com.cyberspectraa.cyberserver;

import com.mojang.brigadier.CommandDispatcher;
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

public final class CyberServerCommands {
    private CyberServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Keep the old convenient command.
        dispatcher.register(buildArrivalCommands());

        // Also expose the system under the new CyberServer namespace.
        dispatcher.register(
                Commands.literal("cyberserver")
                        .requires(source -> source.hasPermission(2))
                        .then(buildArrivalCommands())
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildArrivalCommands() {
        return Commands.literal("arrival")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("setspawn")
                        .executes(ctx -> setSpawn(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("test")
                        .executes(ctx -> test(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> test(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("beam")
                        .executes(ctx -> beam(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> beam(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("resetall")
                        .executes(ctx -> resetAll(ctx.getSource())))
                .then(Commands.literal("mark")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> mark(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int setSpawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();

        if (player.serverLevel() != server.overworld()) {
            source.sendFailure(Component.literal("CyberServer: the church arrival spawn must be set in the Overworld."));
            return 0;
        }

        BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        CyberServerSavedData data = CyberServerSavedData.get(server);
        data.setSpawn(pos, player.getYRot());
        CyberServerEvents.enforceExactWorldSpawn(server, pos, player.getYRot());

        source.sendSuccess(() -> Component.literal(
                "CyberServer arrival spawn set exactly to " +
                        pos.getX() + " " + pos.getY() + " " + pos.getZ() +
                        ". Vanilla spawnRadius is now 0."
        ), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CyberServerSavedData data = CyberServerSavedData.get(server);
        String photon = PhotonBridge.isPhotonLoaded()
                ? "loaded; effect=" + PhotonBridge.EFFECT_ID
                : "not loaded; vanilla fallback active";

        if (!data.isSpawnConfigured()) {
            source.sendSuccess(() -> Component.literal(
                    "CyberServer 0.1.0 | arrival spawn not configured | Photon: " + photon
            ), false);
            return 1;
        }

        BlockPos pos = data.getSpawnPos();
        int radius = server.overworld().getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).get();

        source.sendSuccess(() -> Component.literal(
                "CyberServer 0.1.0 | spawn=" +
                        pos.getX() + " " + pos.getY() + " " + pos.getZ() +
                        " | spawnRadius=" + radius +
                        " | arrivedPlayers=" + data.arrivedCount() +
                        " | Photon: " + photon
        ), false);
        return 1;
    }

    private static int test(CommandSourceStack source, ServerPlayer target) {
        MinecraftServer server = source.getServer();
        CyberServerSavedData data = CyberServerSavedData.get(server);

        if (!data.isSpawnConfigured()) {
            source.sendFailure(Component.literal("CyberServer: set the church spawn first with /arrival setspawn."));
            return 0;
        }

        BlockPos spawn = data.getSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;

        target.teleportTo(server.overworld(), x, y, z, data.getSpawnYaw(), 0.0F);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        CyberServerEvents.startArrival(server.overworld(), x, y, z);

        source.sendSuccess(() -> Component.literal(
                "Played the full CyberServer arrival test for " +
                        target.getGameProfile().getName() +
                        ". First-join state was not changed."
        ), true);
        return 1;
    }

    private static int beam(CommandSourceStack source, ServerPlayer target) {
        CyberServerEvents.startArrival(target.serverLevel(), target.getX(), target.getY(), target.getZ());
        source.sendSuccess(() -> Component.literal(
                "Played the CyberServer summoning VFX at " +
                        target.getGameProfile().getName() + "'s current position."
        ), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        CyberServerSavedData data = CyberServerSavedData.get(source.getServer());
        data.reset(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Reset first-arrival state for " + target.getGameProfile().getName() +
                        ". Their next login will trigger the real arrival."
        ), true);
        return 1;
    }

    private static int resetAll(CommandSourceStack source) {
        CyberServerSavedData data = CyberServerSavedData.get(source.getServer());
        int count = data.resetAll();
        source.sendSuccess(() -> Component.literal(
                "Reset first-arrival state for " + count + " stored player(s)."
        ), true);
        return 1;
    }

    private static int mark(CommandSourceStack source, ServerPlayer target) {
        CyberServerSavedData data = CyberServerSavedData.get(source.getServer());
        data.markArrived(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Marked " + target.getGameProfile().getName() + " as already summoned."
        ), true);
        return 1;
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        CyberServerSavedData data = CyberServerSavedData.get(source.getServer());
        boolean arrived = data.hasArrived(target.getUUID());

        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() +
                        " first-arrival state: " +
                        (arrived ? "already summoned" : "not yet summoned")
        ), false);
        return 1;
    }
}
