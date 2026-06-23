package us.potatoboy.invview.gui;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import us.potatoboy.invview.InvView;

public class SaveSlot extends Slot {
    private final ServerPlayerEntity player;
    private final boolean editMode;

    public SaveSlot(Inventory inventory, int index, ServerPlayerEntity player, boolean editMode) {
        super(inventory, index, 0, 0);
        this.player = player;
        this.editMode = editMode;
    }

    // block player from taking items if not in edit mode so the gui will work like creative inventory
    @Override
    public ItemStack getStack() {
        ItemStack stack = super.getStack();
        if (!editMode) {
            return stack.copy();
        }
        return stack;
    }

    @Override
    public void setStack(ItemStack stack) {
        if (!editMode) {
            return;
        }
        super.setStack(stack);
    }

    @Override
    public ItemStack takeStack(int amount) {
        if (!editMode) {
            ItemStack stack = super.getStack().copy();
            stack.setCount(Math.min(amount, stack.getCount()));
            return stack;
        }
        return super.takeStack(amount);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        if (!editMode) {
            return false;
        }
        return super.canInsert(stack);
    }



    // if edit mod is on don't try to save it
    @Override
    public void markDirty() {
        if (!editMode) {
            return;
        }
        super.markDirty();
        InvView.savePlayerData(player);
    }
}