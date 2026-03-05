package me.fachybounties;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BountyPlugin extends JavaPlugin {

    private Economy econ = null;

    private final Map<UUID, Map<UUID, Double>> detailedBounties = new HashMap<>();

    private final Map<UUID, Long> expirationMap = new HashMap<>();

    private final Map<String, Long> killCooldownMap = new HashMap<>();

    private final List<KillRecord> killHistory = new ArrayList<>();

    private final Map<UUID, Double> totalCache = new HashMap<>();

    private final Map<UUID, String> nameCache = new HashMap<>();

    private File dataFile;
    private YamlConfiguration dataConfig;

    public static class KillRecord {
        public final String killerName;
        public final String victimName;
        public final double amount;
        public final long timestamp;

        public KillRecord(String killerName, String victimName, double amount, long timestamp) {
            this.killerName = killerName;
            this.victimName = victimName;
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("¡Vault o un plugin de economía no encontrado! Desactivando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadData();

        if (getCommand("bounty") != null) {
            BountyCommand bountyCommand = new BountyCommand(this);
            getCommand("bounty").setExecutor(bountyCommand);
            getCommand("bounty").setTabCompleter(bountyCommand);
        }

        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BountyExpansion(this).register();
            getLogger().info("¡PlaceholderAPI detectado y expansión registrada!");
        }

        long interval = 20L * 60 * 5;
        Bukkit.getScheduler().runTaskTimer(this, this::checkExpiredBounties, interval, interval);

        getLogger().info("========================================");
        getLogger().info(" FachyBounties v1.0 - ¡SISTEMA ACTIVO!");
        getLogger().info(" BY: https://github.com/Fachyy");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (dataConfig == null || dataFile == null) {
            getLogger().warning("FachyBounties - No se pudo guardar: datos no inicializados.");
            return;
        }
        writeDataToConfig(dataConfig);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Error al guardar data.yml en shutdown");
        }
        getLogger().info("FachyBounties - Datos guardados correctamente.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public void loadData() {
        detailedBounties.clear();
        expirationMap.clear();
        killCooldownMap.clear();
        killHistory.clear();
        totalCache.clear();
        nameCache.clear();

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) {
                getLogger().severe("No se pudo crear data.yml");
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection bountySection = dataConfig.getConfigurationSection("bounties");
        if (bountySection != null) {
            for (String victimKey : bountySection.getKeys(false)) {
                try {
                    UUID victimUUID = UUID.fromString(victimKey);
                    Map<UUID, Double> senders = new HashMap<>();
                    ConfigurationSection sub = bountySection.getConfigurationSection(victimKey);
                    if (sub != null) {
                        for (String senderKey : sub.getKeys(false)) {
                            try {
                                senders.put(UUID.fromString(senderKey), sub.getDouble(senderKey));
                            } catch (IllegalArgumentException e) {
                                getLogger().warning("Sender UUID inválido: " + senderKey);
                            }
                        }
                    }
                    detailedBounties.put(victimUUID, senders);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Victim UUID inválido: " + victimKey);
                }
            }
        }

        ConfigurationSection expSection = dataConfig.getConfigurationSection("expiration");
        if (expSection != null) {
            for (String key : expSection.getKeys(false)) {
                try {
                    expirationMap.put(UUID.fromString(key), expSection.getLong(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection coolSection = dataConfig.getConfigurationSection("killcooldowns");
        if (coolSection != null) {
            for (String key : coolSection.getKeys(false)) {
                killCooldownMap.put(key, coolSection.getLong(key));
            }
        }

        ConfigurationSection histSection = dataConfig.getConfigurationSection("history");
        if (histSection != null) {
            List<KillRecord> loaded = new ArrayList<>();
            for (String key : histSection.getKeys(false)) {
                try {
                    ConfigurationSection entry = histSection.getConfigurationSection(key);
                    if (entry != null) {
                        loaded.add(new KillRecord(
                                entry.getString("killer", "?"),
                                entry.getString("victim", "?"),
                                entry.getDouble("amount"),
                                entry.getLong("timestamp", System.currentTimeMillis())
                        ));
                    }
                } catch (Exception ignored) {}
            }
            loaded.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            killHistory.addAll(loaded);
        }
    }

    private void writeDataToConfig(YamlConfiguration cfg) {
        cfg.set("bounties",     null);
        cfg.set("expiration",   null);
        cfg.set("killcooldowns", null);
        cfg.set("history",      null);

        detailedBounties.forEach((victim, senders) ->
                senders.forEach((sender, amount) ->
                        cfg.set("bounties." + victim + "." + sender, amount)
                )
        );

        expirationMap.forEach((uuid, ts) ->
                cfg.set("expiration." + uuid, ts)
        );

        long antiFarmMs = getConfig().getLong("settings.anti-farm-hours", 4) * 3600000L;
        if (antiFarmMs > 0) {
            killCooldownMap.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > antiFarmMs);
        }
        killCooldownMap.forEach((key, ts) -> cfg.set("killcooldowns." + key, ts));

        int maxHistory = getConfig().getInt("settings.history-size", 50);
        List<KillRecord> toSave = killHistory.size() > maxHistory
                ? killHistory.subList(0, maxHistory) : killHistory;
        for (int i = 0; i < toSave.size(); i++) {
            KillRecord r = toSave.get(i);
            String base = "history." + i + ".";
            cfg.set(base + "killer",    r.killerName);
            cfg.set(base + "victim",    r.victimName);
            cfg.set(base + "amount",    r.amount);
            cfg.set(base + "timestamp", r.timestamp);
        }
    }

    public void saveBountyData() {

        final YamlConfiguration snapshot = new YamlConfiguration();
        writeDataToConfig(snapshot);
        final File target = dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try { snapshot.save(target); } catch (IOException e) {
                getLogger().severe("Error al guardar data.yml");
            }
        });
    }

    public void checkExpiredBounties() {
        int expiryDays = getConfig().getInt("settings.bounty-expiry-days", 0);
        if (expiryDays <= 0) return;

        long expiryMs = (long) expiryDays * 86400000L;
        long now = System.currentTimeMillis();
        String prefix = colorize(getConfig().getString("messages.prefix", "&6[Bounties] "));

        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : expirationMap.entrySet()) {
            if (now - entry.getValue() >= expiryMs) {
                expired.add(entry.getKey());
            }
        }

        for (UUID victimId : expired) {
            Map<UUID, Double> senders = detailedBounties.get(victimId);
            if (senders != null) {
                for (Map.Entry<UUID, Double> senderEntry : senders.entrySet()) {
                    OfflinePlayer senderOp = Bukkit.getOfflinePlayer(senderEntry.getKey());
                    econ.depositPlayer(senderOp, senderEntry.getValue());
                    Player onlineSender = Bukkit.getPlayer(senderEntry.getKey());
                    if (onlineSender != null) {
                        String msg = getConfig().getString("messages.bounty-expired-refund",
                                "&7La recompensa sobre &e%target% &7expiró. Te devolvieron &a$%amount%&7.");
                        onlineSender.sendMessage(prefix + colorize(msg
                                .replace("%target%", getCachedName(victimId))
                                .replace("%amount%", String.format("%.2f", senderEntry.getValue()))));
                    }
                }
                detailedBounties.remove(victimId);
                invalidateCache(victimId);
            }
            expirationMap.remove(victimId);

            Player victimOnline = Bukkit.getPlayer(victimId);
            if (victimOnline != null) {
                String msg = getConfig().getString("messages.bounty-expired",
                        "&7La recompensa sobre tu cabeza ha expirado.");
                victimOnline.sendMessage(prefix + colorize(msg));
            }

            getLogger().info("Bounty expirado para: " + getCachedName(victimId));
        }

        if (!expired.isEmpty()) saveBountyData();
    }

    public boolean isKillCooldownActive(UUID killerId, UUID victimId) {
        long antiFarmMs = getConfig().getLong("settings.anti-farm-hours", 4) * 3600000L;
        if (antiFarmMs <= 0) return false;
        Long last = killCooldownMap.get(killerId + ":" + victimId);
        if (last == null) return false;
        return System.currentTimeMillis() - last < antiFarmMs;
    }

    public void registerKill(UUID killerId, UUID victimId) {
        long antiFarmMs = getConfig().getLong("settings.anti-farm-hours", 4) * 3600000L;
        if (antiFarmMs <= 0) return;
        killCooldownMap.put(killerId + ":" + victimId, System.currentTimeMillis());
    }

    public void addKillRecord(String killerName, String victimName, double amount) {
        killHistory.add(0, new KillRecord(killerName, victimName, amount, System.currentTimeMillis()));
        int max = getConfig().getInt("settings.history-size", 50);
        if (killHistory.size() > max) killHistory.subList(max, killHistory.size()).clear();
    }

    public List<KillRecord> getKillHistory() { return Collections.unmodifiableList(killHistory); }

    public double getTotalBounty(UUID victim) {
        return totalCache.computeIfAbsent(victim, id -> {
            if (!detailedBounties.containsKey(id)) return 0.0;
            return detailedBounties.get(id).values().stream().mapToDouble(Double::doubleValue).sum();
        });
    }

    public void invalidateCache(UUID victim) { totalCache.remove(victim); }

    public String getCachedName(UUID uuid) {
        return nameCache.computeIfAbsent(uuid, id -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name != null ? name : "Desconocido";
        });
    }

    public void invalidateName(UUID uuid) { nameCache.remove(uuid); }

    public String colorize(String s) { return s == null ? "" : s.replace("&", "§"); }

    public void reloadPluginConfig() { reloadConfig(); loadData(); }

    public Map<UUID, Map<UUID, Double>> getDetailedBounties() { return detailedBounties; }
    public Map<UUID, Long> getExpirationMap()                 { return expirationMap; }
    public Economy getEconomy()                               { return econ; }
}