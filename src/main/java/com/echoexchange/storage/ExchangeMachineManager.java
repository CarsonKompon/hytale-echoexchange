package com.echoexchange.storage;

import com.echoexchange.EchoExchangePlugin;
import com.echoexchange.config.EchoExchangeConfig;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ExchangeMachineManager {
    
    private static ExchangeMachineManager instance;
    
    private final Map<String, MachineData> machineDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerDiscoveries> playerDiscoveriesMap = new ConcurrentHashMap<>();
    private final AtomicBoolean isDirty = new AtomicBoolean(false);
    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor();
    
    private File dataFile;
    private File playerDiscoveriesFile;
    
    private ExchangeMachineManager() {
        saveExecutor.scheduleAtFixedRate(() -> {
            if (isDirty.getAndSet(false)) {
                saveData();
                savePlayerDiscoveries();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    public static ExchangeMachineManager getInstance() {
        if (instance == null) {
            instance = new ExchangeMachineManager();
        }
        return instance;
    }
    
    public void initialize(@Nonnull String universeName) {
        File universeFolder = new File(System.getenv("APPDATA"), "Hytale/universes/" + universeName);
        File echoExchangeFolder = new File(universeFolder, "EchoExchange");
        
        if (!echoExchangeFolder.exists()) {
            echoExchangeFolder.mkdirs();
        }
        
        this.dataFile = new File(echoExchangeFolder, "machines.json");
        this.playerDiscoveriesFile = new File(echoExchangeFolder, "player_discoveries.json");
        loadData();
        loadPlayerDiscoveries();
    }
    
    @Nonnull
    private String getMachineKey(@Nonnull String worldName, @Nonnull Vector3i position) {
        return worldName + ":" + position.x + "," + position.y + "," + position.z;
    }
    
    @Nonnull
    public MachineData getMachineData(@Nonnull String worldName, @Nonnull Vector3i position) {
        String key = getMachineKey(worldName, position);
        return machineDataMap.computeIfAbsent(key, k -> new MachineData());
    }
    
    public void removeMachineData(@Nonnull String worldName, @Nonnull Vector3i position) {
        String key = getMachineKey(worldName, position);
        machineDataMap.remove(key);
        isDirty.set(true);
    }
    
    public void markDirty() {
        isDirty.set(true);
    }
    
    @Nonnull
    public PlayerDiscoveries getPlayerDiscoveries(@Nonnull UUID playerUuid) {
        return playerDiscoveriesMap.computeIfAbsent(playerUuid, k -> new PlayerDiscoveries());
    }
    
    public void discoverItemForPlayer(@Nonnull UUID playerUuid, @Nonnull String itemId) {
        getPlayerDiscoveries(playerUuid).discoverItem(itemId);
        isDirty.set(true);
    }
    
    public boolean hasPlayerDiscovered(@Nonnull UUID playerUuid, @Nonnull String itemId) {
        return getPlayerDiscoveries(playerUuid).hasDiscovered(itemId);
    }
    
    @Nonnull
    public Set<String> getPlayerDiscoveredItems(@Nonnull UUID playerUuid) {
        return getPlayerDiscoveries(playerUuid).getDiscoveredItems();
    }
    
    @Nonnull
    public String getPlayerSearchQuery(@Nonnull UUID playerUuid) {
        return getPlayerDiscoveries(playerUuid).getSearchQuery();
    }
    
    public void setPlayerSearchQuery(@Nonnull UUID playerUuid, @Nonnull String query) {
        getPlayerDiscoveries(playerUuid).setSearchQuery(query);
        markDirty();
    }
    
    private void loadData() {
        if (!dataFile.exists()) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                .log("No existing machine data found, starting fresh");
            return;
        }
        
        try {
            MachineStorage storage = RawJsonReader.readSync(dataFile.toPath(), MachineStorage.CODEC, 
                EchoExchangePlugin.getInstance().getLogger());
            
            if (storage != null && storage.machines != null) {
                for (MachineEntry entry : storage.machines) {
                    machineDataMap.put(entry.key, entry.data);
                }
                
                EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                    .log("Loaded %d Exchange Machines from disk", machineDataMap.size());
            }
        } catch (java.io.IOException e) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.SEVERE)
                .log("Failed to load machine data: " + e.getMessage());
        }
    }
    
    private void saveData() {
        if (!dataFile.exists()) {
            try {
                Files.createDirectories(dataFile.toPath().getParent());
                dataFile.createNewFile();
            } catch (Exception e) {
                EchoExchangePlugin.getInstance().getLogger().at(Level.SEVERE)
                    .log("Failed to create machine data file: " + e.getMessage());
                return;
            }
        }
        
        try {
            List<MachineEntry> entries = new ArrayList<>();
            for (Map.Entry<String, MachineData> entry : machineDataMap.entrySet()) {
                entries.add(new MachineEntry(entry.getKey(), entry.getValue()));
            }
            
            MachineStorage storage = new MachineStorage();
            storage.machines = entries;
            BsonUtil.writeSync(dataFile.toPath(), MachineStorage.CODEC, storage, 
                EchoExchangePlugin.getInstance().getLogger());
            
            EchoExchangePlugin.getInstance().getLogger().at(Level.FINE)
                .log("Saved %d Exchange Machines to disk", entries.size());
        } catch (java.io.IOException e) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.SEVERE)
                .log("Failed to save machine data: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        saveExecutor.shutdown();
        try {
            saveExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        saveData();
        savePlayerDiscoveries();
    }
    
    public static class MachineData {
        private long storedEchoes = 0;
        private final Set<String> discoveredItems = new HashSet<>();
        private final Map<String, Integer> upgradeSlotProgress = new HashMap<>();
        
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
        
        public Set<String> getDiscoveredItems() {
            return discoveredItems;
        }
        
        public void discoverItem(String itemId) {
            discoveredItems.add(itemId);
        }
        
        public boolean hasDiscovered(String itemId) {
            return discoveredItems.contains(itemId);
        }
        
        public int getUpgradeSlotProgress(String itemId) {
            return upgradeSlotProgress.getOrDefault(itemId, 0);
        }
        
        public void setUpgradeSlotProgress(String itemId, int progress) {
            if (progress <= 0) {
                upgradeSlotProgress.remove(itemId);
            } else {
                upgradeSlotProgress.put(itemId, progress);
            }
        }
        
        public void addUpgradeSlotProgress(String itemId, int amount) {
            int current = getUpgradeSlotProgress(itemId);
            setUpgradeSlotProgress(itemId, current + amount);
        }
        
        public int getUpgradeLevel() {
            return getCompletedUpgradeCount();
        }
        
        private int getCompletedUpgradeCount() {
            EchoExchangeConfig config = EchoExchangePlugin.getInstance().getModConfig();
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
        
        @Nonnull
        public List<com.hypixel.hytale.server.core.inventory.ItemStack> getItemsToDrop() {
            List<com.hypixel.hytale.server.core.inventory.ItemStack> drops = new ArrayList<>();
            
            for (Map.Entry<String, Integer> entry : upgradeSlotProgress.entrySet()) {
                String itemId = entry.getKey();
                int amount = entry.getValue();
                
                while (amount > 0) {
                    int stackSize = Math.min(amount, 64);
                    drops.add(new com.hypixel.hytale.server.core.inventory.ItemStack(itemId, stackSize));
                    amount -= stackSize;
                }
            }
            
            return drops;
        }
        
        @Deprecated
        public void setUpgradeLevel(int level) {
        }
        
        public long getMaxCapacity() {
            EchoExchangeConfig config = EchoExchangePlugin.getInstance().getModConfig();
            double capacity = config.getBaseEchoStorage();
            
            // Apply exponential growth: base * multiplier^level
            int completedUpgrades = getCompletedUpgradeCount();
            for (int i = 0; i < completedUpgrades; i++) {
                capacity *= config.getCapacityMultiplier();
            }
            
            return (long) capacity;
        }
        
        private static class UpgradeSlotEntry {
            String itemId;
            int progress;
            
            public UpgradeSlotEntry() {
                this.itemId = "";
                this.progress = 0;
            }
            
            public UpgradeSlotEntry(String itemId, int progress) {
                this.itemId = itemId;
                this.progress = progress;
            }
            
            public static final BuilderCodec<UpgradeSlotEntry> CODEC = BuilderCodec.<UpgradeSlotEntry>builder(
                    UpgradeSlotEntry.class, UpgradeSlotEntry::new)
                    .addField(new KeyedCodec<>("ItemId", Codec.STRING),
                            (entry, value) -> entry.itemId = value,
                            entry -> entry.itemId)
                    .addField(new KeyedCodec<>("Progress", Codec.INTEGER),
                            (entry, value) -> entry.progress = value,
                            entry -> entry.progress)
                    .build();
        }
        
        public static final BuilderCodec<MachineData> CODEC = BuilderCodec.<MachineData>builder(
                MachineData.class, MachineData::new)
                .addField(new KeyedCodec<>("StoredEchoes", Codec.LONG),
                        (data, value) -> data.storedEchoes = value,
                        data -> data.storedEchoes)
                .addField(new KeyedCodec<>("DiscoveredItems", Codec.STRING_ARRAY),
                        (data, value) -> {
                            for (String item : value) {
                                data.discoveredItems.add(item);
                            }
                        },
                        data -> data.discoveredItems.toArray(new String[0]))
                .addField(new KeyedCodec<>("UpgradeSlots", new com.hypixel.hytale.codec.codecs.array.ArrayCodec<>(UpgradeSlotEntry.CODEC, UpgradeSlotEntry[]::new)),
                        (data, value) -> {
                            for (UpgradeSlotEntry entry : value) {
                                data.upgradeSlotProgress.put(entry.itemId, entry.progress);
                            }
                        },
                        data -> {
                            List<UpgradeSlotEntry> entries = new ArrayList<>();
                            for (Map.Entry<String, Integer> entry : data.upgradeSlotProgress.entrySet()) {
                                entries.add(new UpgradeSlotEntry(entry.getKey(), entry.getValue()));
                            }
                            return entries.toArray(new UpgradeSlotEntry[0]);
                        })
                .build();
    }
    
    private static class MachineEntry {
        private String key;
        private MachineData data;
        
        public MachineEntry() {
            this.key = "";
            this.data = new MachineData();
        }
        
        public MachineEntry(String key, MachineData data) {
            this.key = key;
            this.data = data;
        }
        
        public static final BuilderCodec<MachineEntry> CODEC = BuilderCodec.<MachineEntry>builder(
                MachineEntry.class, MachineEntry::new)
                .addField(new KeyedCodec<>("Key", Codec.STRING),
                        (entry, value) -> entry.key = value,
                        entry -> entry.key)
                .addField(new KeyedCodec<>("Data", MachineData.CODEC),
                        (entry, value) -> entry.data = value,
                        entry -> entry.data)
                .build();
    }
    
    private static class MachineStorage {
        private List<MachineEntry> machines = new ArrayList<>();
        
        public MachineStorage() {
        }
        
        public static final BuilderCodec<MachineStorage> CODEC = BuilderCodec.<MachineStorage>builder(
                MachineStorage.class, MachineStorage::new)
                .addField(new KeyedCodec<>("Machines", new com.hypixel.hytale.codec.codecs.array.ArrayCodec<>(MachineEntry.CODEC, MachineEntry[]::new)),
                        (storage, value) -> storage.machines = java.util.Arrays.asList(value),
                        storage -> storage.machines.toArray(new MachineEntry[0]))
                .build();
    }
    
    public static class PlayerDiscoveries {
        private final Set<String> discoveredItems = new HashSet<>();
        private String searchQuery = "";
        
        public PlayerDiscoveries() {
        }
        
        public Set<String> getDiscoveredItems() {
            return discoveredItems;
        }
        
        public void discoverItem(String itemId) {
            discoveredItems.add(itemId);
        }
        
        public boolean hasDiscovered(String itemId) {
            return discoveredItems.contains(itemId);
        }
        
        public String getSearchQuery() {
            return searchQuery;
        }
        
        public void setSearchQuery(String query) {
            this.searchQuery = query != null ? query : "";
        }
        
        public static final BuilderCodec<PlayerDiscoveries> CODEC = BuilderCodec.<PlayerDiscoveries>builder(
                PlayerDiscoveries.class, PlayerDiscoveries::new)
                .addField(new KeyedCodec<>("DiscoveredItems", new com.hypixel.hytale.codec.codecs.array.ArrayCodec<>(Codec.STRING, String[]::new)),
                        (discoveries, value) -> discoveries.discoveredItems.addAll(java.util.Arrays.asList(value)),
                        discoveries -> discoveries.discoveredItems.toArray(new String[0]))
                .addField(new KeyedCodec<>("SearchQuery", Codec.STRING),
                        (discoveries, value) -> discoveries.searchQuery = value != null ? value : "",
                        discoveries -> discoveries.searchQuery != null ? discoveries.searchQuery : "")
                .build();
    }
    
    private void loadPlayerDiscoveries() {
        if (!playerDiscoveriesFile.exists()) {
            return;
        }
        
        try {
            PlayerDiscoveriesStorage storage = RawJsonReader.readSync(playerDiscoveriesFile.toPath(), 
                PlayerDiscoveriesStorage.CODEC, EchoExchangePlugin.getInstance().getLogger());
            
            for (PlayerDiscoveriesEntry entry : storage.players) {
                playerDiscoveriesMap.put(entry.playerUuid, entry.discoveries);
            }
            
            EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                .log("Loaded discoveries for %d players from disk", storage.players.size());
        } catch (Exception e) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.SEVERE)
                .log("Failed to load player discoveries: " + e.getMessage());
        }
    }
    
    private void savePlayerDiscoveries() {
        try {
            List<PlayerDiscoveriesEntry> entries = new ArrayList<>();
            for (Map.Entry<UUID, PlayerDiscoveries> entry : playerDiscoveriesMap.entrySet()) {
                entries.add(new PlayerDiscoveriesEntry(entry.getKey(), entry.getValue()));
            }
            
            PlayerDiscoveriesStorage storage = new PlayerDiscoveriesStorage();
            storage.players = entries;
            BsonUtil.writeSync(playerDiscoveriesFile.toPath(), PlayerDiscoveriesStorage.CODEC, storage,
                EchoExchangePlugin.getInstance().getLogger());
            
            EchoExchangePlugin.getInstance().getLogger().at(Level.FINE)
                .log("Saved discoveries for %d players to disk", entries.size());
        } catch (java.io.IOException e) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.SEVERE)
                .log("Failed to save player discoveries: " + e.getMessage());
        }
    }
    
    private static class PlayerDiscoveriesEntry {
        private UUID playerUuid;
        private PlayerDiscoveries discoveries;
        
        public PlayerDiscoveriesEntry() {
            this.playerUuid = UUID.randomUUID();
            this.discoveries = new PlayerDiscoveries();
        }
        
        public PlayerDiscoveriesEntry(UUID playerUuid, PlayerDiscoveries discoveries) {
            this.playerUuid = playerUuid;
            this.discoveries = discoveries;
        }
        
        public static final BuilderCodec<PlayerDiscoveriesEntry> CODEC = BuilderCodec.<PlayerDiscoveriesEntry>builder(
                PlayerDiscoveriesEntry.class, PlayerDiscoveriesEntry::new)
                .addField(new KeyedCodec<>("PlayerUuid", Codec.STRING),
                        (entry, value) -> entry.playerUuid = UUID.fromString(value),
                        entry -> entry.playerUuid.toString())
                .addField(new KeyedCodec<>("Discoveries", PlayerDiscoveries.CODEC),
                        (entry, value) -> entry.discoveries = value,
                        entry -> entry.discoveries)
                .build();
    }
    
    private static class PlayerDiscoveriesStorage {
        private List<PlayerDiscoveriesEntry> players = new ArrayList<>();
        
        public PlayerDiscoveriesStorage() {
        }
        
        public static final BuilderCodec<PlayerDiscoveriesStorage> CODEC = BuilderCodec.<PlayerDiscoveriesStorage>builder(
                PlayerDiscoveriesStorage.class, PlayerDiscoveriesStorage::new)
                .addField(new KeyedCodec<>("Players", new com.hypixel.hytale.codec.codecs.array.ArrayCodec<>(PlayerDiscoveriesEntry.CODEC, PlayerDiscoveriesEntry[]::new)),
                        (storage, value) -> storage.players = java.util.Arrays.asList(value),
                        storage -> storage.players.toArray(new PlayerDiscoveriesEntry[0]))
                .build();
    }
}
