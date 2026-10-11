package net.renan.photonplugin.menus.resource.preview.common.json;

import net.mcreator.ui.init.L10N;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinimalJsonParser {
    private final String s;
    private int i;

    private MinimalJsonParser(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        MinimalJsonParser p = new MinimalJsonParser(json);
        p.skipWs();
        Object result = p.parseValue();
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    public static double asDouble(Object o, double fallback) {
        return o instanceof Double d ? d : fallback;
    }

    public static String asString(Object o, String fallback) {
        return o instanceof String str ? str : fallback;
    }

    public static boolean asBoolean(Object o, boolean fallback) {
        return o instanceof Boolean b ? b : fallback;
    }

    private Object parseValue() {
        skipWs();
        if (i >= s.length()) {
            throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.json_end"));
        }
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWs();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWs();
            char c = next();
            if (c == '}') {
                break;
            }
            if (c != ',') {
                throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.json_brace", i));
            }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWs();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWs();
            char c = next();
            if (c == ']') {
                break;
            }
            if (c != ',') {
                throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.json_bracket", i));
            }
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char esc = next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double parseNumber() {
        int start = i;
        if (peek() == '-') {
            i++;
        }
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == 'e'
                || s.charAt(i) == 'E' || s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        return Double.parseDouble(s.substring(start, i));
    }

    private Object parseLiteral(String literal, Object value) {
        if (i + literal.length() > s.length() || !s.startsWith(literal, i)) {
            throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.json_literal", i));
        }
        i += literal.length();
        return value;
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        return s.charAt(i);
    }

    private char next() {
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.json_expected", c, i));
        }
        i++;
    }
}
