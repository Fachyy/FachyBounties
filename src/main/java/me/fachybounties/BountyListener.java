package me.fachybounties;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BountyListener implements Listener {

    private final BountyPlugin plugin;

    private final Map<UUID, CombatEntry> combatMap = new HashMap<>();

    private static class CombatEntry {
        final UUID attackerId;
        final long timestamp;
        CombatEntry(UUID attackerId) {
            this.attackerId = attackerId;
            this.timestamp  = System.currentTimeMillis();
        }
    }

    public BountyListener(BountyPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null && !attacker.equals(victim)) {
            combatMap.put(victim.getUniqueId(), new CombatEntry(attacker.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        if (!plugin.getDetailedBounties().containsKey(victimId)) return;

        Player killer = null;

        if (victim.getKiller() != null) {
            killer = victim.getKiller();
        } else {
            int windowMs = plugin.getConfig().getInt("settings.combat-tag-seconds", 10) * 1000;
            CombatEntry entry = combatMap.get(victimId);
            if (entry != null && System.currentTimeMillis() - entry.timestamp <= windowMs) {
                Player lastAttacker = Bukkit.getPlayer(entry.attackerId);
                if (lastAttacker != null && lastAttacker.isOnline()) {
                    killer = lastAttacker;
                }
            }
        }

        combatMap.remove(victimId);
        if (killer == null) return;

        if (plugin.isKillCooldownActive(killer.getUniqueId(), victimId)) {
            String prefix = c(plugin.getConfig().getString("messages.prefix", "&6[Bounties] "));
            killer.sendMessage(prefix + c(plugin.getConfig().getString("messages.anti-farm-blocked",
                    "&cYa cobraste la recompensa de este jugador recientemente. Debes esperar.")));
            return;
        }

        double reward = plugin.getTotalBounty(victimId);
        plugin.getDetailedBounties().remove(victimId);
        plugin.getExpirationMap().remove(victimId);
        plugin.invalidateCache(victimId);
        plugin.getEconomy().depositPlayer(killer, reward);

        plugin.registerKill(killer.getUniqueId(), victimId);
        plugin.addKillRecord(killer.getName(), victim.getName(), reward);
        plugin.saveBountyData();

        String prefix = c(plugin.getConfig().getString("messages.prefix", "&6[Bounties] "));

        if (plugin.getConfig().getBoolean("effects.lightning", true)) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }

        if (plugin.getConfig().getBoolean("effects.fireworks", false)) {
            spawnFirework(victim);
        }

        if (plugin.getConfig().getBoolean("effects.sound-killer-enabled", true)) {
            String soundName = plugin.getConfig().getString("effects.sound-killer", "ENTITY_PLAYER_LEVELUP");
            float volume = (float) plugin.getConfig().getDouble("effects.sound-killer-volume", 1.0);
            float pitch  = (float) plugin.getConfig().getDouble("effects.sound-killer-pitch",  1.0);
            try {
                killer.playSound(killer.getLocation(), Sound.valueOf(soundName.toUpperCase()), volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Sonido inválido en effects.sound-killer: " + soundName);
            }
        }

        if (plugin.getConfig().getBoolean("effects.private-message-killer", true)) {
            killer.sendMessage(prefix + c(plugin.getConfig().getString("messages.bounty-claimed-killer",
                            "&a¡Cobraste &e$%amount% &apor eliminar a &e%victim%&a!")
                    .replace("%amount%", String.format("%.2f", reward))
                    .replace("%victim%",  victim.getName())
                    .replace("%killer%",  killer.getName())));
        }

        if (plugin.getConfig().getBoolean("effects.broadcast-enabled", true)) {
            Bukkit.broadcastMessage(prefix + c(plugin.getConfig().getString("messages.bounty-claimed",
                            "&6&l¡CAZADO! &e%killer% &7cobró &a$%amount% &7por matar a &e%victim%&7.")
                    .replace("%killer%", killer.getName())
                    .replace("%victim%",  victim.getName())
                    .replace("%amount%",  String.format("%.2f", reward))));
        }
    }

    private void spawnFirework(Player victim) {
        try {
            Firework fw = victim.getWorld().spawn(victim.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.YELLOW)
                    .withFade(Color.ORANGE)
                    .with(FireworkEffect.Type.STAR)
                    .trail(true)
                    .build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
        } catch (Exception e) {
            plugin.getLogger().warning("Error al lanzar fuegos artificiales: " + e.getMessage());
        }
    }

    public static void openMenu(Player player, BountyPlugin plugin, int page) {
        if (!player.hasPermission("fachybounties.list") && !player.hasPermission("fachybounties.menu")) {
            player.sendMessage(c(plugin.getConfig().getString("messages.prefix")) +
                    c(plugin.getConfig().getString("messages.no-permission-menu")));
            return;
        }

        String baseTitle = c(plugin.getConfig().getString("menu.title", "&6&lLista de Bounties"));
        Inventory inv = Bukkit.createInventory(null, 54, baseTitle + " # " + (page + 1));

        List<UUID> victims = new ArrayList<>(plugin.getDetailedBounties().keySet());
        victims.sort((a, b) -> Double.compare(plugin.getTotalBounty(b), plugin.getTotalBounty(a)));

        int start = page * 45;
        int end   = Math.min(start + 45, victims.size());

        for (int i = start; i < end; i++) {
            UUID victimId = victims.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(victimId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                String name = plugin.getCachedName(victimId);
                meta.setDisplayName(c(plugin.getConfig().getString("menu.head-name", "&e%target%")
                        .replace("%target%", name)));

                Map<UUID, Double> senders = plugin.getDetailedBounties().get(victimId);
                String senderName = "Desconocido";
                if (senders != null && !senders.isEmpty()) {
                    senderName = plugin.getCachedName(senders.keySet().iterator().next());
                }
                final String finalSender = senderName;

                List<String> lore = new ArrayList<>();
                for (String line : plugin.getConfig().getStringList("menu.lore")) {
                    lore.add(c(line
                            .replace("%amount%", String.format("%.2f", plugin.getTotalBounty(victimId)))
                            .replace("%sender%", finalSender)
                            .replace("%status%", op.isOnline()
                                    ? c(plugin.getConfig().getString("menu.status-online",  "&a● Conectado"))
                                    : c(plugin.getConfig().getString("menu.status-offline", "&8○ Desconectado")))
                            .replace("%target%", name)));
                }
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.addItem(head);
        }

        Material fillerMat = mat(plugin.getConfig().getString("menu.filler-item",  "GRAY_STAINED_GLASS_PANE"));
        Material prevMat   = mat(plugin.getConfig().getString("menu.prev-material", "RED_STAINED_GLASS_PANE"));
        Material nextMat   = mat(plugin.getConfig().getString("menu.next-material", "GREEN_STAINED_GLASS_PANE"));
        Material infoMat   = mat(plugin.getConfig().getString("menu.info-material", "PAPER"));

        int totalPages = (int) Math.ceil((double) victims.size() / 45);
        String infoName = c(plugin.getConfig().getString("menu.info-text", "&7Página &e%page% &7/ &e%total%")
                .replace("%page%",  String.valueOf(page + 1))
                .replace("%total%", String.valueOf(Math.max(1, totalPages))));

        for (int i = 45; i < 54; i++) inv.setItem(i, makeItem(fillerMat, " "));
        inv.setItem(47, makeItem(prevMat, c(plugin.getConfig().getString("menu.previous-button", "&c« Anterior"))));
        inv.setItem(49, makeItem(infoMat, infoName));
        inv.setItem(51, makeItem(nextMat, c(plugin.getConfig().getString("menu.next-button",     "&aSiguiente »"))));

        player.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        String title = c(plugin.getConfig().getString("menu.title"));
        String viewTitle = event.getView().getTitle();
        if (!viewTitle.contains(title)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        String[] parts = viewTitle.split("# ");
        if (parts.length < 2) return;
        try {
            int page = Integer.parseInt(parts[1].trim()) - 1;
            if (event.getSlot() == 47 && page > 0)
                openMenu(p, plugin, page - 1);
            else if (event.getSlot() == 51 && (page + 1) * 45 < plugin.getDetailedBounties().size())
                openMenu(p, plugin, page + 1);
        } catch (NumberFormatException ignored) {}
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        combatMap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        plugin.invalidateName(event.getPlayer().getUniqueId());
    }

    private static Material mat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.GRAY_STAINED_GLASS_PANE; }
    }

    private static ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    private static String c(String s) { return s == null ? "" : s.replace("&", "§"); }
}