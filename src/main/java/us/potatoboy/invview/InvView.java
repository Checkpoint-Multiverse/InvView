package us.potatoboy.invview;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class InvView implements ModInitializer {
    private static MinecraftServer minecraftServer;
    public static boolean isTrinkets = false;
    public static boolean isLuckPerms = false;
    public static boolean isApoli = false;

    @Override
    public void onInitialize() {
        isTrinkets = FabricLoader.getInstance().isModLoaded("trinkets");
        isLuckPerms = FabricLoader.getInstance().isModLoaded("luckperms");
        isApoli = FabricLoader.getInstance().isModLoaded("apoli");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralCommandNode<ServerCommandSource> viewNode = CommandManager
                    .literal("view")
                    .requires(src -> PermissionsCompat.check(src, "invview.command.root", 2))
                    .build();

            LiteralCommandNode<ServerCommandSource> invNode = CommandManager
                    .literal("inv")
                    .requires(src -> PermissionsCompat.check(src, "invview.command.inv", 2))
                    .then(CommandManager.argument("target", StringArgumentType.word())
                            .suggests((context, builder) -> CommandSource.suggestMatching(context.getSource().getPlayerNames(), builder))
                            .executes(ViewCommand::inv))
                    .build();

            LiteralCommandNode<ServerCommandSource> echestNode = CommandManager
                    .literal("echest")
                    .requires(src -> PermissionsCompat.check(src, "invview.command.echest", 2))
                    .then(CommandManager.argument("target", StringArgumentType.word())
                            .suggests((context, builder) -> CommandSource.suggestMatching(context.getSource().getPlayerNames(), builder))
                            .executes(ViewCommand::eChest))
                    .build();

//            LiteralCommandNode<ServerCommandSource> trinketNode = CommandManager
//                    .literal("trinket")
//                    .requires(Permissions.require("invview.command.trinket", 2))
//                    .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
//                            .executes(ViewCommand::trinkets))
//                    .build();
//
//            LiteralCommandNode<ServerCommandSource> apoliNode = CommandManager
//                    .literal("origin-inv")
//                    .requires(Permissions.require("invview.command.origin", 2))
//                    .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
//                            .executes(ViewCommand::apoli))
//                    .build();

            dispatcher.getRoot().addChild(viewNode);
            viewNode.addChild(invNode);
            viewNode.addChild(echestNode);

            if (isTrinkets) {
//                viewNode.addChild(trinketNode);
            }
            if (isApoli) {
//                viewNode.addChild(apoliNode);
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(this::onLogicalServerStarting);
    }

    private void onLogicalServerStarting(MinecraftServer server) {
        minecraftServer = server;
    }

    public static MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }

    // Taken and adapted for 1.21.1 from net.minecraft.world.PlayerSaveHandler.savePlayerData()
    public static void savePlayerData(ServerPlayerEntity player) {
        File playerDataDir = minecraftServer.getSavePath(WorldSavePath.PLAYERDATA).toFile();
        try {
            NbtCompound nbt = new NbtCompound();
            // 1.21.1: write player data directly into NBT
            player.writeNbt(nbt);
            Path dir = playerDataDir.toPath();
            Path temp = Files.createTempFile(dir, player.getUuidAsString() + "-", ".dat");
            // 1.21.1: write compressed NBT to a path
            NbtIo.writeCompressed(nbt, temp);
            Path target = dir.resolve(player.getUuidAsString() + ".dat");
            Path backup = dir.resolve(player.getUuidAsString() + ".dat_old");
            Util.backupAndReplace(target, temp, backup);
        } catch (Exception e) {
            LogUtils.getLogger().warn("Failed to save player data for {}", player.getName().getString());
        }
    }
}
