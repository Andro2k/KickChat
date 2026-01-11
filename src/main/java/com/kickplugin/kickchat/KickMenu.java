package com.kickplugin.kickchat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

public class KickMenu implements Listener {

    private final KickChat plugin;
    private final String TITLE_MAIN = "§8Panel de Control Kick";
    private final String TITLE_LIST = "§8Lista: ";

    public KickMenu(KickChat plugin) {
        this.plugin = plugin;
    }

    // --- MENÚ PRINCIPAL ---
    public void abrirMenuPrincipal(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE_MAIN);

        // Botones de Categorías
        gui.setItem(10, crearItem(Material.GOLDEN_HELMET, "§6§lSuscriptores", "§7Gestionar Subs detectados"));
        gui.setItem(11, crearItem(Material.DIAMOND_SWORD, "§2§lModeradores", "§7Gestionar Mods detectados"));
        gui.setItem(12, crearItem(Material.PLAYER_HEAD, "§7§lUsuarios", "§7Usuarios normales"));
        gui.setItem(14, crearItem(Material.COMMAND_BLOCK, "§b§lBots", "§7Bots del sistema"));
        gui.setItem(16, crearItem(Material.BARRIER, "§c§lSilenciados (Baneados)", "§7Ver lista negra"));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    // --- SUB-MENÚ (LISTA DE USUARIOS) ---
    public void abrirCategoria(Player player, String categoria) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE_LIST + categoria);
        
        Set<String> usuarios;
        
        // Cargamos la lista según la categoría elegida
        if (categoria.equals("Silenciados")) {
            usuarios = new java.util.HashSet<>(plugin.getConfig().getStringList("muted_users"));
        } else if (categoria.equals("Bots")) {
            usuarios = new java.util.HashSet<>(plugin.getConfig().getStringList("bot_settings.known_bots"));
        } else {
            // Buscamos en la sección de roles guardados
            // La estructura en config será: user_data.<nombre>.role = "MOD"
            usuarios = new java.util.HashSet<>();
            if (plugin.getConfig().isConfigurationSection("user_data")) {
                for (String key : plugin.getConfig().getConfigurationSection("user_data").getKeys(false)) {
                    String role = plugin.getConfig().getString("user_data." + key + ".role", "USER");
                    
                    if (categoria.equals("Suscriptores") && role.equals("SUB")) usuarios.add(key);
                    if (categoria.equals("Moderadores") && (role.equals("MOD") || role.equals("STREAMER"))) usuarios.add(key);
                    if (categoria.equals("Usuarios") && role.equals("USER")) usuarios.add(key);
                }
            }
        }

        // Llenar inventario
        List<String> silenciados = plugin.getConfig().getStringList("muted_users");
        
        for (String nombre : usuarios) {
            boolean isMuted = silenciados.contains(nombre);
            Material mat = isMuted ? Material.RED_WOOL : Material.LIME_WOOL;
            
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + nombre);
            meta.setLore(Arrays.asList(
                isMuted ? "§c[SILENCIADO]" : "§a[ACTIVO]", 
                "§7Clic para cambiar estado"
            ));
            item.setItemMeta(meta);
            gui.addItem(item);
        }

        // Botón de Volver
        gui.setItem(49, crearItem(Material.ARROW, "§cVolver", ""));
        
        player.openInventory(gui);
    }

    private ItemStack crearItem(Material mat, String nombre, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nombre);
        if (!lore.isEmpty()) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void alHacerClic(InventoryClickEvent event) {
        String titulo = event.getView().getTitle();
        if (!titulo.startsWith("§8")) return; // Filtro rápido
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        Player player = (Player) event.getWhoClicked();
        String itemNombre = event.getCurrentItem().getItemMeta().getDisplayName();

        // LOGICA MENU PRINCIPAL
        if (titulo.equals(TITLE_MAIN)) {
            if (itemNombre.contains("Suscriptores")) abrirCategoria(player, "Suscriptores");
            if (itemNombre.contains("Moderadores")) abrirCategoria(player, "Moderadores");
            if (itemNombre.contains("Usuarios")) abrirCategoria(player, "Usuarios");
            if (itemNombre.contains("Bots")) abrirCategoria(player, "Bots");
            if (itemNombre.contains("Silenciados")) abrirCategoria(player, "Silenciados");
            return;
        }

        // LOGICA SUB-MENUS
        if (titulo.startsWith(TITLE_LIST)) {
            if (event.getCurrentItem().getType() == Material.ARROW) {
                abrirMenuPrincipal(player);
                return;
            }
            
            // Toggle Mute/Unmute
            String usuarioTarget = itemNombre.replace("§e", "");
            List<String> silenciados = plugin.getConfig().getStringList("muted_users");
            
            if (silenciados.contains(usuarioTarget)) {
                silenciados.remove(usuarioTarget);
                player.sendMessage("§a[KickChat] §fHas desbloqueado a: " + usuarioTarget);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            } else {
                silenciados.add(usuarioTarget);
                player.sendMessage("§c[KickChat] §fHas silenciado a: " + usuarioTarget);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            }
            
            plugin.getConfig().set("muted_users", silenciados);
            plugin.saveConfig();
            
            // Recargar la misma categoría para ver el cambio
            String categoriaActual = titulo.replace(TITLE_LIST, "");
            abrirCategoria(player, categoriaActual);
        }
    }
}