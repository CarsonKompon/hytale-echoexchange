package com.echoexchange.item;

import com.echoexchange.EchoExchangePlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import org.bson.BsonDocument;
import org.bson.BsonInt64;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EchoScrollManager {
    private static final String METADATA_KEY_ECHO_VALUE = "EchoValue";
    
    @Nonnull
    public static ItemStack createEchoScroll(long echoValue) {
        BsonDocument metadata = new BsonDocument();
        metadata.put(METADATA_KEY_ECHO_VALUE, new BsonInt64(echoValue));
        
        ItemStack scroll = new ItemStack("EchoScroll", 1, metadata);
        
        EchoExchangePlugin.getInstance().getLogger().at(java.util.logging.Level.INFO)
            .log("Created Echo Scroll with %d Echoes stored in metadata", echoValue);
        
        return scroll;
    }
    
    @Nullable
    public static Long getScrollValue(@Nonnull ItemStack scroll) {
        if (!scroll.getItemId().equals("EchoScroll")) {
            return null;
        }
        
        return scroll.getFromMetadataOrNull(METADATA_KEY_ECHO_VALUE, Codec.LONG);
    }
    
    @Nonnull
    public static Message createScrollTooltip(long echoValue) {
        return Message.join(
            Message.translation("item.EchoScroll"),
            Message.raw(" ("),
            Message.raw(String.format("%,d", echoValue)),
            Message.raw(" Echoes)")
        );
    }
    
    @Nonnull
    public static Message createScrollTooltip(@Nonnull ItemStack scroll) {
        Long value = getScrollValue(scroll);
        if (value != null) {
            return createScrollTooltip(value);
        }
        return Message.translation("item.EchoScroll");
    }
}
