package us.potatoboy.invview.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import us.potatoboy.invview.InvView;
import us.potatoboy.invview.ViewCommand;

public class SavingPlayerDataGui extends SimpleGui {
    private final ServerPlayerEntity savedPlayer;
    private final boolean editMode;

    /**
     * Constructs a new simple container gui for the supplied player.
     *
     * @param type   the screen handler that the client should display
     * @param player the player to server this gui to
     */
    public SavingPlayerDataGui(ScreenHandlerType<?> type, ServerPlayerEntity player, ServerPlayerEntity savedPlayer,boolean editMode) {
        super(type, player, false);
        this.savedPlayer = savedPlayer;
        this.editMode = editMode;
    }

    // shift click makes the inventory full of same item it stucks in the loop so we have to disable
    @Override
    public ItemStack quickMove(int index) {
        if (!editMode) {
            this.getPlayer().currentScreenHandler.onSlotClick(index, 0, SlotActionType.PICKUP, this.getPlayer());
            return ItemStack.EMPTY;
        }
        return super.quickMove(index);
    }

    @Override
    public void onClose() {
        InvView.savePlayerData(savedPlayer);
        // Notify ViewCommand to cleanup viewer tracking/cache
        ViewCommand.onGuiClosed(this.getPlayer().getUuid());
    }
}
