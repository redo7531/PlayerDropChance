package com.example.playerdropchance;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PlayerDropChance
 *
 * Server-side-only mod. On player death, every item stack (main inventory,
 * armor slots, offhand) is independently rolled against a configurable
 * chance to drop on the ground. Everything that does NOT drop is safely
 * held and restored to the player immediately after they respawn.
 *
 * Vanilla experience, death messages, and all other gameplay are untouched:
 * the interception happens purely at the item-drop level (see
 * {@link com.example.playerdropchance.mixin.PlayerDeathMixin}).
 */
public class PlayerDropChance implements ModInitializer {
    public static final String MOD_ID = "playerdropchance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[PlayerDropChance] Initializing...");

        ModConfig.load();
        DropStorage.init(FabricLoader.getInstance().getConfigDir());

        // Primary restoration path: fires right after the player's new
        // entity is created on respawn (including the "auto respawn" that
        // happens if the player disconnected on the death screen and later
        // reconnects).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> restore(newPlayer));

        // Safety net: if a player somehow rejoins already alive while we
        // still have pending items on disk for them (e.g. an edge case
        // around a server crash between death and respawn), restore on
        // join as well instead of silently losing the items.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (DropStorage.hasPending(player.getUUID())) {
                restore(player);
            }
        });
    }

    private static void restore(ServerPlayer player) {
        List<ItemStack> restored = DropStorage.take(player);
        if (restored == null || restored.isEmpty()) {
            return;
        }

        for (ItemStack stack : restored) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            boolean added = player.getInventory().add(stack);
            if (!added || !stack.isEmpty()) {
                // Inventory couldn't hold it all: drop the remainder at the
                // player's feet rather than lose it.
                player.drop(stack, false);
            }
        }

        LOGGER.info("[PlayerDropChance] Restored {} kept item stack(s) to {} after respawn.",
                restored.size(), player.getGameProfile().name());
    }
}
