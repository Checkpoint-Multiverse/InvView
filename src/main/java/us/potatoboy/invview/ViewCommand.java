package us.potatoboy.invview;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.WorldSavePath;
import us.potatoboy.invview.gui.SavingPlayerDataGui;
import us.potatoboy.invview.gui.UnmodifiableSlot;
import us.potatoboy.invview.mixin.EntityAccessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ViewCommand {
    private static final MinecraftServer minecraftServer = InvView.getMinecraftServer();

    private static final String permProtected = "invview.protected";
    private static final String permModify = "invview.can_modify";
    private static final String msgProtected = "Requested inventory is protected";

    public static int inv(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        ServerPlayerEntity requestedPlayer = getRequestedPlayer(context);

        boolean canModify = Permissions.check(context.getSource(), permModify, true);

        Permissions.check(requestedPlayer.getUuid(), permProtected, false).thenAcceptAsync(isProtected -> {
            if (isProtected) {
                context.getSource().sendError(Text.literal(msgProtected));
            } else {
                SimpleGui gui = new SavingPlayerDataGui(ScreenHandlerType.GENERIC_9X5, player, requestedPlayer);
                gui.setTitle(requestedPlayer.getName());
                addBackground(gui);
                for (int i = 0; i < requestedPlayer.getInventory().size(); i++) {
                    gui.setSlotRedirect(i, canModify ? new Slot(requestedPlayer.getInventory(), i, 0, 0)
                            : new UnmodifiableSlot(requestedPlayer.getInventory(), i));
                }

                gui.open();
            }
        });

        return 1;
    }

    public static int eChest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        ServerPlayerEntity requestedPlayer = getRequestedPlayer(context);
        EnderChestInventory requestedEchest = requestedPlayer.getEnderChestInventory();

        boolean canModify = Permissions.check(context.getSource(), permModify, true);

        Permissions.check(requestedPlayer.getUuid(), permProtected, false).thenAcceptAsync(isProtected -> {
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

                gui.open();
            }
        });

        return 1;
    }

    private static ServerPlayerEntity getRequestedPlayer(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        MinecraftServer server = context.getSource().getServer();
        // Try to get an online player first
        ServerPlayerEntity requestedPlayer = server.getPlayerManager().getPlayer(targetName);

        // If player is not currently online, create a fake/offline profile so we can load/store data
        GameProfile profile = requestedPlayer != null ? requestedPlayer.getGameProfile()
                : new GameProfile(Uuids.getOfflinePlayerUuid(targetName), targetName);

        // If player is not currently online
        if (requestedPlayer == null) {
            requestedPlayer = new ServerPlayerEntity(server, server.getOverworld(), profile,
                    SyncedClientOptions.createDefault());

            // Attempt to read existing player data directly from disk (PLAYERDATA/uuid.dat)
            Optional<ReadView> readViewOpt = Optional.empty();
            try (ErrorReporter.Logging logging = new ErrorReporter.Logging(LogUtils.getLogger())) {
                Path playerDataDir = server.getSavePath(WorldSavePath.PLAYERDATA);
                Path playerDat = playerDataDir.resolve(requestedPlayer.getUuidAsString() + ".dat");
                if (Files.exists(playerDat)) {
                    NbtCompound nbt = NbtIo.readCompressed(playerDat, NbtSizeTracker.ofUnlimitedBytes());
                    ReadView view = NbtReadView.create(logging, server.getRegistryManager(), nbt);
                    readViewOpt = Optional.of(view);
                    requestedPlayer.readData(view);
                }
            } catch (Exception e) {
                LogUtils.getLogger().warn("Failed to load player data for {}", requestedPlayer.getName().getString());
            }

            // Avoids player's dimension being reset to the overworld
            if (readViewOpt.isPresent()) {
                ReadView readView = readViewOpt.get();
                Optional<String> dimension = readView.getOptionalString("Dimension");

                if (dimension.isPresent()) {
                    ServerWorld world = server.getWorld(
                            RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimension.get())));

                    if (world != null) {
                        ((EntityAccessor) requestedPlayer).callSetWorld(world);
                    }
                }
            }
        }

        return requestedPlayer;
    }

    private static void addBackground(SimpleGui gui) {
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.BARRIER).setName(Text.literal("")).build());
        }
    }
}
