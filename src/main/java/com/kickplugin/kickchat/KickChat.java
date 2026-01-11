package com.kickplugin.kickchat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;

public class KickChat extends JavaPlugin {

    private Pusher pusher;
    private Channel chatChannel;
    private Channel eventsChannel;
    
    // Claves públicas de Kick
    private final String KICK_KEY = "32cbd69e4b950bf97679";
    private final String KICK_CLUSTER = "us2";

    @Override
    public void onEnable() {
        mostrarBanner();
        saveDefaultConfig();
        
        // 1. REGISTRO DE COMANDOS
        var cmd = getCommand("kickchat");
        if (cmd != null) {
            KickCommand executor = new KickCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        
        // 2. REGISTRO DE EVENTOS (Menú Gráfico y Listener de Admins)
        getServer().getPluginManager().registerEvents(new KickMenu(this), this);
        getServer().getPluginManager().registerEvents(new AdminListener(this), this);
        
        // 3. CONEXIÓN INICIAL
        conectarChat(Bukkit.getConsoleSender());
    }

    @Override
    public void onDisable() {
        desconectar();
        getLogger().info("KickChat desactivado.");
    }

    // --- MÉTODOS PÚBLICOS (API INTERNA) ---

    public void cambiarStreamer(String nuevoNombre, CommandSender sender) {
        sender.sendMessage("§a[KickChat] §7Actualizando streamer a: §e" + nuevoNombre);
        getConfig().set("streamer_name", nuevoNombre);
        // Reseteamos IDs para obligar a buscar los nuevos
        getConfig().set("chatroom_id", 0); 
        getConfig().set("channel_id", 0);
        saveConfig();
        conectarChat(sender);
    }

    public void silenciarUsuario(CommandSender sender, String usuario, boolean silenciar) {
        List<String> silenciados = getConfig().getStringList("muted_users");
        
        if (silenciar) {
            if (!silenciados.contains(usuario)) {
                silenciados.add(usuario);
                sender.sendMessage("§c[KickChat] §fUsuario " + usuario + " silenciado.");
            } else {
                sender.sendMessage("§c[KickChat] §fEl usuario ya estaba silenciado.");
            }
        } else {
            if (silenciados.remove(usuario)) {
                sender.sendMessage("§a[KickChat] §fUsuario " + usuario + " perdonado.");
                // Reiniciamos sus intentos de apelación al perdonarlo
                getConfig().set("user_data." + usuario + ".appeals", 0);
            } else {
                sender.sendMessage("§c[KickChat] §fEse usuario no estaba silenciado.");
            }
        }
        getConfig().set("muted_users", silenciados);
        saveConfig();
    }

    // --- LÓGICA PRINCIPAL: PROCESAMIENTO DE MENSAJES ---

    private void procesarMensaje(String json) {
        // Verificar si el chat está apagado globalmente
        if (!getConfig().getBoolean("chat_enabled", true)) return;

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                JsonObject data = JsonParser.parseString(json).getAsJsonObject();
                String mensaje = data.get("content").getAsString();
                JsonObject senderObj = data.get("sender").getAsJsonObject();
                String senderName = senderObj.get("username").getAsString();

                // 1. DETECTOR DE ROLES (Para clasificar en el Menú)
                String role = "USER";
                if (senderObj.has("identity") && senderObj.getAsJsonObject("identity").has("badges")) {
                    JsonArray badges = senderObj.getAsJsonObject("identity").getAsJsonArray("badges");
                    for (JsonElement badge : badges) {
                        String type = badge.getAsJsonObject().get("type").getAsString();
                        if (type.equals("broadcaster")) role = "STREAMER";
                        else if (type.equals("moderator")) role = "MOD";
                        else if (type.equals("subscriber")) role = "SUB";
                    }
                }
                
                // Guardamos el rol en la config si no es un bot
                if (!senderName.startsWith("@") && !senderName.equalsIgnoreCase("Kicklet")) {
                    getConfig().set("user_data." + senderName + ".role", role);
                }

                // 2. SISTEMA DE APELACIÓN (!unban)
                List<String> silenciados = getConfig().getStringList("muted_users");
                if (silenciados.contains(senderName)) {
                    // Solo escuchamos si pide desban
                    if (mensaje.toLowerCase().startsWith("!unban")) {
                        int intentos = getConfig().getInt("user_data." + senderName + ".appeals", 0);
                        
                        if (intentos < 2) {
                            String razon = mensaje.length() > 7 ? mensaje.substring(7) : "Sin razón";
                            // Avisar a admins conectados
                            Bukkit.broadcast("§c§l[Solicitud Desban] §e" + senderName + ": §f" + razon, "kickchat.admin");
                            
                            // Aumentar contador y guardar
                            getConfig().set("user_data." + senderName + ".appeals", intentos + 1);
                            saveConfig();
                        }
                    }
                    return; // Muteado = No sale en chat público
                }

                // 3. APRENDIZAJE DE BOTS
                if (senderName.startsWith("@") || senderName.equalsIgnoreCase("Kicklet")) {
                    List<String> conocidos = getConfig().getStringList("bot_settings.known_bots");
                    if (!conocidos.contains(senderName)) {
                        conocidos.add(senderName);
                        getConfig().set("bot_settings.known_bots", conocidos);
                        saveConfig(); 
                    }
                }

                // 4. FILTRO DE BOTS BLOQUEADOS
                if (getConfig().getBoolean("bot_settings.filter_enabled", true)) {
                    List<String> botsBloqueados = getConfig().getStringList("bot_settings.blocked_list");
                    for (String botName : botsBloqueados) {
                        if (senderName.equalsIgnoreCase(botName)) return;
                    }
                }

                // 5. ASIGNACIÓN DE COLORES POR ROL
                String prefixColor = "§7"; // Gris por defecto
                if (role.equals("STREAMER")) prefixColor = "§4§lSTREAMER §c";
                else if (role.equals("MOD")) prefixColor = "§2§lMOD §a";
                else if (role.equals("SUB")) prefixColor = "§6§lSUB §e";
                
                // Color especial para Bots
                if (senderName.startsWith("@")) {
                    prefixColor = getConfig().getString("bot_settings.at_name_color", "&b").replace("&", "§");
                }

                // 6. TRADUCCIÓN DE EMOTES
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[emote:\\d+:([^\\]]+)\\]");
                java.util.regex.Matcher matcher = pattern.matcher(mensaje);
                StringBuilder buffer = new StringBuilder();
                while (matcher.find()) {
                    String nombreEmote = matcher.group(1); 
                    String emojiReal = EmojiUtils.traducir(nombreEmote);
                    matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(emojiReal));
                }
                matcher.appendTail(buffer);
                String mensajeFinal = buffer.toString();

