package me.fachybounties;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class BountyExpansion extends PlaceholderExpansion {

    private final BountyPlugin plugin;

    private volatile List<Map.Entry<UUID, Double>> cachedTop = new ArrayList<>();
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 5000;

    public BountyExpansion(BountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "fachybounties"; }

    @Override
    public @NotNull String getAuthor() { return "Fachy"; }

    @Override
    public @NotNull String getVersion() { return "1.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (plugin.getDetailedBounties().isEmpty()) return "0";

        if (params.startsWith("top_name_")) {
            try {
                int index = Integer.parseInt(params.replace("top_name_", "")) - 1;
                List<Map.Entry<UUID, Double>> top = getSortedBounties();
                if (index >= 0 && index < top.size()) {
                    return plugin.getCachedName(top.get(index).getKey());
                }
            } catch (NumberFormatException e) { return "Error"; }
            return "Nadie";
        }

        if (params.startsWith("top_amount_")) {
            try {
                int index = Integer.parseInt(params.replace("top_amount_", "")) - 1;
                List<Map.Entry<UUID, Double>> top = getSortedBounties();
                if (index >= 0 && index < top.size()) {
                    return String.format("%.2f", top.get(index).getValue());
                }
            } catch (NumberFormatException e) { return "Error"; }
            return "0";
        }

        if (params.equalsIgnoreCase("amount")) {
            if (player == null) return "0";
            return String.format("%.2f", plugin.getTotalBounty(player.getUniqueId()));
        }

        return null;
    }

    private synchronized List<Map.Entry<UUID, Double>> getSortedBounties() {
        if (System.currentTimeMillis() - lastCacheTime > CACHE_TTL_MS) {
            cachedTop = plugin.getDetailedBounties().keySet().stream()
                    .map(uuid -> Map.entry(uuid, plugin.getTotalBounty(uuid)))
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .collect(Collectors.toList());
            lastCacheTime = System.currentTimeMillis();
        }
        return cachedTop;
    }
}