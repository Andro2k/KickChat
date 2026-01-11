package com.kickplugin.kickchat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KickCommand implements CommandExecutor, TabCompleter {

    private final KickChat plugin;

    public KickCommand(KickChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 1. CORRECCIÓN IMPORTANTE: Verificar si escribió argumentos
        if (args.length == 0) {
            enviarAyuda(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // Unifiqué menu y bots aquí dentro para que se vea ordenado
            case "menu":
            case "bots":
                if (sender instanceof Player) {
                    new KickMenu(plugin).abrirMenuPrincipal((Player) sender);
                } else {
                    sender.sendMessage("§cSolo jugadores pueden abrir el menú.");
                }
                return true;

            case "setstreamer":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /kickchat setstreamer <nombre>");
                    return true;
                }
                plugin.cambiarStreamer(args[1], sender);
                return true;

            case "reload":
                plugin.reloadConfig();
                sender.sendMessage("§a[KickChat] §fConfiguración recargada.");
                return true;

            case "start":
                plugin.conectarChat(sender);
                return true;

            case "toggle":
                boolean estadoActual = plugin.getConfig().getBoolean("chat_enabled", true);
                plugin.getConfig().set("chat_enabled", !estadoActual);
                plugin.saveConfig();
                sender.sendMessage(!estadoActual ? "§a[KickChat] §fChat visible." : "§c[KickChat] §fChat pausado globalmente.");
                return true;

            case "mute":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /kickchat mute <usuario_kick>");
                    return true;
                }
                plugin.silenciarUsuario(sender, args[1], true);
                return true;

            case "unmute":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /kickchat unmute <usuario_kick>");
                    return true;
                }
                plugin.silenciarUsuario(sender, args[1], false);
                return true;
                
            default:
                enviarAyuda(sender);
                return true;
        }
    }

    private void enviarAyuda(CommandSender sender) {
        sender.sendMessage("§a[KickChat] §fComandos:");
        sender.sendMessage("§7/kc menu §f- Panel de Control (Bots/Usuarios/Roles).");
        sender.sendMessage("§7/kc setstreamer <nombre> §f- Vincular canal.");
        sender.sendMessage("§7/kc toggle §f- Pausar/Reanudar chat.");
        sender.sendMessage("§7/kc mute <user> §f- Silenciar usuario.");
        sender.sendMessage("§7/kc unmute <user> §f- Des-silenciar.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> sugerencias = new ArrayList<>();

        if (args.length == 1) {
            sugerencias.add("menu");
            sugerencias.add("setstreamer");
            sugerencias.add("reload");
            sugerencias.add("start");
            sugerencias.add("toggle");
            sugerencias.add("mute");
            sugerencias.add("unmute");
            return filtrar(sugerencias, args[0]);
        }
        
        // BONUS: Sugerir usuarios muteados al escribir /kc unmute
        if (args.length == 2 && args[0].equalsIgnoreCase("unmute")) {
            return filtrar(plugin.getConfig().getStringList("muted_users"), args[1]);
        }

        return null;
    }

    private List<String> filtrar(List<String> lista, String actual) {
        List<String> resultado = new ArrayList<>();
        for (String s : lista) {
            if (s.toLowerCase().startsWith(actual.toLowerCase())) resultado.add(s);
        }
        return resultado;
    }
}