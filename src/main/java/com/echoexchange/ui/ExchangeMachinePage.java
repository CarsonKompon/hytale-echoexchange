package com.echoexchange.ui;

import com.echoexchange.EchoExchangePlugin;
import com.echoexchange.echo.EchoValueCalculator;
import com.echoexchange.storage.ExchangeMachineManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.UUID;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class ExchangeMachinePage extends InteractiveCustomUIPage<ExchangeMachinePage.ExchangeMachinePageData> {
    
    private final ItemContainerState containerState;
    private final Vector3i blockPosition;
    private final String worldName;
    private String searchText = "";
    private boolean scribbleToggle = false;

    public ExchangeMachinePage(@Nonnull PlayerRef playerRef, @Nonnull ItemContainerState containerState) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ExchangeMachinePageData.CODEC);
        this.containerState = containerState;
        this.blockPosition = containerState.getBlockPosition();
        this.worldName = containerState.getChunk() != null ? 
            containerState.getChunk().getWorld().getName() : "unknown";
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ExchangeMachinePageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        Inventory inventory = player.getInventory();
        ExchangeMachineManager.MachineData machineData = 
            ExchangeMachineManager.getInstance().getMachineData(worldName, blockPosition);
        
        String action = data.action;
        
        if ((action == null || action.isEmpty()) && data.searchText != null) {
            searchText = data.searchText.toLowerCase();
            
            ExchangeMachineManager.getInstance().setPlayerSearchQuery(player.getUuid(), searchText);
            
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildDiscoveredItemsGrid(commands, events, player, machineData);
            sendUpdate(commands, events, false);
            return;
        }
        
        // If no action, nothing else to do
        if (action == null || action.isEmpty()) return;
        
        if (action.startsWith("burn_inventory:")) {
            int slot = Integer.parseInt(action.substring("burn_inventory:".length()));
            ItemStack stack = inventory.getStorage().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, machineData, inventory.getStorage(), (short) slot, stack.getItemId(), stack.getQuantity());
            }
        } else if (action.startsWith("burn_hotbar:")) {
            int slot = Integer.parseInt(action.substring("burn_hotbar:".length()));
            ItemStack stack = inventory.getHotbar().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, machineData, inventory.getHotbar(), (short) slot, stack.getItemId(), stack.getQuantity());
            }
        } else if (action.startsWith("burn_one_inventory:")) {
            int slot = Integer.parseInt(action.substring("burn_one_inventory:".length()));
            ItemStack stack = inventory.getStorage().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, machineData, inventory.getStorage(), (short) slot, stack.getItemId(), 1);
            }
        } else if (action.startsWith("burn_one_hotbar:")) {
            int slot = Integer.parseInt(action.substring("burn_one_hotbar:".length()));
            ItemStack stack = inventory.getHotbar().getItemStack((short) slot);
            if (stack != null) {
                handleBurnItem(player, inventory, machineData, inventory.getHotbar(), (short) slot, stack.getItemId(), 1);
            }
        } else if (action.startsWith("transmute_max:")) {
            String itemId = action.substring("transmute_max:".length());
            // Get the item's max stack size
            Item item = Item.getAssetMap().getAsset(itemId);
            int maxStack = item != null ? item.getMaxStack() : 64;
            
            // Calculate how many we can afford up to one full stack
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            int affordableQuantity = echoValue > 0 ? (int) (machineData.getStoredEchoes() / echoValue) : 0;
            int quantity = Math.min(maxStack, affordableQuantity);
            
            if (quantity > 0) {
                handleTransmuteItem(player, inventory, machineData, itemId, quantity);
            }
        } else if (action.startsWith("transmute_one:")) {
            String itemId = action.substring("transmute_one:".length());
            handleTransmuteItem(player, inventory, machineData, itemId, 1);
        } else if (action.startsWith("upgrade_click:")) {
            int slotIndex = Integer.parseInt(action.substring("upgrade_click:".length()));
            handleUpgradeClick(inventory, machineData, slotIndex);
        }
        
        // Rebuild UI to reflect changes
        rebuild();
    }
    
    private void handleBurnItem(@Nonnull Player player, @Nonnull Inventory inventory, @Nonnull ExchangeMachineManager.MachineData machineData,
                                @Nonnull ItemContainer container, short slot, @Nonnull String itemId, int quantity) {
        ItemStack stack = container.getItemStack(slot);
        if (stack == null || !itemId.equals(stack.getItemId())) {
            return; // Slot changed or empty
        }
        
        // Limit quantity to what's actually in the slot
        int available = stack.getQuantity();
        if (quantity > available) {
            quantity = available;
        }
        
        if (quantity <= 0) return;
        
        // Special handling for Echo Scrolls - they store their echo value in metadata
        long totalEchoes;
        if (itemId.equals("EchoScroll")) {
            Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(stack);
            if (scrollValue != null && scrollValue > 0) {
                totalEchoes = scrollValue * quantity; // Each scroll contains stored echoes
                
                EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                    .log("Burning Echo Scroll with %d stored echoes", scrollValue);
            } else {
                // Fallback if metadata is missing
                EchoExchangePlugin.getInstance().getLogger().at(Level.WARNING)
                    .log("Echo Scroll missing metadata, using default value");
                totalEchoes = 1; // Minimal fallback
            }
        } else {
            // Normal items: calculate Echo value
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            totalEchoes = (long) echoValue * quantity;
        }
        
        // Check if machine has capacity
        long currentEchoes = machineData.getStoredEchoes();
        long maxCapacity = machineData.getMaxCapacity();
        if (currentEchoes + totalEchoes > maxCapacity) {
            // Can't fit all
            if (itemId.equals("EchoScroll")) {
                // Scrolls can't be partially burned since each scroll has a fixed value
                EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
                    .log("Not enough capacity to burn Echo Scroll");
                return;
            }
            
            // For normal items, calculate how many we can burn
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            long availableSpace = maxCapacity - currentEchoes;
            quantity = (int) (availableSpace / echoValue);
            totalEchoes = (long) echoValue * quantity;
            
            if (quantity <= 0) {
                return;
            }
        }
        
        // Remove items from the specific slot
        container.removeItemStackFromSlot(slot, quantity);
        
        // Add Echoes to machine
        machineData.addEchoes(totalEchoes);
        
        // Discover the item (only for normal items, not scrolls) - stored per-player
        if (!itemId.equals("EchoScroll")) {
            ExchangeMachineManager.getInstance().discoverItemForPlayer(player.getUuid(), itemId);
        }
        
        // Mark data as dirty for saving
        ExchangeMachineManager.getInstance().markDirty();
        
        // Trigger scribble animation
        triggerScribbleAnimation();
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Player burned %d x %s for %d Echoes", quantity, itemId, totalEchoes);
    }
    
    private void handleTransmuteItem(@Nonnull Player player, @Nonnull Inventory inventory, @Nonnull ExchangeMachineManager.MachineData machineData,
                                    @Nonnull String itemId, int quantity) {
        if (!ExchangeMachineManager.getInstance().hasPlayerDiscovered(player.getUuid(), itemId)) {
            playerRef.sendMessage(Message.raw("Item not discovered yet!"));
            return;
        }
        
        // Calculate Echo cost
        int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
        long totalCost = (long) echoValue * quantity;
        
        // Check if machine has enough Echoes
        if (machineData.getStoredEchoes() < totalCost) {
            // Calculate max we can afford
            quantity = (int) (machineData.getStoredEchoes() / echoValue);
            totalCost = (long) echoValue * quantity;
            
            if (quantity <= 0) {
                playerRef.sendMessage(Message.raw("Not enough Echoes!"));
                return;
            }
        }
        
        // Create item stack
        ItemStack newStack = new ItemStack(itemId, quantity);
        
        // Try to add to inventory
        ItemContainer combined = inventory.getCombinedHotbarFirst();
        var transaction = combined.addItemStack(newStack);
        ItemStack remainder = transaction.getRemainder();
        
        if (remainder != null && remainder.getQuantity() > 0) {
            // Couldn't add all items, refund the cost of remainder
            int actualQuantity = quantity - remainder.getQuantity();
            totalCost = (long) echoValue * actualQuantity;
            
            if (actualQuantity <= 0) {
                return;
            }
        }
        
        // Remove Echoes from machine
        machineData.removeEchoes(totalCost);
        
        // Mark data as dirty for saving
        ExchangeMachineManager.getInstance().markDirty();
        
        // Trigger scribble animation
        triggerScribbleAnimation();
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Player transmuted %d x %s for %d Echoes", quantity, itemId, totalCost);
    }

    private void triggerScribbleAnimation() {
        WorldChunk chunk = containerState.getChunk();
        if (chunk == null) return;
        
        World world = chunk.getWorld();
        BlockType blockType = world.getBlockType(blockPosition.x, blockPosition.y, blockPosition.z);
        if (blockType == null) return;
        
        scribbleToggle = !scribbleToggle;
        String state = scribbleToggle ? "Scribbling1" : "Scribbling2";
        world.setBlockInteractionState(blockPosition, blockType, state);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                     @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        if (searchText.isEmpty()) {
            searchText = ExchangeMachineManager.getInstance().getPlayerSearchQuery(player.getUuid());
        }
        
        // Load the UI from the UI file
        commands.append("Pages/ExchangeMachine.ui");
        
        // Get machine data
        ExchangeMachineManager.MachineData machineData = 
            ExchangeMachineManager.getInstance().getMachineData(worldName, blockPosition);
        
        // Update Echo display
        long currentEchoes = machineData.getStoredEchoes();
        long maxCapacity = machineData.getMaxCapacity();
        double progressPercent = maxCapacity > 0 ? ((double) currentEchoes / maxCapacity) : 0.0;
        
        // Update labels with current values using set() with selector.Property syntax
        commands.set("#EchoBalanceLabel.Text", "Echoes: " + formatNumberWithCommas(currentEchoes) + " / " + formatNumberWithCommas(maxCapacity));
        
        // Update progress bar value (0.0 to 1.0)
        commands.set("#EchoProgressBar.Value", progressPercent);
        
        // Build discovered items section (includes search field and grid)
        buildDiscoveredItems(commands, events, player, machineData);
        
        // Build player inventory
        Inventory inventory = player.getInventory();
        buildPlayerInventory(commands, events, inventory, machineData);
        
        // Build info panel with upgrades
        buildInfoPanel(commands, events, inventory, machineData);
    }
    
    /**
     * Builds the discovered items grid.
     */
    private void buildDiscoveredItems(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                      @Nonnull Player player, @Nonnull ExchangeMachineManager.MachineData machineData) {
        // Set up search field
        commands.set("#SearchInput.Value", searchText);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchText", "#SearchInput.Value"),
                false
        );
        
        // Build the grid content
        buildDiscoveredItemsGrid(commands, events, player, machineData);
    }
    
    /**
     * Builds only the discovered items grid content (for partial updates during search).
     */
    private void buildDiscoveredItemsGrid(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                          @Nonnull Player player, @Nonnull ExchangeMachineManager.MachineData machineData) {
        commands.clear("#DiscoveredItemsGrid");
        
        java.util.Set<String> discoveredItems = ExchangeMachineManager.getInstance().getPlayerDiscoveredItems(player.getUuid());
        if (discoveredItems.isEmpty()) {
            // No items discovered - the static label in UI will show
            return;
        }
        
        long currentEchoes = machineData.getStoredEchoes();
        
        // Filter and sort discovered items
        java.util.List<String> sortedItems = new java.util.ArrayList<>(discoveredItems);
        
        // Apply search filter
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
            
            // Affordable items come first
            if (canAfford1 != canAfford2) {
                return canAfford1 ? -1 : 1;
            }
            
            // Within same affordability, sort by echo value (high to low)
            return Integer.compare(value2, value1);
        });
        
        // Build discovered item slots
        int slotIndex = 0;
        for (String itemId : sortedItems) {
            String selector = "#DiscoveredItemsGrid[" + slotIndex + "]";
            int echoValue = EchoValueCalculator.getInstance().getEchoValue(itemId);
            boolean canAfford = echoValue <= currentEchoes;
            
            commands.append("#DiscoveredItemsGrid", "Pages/SimpleStorageSlot.ui");
            
            // Set item icon
            commands.set(selector + " #SlotItem.ItemId", itemId);
            
            // Show quantity (how many can be transmuted)
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
            
            // Show cost label in bottom-left (purple if affordable, grey if not)
            commands.set(selector + " #EchoesLabel.Visible", true);
            commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
            commands.set(selector + " #EchoesLabel.Style.TextColor", canAfford ? "#a78bfa" : "#9ca3af");
            
            // Show overlay for unaffordable items
            commands.set(selector + " #UnaffordableOverlay.Visible", !canAfford);
            
            // Hide hotbar number
            commands.set(selector + " #HotbarNumberBg.Visible", false);
            
            // Set tooltip with Echo value
            commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(itemId, echoValue, 1));
            
            // Add click handlers for transmutation
            // Left click: transmute full stack (64)
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "transmute_max:" + itemId),
                    false
            );
            
            // Right click: transmute one
            events.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of("Action", "transmute_one:" + itemId),
                    false
            );
            
            slotIndex++;
        }
        
        // Fill remaining slots to make a multiple of 9
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
    
    /**
     * Formats a quantity for display (1.3K, 2.5M, etc.)
     */
    private String formatQuantity(long quantity) {
        if (quantity >= 1000000) {
            return String.format("%.1fM", quantity / 1000000.0);
        } else if (quantity >= 1000) {
            return String.format("%.1fK", quantity / 1000.0);
        }
        return String.valueOf(quantity);
    }
    
    /**
     * Formats a number with commas (1,234,567)
     */
    private String formatNumberWithCommas(long number) {
        return String.format("%,d", number);
    }
    
    /**
     * Abbreviates large numbers (1200 -> 1.2K, 1000000 -> 1M, etc.).
     */
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
    
    /**
     * Gets the localized name for an item for search filtering.
     */
    private String getItemName(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item != null) {
            // Get translation key and try to resolve it
            // For search purposes, we use the English translation
            // Note: In a real implementation, you'd want to use the player's locale
            try {
                String translationKey = item.getTranslationKey();
                // For now, use a simple approach - check if we have a direct name
                // Otherwise fall back to processing the ID
                if (item.getTranslationProperties() != null && 
                    item.getTranslationProperties().getName() != null &&
                    !item.getTranslationProperties().getName().startsWith("server.")) {
                    return item.getTranslationProperties().getName();
                }
            } catch (Exception e) {
                // Fall through to fallback
            }
        }
        // Fallback: replace underscores with spaces
        return itemId.replace("_", " ");
    }
    
    /**
     * Creates a tooltip message with item name and Echo value.
     */
    private Message getItemTooltipWithEchoes(@Nonnull String itemId, int echoValue, int quantity) {
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
    
    /**
     * Builds the player inventory slots.
     */
    private void buildPlayerInventory(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                     @Nonnull Inventory playerInventory, @Nonnull ExchangeMachineManager.MachineData machineData) {
        ItemContainer invStorage = playerInventory.getStorage();
        ItemContainer hotbar = playerInventory.getHotbar();
        
        commands.clear("#PlayerInventory");
        
        long currentEchoes = machineData.getStoredEchoes();
        long maxCapacity = machineData.getMaxCapacity();
        long remainingCapacity = maxCapacity - currentEchoes;
        
        int playerSlotIndex = 0;
        
        // First add storage slots (27 slots = 3 rows of 9)
        for (short slot = 0; slot < invStorage.getCapacity(); slot++) {
            ItemStack item = invStorage.getItemStack(slot);
            String selector = "#PlayerInventory[" + playerSlotIndex + "]";
            
            commands.append("#PlayerInventory", "Pages/SimpleStorageSlot.ui");
            
            if (item != null) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                
                // Show Echo value in bottom-left
                int echoValue;
                if (item.getItemId().equals("EchoScroll")) {
                    // Read echo value from scroll's metadata
                    Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(item);
                    echoValue = scrollValue != null ? scrollValue.intValue() : 1;
                } else {
                    echoValue = EchoValueCalculator.getInstance().getEchoValue(item.getItemId());
                }
                commands.set(selector + " #EchoesLabel.Visible", true);
                commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
                
                // Show overlay if item can't be deposited (not enough capacity for even one)
                boolean canDeposit = echoValue <= remainingCapacity;
                commands.set(selector + " #UnaffordableOverlay.Visible", !canDeposit);
                
                // Add Echo value to tooltip
                commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(item.getItemId(), echoValue, item.getQuantity()));
            } else {
                commands.set(selector + " #SlotItem.ItemId", "");
                commands.set(selector + " #QuantityLabel.Visible", false);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.empty());
            }
            
            // Add click handlers for burning
            // Left click: burn full stack
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "burn_inventory:" + slot),
                    false
            );
            
            // Right click: burn one
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
            
            if (item != null) {
                commands.set(selector + " #SlotItem.ItemId", item.getItemId());
                commands.set(selector + " #QuantityLabel.Text", String.valueOf(item.getQuantity()));
                commands.set(selector + " #QuantityLabel.Visible", item.getQuantity() > 1);
                
                // Show Echo value in bottom-left
                int echoValue;
                if (item.getItemId().equals("EchoScroll")) {
                    // Read echo value from scroll's metadata
                    Long scrollValue = com.echoexchange.item.EchoScrollManager.getScrollValue(item);
                    echoValue = scrollValue != null ? scrollValue.intValue() : 1;
                } else {
                    echoValue = EchoValueCalculator.getInstance().getEchoValue(item.getItemId());
                }
                commands.set(selector + " #EchoesLabel.Visible", true);
                commands.set(selector + " #EchoesLabel.Text", formatQuantity(echoValue));
                
                // Show overlay if item can't be deposited (not enough capacity for even one)
                boolean canDeposit = echoValue <= remainingCapacity;
                commands.set(selector + " #UnaffordableOverlay.Visible", !canDeposit);
                
                // Add Echo value to tooltip
                commands.set(selector + ".TooltipTextSpans", getItemTooltipWithEchoes(item.getItemId(), echoValue, item.getQuantity()));
            } else {
                commands.set(selector + " #SlotItem.ItemId", "");
                commands.set(selector + " #QuantityLabel.Visible", false);
                commands.set(selector + " #EchoesLabel.Visible", false);
                commands.set(selector + ".TooltipTextSpans", Message.empty());
            }
            
            // Show hotbar number (1-9) with background
            String hotbarNumber = String.valueOf(slot + 1);
            commands.set(selector + " #HotbarNumberBg #HotbarNumber.Text", hotbarNumber);
            commands.set(selector + " #HotbarNumberBg.Visible", true);
            
            // Add click handlers for burning
            // Left click: burn full stack
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("Action", "burn_hotbar:" + slot),
                    false
            );
            
            // Right click: burn one
            events.addEventBinding(
                    CustomUIEventBindingType.RightClicking,
                    selector,
                    EventData.of("Action", "burn_one_hotbar:" + slot),
                    false
            );
            
            playerSlotIndex++;
        }
    }
    
    /**
     * Builds the info panel showing block icon and upgrades.
     */
    private void buildInfoPanel(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events,
                                @Nonnull Inventory playerInventory, @Nonnull ExchangeMachineManager.MachineData machineData) {
        // Set the block icon to show the Exchange Machine block item
        commands.set("#BlockIcon.ItemId", "ExchangeMachine");
        
        var config = EchoExchangePlugin.getInstance().getModConfig();
        var upgradeConfigs = config.getUpgradeSlots();
        int totalUpgrades = upgradeConfigs.size();
        int completedUpgrades = machineData.getUpgradeLevel();
        
        // Update upgrade count display
        commands.set("#UpgradeCount.Text", completedUpgrades + " / " + totalUpgrades);
        
        // Update capacity info
        long currentCapacity = machineData.getMaxCapacity();
        long nextCapacity = (long) (config.getBaseEchoStorage() * Math.pow(config.getCapacityMultiplier(), completedUpgrades + 1));
        
        if (completedUpgrades < totalUpgrades) {
            commands.set("#CapacityInfo.Text", "Next: " + formatNumberWithCommas(currentCapacity) + " -> " + formatNumberWithCommas(nextCapacity));
        } else {
            commands.set("#CapacityInfo.Text", "Max capacity reached!");
        }
        
        // Build upgrade slots dynamically
        commands.clear("#UpgradeSlotsContainer");
        
        for (int i = 0; i < totalUpgrades; i++) {
            var slotConfig = upgradeConfigs.get(i);
            String selector = "#UpgradeSlotsContainer[" + i + "]";
            
            commands.append("#UpgradeSlotsContainer", "Pages/UpgradeSlot.ui");
            
            // Set the required item
            commands.set(selector + " #RequiredItem.ItemId", slotConfig.getItemId());
            commands.set(selector + " #RequiredItem.Visible", true);
            commands.set(selector + " #ResourceTypeIcon.Visible", false);
            
            int requiredAmount = slotConfig.getRequiredAmount();
            int currentProgress = machineData.getUpgradeSlotProgress(slotConfig.getItemId());
            boolean isComplete = currentProgress >= requiredAmount;
            
            // Set progress text
            String progressText = currentProgress + "/" + requiredAmount;
            commands.set(selector + " #ProgressLabel.Text", progressText);
            
            // Show/hide completed overlay
            commands.set(selector + " #CompletedOverlay.Visible", isComplete);
            
            // Check if player has any of the required item
            boolean hasItem = countPlayerItems(playerInventory, slotConfig.getItemId()) > 0;
            
            // Show/hide dimmed overlay and clickable highlight
            // Can click any incomplete upgrade if player has items (non-sequential)
            boolean canClick = hasItem && !isComplete;
            commands.set(selector + " #DimmedOverlay.Visible", !hasItem && !isComplete);
            commands.set(selector + " #ClickableHighlight.Visible", canClick);
            
            // Set tooltip
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
            
            // Add click event for any incomplete upgrade
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
    
    /**
     * Handles clicking on an upgrade slot.
     * Consumes all required items from player inventory and increments upgrade level.
     */
    private void handleUpgradeClick(@Nonnull Inventory playerInventory, 
                                   @Nonnull ExchangeMachineManager.MachineData machineData,
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
        int currentProgress = machineData.getUpgradeSlotProgress(itemId);
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
        
        // Remove from hotbar
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
            machineData.addUpgradeSlotProgress(itemId, actuallyTaken);
            ExchangeMachineManager.getInstance().markDirty();
        }
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Player completed upgrade %d for Exchange Machine at %s", slotIndex + 1, blockPosition);
    }
    
    /**
     * Counts how many of a specific item the player has in their inventory.
     */
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
     * Data class for UI events from the Exchange Machine page.
     */
    public static class ExchangeMachinePageData {
        private String action = "";
        private String searchText = "";
        
        public static final BuilderCodec<ExchangeMachinePageData> CODEC = BuilderCodec.builder(
                ExchangeMachinePageData.class, ExchangeMachinePageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING),
                        (data, value) -> data.action = value,
                        data -> data.action)
                .addField(new KeyedCodec<>("@SearchText", Codec.STRING),
                        (data, value) -> data.searchText = value,
                        data -> data.searchText)
                .build();

        public ExchangeMachinePageData() {
        }
    }
}
