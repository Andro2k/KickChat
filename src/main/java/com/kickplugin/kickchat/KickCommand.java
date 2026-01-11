package com.kickplugin.kickchat;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter; // Importante para las sugerencias
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// AÑADIDO: "implements TabCompleter"
public class KickCommand implements CommandExecutor, TabCompleter {

    private final KickChat plugin;

    public KickCommand(KickChat plugin) {
        this.plugin = plugin;
    }

    // --- EJECUCIÓN DEL COMANDO ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[KickChat] §fComandos disponibles:");
            sender.sendMessage("§7/kickchat bots §f- Gestionar bots.");
            sender.sendMessage("§7/kickchat setstreamer <nombre> §f- Cambiar canal.");
            sender.sendMessage("§7/kickchat reload §f- Recargar config.");
            sender.sendMessage("§7/kickchat start §f- Reconectar.");
            return true;
        }

        if (args[0].equalsIgnoreCase("bots")) {
            if (sender instanceof Player) {
                new BotInventory(plugin).abrirMenu((Player) sender);
            } else {
                sender.sendMessage("§cSolo jugadores.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("setstreamer")) {
            if (args.length < 2) {
                sender.sendMessage("§cUso: /kickchat setstreamer <nombre>");
                return true;
            }
            plugin.cambiarStreamer(args[1], sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§a[KickChat] §fConfiguración recargada.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            plugin.conectarChat(sender);
            return true;
        }

        return false;
    }

    // --- NUEVO: SUGERENCIAS AL PULSAR TAB ---
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> sugerencias = new ArrayList<>();

        // Si el usuario está escribiendo el PRIMER argumento (ej: /kickchat s...)
        if (args.length == 1) {
            sugerencias.add("bots");
            sugerencias.add("setstreamer");
            sugerencias.add("reload");
            sugerencias.add("start");
            return filtrar(sugerencias, args[0]);
        }
        
        // Si está escribiendo el SEGUNDO argumento para "setstreamer"
        if (args.length == 2 && args[0].equalsIgnoreCase("setstreamer")) {
            sugerencias.add("<nombre_streamer>"); // Texto de ayuda visual
            return sugerencias;
        }

        return null; // Devuelve null para usar sugerencias por defecto (nombres de jugadores)
    }

    // Utilidad para filtrar sugerencias mientras escribes (ej: Si escribe "se", sugiere "setstreamer")
    private List<String> filtrar(List<String> lista, String actual) {
        List<String> resultado = new ArrayList<>();
        for (String s : lista) {
            if (s.toLowerCase().startsWith(actual.toLowerCase())) {
                resultado.add(s);
            }
        }
        return resultado;
    }
}