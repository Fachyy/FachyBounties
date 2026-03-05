package me.fachybounties;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public BountyCommand(BountyPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String prefix = c(plugin.getConfig().getString("messages.prefix", "&6[Bounties] "));

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "list": {
                if (!player.hasPermission("fachybounties.list") && !player.hasPermission("fachybounties.menu")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-menu")));
                    return true;
                }
                BountyListener.openMenu(player, plugin, 0);
                return true;
            }

            case "top": {
                if (!player.hasPermission("fachybounties.top")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-top",
                            "&cNo tienes permiso para ver el top de recompensas.")));
                    return true;
                }
                sendTop(player, prefix);
                return true;
            }

            case "history": {
                if (!player.hasPermission("fachybounties.history")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-history",
                            "&cNo tienes permiso para ver el historial.")));
                    return true;
                }
                sendHistory(player, prefix, args);
                return true;
            }

            case "reload": {
                if (!player.hasPermission("fachybounties.admin")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-admin")));
                    return true;
                }
                plugin.reloadPluginConfig();
                player.sendMessage(prefix + "§aConfiguración y datos recargados correctamente.");
                return true;
            }

            case "search": {
                if (!player.hasPermission("fachybounties.search")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-search")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(prefix + "§cUso: /bounty search <jugador>");
                    return true;
                }
                final String searchName = args[1];
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(searchName);
                    UUID targetId = target.getUniqueId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        double total = plugin.getTotalBounty(targetId);
                        if (total > 0) {
                            Map<UUID, Double> senders = plugin.getDetailedBounties().get(targetId);
                            String senderName = "Desconocido";
                            if (senders != null && !senders.isEmpty()) {
                                senderName = plugin.getCachedName(senders.keySet().iterator().next());
                            }
                            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.search-result",
                                            "&e%player% &7tiene &a$%amount% &7puesto por &e%sender%")
                                    .replace("%player%", plugin.getCachedName(targetId))
                                    .replace("%amount%", String.format("%.2f", total))
                                    .replace("%sender%", senderName)));
                        } else {
                            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.search-not-found",
                                    "&cEse jugador no tiene recompensas activas.")));
                        }
                    });
                });
                return true;
            }

            case "remove": {
                if (!player.hasPermission("fachybounties.remove") && !player.hasPermission("fachybounties.admin")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-remove")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(prefix + "§cUso: /bounty remove <jugador>");
                    return true;
                }
                handleRemove(player, args[1], prefix);
                return true;
            }

            case "set": {
                if (!player.hasPermission("fachybounties.set")) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-permission-set")));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(prefix + "§cUso: /bounty set <jugador> <cantidad>");
                    return true;
                }
                handleSet(player, args[1], args[2], prefix);
                return true;
            }

            default:
                sendHelpMenu(player);
                return true;
        }
    }

    private void sendTop(Player player, String prefix) {
        List<UUID> victims = new ArrayList<>(plugin.getDetailedBounties().keySet());
        victims.sort((a, b) -> Double.compare(plugin.getTotalBounty(b), plugin.getTotalBounty(a)));

        String header = c(plugin.getConfig().getString("messages.top-header",
                "&6&l========= Top Recompensas ========="));
        String entryFmt = c(plugin.getConfig().getString("messages.top-entry",
                "&e#%pos% &f%player% &7- &a$%amount%"));
        String empty = c(plugin.getConfig().getString("messages.top-empty",
                "&7No hay recompensas activas."));
        String footer = c(plugin.getConfig().getString("messages.top-footer",
                "&6&l===================================="));

        int topSize = plugin.getConfig().getInt("settings.top-size", 5);

        player.sendMessage(header);
        if (victims.isEmpty()) {
            player.sendMessage(empty);
        } else {
            int count = Math.min(topSize, victims.size());
            for (int i = 0; i < count; i++) {
                UUID id = victims.get(i);
                player.sendMessage(entryFmt
                        .replace("%pos%",    String.valueOf(i + 1))
                        .replace("%player%", plugin.getCachedName(id))
                        .replace("%amount%", String.format("%.2f", plugin.getTotalBounty(id))));
            }
        }
        player.sendMessage(footer);
    }

    private void sendHistory(Player player, String prefix, String[] args) {
        List<BountyPlugin.KillRecord> allHistory = plugin.getKillHistory();
        List<BountyPlugin.KillRecord> history;

        String filterName = args.length >= 2 ? args[1].toLowerCase() : null;
        if (filterName != null) {
            List<BountyPlugin.KillRecord> filtered = new ArrayList<>();
            for (BountyPlugin.KillRecord r : allHistory) {
                if (r.killerName.equalsIgnoreCase(filterName) || r.victimName.equalsIgnoreCase(filterName)) {
                    filtered.add(r);
                }
            }
            history = filtered;
        } else {
            history = allHistory;
        }

        String header = c(plugin.getConfig().getString("messages.history-header",
                "&6&l======== Historial de Kills ========"));
        String entryFmt = c(plugin.getConfig().getString("messages.history-entry",
                "&e%killer% &7mató a &c%victim% &7y cobró &a$%amount% &8(%date%)"));
        String empty = c(plugin.getConfig().getString("messages.history-empty",
                "&7No hay registros en el historial."));
        String footer = c(plugin.getConfig().getString("messages.history-footer",
                "&6&l===================================="));

        String dateFormat = plugin.getConfig().getString("settings.history-date-format", "dd/MM/yyyy HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        int pageSize = plugin.getConfig().getInt("settings.history-page-size", 10);

        player.sendMessage(header);
        if (history.isEmpty()) {
            player.sendMessage(empty);
        } else {
            int count = Math.min(pageSize, history.size());
            for (int i = 0; i < count; i++) {
                BountyPlugin.KillRecord r = history.get(i);
                player.sendMessage(entryFmt
                        .replace("%killer%", r.killerName)
                        .replace("%victim%",  r.victimName)
                        .replace("%amount%",  String.format("%.2f", r.amount))
                        .replace("%date%",    sdf.format(new Date(r.timestamp))));
            }
        }
        player.sendMessage(footer);
    }

    private void handleRemove(Player player, String targetName, String prefix) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            UUID victimId = target.getUniqueId();
            UUID playerId = player.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> handleRemoveSync(player, victimId, playerId, prefix));
        });
    }

    private void handleRemoveSync(Player player, UUID victimId, UUID playerId, String prefix) {
        if (!player.isOnline()) return;

        if (!plugin.getDetailedBounties().containsKey(victimId)) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.no-bounty-found",
                    "&cEse jugador no tiene recompensas activas.")));
            return;
        }

        Map<UUID, Double> senders = plugin.getDetailedBounties().get(victimId);

        if (player.hasPermission("fachybounties.admin")) {

            plugin.getDetailedBounties().remove(victimId);
            plugin.getExpirationMap().remove(victimId);
            plugin.invalidateCache(victimId);
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.admin-remove-success",
                            "&c(Admin) Recompensa sobre &e%player% &celiminada.")
                    .replace("%player%", plugin.getCachedName(victimId))));
            plugin.saveBountyData();
        } else if (senders.containsKey(playerId)) {
            double refund = senders.remove(playerId);
            plugin.getEconomy().depositPlayer(player, refund);
            if (senders.isEmpty()) {
                plugin.getDetailedBounties().remove(victimId);
                plugin.getExpirationMap().remove(victimId);
            }
            plugin.invalidateCache(victimId);
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.remove-success",
                            "&7Recompensa retirada. Se te devolvieron &a$%amount%&7.")
                    .replace("%amount%", String.format("%.2f", refund))
                    .replace("%player%", plugin.getCachedName(victimId))));
            plugin.saveBountyData();
        } else {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.remove-not-yours",
                    "&cNo pusiste dinero sobre ese jugador.")));
        }
    }

    private void handleSet(Player player, String targetName, String amountStr, String prefix) {

        if (!player.hasPermission("fachybounties.bypass.cooldown")) {
            int cooldownMs = plugin.getConfig().getInt("settings.cooldown", 10) * 1000;
            Long last = cooldowns.get(player.getUniqueId());
            if (last != null) {
                long secondsLeft = (cooldownMs - (System.currentTimeMillis() - last)) / 1000;
                if (secondsLeft > 0) {
                    player.sendMessage(prefix + c(plugin.getConfig().getString("messages.cooldown-active",
                                    "&cEspera &e%seconds%s &cantes de volver a poner una recompensa.")
                            .replace("%seconds%", String.valueOf(secondsLeft))));
                    return;
                }
            }
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.invalid-amount",
                    "&cCantidad inválida. Usa un número.")));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.invalid-amount",
                    "&cCantidad inválida. Usa un número.")));
            return;
        }

        double minBounty = getMinBountyForPlayer(player);
        if (amount < minBounty) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.min-bounty",
                            "&cLa recompensa mínima es &e$%min%&c.")
                    .replace("%min%", String.format("%.2f", minBounty))));
            return;
        }

        double maxBounty = plugin.getConfig().getDouble("settings.max-bounty", 0);
        if (maxBounty > 0 && amount > maxBounty) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.max-bounty",
                            "&cLa recompensa máxima permitida es &e$%max%&c.")
                    .replace("%max%", String.format("%.2f", maxBounty))));
            return;
        }

        boolean bypassTax = player.hasPermission("fachybounties.bypass.tax");
        double taxRate = bypassTax ? 0 : plugin.getConfig().getDouble("settings.tax-rate", 0.10);
        double tax = amount * taxRate;
        double total = amount + tax;

        if (!player.hasPermission("fachybounties.bypass.cooldown")) {
            registerCooldown(player.getUniqueId());
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            boolean hasPlayed = target.hasPlayedBefore() || target.isOnline();
            UUID victimId = target.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () ->
                    handleSetSync(player, victimId, hasPlayed, amount, bypassTax, tax, total, prefix));
        });
    }

    private void handleSetSync(Player player, UUID victimId, boolean hasPlayed,
                               double amount, boolean bypassTax, double tax, double total, String prefix) {
        if (!player.isOnline()) return;
        if (!hasPlayed) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.player-never-joined",
                    "&cEse jugador nunca se ha conectado al servidor.")));
            return;
        }

        if (victimId.equals(player.getUniqueId())) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.self-bounty",
                    "&cNo puedes ponerte una recompensa a ti mismo.")));
            return;
        }

        UUID playerId = player.getUniqueId();
        Map<UUID, Double> senders = plugin.getDetailedBounties().get(victimId);

        if (senders != null && !senders.isEmpty()) {
            double currentTop = senders.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            if (amount <= currentTop) {
                player.sendMessage(prefix + c(plugin.getConfig().getString("messages.bounty-not-enough",
                                "&cDebes superar la recompensa actual de &e$%top%&c.")
                        .replace("%top%", String.format("%.2f", currentTop))));
                return;
            }
        }

        if (!plugin.getEconomy().has(player, total)) {
            player.sendMessage(prefix + c(plugin.getConfig().getString("messages.not-enough-money",
                            "&cNo tienes suficiente dinero. Necesitas &e$%total%&c.")
                    .replace("%total%", String.format("%.2f", total))));
            return;
        }

        if (senders != null && !senders.isEmpty()) {
            for (Map.Entry<UUID, Double> entry : senders.entrySet()) {
                OfflinePlayer prev = Bukkit.getOfflinePlayer(entry.getKey());
                plugin.getEconomy().depositPlayer(prev, entry.getValue());
                Player prevOnline = Bukkit.getPlayer(entry.getKey());
                if (prevOnline != null) {
                    prevOnline.sendMessage(prefix + c(plugin.getConfig().getString(
                                    "messages.bounty-replaced-refund",
                                    "&7Tu recompensa de &e$%amount% &7por &e%target% &7fue superada. Se te devolvió el dinero.")
                            .replace("%amount%", String.format("%.2f", entry.getValue()))
                            .replace("%target%", plugin.getCachedName(victimId))));
                }
            }
            senders.clear();
        }

        plugin.getEconomy().withdrawPlayer(player, total);
        plugin.getDetailedBounties()
                .computeIfAbsent(victimId, k -> new HashMap<>())
                .put(playerId, amount);

        plugin.getExpirationMap().put(victimId, System.currentTimeMillis());

        plugin.invalidateCache(victimId);
        plugin.saveBountyData();

        String successMsg = bypassTax
                ? plugin.getConfig().getString("messages.bounty-set-success-notax",
                "&aRecompensa de &e$%total% &apuesta sobre &e%target%&a.")
                : plugin.getConfig().getString("messages.bounty-set-success",
                "&aRecompensa puesta. Se cobraron &e$%total% &a(incluye &e$%tax% &ade comisión).");
        player.sendMessage(prefix + c(successMsg
                .replace("%total%",  String.format("%.2f", total))
                .replace("%tax%",    String.format("%.2f", tax))
                .replace("%target%", plugin.getCachedName(victimId))));

        Bukkit.broadcastMessage(prefix + c(plugin.getConfig().getString("messages.bounty-broadcast",
                        "&e%player% &7puso una recompensa de &a$%amount% &7sobre &e%target%&7.")
                .replace("%player%",  player.getName())
                .replace("%amount%",  String.format("%.2f", amount))
                .replace("%target%",  plugin.getCachedName(victimId))));

        if (plugin.getConfig().getBoolean("notify.on-bounty-placed", true)) {
            Player targetOnline = Bukkit.getPlayer(victimId);
            if (targetOnline != null) {
                targetOnline.sendMessage(prefix + c(plugin.getConfig().getString(
                                "messages.bounty-placed-notify",
                                "&c¡Alguien puso una recompensa de &e$%amount% &csobre tu cabeza!")
                        .replace("%amount%",  String.format("%.2f", amount))
                        .replace("%placer%",  player.getName())));
            }
        }
    }

    private double getMinBountyForPlayer(Player player) {
        double baseMin = plugin.getConfig().getDouble("settings.min-bounty", 100);
        double highest = -1;

        org.bukkit.configuration.ConfigurationSection minSection = plugin.getConfig().getConfigurationSection("rank-min-bounty");
        if (minSection == null) return baseMin;

        for (String key : minSection.getKeys(false)) {
            try {
                double val = Double.parseDouble(key);
                if (player.hasPermission("fachybounties.minbounty." + key) && val > highest) {
                    highest = val;
                }
            } catch (NumberFormatException ignored) {}
        }

        return highest > 0 ? highest : baseMin;
    }

    private void sendHelpMenu(Player player) {
        for (String line : plugin.getConfig().getStringList("help")) {
            String permission = null;
            String display = line;

            if      (line.startsWith("[list]"))    { permission = "fachybounties.list";    display = line.substring(6); }
            else if (line.startsWith("[set]"))     { permission = "fachybounties.set";     display = line.substring(5); }
            else if (line.startsWith("[search]"))  { permission = "fachybounties.search";  display = line.substring(8); }
            else if (line.startsWith("[remove]"))  { permission = "fachybounties.remove";  display = line.substring(8); }
            else if (line.startsWith("[top]"))     { permission = "fachybounties.top";     display = line.substring(5); }
            else if (line.startsWith("[history]")) { permission = "fachybounties.history"; display = line.substring(9); }
            else if (line.startsWith("[admin]"))   { permission = "fachybounties.admin";   display = line.substring(7); }

            if (permission == null || player.hasPermission(permission)) {
                player.sendMessage(c(display));
            }
        }
    }

    private void registerCooldown(UUID id) {
        int cooldownMs = plugin.getConfig().getInt("settings.cooldown", 10) * 1000;
        cooldowns.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > cooldownMs);
        cooldowns.put(id, System.currentTimeMillis());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("help");
            if (sender.hasPermission("fachybounties.list") || sender.hasPermission("fachybounties.menu")) subs.add("list");
            if (sender.hasPermission("fachybounties.set"))     subs.add("set");
            if (sender.hasPermission("fachybounties.search"))  subs.add("search");
            if (sender.hasPermission("fachybounties.remove"))  subs.add("remove");
            if (sender.hasPermission("fachybounties.top"))     subs.add("top");
            if (sender.hasPermission("fachybounties.history")) subs.add("history");
            if (sender.hasPermission("fachybounties.admin"))   subs.add("reload");
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set") || sub.equals("search") || sub.equals("remove") || sub.equals("history")) {
                return null;
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private String c(String s) { return plugin.colorize(s); }

}