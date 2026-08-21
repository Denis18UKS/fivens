package ru.fifth.horror.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NpcTemplateStore {
    private static final Gson GSON = new Gson();
    private NpcTemplateStore() {}

    private static Path dir() {
        Path p = MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("fiven").resolve("npc_templates");
        try { Files.createDirectories(p); } catch (Exception ignored) {}
        return p;
    }

    public static String save(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : "npc";
            id = id.replaceAll("[^a-zA-Z0-9_\\-а-яА-Я]", "_");
            if (id.isBlank()) id = "npc";
            Path file = dir().resolve(id + ".json");
            Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return file.getFileName().toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Template> list() {
        List<Template> out = new ArrayList<>();
        try (var stream = Files.list(dir())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    String id = o.has("id") ? o.get("id").getAsString() : p.getFileName().toString();
                    String name = o.has("name") ? o.get("name").getAsString() : id;
                    out.add(new Template(id, name, json, p));
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        out.sort(Comparator.comparing(Template::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    public record Template(String id, String name, String json, Path path) {}
}
