package us.potatoboy.invview;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.WorldSavePath;
import us.potatoboy.invview.gui.SaveSlot;
import us.potatoboy.invview.gui.SavingPlayerDataGui;
import us.potatoboy.invview.gui.UnmodifiableSlot;
import us.potatoboy.invview.mixin.EntityAccessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ViewCommand {
    public static final Map<UUID, UUID> OPEN_INVENTORIES = new HashMap<>();
    private static final Map<UUID, ServerPlayerEntity> OFFLINE_PLAYERS_CACHE = new HashMap<>();

    private static final MinecraftServer minecraftServer = InvView.getMinecraftServer();
    private static final String permProtected = "invview.protected";
    private static final String permModify = "invview.can_modify";
    private static final String msgProtected = "Requested inventory is protected";

    public static int inv(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        ServerPlayerEntity requestedPlayer = getRequestedPlayer(context);

        boolean canModify = PermissionsCompat.check(context.getSource(), permModify, 2);

        boolean isProtected = PermissionsCompat.check(requestedPlayer.getUuid(), permProtected, false);
        if (isProtected) {
            context.getSource().sendError(Text.literal(msgProtected));
        } else {
            SimpleGui gui = new SavingPlayerDataGui(ScreenHandlerType.GENERIC_9X5, player, requestedPlayer);
            gui.setTitle(requestedPlayer.getName());
            addBackground(gui);
            for (int i = 0; i < requestedPlayer.getInventory().size(); i++) {
                gui.setSlotRedirect(i, canModify ? new SaveSlot(requestedPlayer.getInventory(), i, requestedPlayer)
                        : new UnmodifiableSlot(requestedPlayer.getInventory(), i));
            }

            if (player != null) {
                OPEN_INVENTORIES.put(player.getUuid(), requestedPlayer.getUuid());
            }
            gui.open();
        }

        return 1;
    }

    public static int eChest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        ServerPlayerEntity requestedPlayer = getRequestedPlayer(context);
        EnderChestInventory requestedEchest = requestedPlayer.getEnderChestInventory();

        boolean canModify = PermissionsCompat.check(context.getSource(), permModify, 2);

        boolean isProtected = PermissionsCompat.check(requestedPlayer.getUuid(), permProtected, false);
        if (isProtected) {
            context.getSource().sendError(Text.literal(msgProtected));
        } else {
            ScreenHandlerType<?> screenHandlerType = switch (requestedEchest.size()) {
                case 9 -> ScreenHandlerType.GENERIC_9X1;
                case 18 -> ScreenHandlerType.GENERIC_9X2;
                case 36 -> ScreenHandlerType.GENERIC_9X4;
                case 45 -> ScreenHandlerType.GENERIC_9X5;
                case 54 -> ScreenHandlerType.GENERIC_9X6;
                default -> ScreenHandlerType.GENERIC_9X3;
            };
            SimpleGui gui = new SavingPlayerDataGui(screenHandlerType, player, requestedPlayer);
            gui.setTitle(requestedPlayer.getName());
            addBackground(gui);
            for (int i = 0; i < requestedEchest.size(); i++) {
                gui.setSlotRedirect(i,
                        canModify ? new Slot(requestedEchest, i, 0, 0) : new UnmodifiableSlot(requestedEchest, i));
            }

            if (player != null) {
                OPEN_INVENTORIES.put(player.getUuid(), requestedPlayer.getUuid());
            }
            gui.open();
        }

        return 1;
    }

    private static ServerPlayerEntity getRequestedPlayer(CommandContext<ServerCommandSource> context) {
        String targetName = StringArgumentType.getString(context, "target");
        MinecraftServer server = context.getSource().getServer();
        // Try to get an online player first
        ServerPlayerEntity requestedPlayer = server.getPlayerManager().getPlayer(targetName);

        // If player is not currently online, create a fake/offline profile so we can load/store data
        GameProfile profile = requestedPlayer != null ? requestedPlayer.getGameProfile()
                : new GameProfile(Uuids.getOfflinePlayerUuid(targetName), targetName);

        // If player is not currently online
        if (requestedPlayer == null) {
            // Use cached offline instance if available so multiple viewers share inventories
            ServerPlayerEntity cached = OFFLINE_PLAYERS_CACHE.get(profile.getId());
            if (cached != null) {
                return cached;
            }
            // In 1.21.1 the constructor doesn't require SyncedClientOptions
            requestedPlayer = new ServerPlayerEntity(server, server.getOverworld(), profile, null);

            // Attempt to read existing player data directly from disk (PLAYERDATA/uuid.dat)
            Optional<NbtCompound> nbtOpt = Optional.empty();
            try {
                Path playerDataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
                Path playerDat = playerDataDir.resolve(requestedPlayer.getUuidAsString() + ".dat");
                if (Files.exists(playerDat)) {
                    // 1.20.1: read NBT directly and load into the player
                    NbtSizeTracker nbtSizeTracker = new NbtSizeTracker(Long.MAX_VALUE, Integer.MAX_VALUE);
                    NbtCompound nbt = NbtIo.readCompressed(playerDat, nbtSizeTracker);
                    nbtOpt = Optional.of(nbt);
                    requestedPlayer.readNbt(nbt);
                }
            } catch (Exception e) {
                LogUtils.getLogger().warn("Failed to load player data for {}", requestedPlayer.getGameProfile().getName());
            }

            // Avoids player's dimension being reset to the overworld
            if (nbtOpt.isPresent()) {
                NbtCompound nbt = nbtOpt.get();
                if (nbt.contains("Dimension")) {
                    String dimension = nbt.getString("Dimension");
                    ServerWorld world = server.getWorld(
                            RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimension)));
                    if (world != null) {
                        ((EntityAccessor) requestedPlayer).callSetWorld(world);
                    }
                }
            }

            // Cache new offline player instance for real-time syncing across viewers
            OFFLINE_PLAYERS_CACHE.put(requestedPlayer.getUuid(), requestedPlayer);
        }

        return requestedPlayer;
    }

