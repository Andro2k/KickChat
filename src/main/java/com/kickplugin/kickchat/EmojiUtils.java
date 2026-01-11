package com.kickplugin.kickchat;

import java.util.HashMap;
import java.util.Map;

public class EmojiUtils {

    private static final Map<String, String> emojiMap = new HashMap<>();

    static {
        // --- CARAS / EMOCIONES ---
        emojiMap.put("emojiclown", "🤡");
        emojiMap.put("clown", "🤡");
        emojiMap.put("emojiangel", "😇");
        emojiMap.put("angel", "😇");
        emojiMap.put("emojiawake", "😳");
        emojiMap.put("emojiblowkiss", "😘");
        emojiMap.put("joy", "😂");
        emojiMap.put("sob", "😭");
        emojiMap.put("smile", "🙂");
        emojiMap.put("smiley", "😃");
        emojiMap.put("heart_eyes", "😍");
        emojiMap.put("sunglasses", "😎");
        emojiMap.put("thinking", "🤔");
        emojiMap.put("sweat_smile", "😅");
        emojiMap.put("rofl", "🤣");
        emojiMap.put("scream", "😱");
        emojiMap.put("rage", "😡");
        emojiMap.put("pog", "😮"); 
        emojiMap.put("skull", "💀");
        emojiMap.put("nerd", "🤓");

        // --- MANOS / GESTOS ---
        emojiMap.put("thumbsup", "👍");
        emojiMap.put("thumbsdown", "👎");
        emojiMap.put("ok_hand", "👌");
        emojiMap.put("wave", "👋");
        emojiMap.put("clap", "👏");
        emojiMap.put("pray", "🙏");
        emojiMap.put("muscle", "💪");
        emojiMap.put("fire", "🔥");
        emojiMap.put("100", "💯");

        // --- CORAZONES ---
        emojiMap.put("heart", "❤️");
        emojiMap.put("blue_heart", "💙");
        emojiMap.put("green_heart", "💚");
        emojiMap.put("purple_heart", "💜");
        emojiMap.put("broken_heart", "💔");
        
        // --- OTROS ---
        emojiMap.put("poop", "💩");
        emojiMap.put("check", "✅");
        emojiMap.put("x", "❌");
        emojiMap.put("warning", "⚠");
    }

    /**
     * Busca el emoji correspondiente. 
     * Si no existe, devuelve el nombre original entre dos puntos (ej: :emojiRaro:)
     */
    public static String traducir(String nombreEmote) {
        // Convertimos a minúsculas para buscar sin importar mayúsculas
        String lowerName = nombreEmote.toLowerCase();
        
        // Quitamos prefijos comunes si los hay para mejorar la búsqueda
        // Ejemplo: "emojiSmile" -> "smile"
        if (lowerName.startsWith("emoji")) {
            String shortName = lowerName.replace("emoji", "");
            if (emojiMap.containsKey(shortName)) {
                return emojiMap.get(shortName);
            }
        }

        // Buscamos coincidencia exacta
        if (emojiMap.containsKey(lowerName)) {
            return emojiMap.get(lowerName);
        }

        // Si no encontramos dibujo, devolvemos el texto formateado
        return ":" + nombreEmote + ":";
    }
}