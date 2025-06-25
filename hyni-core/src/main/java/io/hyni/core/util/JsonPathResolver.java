package io.hyni.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for resolving JSON paths
 */
public class JsonPathResolver {

    /**
     * Resolve a path in a JSON structure
     */
    public static JsonNode resolvePath(JsonNode json, List<String> path) {
        JsonNode current = json;

        for (String key : path) {
            if (isNumeric(key)) {
                // Array index
                int index = Integer.parseInt(key);
                if (!current.isArray() || index >= current.size()) {
                    throw new IllegalArgumentException("Invalid array access: index " + key);
                }
                current = current.get(index);
            } else {
                // Object key
                if (!current.isObject() || !current.has(key)) {
                    throw new IllegalArgumentException("Invalid object access: key " + key);
                }
                current = current.get(key);
            }
        }

        return current;
    }

    /**
     * Parse JSON path array to string list
     */
    public static List<String> parseJsonPath(JsonNode pathArray) {
        List<String> path = new ArrayList<>();

        if (pathArray.isArray()) {
            for (JsonNode element : pathArray) {
                if (element.isTextual()) {
                    path.add(element.asText());
                } else if (element.isNumber()) {
                    path.add(String.valueOf(element.asInt()));
                }
            }
        }

        return path;
    }

    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
