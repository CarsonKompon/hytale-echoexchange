package com.echoexchange.block;

import com.echoexchange.item.EchoScrollManager;
import com.echoexchange.storage.ExchangeMachineManager;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ExchangeMachineDestroySystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    private static final String EXCHANGE_MACHINE_BLOCK_ID = "ExchangeMachine";
    
    public ExchangeMachineDestroySystem() {
        super(BreakBlockEvent.class);
    }
    
    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
    
    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
    ) {
        String blockId = event.getBlockType().getId();
        
        if (!EXCHANGE_MACHINE_BLOCK_ID.equals(blockId) && 
            !blockId.contains("*" + EXCHANGE_MACHINE_BLOCK_ID)) {
            return;
        }
        
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();
        String worldName = world.getName();
        
        Vector3i blockPos = event.getTargetBlock();
        
        ExchangeMachineManager.MachineData machineData = 
                ExchangeMachineManager.getInstance().getMachineData(worldName, blockPos);
        List<ItemStack> itemsToDrop = machineData.getItemsToDrop();
        
        long storedEchoes = machineData.getStoredEchoes();
        if (storedEchoes > 0) {
            ItemStack echoScroll = EchoScrollManager.createEchoScroll(storedEchoes);
            itemsToDrop.add(echoScroll);
            
            com.echoexchange.EchoExchangePlugin.getInstance().getLogger().at(java.util.logging.Level.INFO)
                    .log("Exchange Machine destroyed with %d echoes - dropping Echo Scroll with metadata", storedEchoes);
        }
        
        ExchangeMachineManager.getInstance().removeMachineData(worldName, blockPos);
        
        if (!itemsToDrop.isEmpty()) {
            Vector3d dropPosition = new Vector3d(
                    blockPos.x + 0.5,
                    blockPos.y + 0.5,
                    blockPos.z + 0.5
            );
            
            Holder<EntityStore>[] itemDrops = ItemComponent.generateItemDrops(
                    store,
                    itemsToDrop,
                    dropPosition,
                    Vector3f.ZERO
            );
            
            for (Holder<EntityStore> itemDrop : itemDrops) {
                if (itemDrop != null) {
                    commandBuffer.addEntity(itemDrop, AddReason.SPAWN);
                }
            }
        }
    }
}
