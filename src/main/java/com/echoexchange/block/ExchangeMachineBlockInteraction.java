package com.echoexchange.block;

import com.echoexchange.EchoExchangePlugin;
import com.echoexchange.ui.ExchangeMachinePage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;

public class ExchangeMachineBlockInteraction {

    public static final String EXCHANGE_MACHINE_PAGE_ID = "echoexchange:ExchangeMachinePage";

    public static void register(EchoExchangePlugin plugin) {
        OpenCustomUIInteraction.registerBlockCustomPage(
            plugin,
            ExchangeMachineBlockInteraction.class,
            EXCHANGE_MACHINE_PAGE_ID,
            ItemContainerState.class,
            (playerRef, containerState) -> new ExchangeMachinePage(playerRef, containerState)
        );
    }
}
