package com.echoexchange.item;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.echoexchange.ui.ExchangeTabletPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExchangeTabletPageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    
    public static final BuilderCodec<ExchangeTabletPageSupplier> CODEC = 
        BuilderCodec.builder(ExchangeTabletPageSupplier.class, ExchangeTabletPageSupplier::new)
            .build();

    public ExchangeTabletPageSupplier() {
    }

    @Nullable
    @Override
    public CustomUIPage tryCreate(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return null;
        }

        ItemContext itemContext = context.createHeldItemContext();
        if (itemContext == null) {
            return null;
        }

        ItemStack tabletStack = itemContext.getItemStack();
        if (tabletStack == null || !tabletStack.getItemId().equals("ExchangeTablet")) {
            return null;
        }

        return new ExchangeTabletPage(playerRef, tabletStack);
    }
}
