package com.kickplugin.kickchat;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BotInventory implements Listener {

    private final KickChat plugin;
    private final String TITLE = "§8Gestión de Bots Kick";

    public BotInventory(KickChat plugin) {
        this.plugin = plugin;
    }

    // Método para abrir el menú
    public void abrirMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE);

        List<String> conocidos = plugin.getConfig().getStringList("bot_settings.known_bots");
        List<String> bloqueados = plugin.getConfig().getStringList("bot_settings.blocked_list");

        for (String botName : conocidos) {
            boolean isBlocked = bloqueados.contains(botName);
            
            // Verde = Activo, Rojo = Bloqueado
            Material material = isBlocked ? Material.RED_WOOL : Material.LIME_WOOL;
            String estado = isBlocked ? "§c[SILENCIADO]" : "§a[ACTIVO]";

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            meta.setDisplayName("§e" + botName);
            meta.setLore(Arrays.asList(
                "§7Estado actual: " + estado,
                "",
                isBlocked ? "§eClic para §aACTIVAR" : "§eClic para §cSILENCIAR"
            ));
            
            item.setItemMeta(meta);
            gui.addItem(item);
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    // Evento al hacer clic
    @EventHandler
    public void alHacerClic(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true); // No robar items
        
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        String botName = event.getCurrentItem().getItemMeta().getDisplayName().replace("§e", "");
        
        List<String> bloqueados = plugin.getConfig().getStringList("bot_settings.blocked_list");

        if (bloqueados.contains(botName)) {
            bloqueados.remove(botName); // Desbloquear
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        } else {
            bloqueados.add(botName); // Bloquear
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        }

        plugin.getConfig().set("bot_settings.blocked_list", bloqueados);
        plugin.saveConfig();
        
        abrirMenu(player); // Refrescar menú
    }
}