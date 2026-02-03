package com.echoexchange.ui;

import com.echoexchange.EchoExchangePlugin;
import com.echoexchange.echo.EchoValueCalculator;
import com.echoexchange.item.ExchangeTabletManager;
import com.echoexchange.storage.ExchangeMachineManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class ExchangeTabletPage extends InteractiveCustomUIPage<ExchangeTabletPage.TabletPageData> {
    
    private ItemStack tabletStack;
    private short tabletSlot;
    private ItemContainer tabletContainer;
    private String searchText = "";

    public ExchangeTabletPage(@Nonnull PlayerRef playerRef, @Nonnull ItemStack tabletStack) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, TabletPageData.CODEC);
        this.tabletStack = tabletStack;
        this.tabletSlot = -1;
        this.tabletContainer = null;
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, TabletPageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        Inventory inventory = player.getInventory();
        
        // Find the tablet in the player's inventory
        findTablet(inventory);
        if (tabletContainer == null || tabletSlot == -1) {
            playerRef.sendMessage(Message.raw("Tablet not found in inventory!"));
            return;
        }
        
        ExchangeTabletManager.TabletData tabletData = ExchangeTabletManager.getTabletData(tabletStack);
        if (tabletData == null) {
            playerRef.sendMessage(Message.raw("Invalid tablet data!"));
            return;
        }
        
        if ((data.action == null || data.action.isEmpty()) && data.searchText != null) {
            searchText = data.searchText.toLowerCase();
            
            ExchangeMachineManager.getInstance().setPlayerSearchQuery(player.getUuid(), searchText);
            
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildDiscoveredItemsGrid(commands, events, player, tabletData);
            sendUpdate(commands, events, false);
            return;
        }
        
        String action = data.action;
        if (action == null || action.isEmpty()) return;
        
        // Handle actions
        if (action.startsWith("burn_inventory:")) {
            int slot = Integer.parseInt(action.substring("burn_inventory:".length()));
            ItemStack stack = inventory.getStorage().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, tabletData, inventory.getStorage(), (short) slot, stack.getItemId(), stack.getQuantity());
            }
        } else if (action.startsWith("burn_hotbar:")) {
            int slot = Integer.parseInt(action.substring("burn_hotbar:".length()));
            ItemStack stack = inventory.getHotbar().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, tabletData, inventory.getHotbar(), (short) slot, stack.getItemId(), stack.getQuantity());
            }
        } else if (action.startsWith("burn_one_inventory:")) {
            int slot = Integer.parseInt(action.substring("burn_one_inventory:".length()));
            ItemStack stack = inventory.getStorage().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, tabletData, inventory.getStorage(), (short) slot, stack.getItemId(), 1);
            }
        } else if (action.startsWith("burn_one_hotbar:")) {
            int slot = Integer.parseInt(action.substring("burn_one_hotbar:".length()));
            ItemStack stack = inventory.getHotbar().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, tabletData, inventory.getHotbar(), (short) slot, stack.getItemId(), 1);
            }
        } else if (action.startsWith("transmute_max:")) {
            String itemId = action.substring("transmute_max:".length());
            // Get the item's max stack size
            Item item = Item.getAssetMap().getAsset(itemId);
            int maxStack = item != null ? item.getMaxStack() : 64;
            
            // Calculate how many we can afford up to one full stack
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            int affordableQuantity = echoValue > 0 ? (int) (tabletData.getStoredEchoes() / echoValue) : 0;
            int quantity = Math.min(maxStack, affordableQuantity);
            
            if (quantity > 0) {
                handleTransmuteItem(player, inventory, tabletData, itemId, quantity);
            }
        } else if (action.startsWith("transmute_one:")) {
            String itemId = action.substring("transmute_one:".length());
            handleTransmuteItem(player, inventory, tabletData, itemId, 1);
        } else if (action.startsWith("upgrade_click:")) {
            int slotIndex = Integer.parseInt(action.substring("upgrade_click:".length()));
            handleUpgradeClick(inventory, tabletData, slotIndex);
        }
        
        // Save tablet data back to item by replacing it
        ItemStack updatedTablet = ExchangeTabletManager.updateTabletData(tabletStack, tabletData);
        tabletContainer.removeItemStackFromSlot(tabletSlot, 1);
        tabletContainer.addItemStack(updatedTablet);
        tabletStack = updatedTablet;
        
        // Rebuild UI
        rebuild();
    }
    
    private void findTablet(Inventory inventory) {
        for (short i = 0; i < inventory.getHotbar().getCapacity(); i++) {
            ItemStack stack = inventory.getHotbar().getItemStack(i);
            if (stack != null && stack.getItemId().equals("ExchangeTablet")) {
                tabletSlot = i;
                tabletContainer = inventory.getHotbar();
                tabletStack = stack;
                return;
            }
        }
        
        // Check storage
        for (short i = 0; i < inventory.getStorage().getCapacity(); i++) {
            ItemStack stack = inventory.getStorage().getItemStack(i);
            if (stack != null && stack.getItemId().equals("ExchangeTablet")) {
                tabletSlot = i;
                tabletContainer = inventory.getStorage();
                tabletStack = stack;
                return;
            }
        }
    }
    
    private void handleBurnItem(@Nonnull Player player, @Nonnull Inventory inventory, 
                                @Nonnull ExchangeTabletManager.TabletData tabletData,
                                @Nonnull ItemContainer container, short slot, 
                                @Nonnull String itemId, int quantity) {
        if (itemId.equals("ExchangeTablet")) {
            playerRef.sendMessage(Message.raw("Cannot burn the Exchange Tablet!"));
            return;
        }
        
        ItemStack stack = container.getItemStack(slot);
        if (stack == null || !itemId.equals(stack.getItemId())) {
            return;
        }
        
        int available = stack.getQuantity();
        if (quantity > available) {
            quantity = available;
        }
        
        if (quantity <= 0) return;
        
        // Calculate Echo value
        long totalEchoes;
        if (itemId.equals("EchoScroll")) {
            Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(stack);
            if (scrollValue != null && scrollValue > 0) {
                totalEchoes = scrollValue * quantity;
            } else {
                totalEchoes = 1;
            }
        } else {
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            totalEchoes = (long) echoValue * quantity;
        }
        
        // Check capacity
        long currentEchoes = tabletData.getStoredEchoes();
        long maxCapacity = tabletData.getMaxCapacity();
        if (currentEchoes + totalEchoes > maxCapacity) {
            if (itemId.equals("EchoScroll")) {
                return;
            }
            
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            long availableSpace = maxCapacity - currentEchoes;
            if (availableSpace <= 0) {
                playerRef.sendMessage(Message.raw("Tablet is full!"));
                return;
            }
            
            quantity = (int) (availableSpace / echoValue);
            totalEchoes = (long) echoValue * quantity;
        }
        
        // Remove items
        container.removeItemStackFromSlot(slot, quantity);
        
        // Add Echoes
        tabletData.addEchoes(totalEchoes);
        
        // Discover item
        if (!itemId.equals("EchoScroll")) {
            ExchangeMachineManager.getInstance().discoverItemForPlayer(player.getUuid(), itemId);
        }
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Player burned %d x %s for %d Echoes in tablet", quantity, itemId, totalEchoes);
    }
    
    private void handleTransmuteItem(@Nonnull Player player, @Nonnull Inventory inventory, 
                                     @Nonnull ExchangeTabletManager.TabletData tabletData,
                                     @Nonnull String itemId, int quantity) {
        if (!ExchangeMachineManager.getInstance().hasPlayerDiscovered(player.getUuid(), itemId)) {
            playerRef.sendMessage(Message.raw("Item not discovered yet!"));
            return;
        }
        
        int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
        long totalCost = (long) echoValue * quantity;
        
        if (tabletData.getStoredEchoes() < totalCost) {
            quantity = (int) (tabletData.getStoredEchoes() / echoValue);
            totalCost = (long) echoValue * quantity;
            
            if (quantity <= 0) {
                playerRef.sendMessage(Message.raw("Not enough Echoes!"));
                return;
            }
        }
        
        ItemStack newStack = new ItemStack(itemId, quantity);
        ItemContainer combined = inventory.getCombinedHotbarFirst();
        var transaction = combined.addItemStack(newStack);
        ItemStack remainder = transaction.getRemainder();
        
        if (remainder != null && remainder.getQuantity() > 0) {
            int actualQuantity = quantity - remainder.getQuantity();
            totalCost = (long) echoValue * actualQuantity;
            
            if (actualQuantity <= 0) {
                playerRef.sendMessage(Message.raw("Inventory full!"));
                return;
            }
        }
        
        tabletData.removeEchoes(totalCost);
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Player transmuted %d x %s for %d Echoes from tablet", quantity, itemId, totalCost);
    }
    
    private void handleUpgradeClick(@Nonnull Inventory playerInventory, 
                                   @Nonnull ExchangeTabletManager.TabletData tabletData, 
                                   int slotIndex) {
        var config = EchoExchangePlugin.getInstance().getModConfig();
        var upgradeConfigs = config.getUpgradeSlots();
        
        if (slotIndex < 0 || slotIndex >= upgradeConfigs.size()) {
            return;
        }
        
        var slotConfig = upgradeConfigs.get(slotIndex);
        int requiredAmount = slotConfig.getRequiredAmount();
        String itemId = slotConfig.getItemId();
        
        // Get current progress
        int currentProgress = tabletData.getUpgradeSlotProgress(itemId);
        int stillNeeded = requiredAmount - currentProgress;
        
        if (stillNeeded <= 0) {
            // Already complete
            return;
        }
        
        // Count how many items the player has
        int available = countPlayerItems(playerInventory, itemId);
        
        if (available <= 0) {
            // No items to add
            return;
        }
        
        // Take as many as possible (up to what's still needed)
        int toTake = Math.min(available, stillNeeded);
        
        // Remove items from player inventory
        int remaining = toTake;
        
        // Remove from storage first
        ItemContainer storage = playerInventory.getStorage();
        for (short slot = 0; slot < storage.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = storage.getItemStack(slot);
            if (stack != null && stack.getItemId().equals(itemId)) {
                int toRemove = Math.min(stack.getQuantity(), remaining);
                storage.removeItemStackFromSlot(slot, toRemove);
                remaining -= toRemove;
            }
        }
        
        // Remove from hotbar (except the tablet itself)
        ItemContainer hotbar = playerInventory.getHotbar();
        for (short slot = 0; slot < hotbar.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack != null && stack.getItemId().equals(itemId)) {
                int toRemove = Math.min(stack.getQuantity(), remaining);
                hotbar.removeItemStackFromSlot(slot, toRemove);
                remaining -= toRemove;
            }
        }
        
        // Add taken items to upgrade slot progress
        if (toTake > remaining) {
            int actuallyTaken = toTake - remaining;
            tabletData.addUpgradeSlotProgress(itemId, actuallyTaken);
            
            // Update tablet item with new data
            updateTabletInInventory(playerInventory, tabletData);
            
            EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                .log("Tablet upgrade progress: %s -> %d/%d", itemId, 
                     tabletData.getUpgradeSlotProgress(itemId), requiredAmount);
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                     @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        Inventory inventory = player.getInventory();
        
        // Find the tablet
        findTablet(inventory);
        if (tabletContainer == null || tabletSlot == -1) {
            commands.append("Pages/ExchangeMachine.ui");
            return;
        }
        
        // Get tablet data
        ExchangeTabletManager.TabletData tabletData = ExchangeTabletManager.getTabletData(tabletStack);
        if (tabletData == null) {
            tabletData = new ExchangeTabletManager.TabletData();
        }
        
        // Load saved search query
        if (searchText.isEmpty()) {
            searchText = ExchangeMachineManager.getInstance().getPlayerSearchQuery(player.getUuid());
        }
        
        // Load UI
        commands.append("Pages/ExchangeMachine.ui");
        
        // Update Echo display
        long currentEchoes = tabletData.getStoredEchoes();
        long maxCapacity = tabletData.getMaxCapacity();
        double progressPercent = maxCapacity > 0 ? ((double) currentEchoes / maxCapacity) : 0.0;
        
        commands.set("#EchoBalanceLabel.Text", "Echoes: " + formatNumberWithCommas(currentEchoes) + " / " + formatNumberWithCommas(maxCapacity));
        commands.set("#EchoProgressBar.Value", progressPercent);
        
        // Build discovered items
        buildDiscoveredItems(commands, events, player, tabletData);
        
        // Build player inventory for burning items
        buildPlayerInventory(commands, events, inventory, tabletData);
        
        // Build info panel with upgrades
        buildInfoPanel(commands, events, inventory, tabletData);
    }
    
    private void buildDiscoveredItems(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                      @Nonnull Player player, @Nonnull ExchangeTabletManager.TabletData tabletData) {
        // Set up search field
        commands.set("#SearchInput.Value", searchText);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchText", "#SearchInput.Value"),
                false
        );
        
        // Build the grid content
        buildDiscoveredItemsGrid(commands, events, player, tabletData);
    }
    
    private void buildDiscoveredItemsGrid(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                          @Nonnull Player player, @Nonnull ExchangeTabletManager.TabletData tabletData) {
        commands.clear("#DiscoveredItemsGrid");
        
        java.util.Set<String> discoveredItems = ExchangeMachineManager.getInstance().getPlayerDiscoveredItems(player.getUuid());
        if (discoveredItems.isEmpty()) {
            return;
        }
        
        long currentEchoes = tabletData.getStoredEchoes();
        
        // Filter and sort
        java.util.List<String> sortedItems = new java.util.ArrayList<>(discoveredItems);
        
        if (!searchText.isEmpty()) {
            sortedItems.removeIf(itemId -> {
                String itemName = getItemName(itemId).toLowerCase();
                return !itemName.contains(searchText) && !itemId.toLowerCase().contains(searchText);
            });
        }
        
        sortedItems.sort((item1, item2) -> {
            int value1 = EchoValueCalculator.getInstance().getEchoValue(item1);
            int value2 = EchoValueCalculator.getInstance().getEchoValue(item2);
            boolean canAfford1 = value1 <= currentEchoes;
            boolean canAfford2 = value2 <= currentEchoes;
            
            if (canAfford1 != canAfford2) {
                return canAfford1 ? -1 : 1;
            }
            
            return Integer.compare(value2, value1);
        });
        
        // Build item slots
        int slotIndex = 0;
        for (String itemId : sortedItems) {
            String selector = "#DiscoveredItemsGrid[" + slotIndex + "]";
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            boolean canAfford = echoValue <= currentEchoes;
            
            commands.append("#DiscoveredItemsGrid", "Pages/SimpleStorageSlot.ui");
            
            commands.set(selector + " #SlotItem.ItemId", itemId);
            
            if (canAfford && echoValue > 0) {
                int maxQuantity = (int) (currentEchoes / echoValue);
                if (maxQuantity > 0) {
                    commands.set(selector + " #QuantityLabel.Text", abbreviateNumber(maxQuantity));
                    commands.set(selector + " #QuantityLabel.Visible", true);
                } else {
                    commands.set(selector + " #QuantityLabel.Visible", false);
                }
            } else {
                commands.set(selector + " #QuantityLabel.Visible", false);
            }
            
            commands.set(selector + " #EchoesLabel.Visible", true);
            commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
            commands.set(selector + " #EchoesLabel.Style.TextColor", canAfford ? "#a78bfa" : "#9ca3af");
            
            commands.set(selector + " #UnaffordableOverlay.Visible", !canAfford);
            commands.set(selector + " #HotbarNumberBg.Visible", false);
            
            commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(itemId, echoValue, 1));
            
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "transmute_max:" + itemId),
                    false
            );
            
            events.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of("Action", "transmute_one:" + itemId),
                    false
            );
            
            slotIndex++;
        }
        
        // Fill remainder
        int remainder = slotIndex % 9;
        if (remainder != 0 && slotIndex > 0) {
            int emptySlots = 9 - remainder;
            for (int i = 0; i < emptySlots; i++) {
                commands.append("#DiscoveredItemsGrid", "Pages/SimpleStorageSlot.ui");
                String emptySelector = "#DiscoveredItemsGrid[" + slotIndex + "]";
                commands.set(emptySelector + " #SlotItem.ItemId", "");
                commands.set(emptySelector + " #QuantityLabel.Visible", false);
                commands.set(emptySelector + " #HotbarNumberBg.Visible", false);
                slotIndex++;
            }
        }
    }
    
    private void buildPlayerInventory(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                     @Nonnull Inventory playerInventory, @Nonnull ExchangeTabletManager.TabletData tabletData) {
        ItemContainer invStorage = playerInventory.getStorage();
        ItemContainer hotbar = playerInventory.getHotbar();
        
        commands.clear("#PlayerInventory");
        
        long currentEchoes = tabletData.getStoredEchoes();
        long maxCapacity = tabletData.getMaxCapacity();
        long remainingCapacity = maxCapacity - currentEchoes;
        
        int playerSlotIndex = 0;
        
        // First add storage slots (27 slots = 3 rows of 9)
        for (short slot = 0; slot < invStorage.getCapacity(); slot++) {
            ItemStack item = invStorage.getItemStack(slot);
            String selector = "#PlayerInventory[" + playerSlotIndex + "]";
            
            commands.append("#PlayerInventory", "Pages/SimpleStorageSlot.ui");
            
            // Don't allow burning the tablet itself
            if (item != null && item.getItemId().equals("ExchangeTablet")) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + " #UnaffordableOverlay.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.raw("Exchange Tablet (Cannot burn)"));
                playerSlotIndex++;
                continue;
            }
            
            if (item != null) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                
                // Show Echo value
                int echoValue;
                if (item.getItemId().equals("EchoScroll")) {
                    Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(item);
                    echoValue = scrollValue != null ? scrollValue.intValue() : 1;
                } else {
                    echoValue = EchoValueCalculator.getInstance().getEchoValue(item.getItemId());
                }
                commands.set(selector + " #EchoesLabel.Visible", true);
                commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
                
                boolean canDeposit = echoValue <= remainingCapacity;
                commands.set(selector + " #UnaffordableOverlay.Visible", !canDeposit);
                commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(item.getItemId(), echoValue, item.getQuantity()));
            } else {
                commands.set(selector + " #SlotItem.ItemId", "");
                commands.set(selector + " #QuantityLabel.Visible", false);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.empty());
            }
            
            // Add click handlers
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "burn_inventory:" + slot),
                    false
            );
            
            events.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of("Action", "burn_one_inventory:" + slot),
                    false
            );
            
            playerSlotIndex++;
        }
        
        // Then add hotbar slots (9 slots = 1 row)
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack item = hotbar.getItemStack(slot);
            String selector = "#PlayerInventory[" + playerSlotIndex + "]";
            
            commands.append("#PlayerInventory", "Pages/SimpleStorageSlot.ui");
            
            // Don't allow burning the tablet itself
            if (item != null && item.getItemId().equals("ExchangeTablet")) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + " #UnaffordableOverlay.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.raw("Exchange Tablet (Cannot burn)"));
                
                String hotbarNumber = String.valueOf(slot + 1);
                commands.set(selector + " #HotbarNumberBg #HotbarNumber.Text", hotbarNumber);
                commands.set(selector + " #HotbarNumberBg.Visible", true);
                
                playerSlotIndex++;
                continue;
            }
            
            if (item != null) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                
                int echoValue;
                if (item.getItemId().equals("EchoScroll")) {
                    Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(item);
                    echoValue = scrollValue != null ? scrollValue.intValue() : 1;
                } else {
                    echoValue = EchoValueCalculator.getInstance().getEchoValue(item.getItemId());
                }
                commands.set(selector + " #EchoesLabel.Visible", true);
                commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
                
                boolean canDeposit = echoValue <= remainingCapacity;
                commands.set(selector + " #UnaffordableOverlay.Visible", !canDeposit);
                commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(item.getItemId(), echoValue, item.getQuantity()));
            } else {
                commands.set(selector + " #SlotItem.ItemId", "");
                commands.set(selector + " #QuantityLabel.Visible", false);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.empty());
            }
            
            String hotbarNumber = String.valueOf(slot + 1);
            commands.set(selector + " #HotbarNumberBg #HotbarNumber.Text", hotbarNumber);
            commands.set(selector + " #HotbarNumberBg.Visible", true);
            
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "burn_hotbar:" + slot),
                    false
            );
            
            events.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of("Action", "burn_one_hotbar:" + slot),
                    false
            );
            
            playerSlotIndex++;
        }
    }
    
    private void buildInfoPanel(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                @Nonnull Inventory playerInventory, @Nonnull ExchangeTabletManager.TabletData tabletData) {
        // Set the block icon to show the Exchange Tablet item
        commands.set("#BlockIcon.ItemId", "ExchangeTablet");
        
        var config = EchoExchangePlugin.getInstance().getModConfig();
        var upgradeConfigs = config.getUpgradeSlots();
        int totalUpgrades = upgradeConfigs.size();
        
        // Calculate completed upgrades from tablet metadata
        int completedUpgrades = tabletData.getUpgradeLevel();
        
        // Update upgrade count display
        commands.set("#UpgradeCount.Text", completedUpgrades + " / " + totalUpgrades);
        
        // Update capacity info
        long currentCapacity = tabletData.getMaxCapacity();
        long nextCapacity = (long) (config.getBaseEchoStorage() * Math.pow(config.getCapacityMultiplier(), completedUpgrades + 1));
        
        if (completedUpgrades < totalUpgrades) {
            commands.set("#CapacityInfo.Text", "Next: " + formatNumberWithCommas(currentCapacity) + " -> " + formatNumberWithCommas(nextCapacity));
        } else {
            commands.set("#CapacityInfo.Text", "Max capacity reached!");
        }
        
        // Build upgrade slots dynamically (stored in tablet metadata)
        commands.clear("#UpgradeSlotsContainer");
        
        for (int i = 0; i < totalUpgrades; i++) {
            var slotConfig = upgradeConfigs.get(i);
            String selector = "#UpgradeSlotsContainer[" + i + "]";
            
            commands.append("#UpgradeSlotsContainer", "Pages/UpgradeSlot.ui");
            
            commands.set(selector + " #RequiredItem.ItemId", slotConfig.getItemId());
            commands.set(selector + " #RequiredItem.Visible", true);
            commands.set(selector + " #ResourceTypeIcon.Visible", false);
            
            int requiredAmount = slotConfig.getRequiredAmount();
            int currentProgress = tabletData.getUpgradeSlotProgress(slotConfig.getItemId());
            boolean isComplete = currentProgress >= requiredAmount;
            
            String progressText = currentProgress + "/" + requiredAmount;
            commands.set(selector + " #ProgressLabel.Text", progressText);
            commands.set(selector + " #CompletedOverlay.Visible", isComplete);
            
            boolean hasItem = countPlayerItems(playerInventory, slotConfig.getItemId()) > 0;
            boolean canClick = hasItem && !isComplete;
            commands.set(selector + " #DimmedOverlay.Visible", !hasItem && !isComplete);
            commands.set(selector + " #ClickableHighlight.Visible", canClick);
            
            Item item = Item.getAssetMap().getAsset(slotConfig.getItemId());
            Message tooltip;
            if (item != null) {
                tooltip = Message.translation(item.getTranslationKey());
            } else {
                tooltip = Message.raw(slotConfig.getItemId());
            }
            
            if (isComplete) {
                tooltip = Message.join(tooltip, Message.raw(" (Complete)"));
            } else {
                tooltip = Message.join(tooltip, Message.raw(" (" + currentProgress + "/" + requiredAmount + ")"));
            }
            commands.set(selector + ".TooltipTextSpans", tooltip);
            
            if (!isComplete) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector,
                        EventData.of("Action", "upgrade_click:" + i),
                        false
                );
            }
        }
    }
    
    // Utility methods
    
    private String getItemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item != null && item.getTranslationProperties() != null) {
            return item.getTranslationProperties().getName();
        }
        return itemId;
    }
    
    private String formatQuantity(long quantity) {
        if (quantity >= 1000000) {
            return String.format("%.1fM", quantity / 1000000.0);
        } else if (quantity >= 1000) {
            return String.format("%.1fK", quantity / 1000.0);
        }
        return String.valueOf(quantity);
    }
    
    private String formatNumberWithCommas(long number) {
        return String.format("%,d", number);
    }
    
    private static String abbreviateNumber(int value) {
        if (value < 1000) {
            return String.valueOf(value);
        } else if (value < 1000000) {
            double k = value / 1000.0;
            if (k >= 100) {
                return String.format("%.0fK", k);
            } else {
                return String.format("%.1fK", k);
            }
        } else if (value < 1000000000) {
            double m = value / 1000000.0;
            if (m >= 100) {
                return String.format("%.0fM", m);
            } else {
                return String.format("%.1fM", m);
            }
        } else {
            double b = value / 1000000000.0;
            return String.format("%.1fB", b);
        }
    }
    
    private Message getItemTooltipWithEchoes(String itemId, int echoValue, int quantity) {
        Item item = Item.getAssetMap().getAsset(itemId);
        Message itemName;
        
        if (item != null) {
            // Use translation key to get proper localized name
            itemName = Message.translation(item.getTranslationKey());
        } else {
            // Fallback: use itemId with underscores replaced
            itemName = Message.raw(itemId.replace("_", " "));
        }
        
        Message tooltip = itemName
                .insert("\n")
                .insert(Message.raw("Echo Value: " + formatNumberWithCommas(echoValue)).color("#a78bfa"));
        
        // Add total stack value if quantity > 1
        if (quantity > 1) {
            long totalValue = (long) echoValue * quantity;
            tooltip = tooltip
                    .insert("\n")
                    .insert(Message.raw("Total Stack Value: " + formatNumberWithCommas(totalValue)).color("#c4b5fd"));
        }
        
        return tooltip;
    }
    
    private int countPlayerItems(@Nonnull Inventory playerInventory, @Nonnull String itemId) {
        int count = 0;
        
        ItemContainer storage = playerInventory.getStorage();
        for (short slot = 0; slot < storage.getCapacity(); slot++) {
            ItemStack item = storage.getItemStack(slot);
            if (item != null && item.getItemId().equals(itemId)) {
                count += item.getQuantity();
            }
        }
        
        ItemContainer hotbar = playerInventory.getHotbar();
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack item = hotbar.getItemStack(slot);
            if (item != null && item.getItemId().equals(itemId)) {
                count += item.getQuantity();
            }
        }
        
        return count;
    }
    
    /**
     * Updates the tablet item in the player's inventory with new data.
     * Finds the tablet and replaces it with an updated version.
     */
    private void updateTabletInInventory(@Nonnull Inventory inventory, @Nonnull ExchangeTabletManager.TabletData tabletData) {
        // Find and replace the tablet
        ItemStack updatedTablet = ExchangeTabletManager.updateTabletData(tabletStack, tabletData);
        
        if (tabletContainer != null && tabletSlot != -1) {
            tabletContainer.setItemStackForSlot(tabletSlot, updatedTablet);
            tabletStack = updatedTablet; // Update our reference
        }
    }
    
    /**
     * Data class for tablet page events.
     */
    public static class TabletPageData {
        public String action;
        public String searchText;
        
        public TabletPageData() {
            this.action = "";
            this.searchText = "";
        }
        
        public static final BuilderCodec<TabletPageData> CODEC = BuilderCodec.<TabletPageData>builder(
                TabletPageData.class, TabletPageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action)
                .addField(new KeyedCodec<>("@SearchText", Codec.STRING),
                        (data, value) -> data.searchText = value,
                        data -> data.searchText)
                .build();
    }
}
