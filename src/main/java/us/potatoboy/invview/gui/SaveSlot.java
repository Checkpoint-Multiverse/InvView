package us.potatoboy.invview.gui;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import us.potatoboy.invview.InvView;

public class SaveSlot extends Slot {
    private final ServerPlayerEntity player;

    public SaveSlot(Inventory inventory, int index, ServerPlayerEntity player) {
        super(inventory, index, 0, 0);
        this.player = player;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        InvView.savePlayerData(player);
    }
}