package com.echoexchange;

import com.echoexchange.block.ExchangeMachineBlockInteraction;
import com.echoexchange.block.ExchangeMachineDestroySystem;
import com.echoexchange.config.EchoExchangeConfig;
import com.echoexchange.echo.EchoValueCalculator;
import com.echoexchange.item.ExchangeTabletPageSupplier;
import com.echoexchange.storage.ExchangeMachineManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class EchoExchangePlugin extends JavaPlugin {

    private static EchoExchangePlugin instance;
    
    private final Config<EchoExchangeConfig> config;

    public EchoExchangePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = withConfig(EchoExchangeConfig.CODEC);
    }

    public static EchoExchangePlugin getInstance() {
        return instance;
    }
    
    public EchoExchangeConfig getModConfig() {
        return config.get();
    }

    @Override
    protected void setup() {
        config.save();
        
        ExchangeMachineManager.getInstance().initialize("default");
        ExchangeMachineBlockInteraction.register(this);
        OpenCustomUIInteraction.PAGE_CODEC.register("ExchangeTablet", ExchangeTabletPageSupplier.class, ExchangeTabletPageSupplier.CODEC);
        getEntityStoreRegistry().registerSystem(new ExchangeMachineDestroySystem());

        getLogger().at(Level.INFO).log("Echo Exchange Plugin initialized!");
        getLogger().at(Level.INFO).log("Base Echo storage: %d", getModConfig().getBaseEchoStorage());
        getLogger().at(Level.INFO).log("Capacity multiplier: %.2f", getModConfig().getCapacityMultiplier());
        
        EchoValueCalculator.getInstance().precalculateAll();
    }
}
