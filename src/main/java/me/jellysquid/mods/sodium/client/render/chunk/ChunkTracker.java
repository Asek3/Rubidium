package me.jellysquid.mods.sodium.client.render.chunk;

import java.util.stream.LongStream;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.ChunkPos;

public class ChunkTracker {
    private final Long2IntOpenHashMap single = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap merged = new Long2IntOpenHashMap();

    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();

    public ChunkTracker() {
        this.single.defaultReturnValue(0);
        this.merged.defaultReturnValue(0);
    }

    public void update() {
        if (this.dirty.isEmpty()) {
            return;
        }

        var dirty = this.markDirtyChunks();
        this.recalculateChunks(dirty);

        this.dirty.clear();
    }

    private void recalculateChunks(LongSet set) {
        LongIterator it = set.iterator();

        while (it.hasNext()) {
            long key = it.nextLong();

            var x = ChunkPos.getPackedX(key);
            var z = ChunkPos.getPackedZ(key);

            int flags = this.single.get(key);

            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    flags &= this.single.get(ChunkPos.toLong(ox + x, oz + z));
                }
            }

            if (flags != 0) {
                this.merged.put(key, flags);
            } else {
                this.merged.remove(key);
            }
        }
    }

    private LongSet markDirtyChunks() {
        var dirty = new LongOpenHashSet(this.dirty);
        var it = this.dirty.iterator();

        while (it.hasNext()) {
            var key = it.nextLong();
            var x = ChunkPos.getPackedX(key);
            var z = ChunkPos.getPackedZ(key);

            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    dirty.add(ChunkPos.toLong(ox + x, oz + z));
                }
            }
        }

        return dirty;
    }

    public boolean loadChunk(int x, int z) {
        var key = ChunkPos.toLong(x, z);
        var flags = this.single.get(key) | ChunkStatus.FLAG_HAS_BLOCK_DATA;

        if (this.single.put(key, flags) == flags) {
            return false;
        }

        this.dirty.add(key);

        return true;
    }

    public void onLightDataAdded(int x, int z) {
        var key = ChunkPos.toLong(x, z);
        var existingFlags = this.single.get(key);

        // Useless
        /*if ((existingFlags & ChunkStatus.FLAG_HAS_BLOCK_DATA) == 0) {
            SodiumClientMod.logger().warn("Tried to mark light data as ready for chunk [%s, %s] but it hasn't been loaded yet".formatted(x, z));
        }*/

        this.single.put(key, existingFlags | ChunkStatus.FLAG_HAS_LIGHT_DATA);
        this.dirty.add(key);
    }

    public boolean unloadChunk(int x, int z) {
        long key = ChunkPos.toLong(x, z);

        if (this.single.remove(key) == 0) {
            return false;
        }

        this.dirty.add(key);

        return true;
    }

    public boolean hasMergedFlags(int x, int z, int flags) {
        return (this.merged.get(ChunkPos.toLong(x, z)) & flags) == flags;
    }

    public LongStream getChunks(int flags) {
        return this.single
                .long2IntEntrySet()
                .stream()
                .filter(entry -> (entry.getIntValue() & flags) == flags)
                .mapToLong(Long2IntMap.Entry::getLongKey);
    }
}
