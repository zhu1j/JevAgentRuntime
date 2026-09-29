package com.jev.runtime.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin API key pool per provider. Keys are rotated so a single key's
 * rate limit does not throttle the whole tenant fleet.
 */
public class KeyPool {

    private final Map<String, List<String>> keysByProvider = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> cursor = new ConcurrentHashMap<>();

    public void register(String provider, List<String> keys) {
        keysByProvider.put(provider, List.copyOf(keys));
        cursor.putIfAbsent(provider, new AtomicInteger(0));
    }

    /** Next key for the provider, or null when none configured. */
    public String next(String provider) {
        List<String> keys = keysByProvider.getOrDefault(provider, List.of());
        if (keys.isEmpty()) {
            return null;
        }
        int idx = Math.floorMod(cursor.get(provider).getAndIncrement(), keys.size());
        return keys.get(idx);
    }

    public boolean hasKeys(String provider) {
        return !keysByProvider.getOrDefault(provider, List.of()).isEmpty();
    }
}
