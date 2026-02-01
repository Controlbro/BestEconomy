package com.controlbro.besteconomy.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?<!<)#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TAGS = new HashMap<>();

    static {
        LEGACY_TAGS.put('0', "<black>");
        LEGACY_TAGS.put('1', "<dark_blue>");
        LEGACY_TAGS.put('2', "<dark_green>");
        LEGACY_TAGS.put('3', "<dark_aqua>");
        LEGACY_TAGS.put('4', "<dark_red>");
        LEGACY_TAGS.put('5', "<dark_purple>");
        LEGACY_TAGS.put('6', "<gold>");
        LEGACY_TAGS.put('7', "<gray>");
        LEGACY_TAGS.put('8', "<dark_gray>");
        LEGACY_TAGS.put('9', "<blue>");
        LEGACY_TAGS.put('a', "<green>");
        LEGACY_TAGS.put('b', "<aqua>");
        LEGACY_TAGS.put('c', "<red>");
        LEGACY_TAGS.put('d', "<light_purple>");
        LEGACY_TAGS.put('e', "<yellow>");
        LEGACY_TAGS.put('f', "<white>");
        LEGACY_TAGS.put('k', "<obfuscated>");
        LEGACY_TAGS.put('l', "<bold>");
        LEGACY_TAGS.put('m', "<strikethrough>");
        LEGACY_TAGS.put('n', "<underlined>");
        LEGACY_TAGS.put('o', "<italic>");
        LEGACY_TAGS.put('r', "<reset>");
    }

    private ColorUtil() {
    }

    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        String processed = replaceLegacyCodes(message);
        processed = replaceHexCodes(processed);
        return MINI_MESSAGE.deserialize(processed);
    }

    private static String replaceLegacyCodes(String input) {
        StringBuilder builder = new StringBuilder(input.length() + 16);
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            if (current == '&' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                String tag = LEGACY_TAGS.get(code);
                if (tag != null) {
                    builder.append(tag);
                    i++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static String replaceHexCodes(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