                // 7. EMITIR MENSAJE
                Bukkit.broadcastMessage("§a[Kick] " + prefixColor + senderName + "§8: §f" + mensajeFinal);

            } catch (Exception e) {
                // Error parseando JSON, ignoramos
            }
        });
    }

    // --- CONEXIÓN A KICK ---

    public void conectarChat(CommandSender sender) {
        desconectar();
        String streamer = getConfig().getString("streamer_name");
        int chatroomId = getConfig().getInt("chatroom_id", 0);
        int channelId = getConfig().getInt("channel_id", 0);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int finalChatId = chatroomId;
            int finalChannelId = channelId;

            // Si falta algún ID, buscamos en la API
            if (finalChatId == 0 || finalChannelId == 0) {
                sender.sendMessage("§a[KickChat] §7Buscando IDs para: " + streamer + "...");
                int[] ids = obtenerIds(streamer);
                
                if (ids != null) {
                    finalChannelId = ids[0];
                    finalChatId = ids[1];
                    getConfig().set("channel_id", finalChannelId);
                    getConfig().set("chatroom_id", finalChatId);
                    saveConfig();
                    sender.sendMessage("§a[KickChat] §fIDs guardados. Chat: " + finalChatId);
                } else {
                    sender.sendMessage("§c[KickChat] §eError API. Revisa el nombre o intenta más tarde.");
                    return; 
                }
            }

            try {
                conectarPusher(finalChannelId, finalChatId, sender);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void conectarPusher(int channelId, int chatroomId, CommandSender sender) {
        PusherOptions options = new PusherOptions().setCluster(KICK_CLUSTER);
        pusher = new Pusher(KICK_KEY, options);

        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                if (change.getCurrentState() == ConnectionState.CONNECTED) {
                     Bukkit.getScheduler().runTask(KickChat.this, () -> 
                         sender.sendMessage("§a[KickChat] §fConectado exitosamente.")
                     );
                }
            }
            @Override
            public void onError(String message, String code, Exception e) {
                getLogger().severe("Error Pusher: " + message);
            }
        }, ConnectionState.ALL);

        // Suscripción al Chat
        chatChannel = pusher.subscribe("chatrooms." + chatroomId + ".v2");
        chatChannel.bind("App\\Events\\ChatMessageEvent", event -> procesarMensaje(event.getData()));

        // Suscripción a Eventos (Followers)
        eventsChannel = pusher.subscribe("channel." + channelId);
        eventsChannel.bind("App\\Events\\FollowersUpdated", event -> lanzarCelebracion());
    }

    // --- UTILIDADES ---

    private void lanzarCelebracion() {
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.broadcastMessage("§d§l§k|||§r §d§l¡NUEVO SEGUIDOR EN KICK! §d§l§k|||");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§d¡Nuevo Follower!", "§fGracias por el apoyo ❤", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                lanzarFuegosArtificiales(p);
            }
        });
    }

    private void lanzarFuegosArtificiales(Player p) {
        Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.LIME, Color.GREEN)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    private int[] obtenerIds(String streamerSlug) {
        try {
            String url = "https://kick.com/api/v1/channels/" + streamerSlug;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // Header largo para evitar bloqueo 403 de Cloudflare
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                int channelId = json.get("id").getAsInt();
                int chatroomId = json.has("chatroom") ? json.get("chatroom").getAsJsonObject().get("id").getAsInt() : 0;
                return new int[]{channelId, chatroomId};
            } else {
                getLogger().warning("Error conectando a Kick. Código: " + response.statusCode());
            }
        } catch (Exception e) {
            getLogger().warning("Error API Kick: " + e.getMessage());
        }
        return null;
    }
    
    public void desconectar() {
        if (pusher != null) {
            pusher.disconnect();
            pusher = null;
        }
    }
    
    private void mostrarBanner() {
        String[] banner = {
            "§a██████████████████████████████████████████",
            "§a█─██─█───█────█─██─████────█─██─█────█───█",
            "§a█─█─███─██─██─█─█─█████─██─█─██─█─██─██─██",
            "§a█──████─██─████──██████─████────█────██─██",
            "§a█─█─███─██─██─█─█─█████─██─█─██─█─██─██─██",
            "§a█─██─█───█────█─██─████────█─██─█─██─██─██",
            "§a██████████████████████████████████████████",
            "§7        >> §fKickChat v1.0.5 FULL §7<<"
        };
        for (String linea : banner) {
            Bukkit.getConsoleSender().sendMessage(linea);
        }
    }
}