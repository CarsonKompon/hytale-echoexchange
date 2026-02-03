package com.echoexchange.item;

import com.echoexchange.EchoExchangePlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ExchangeTabletManager {
    private static final String METADATA_KEY_STORED_ECHOES = "StoredEchoes";
    private static final String METADATA_KEY_CAPACITY_UPGRADE = "CapacityUpgrade";
    private static final String METADATA_KEY_EFFICIENCY_UPGRADE = "EfficiencyUpgrade";
    private static final String METADATA_KEY_SPEED_UPGRADE = "SpeedUpgrade";
    private static final String METADATA_KEY_UPGRADE_PROGRESS = "UpgradeSlotProgress";
    
    public static class TabletData {
        private long storedEchoes;
        private int capacityUpgrade;
        private int efficiencyUpgrade;
        private int speedUpgrade;
        private final Map<String, Integer> upgradeSlotProgress = new HashMap<>();
        
        public TabletData() {
            this.storedEchoes = 0;
            this.capacityUpgrade = 0;
            this.efficiencyUpgrade = 0;
            this.speedUpgrade = 0;
        }
        
        public TabletData(long storedEchoes, int capacityUpgrade, int efficiencyUpgrade, int speedUpgrade) {
            this.storedEchoes = storedEchoes;
            this.capacityUpgrade = capacityUpgrade;
            this.efficiencyUpgrade = efficiencyUpgrade;
            this.speedUpgrade = speedUpgrade;
        }
        
        public long getStoredEchoes() {
            return storedEchoes;
        }
        
        public void setStoredEchoes(long echoes) {
            this.storedEchoes = Math.max(0, echoes);
        }
        
        public void addEchoes(long amount) {
            this.storedEchoes += amount;
        }
        
        public boolean removeEchoes(long amount) {
            if (storedEchoes >= amount) {
                storedEchoes -= amount;
                return true;
            }
            return false;
        }
        
        public int getCapacityUpgrade() {
            return capacityUpgrade;
        }
        
        public void setCapacityUpgrade(int level) {
            this.capacityUpgrade = Math.max(0, level);
        }
        
        public int getEfficiencyUpgrade() {
            return efficiencyUpgrade;
        }
        
        public void setEfficiencyUpgrade(int level) {
            this.efficiencyUpgrade = Math.max(0, level);
        }
        
        public int getSpeedUpgrade() {
            return speedUpgrade;
        }
        
        public void setSpeedUpgrade(int level) {
            this.speedUpgrade = Math.max(0, level);
        }
        
        public long getMaxCapacity() {
            long baseCapacity = EchoExchangePlugin.getInstance().getModConfig().getBaseEchoStorage();
            double multiplier = EchoExchangePlugin.getInstance().getModConfig().getCapacityMultiplier();
            return (long) (baseCapacity * Math.pow(multiplier, getUpgradeLevel()));
        }
        
        public int getUpgradeSlotProgress(String itemId) {
            return upgradeSlotProgress.getOrDefault(itemId, 0);
        }
        
        public void addUpgradeSlotProgress(String itemId, int amount) {
            int current = getUpgradeSlotProgress(itemId);
            upgradeSlotProgress.put(itemId, current + amount);
        }
        
        public int getUpgradeLevel() {
            var config = EchoExchangePlugin.getInstance().getModConfig();
            var upgradeConfigs = config.getUpgradeSlots();
            int completed = 0;
            
            for (var slotConfig : upgradeConfigs) {
                int progress = getUpgradeSlotProgress(slotConfig.getItemId());
                if (progress >= slotConfig.getRequiredAmount()) {
                    completed++;
                }
            }
            
            return completed;
        }
    }
    
    @Nonnull
    public static ItemStack createTablet() {
        return createTablet(0, 0, 0, 0);
    }
    
    @Nonnull
    public static ItemStack createTablet(long storedEchoes, int capacityUpgrade, int efficiencyUpgrade, int speedUpgrade) {
        return createTablet(storedEchoes, capacityUpgrade, efficiencyUpgrade, speedUpgrade, new HashMap<>());
    }
    
    @Nonnull
    public static ItemStack createTablet(long storedEchoes, int capacityUpgrade, int efficiencyUpgrade, int speedUpgrade, Map<String, Integer> upgradeProgress) {
        BsonDocument metadata = new BsonDocument();
        metadata.put(METADATA_KEY_STORED_ECHOES, new BsonInt64(storedEchoes));
        metadata.put(METADATA_KEY_CAPACITY_UPGRADE, new BsonInt32(capacityUpgrade));
        metadata.put(METADATA_KEY_EFFICIENCY_UPGRADE, new BsonInt32(efficiencyUpgrade));
        metadata.put(METADATA_KEY_SPEED_UPGRADE, new BsonInt32(speedUpgrade));
        
        if (upgradeProgress != null && !upgradeProgress.isEmpty()) {
            BsonDocument progressDoc = new BsonDocument();
            for (Map.Entry<String, Integer> entry : upgradeProgress.entrySet()) {
                progressDoc.put(entry.getKey(), new BsonInt32(entry.getValue()));
            }
            metadata.put(METADATA_KEY_UPGRADE_PROGRESS, progressDoc);
        }
        
        return new ItemStack("ExchangeTablet", 1, metadata);
    }
    
    @Nullable
    public static TabletData getTabletData(@Nonnull ItemStack stack) {
        if (!stack.getItemId().equals("ExchangeTablet")) {
            return null;
        }
        
        Long storedEchoes = stack.getFromMetadataOrNull(METADATA_KEY_STORED_ECHOES, Codec.LONG);
        Integer capacityUpgrade = stack.getFromMetadataOrNull(METADATA_KEY_CAPACITY_UPGRADE, Codec.INTEGER);
        Integer efficiencyUpgrade = stack.getFromMetadataOrNull(METADATA_KEY_EFFICIENCY_UPGRADE, Codec.INTEGER);
        Integer speedUpgrade = stack.getFromMetadataOrNull(METADATA_KEY_SPEED_UPGRADE, Codec.INTEGER);
        
        TabletData data = new TabletData(
            storedEchoes != null ? storedEchoes : 0,
            capacityUpgrade != null ? capacityUpgrade : 0,
            efficiencyUpgrade != null ? efficiencyUpgrade : 0,
            speedUpgrade != null ? speedUpgrade : 0
        );
        
        BsonDocument metadata = stack.getMetadata();
        if (metadata != null && metadata.containsKey(METADATA_KEY_UPGRADE_PROGRESS)) {
            BsonDocument progressDoc = metadata.getDocument(METADATA_KEY_UPGRADE_PROGRESS);
            for (String key : progressDoc.keySet()) {
                data.upgradeSlotProgress.put(key, progressDoc.getInt32(key).getValue());
            }
        }
        
        return data;
    }
    
    @Nonnull
    public static ItemStack updateTabletData(@Nonnull ItemStack stack, @Nonnull TabletData data) {
        if (!stack.getItemId().equals("ExchangeTablet")) {
            return stack;
        }
        
        return createTablet(
            data.getStoredEchoes(),
            data.getCapacityUpgrade(),
            data.getEfficiencyUpgrade(),
            data.getSpeedUpgrade(),
            data.upgradeSlotProgress
        );
    }
}
