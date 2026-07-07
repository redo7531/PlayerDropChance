package com.example.playerdropchance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the item stacks that were NOT dropped at death ("kept" stacks)
 * until the owning player respawns.
 *
 * Two layers of storage are used so items are never silently lost:
 *  1. An in-memory map, for the common case (player is on the death screen
 *     or respawns normally in the same server session).
 *  2. A disk-backed NBT file per pending player, written synchronously at
 *     death, so items also survive a full server restart while a player is
 *     disconnected between dying and respawning.
 *
 * Both layers are cleared together once the items are restored.
 */
public final class DropStorage {
    private static final Map<UUID, List<ItemStack>> PENDING = new ConcurrentHashMap<>();
    private static Path storageDir;

    private DropStorage() {
    }

    public static void init(Path configDir) {
        storageDir = configDir.resolve("playerdropchance_pending");
        storageDir.toFile().mkdirs();
    }

    public static boolean hasPending(UUID id) {
        if (PENDING.containsKey(id)) {
            return true;
        }
        return storageDir != null && storageDir.resolve(id + ".dat").toFile().exists();
    }

    public static void store(ServerPlayer player, List<ItemStack> stacks) {
        UUID id = player.getUUID();
        // Keep copies in memory; this is the fast, primary path.
        PENDING.put(id, stacks);
        writeToDisk(player, id, stacks);
    }

    public static List<ItemStack> take(ServerPlayer player) {
        UUID id = player.getUUID();
        List<ItemStack> stacks = PENDING.remove(id);
        if (stacks == null) {
            stacks = readFromDisk(player, id);
        }
        deleteFromDisk(id);
        return stacks;
    }

    private static void writeToDisk(ServerPlayer player, UUID id, List<ItemStack> stacks) {
        if (storageDir == null) {
            return;
        }
        try {
            HolderLookup.Provider registries = player.registryAccess();
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Tag saved = stack.save(registries);
                if (saved != null) {
                    list.add(saved);
                }
            }
            root.put("Items", list);
            NbtIo.write(root, storageDir.resolve(id + ".dat").toFile());
        } catch (IOException e) {
            PlayerDropChance.LOGGER.warn(
                    "[PlayerDropChance] Failed to persist pending items for {} to disk; "
                            + "they remain available in memory for this server session.",
                    id, e);
        }
    }

    private static List<ItemStack> readFromDisk(ServerPlayer player, UUID id) {
        if (storageDir == null) {
            return null;
        }
        File file = storageDir.resolve(id + ".dat").toFile();
        if (!file.exists()) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) {
                return null;
            }
            HolderLookup.Provider registries = player.registryAccess();
            ListTag list = root.getList("Items", Tag.TAG_COMPOUND);
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                ItemStack.parse(registries, itemTag).ifPresent(result::add);
            }
            return result;
        } catch (IOException e) {
            PlayerDropChance.LOGGER.error(
                    "[PlayerDropChance] Failed to read pending items for {} from disk.", id, e);
            return null;
        }
    }

    private static void deleteFromDisk(UUID id) {
        if (storageDir == null) {
            return;
        }
        storageDir.resolve(id + ".dat").toFile().delete();
    }
}
