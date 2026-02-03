package com.echoexchange.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EchoExchangeConfig {

    public static final BuilderCodec<EchoExchangeConfig> CODEC = BuilderCodec.<EchoExchangeConfig>builder(
            EchoExchangeConfig.class, EchoExchangeConfig::new)
            .addField(new KeyedCodec<>("BaseEchoStorage", Codec.INTEGER),
                    (config, value) -> config.baseEchoStorage = value,
                    config -> config.baseEchoStorage)
            .addField(new KeyedCodec<>("CapacityMultiplier", Codec.DOUBLE),
                    (config, value) -> config.capacityMultiplier = value,
                    config -> config.capacityMultiplier)
            .addField(new KeyedCodec<>("DiscoveryMode", Codec.STRING),
                    (config, value) -> config.discoveryMode = DiscoveryMode.fromString(value),
                    config -> config.discoveryMode.name())
            .addField(new KeyedCodec<>("UpgradeSlots", new ArrayCodec<>(UpgradeSlotConfig.CODEC, UpgradeSlotConfig[]::new)),
                    (config, value) -> {
                        config.upgradeSlots.clear();
                        for (UpgradeSlotConfig slot : value) {
                            config.upgradeSlots.add(slot);
                        }
                    },
                    config -> config.upgradeSlots.toArray(new UpgradeSlotConfig[0]))
            .build();

    private int baseEchoStorage = 10000;
    private double capacityMultiplier = 2.0;
    private Map<String, Integer> echoValueOverrides = new HashMap<>();
    private DiscoveryMode discoveryMode = DiscoveryMode.PerPlayer;
    private List<UpgradeSlotConfig> upgradeSlots = new ArrayList<>();

    public EchoExchangeConfig() {
        
        // Initialize upgrade slots matching SimpleStorage's progression
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Stick", 100));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Fibre", 100));
        upgradeSlots.add(new UpgradeSlotConfig("Rubble_Stone", 100));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Copper", 25));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Iron", 25));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Gold", 25));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Thorium", 15));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Cobalt", 15));
        upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Bar_Adamantite", 15));
        // upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Life_Essence", 100));
        // upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Void_Essence", 50));
        // upgradeSlots.add(new UpgradeSlotConfig("Ingredient_Fire_Essence", 25));
    }

    public int getBaseEchoStorage() {
        return baseEchoStorage;
    }

    public double getCapacityMultiplier() {
        return capacityMultiplier;
    }

    @Nonnull
    public Map<String, Integer> getEchoValueOverrides() {
        return echoValueOverrides;
    }

    @Nonnull
    public DiscoveryMode getDiscoveryMode() {
        return discoveryMode;
    }

    @Nonnull
    public List<UpgradeSlotConfig> getUpgradeSlots() {
        return upgradeSlots;
    }

    public static class UpgradeSlotConfig {
        public static final BuilderCodec<UpgradeSlotConfig> CODEC = BuilderCodec.<UpgradeSlotConfig>builder(
                UpgradeSlotConfig.class, UpgradeSlotConfig::new)
                .addField(new KeyedCodec<>("ItemId", Codec.STRING),
                        (config, value) -> config.itemId = value,
                        config -> config.itemId)
                .addField(new KeyedCodec<>("RequiredAmount", Codec.INTEGER),
                        (config, value) -> config.requiredAmount = value,
                        config -> config.requiredAmount)
                .build();

        private String itemId = "";
        private int requiredAmount = 0;

        public UpgradeSlotConfig() {}

        public UpgradeSlotConfig(String itemId, int requiredAmount) {
            this.itemId = itemId;
            this.requiredAmount = requiredAmount;
        }

        @Nonnull
        public String getItemId() {
            return itemId;
        }

        public int getRequiredAmount() {
            return requiredAmount;
        }
    }

    public enum DiscoveryMode {
        PerPlayer,
        Global,
        PerMachine;

        public static DiscoveryMode fromString(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return PerPlayer; // Default fallback
            }
        }
    }
}
