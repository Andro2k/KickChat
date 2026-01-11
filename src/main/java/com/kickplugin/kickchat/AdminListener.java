package com.kickplugin.kickchat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AdminListener implements Listener {

    private final KickChat plugin;

    public AdminListener(KickChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        // Solo avisamos a los administradores
        if (!event.getPlayer().isOp() && !event.getPlayer().hasPermission("kickchat.admin")) {
            return;
        }

        String streamer = plugin.getConfig().getString("streamer_name");
        int chatroomId = plugin.getConfig().getInt("chatroom_id");

        // Si no está configurado o parece ser el default
        if (chatroomId == 0 || "theandro2k".equalsIgnoreCase(streamer)) {
            event.getPlayer().sendMessage("");
            event.getPlayer().sendMessage("§a§l[KickChat] §e¡Hola Admin!");
            event.getPlayer().sendMessage("§7Parece que el plugin aún no está vinculado a tu canal.");
            event.getPlayer().sendMessage("§7Usa: §f/kickchat setstreamer <tu_canal> §7para empezar.");
            event.getPlayer().sendMessage("");
        }
    }
}