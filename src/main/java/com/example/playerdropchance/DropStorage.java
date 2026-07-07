package com.example.playerdropchance;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DropStorage {
    private static final Map<UUID, List<ItemStack>> PENDING = new ConcurrentHashMap<>();
    private static Path storageDir;

    private DropStorage() {
    }

    public static void init(Path configDir) {
        storageDir = configDir.resolve("playerdropchance_pending");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            PlayerDropChance.LOGGER.error("[PlayerDropChance] Failed to create storage directory {}", storageDir, e);
        }
    }

    public static boolean hasPending(UUID id) {
        if (PENDING.containsKey(id)) {
            return true;
        }
        return storageDir != null && Files.exists(storageDir.resolve(id + ".dat"));
    }

    public static void store(ServerPlayer player, List<ItemStack> stacks) {
        UUID id = player.getUUID();
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
                DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(
                        registries.createSerializationContext(NbtOps.INSTANCE), stack);
                encoded.result().ifPresent(list::add);
            }
            root.put("Items", list);
            NbtIo.write(root, storageDir.resolve(id + ".dat"));
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
        Path file = storageDir.resolve(id + ".dat");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) {
                return null;
            }
            HolderLookup.Provider registries = player.registryAccess();
            Optional<ListTag> listOpt = root.getList("Items");
            ListTag list = listOpt.orElseGet(ListTag::new);
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Optional<CompoundTag> itemTag = list.getCompound(i);
                if (itemTag.isEmpty()) {
                    continue;
                }
                DataResult<ItemStack> decoded = ItemStack.CODEC.parse(
                        registries.createSerializationContext(NbtOps.INSTANCE), itemTag.get());
                decoded.result().ifPresent(result::add);
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
        try {
            Files.deleteIfExists(storageDir.resolve(id + ".dat"));
        } catch (IOException e) {
            PlayerDropChance.LOGGER.warn("[PlayerDropChance] Failed to delete pending-items file for {}", id, e);
        }
    }
}
