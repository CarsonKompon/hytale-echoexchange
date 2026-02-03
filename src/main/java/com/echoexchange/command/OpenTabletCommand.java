package com.echoexchange.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.echoexchange.ui.ExchangeTabletPage;
import javax.annotation.Nonnull;

public class OpenTabletCommand extends AbstractPlayerCommand {

    public OpenTabletCommand() {
        super("opentablet", "Opens the Exchange Tablet if you're holding one");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        ItemStack tabletStack = null;
        for (short slot = 0; slot < player.getInventory().getHotbar().getCapacity(); slot++) {
            ItemStack stack = player.getInventory().getHotbar().getItemStack(slot);
            if (stack != null && stack.getItemId().equals("ExchangeTablet")) {
                tabletStack = stack;
                break;
            }
        }
        
        if (tabletStack == null) {
            return;
        }
        
        player.getPageManager().openCustomPage(ref, store, new ExchangeTabletPage(playerRef, tabletStack));
    }
}
