package com.echoexchange.echo;

import com.echoexchange.EchoExchangePlugin;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EchoValueCalculator {
    
    private static EchoValueCalculator instance;
    
    private final Map<String, Integer> valueCache = new ConcurrentHashMap<>();
    
    private final ThreadLocal<java.util.Set<String>> calculationStack = 
        ThreadLocal.withInitial(java.util.HashSet::new);
    
    private EchoValueCalculator() {
        // Singleton
    }
    
    public static EchoValueCalculator getInstance() {
        if (instance == null) {
            instance = new EchoValueCalculator();
        }
        return instance;
    }
    
    public int getEchoValue(@Nonnull String itemId) {
        Integer cached = valueCache.get(itemId);
        if (cached != null) {
            return cached;
        }
        
        int value = calculateEchoValue(itemId);
        valueCache.put(itemId, value);
        return value;
    }
    
    private int calculateEchoValue(@Nonnull String itemId) {
        Map<String, Integer> overrides = EchoExchangePlugin.getInstance().getModConfig().getEchoValueOverrides();
        if (overrides.containsKey(itemId)) {
            return overrides.get(itemId);
        }
        
        // Get item asset
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.WARNING)
                .log("Item not found: %s, defaulting to 1 Echo", itemId);
            return 1;
        }
        
        // Tier 2: Calculate from recipe if item has one
        int recipeValue = calculateFromRecipes(itemId);
        if (recipeValue > 0) {
            return recipeValue;
        }
        
        // Tier 3: Fallback to ItemLevel-based calculation
        return calculateFallbackValue(item);
    }
    
    private int calculateFromRecipes(@Nonnull String itemId) {
        java.util.Set<String> stack = calculationStack.get();
        if (stack.contains(itemId)) {
            EchoExchangePlugin.getInstance().getLogger().at(Level.WARNING)
                .log("Circular recipe dependency detected for: %s", itemId);
            return 0;
        }
        
        try {
            stack.add(itemId);
            
            for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
                MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
                if (primaryOutput != null && itemId.equals(primaryOutput.getItemId())) {
                    int recipeValue = calculateRecipeValue(recipe);
                    if (recipeValue > 0) {
                        return recipeValue;
                    }
                }
            }
            
            return 0;
            
        } finally {
            stack.remove(itemId);
        }
    }
    
    private int calculateRecipeValue(@Nonnull CraftingRecipe recipe) {
        int totalValue = 0;
        
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs != null) {
            for (MaterialQuantity input : inputs) {
                String inputItemId = input.getItemId();
                if (inputItemId != null) {
                    int inputValue = getEchoValue(inputItemId);
                    totalValue += inputValue * input.getQuantity();
                } else if (input.getResourceTypeId() != null) {
                    totalValue += 1 * input.getQuantity();
                }
            }
        }
        
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        int outputQuantity = (primaryOutput != null && primaryOutput.getQuantity() > 0) 
            ? primaryOutput.getQuantity() 
            : 1;
        
        return Math.max(1, totalValue / outputQuantity);
    }
    
    private int calculateFallbackValue(@Nonnull Item item) {
        int itemLevel = item.getItemLevel();
        int baseValue = itemLevel * itemLevel * Math.max((101 - item.getMaxStack()) / 10, 1 );
        return (Math.max(1, baseValue));
    }
    
    public void clearCache() {
        valueCache.clear();
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Echo value cache cleared");
    }
    
    public void precalculateAll() {
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Pre-calculating Echo values for all items...");
        
        int count = 0;
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            getEchoValue(item.getId());
            count++;
        }
        
        EchoExchangePlugin.getInstance().getLogger().at(Level.INFO)
            .log("Pre-calculated %d item Echo values", count);
    }
}
