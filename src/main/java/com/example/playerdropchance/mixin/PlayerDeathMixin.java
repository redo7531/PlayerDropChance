package com.example.playerdropchance.mixin;

import com.example.playerdropchance.DropStorage;
import com.example.playerdropchance.ModConfig;
import com.example.playerdropchance.PlayerDropChance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Replaces vanilla's "drop everything" death behaviour with a per-stack
 * dice roll. Every stack in the main inventory, armor slots, and offhand
 * (all covered by {@link Inventory}'s combined slot indexing) is rolled
 * independently:
 *  - on a "hit" (chance from {@link ModConfig#dropChance}), it is dropped
 *    on the ground exactly like vanilla would.
 *  - otherwise, it is cleared from the inventory and handed to
 *    {@link DropStorage} to be restored on respawn.
 *
 * This mixin only touches item drops. Experience and death messages are
 * handled by unrelated vanilla code paths and are left completely alone.
 */
@Mixin(Player.class)
public abstract class PlayerDeathMixin {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void playerDropChance$onDropAllDeathLoot(
            ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }

        double dropChance = ModConfig.get().dropChance;
        Inventory inventory = player.getInventory();
        List<ItemStack> kept = new ArrayList<>();
        int droppedCount = 0;

        // getContainerSize()/getItem()/setItem() on Inventory transparently
        // index across the main inventory, armor slots, and the offhand
        // slot, so this single loop covers requirement (1) in full.
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack toProcess = stack.copy();
            // Clear the original slot immediately so nothing can be read
            // twice from the same slot (duplication prevention).
            inventory.setItem(slot, ItemStack.EMPTY);

            if (ThreadLocalRandom.current().nextDouble() < dropChance) {
                player.drop(toProcess, true, false);
                droppedCount++;
            } else {
                kept.add(toProcess);
            }
        }

        DropStorage.store(player, kept);

        PlayerDropChance.LOGGER.info(
                "[PlayerDropChance] {} died: {} stack(s) dropped, {} stack(s) held for respawn (chance={}).",
                player.getGameProfile().getName(), droppedCount, kept.size(), dropChance);

        // Prevent vanilla's own drop logic from running afterwards, which
        // would otherwise drop everything a second time.
        ci.cancel();
    }
}
