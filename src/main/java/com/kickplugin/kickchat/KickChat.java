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
    private Channel chatChannel;   // Canal del Chat
    private Channel eventsChannel; // Canal de Eventos (Follows)
    
    private final String KICK_KEY = "32cbd69e4b950bf97679";
    private final String KICK_CLUSTER = "us2";

    @Override
    public void onEnable() {
        mostrarBanner();
        saveDefaultConfig();
        
        var cmd = getCommand("kickchat");
        
        if (cmd != null) {
            KickCommand executor = new KickCommand(this);
            cmd.setExecutor(executor);     // Quién ejecuta
            cmd.setTabCompleter(executor); // Quién da sugerencias (¡Nuevo!)
        }
        
        getServer().getPluginManager().registerEvents(new BotInventory(this), this);
        conectarChat(Bukkit.getConsoleSender());
    }

    @Override
    public void onDisable() {
        desconectar();
        getLogger().info("KickChat desactivado.");
    }

    public void cambiarStreamer(String nuevoNombre, CommandSender sender) {
        sender.sendMessage("§a[KickChat] §7Actualizando streamer a: §e" + nuevoNombre);
        getConfig().set("streamer_name", nuevoNombre);
        getConfig().set("chatroom_id", 0); 
        getConfig().set("channel_id", 0); // También reseteamos el Channel ID
        saveConfig();
        conectarChat(sender);
    }

    public void conectarChat(CommandSender sender) {
        desconectar();
        
        String streamer = getConfig().getString("streamer_name");
        int chatroomId = getConfig().getInt("chatroom_id", 0);
        int channelId = getConfig().getInt("channel_id", 0);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int finalChatId = chatroomId;
            int finalChannelId = channelId;

            // Si falta alguno de los IDs, buscamos en la API
            if (finalChatId == 0 || finalChannelId == 0) {
                sender.sendMessage("§a[KickChat] §7Buscando IDs para: " + streamer + "...");
                int[] ids = obtenerIds(streamer); // Devuelve [ChannelID, ChatroomID]
                
                if (ids != null) {
                    finalChannelId = ids[0];
                    finalChatId = ids[1];
                    
                    getConfig().set("channel_id", finalChannelId);
                    getConfig().set("chatroom_id", finalChatId);
                    saveConfig();
                    sender.sendMessage("§a[KickChat] §fIDs guardados. Canal: " + finalChannelId + " | Chat: " + finalChatId);
                } else {
                    sender.sendMessage("§c[KickChat] §eError API. No se pudo conectar.");
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
                         sender.sendMessage("§a[KickChat] §fConectado a Kick (Chat y Eventos).")
                     );
                }
            }
            @Override
            public void onError(String message, String code, Exception e) {
                getLogger().severe("Error Pusher: " + message);
            }
        }, ConnectionState.ALL);

        // 1. SUSCRIPCIÓN AL CHAT
        chatChannel = pusher.subscribe("chatrooms." + chatroomId + ".v2");
        chatChannel.bind("App\\Events\\ChatMessageEvent", event -> procesarMensaje(event.getData()));

        // 2. SUSCRIPCIÓN A EVENTOS DEL CANAL (Followers)
        eventsChannel = pusher.subscribe("channel." + channelId);
        
        // Evento: FollowersUpdated (Kick lo manda cuando sube el contador)
        eventsChannel.bind("App\\Events\\FollowersUpdated", event -> {
            // Kick a veces manda el nombre en el payload, a veces solo el numero.
            // Por seguridad, celebramos genéricamente.
            lanzarCelebracion();
        });
    }

    // --- NUEVO: CELEBRACIÓN DE FOLLOWER ---
    private void lanzarCelebracion() {
        Bukkit.getScheduler().runTask(this, () -> {
            // 1. Anuncio Global
            Bukkit.broadcastMessage("§d§l§k|||§r §d§l¡NUEVO SEGUIDOR EN KICK! §d§l§k|||");
            
            // 2. Título en pantalla para todos
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§d¡Nuevo Follower!", "§fGracias por el apoyo ❤", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                
                // 3. Fuegos Artificiales
                lanzarFuegosArtificiales(p);
            }
        });
    }

    private void lanzarFuegosArtificiales(Player p) {
        Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.LIME, Color.GREEN) // Colores de Kick
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());
        
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    private void procesarMensaje(String json) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                JsonObject data = JsonParser.parseString(json).getAsJsonObject();
                String mensaje = data.get("content").getAsString();
                String senderName = data.get("sender").getAsJsonObject().get("username").getAsString();

                if (senderName.startsWith("@") || senderName.equalsIgnoreCase("Kicklet")) {
                    List<String> conocidos = getConfig().getStringList("bot_settings.known_bots");
                    if (!conocidos.contains(senderName)) {
                        conocidos.add(senderName);
                        getConfig().set("bot_settings.known_bots", conocidos);
                        saveConfig(); 
                    }
                }

                boolean filtroActivo = getConfig().getBoolean("bot_settings.filter_enabled", true);
                if (filtroActivo) {
                    List<String> botsBloqueados = getConfig().getStringList("bot_settings.blocked_list");
                    for (String botName : botsBloqueados) {
                        if (senderName.equalsIgnoreCase(botName)) return; 
                    }
                }

                String nombreFormateado;
                if (senderName.startsWith("@")) {
                    String colorEspecial = getConfig().getString("bot_settings.at_name_color", "&b").replace("&", "§");
                    nombreFormateado = colorEspecial + senderName;
                } else {
                    nombreFormateado = "§7" + senderName;
                }

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

                Bukkit.broadcastMessage("§a[Kick] " + nombreFormateado + "§8: §f" + mensajeFinal);

            } catch (Exception e) {}
        });
    }

    // --- MODIFICADO: AHORA BUSCA 2 IDs ---
    private int[] obtenerIds(String streamerSlug) {
        try {
            String url = "https://kick.com/api/v1/channels/" + streamerSlug;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                
                int channelId = json.get("id").getAsInt(); // ID del canal
                int chatroomId = 0;
                
                if (json.has("chatroom")) {
                    chatroomId = json.get("chatroom").getAsJsonObject().get("id").getAsInt();
                }
                
                return new int[]{channelId, chatroomId};
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
            "§7        >> §fKickChat v1.1 + Alerts §7<<"
        };
        for (String linea : banner) {
            Bukkit.getConsoleSender().sendMessage(linea);
        }
    }
}