// My method of getting requested player

//    private static ServerPlayerEntity getRequestedPlayer(CommandContext<ServerCommandSource> context)
//            throws CommandSyntaxException {
//        //GameProfile requestedProfile = GameProfileArgumentType.getProfileArgument(context, "target").iterator().next();
//        //ServerPlayerEntity requestedPlayer = minecraftServer.getPlayerManager().getPlayer(requestedProfile.getName());
//
//        MinecraftServer server = context.getSource().getServer();
//        String name = StringArgumentType.getString(context, "target");
//        ServerPlayerEntity requestedPlayer = server.getPlayerManager().getPlayer(name);
//
//        if (requestedPlayer == null) {
//
//            //requestedPlayer = minecraftServer.getPlayerManager().createPlayer(requestedProfile);
//            //NbtCompound compound = minecraftServer.getPlayerManager().loadPlayerData(requestedPlayer);
//
//            GameProfile profile = server.getUserCache()
//                    .findByName(name)
//                    .orElseGet(() -> new GameProfile(Uuids.getOfflinePlayerUuid(name), name));
//
//            requestedPlayer = server.getPlayerManager().createPlayer(profile);
//            NbtCompound compound = server.getPlayerManager().loadPlayerData(requestedPlayer);
//
//            if (compound != null) {
//                ServerWorld world = minecraftServer.getWorld(
//                        DimensionType.worldFromDimensionNbt(new Dynamic<>(NbtOps.INSTANCE, compound.get("Dimension")))
//                                .result().get());
//
//                if (world != null) {
//                    ((EntityAccessor) requestedPlayer).callSetWorld(world);
//                }
//            }
//        }
//
//        return requestedPlayer;
//    }

    private static void addBackground(SimpleGui gui) {
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.BARRIER).setName(Text.literal("")).build());
        }
    }

    public static void onGuiClosed(UUID viewerUuid) {
        UUID targetUuid = OPEN_INVENTORIES.remove(viewerUuid);
        if (targetUuid == null) {
            return;
        }
        // If no other viewers for this target remain, save and evict cached offline player
        boolean othersViewing = OPEN_INVENTORIES.values().stream().anyMatch(uuid -> uuid.equals(targetUuid));
        if (!othersViewing) {
            ServerPlayerEntity offline = OFFLINE_PLAYERS_CACHE.remove(targetUuid);
            if (offline != null) {
                InvView.savePlayerData(offline);
            }
        }
    }
}
