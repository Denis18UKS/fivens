package ru.fifth.horror.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnimationCatalog implements SimpleSynchronousResourceReloadListener {
    public static final AnimationCatalog INSTANCE = new AnimationCatalog();
    private volatile List<Entry> entries = List.of();
    private AnimationCatalog() {}

    @Override public Identifier getFabricId() { return FifthMod.id("animation_catalog"); }

    @Override public void reload(ResourceManager manager) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = manager.findResources("animations", id -> id.getPath().endsWith(".json"));
        resources.forEach((file, resource) -> {
            try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject animations = root.getAsJsonObject("animations");
                if (animations != null) {
                    for (String name : animations.keySet()) {
                        String key = file + "|" + name;
                        unique.putIfAbsent(key, new Entry(name, file, describe(name)));
                    }
                }
            } catch (Exception ignored) {}
        });
        List<Entry> found = new ArrayList<>(unique.values());
        found.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.file().toString()));
        entries = List.copyOf(found);
    }

    public List<Entry> entries() { return entries; }

    private static String describe(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (containsAny(n, "idle", "stand")) return "Ожидание: персонаж стоит на месте";
        if (containsAny(n, "click_on_btn", "button", "btn")) return "Нажатие кнопки / панели лифта";
        if (containsAny(n, "animation_doors", "door", "doors")) return "Движение дверей лифта";
        if (containsAny(n, "walk")) return "Ходьба персонажа";
        if (containsAny(n, "run", "sprint")) return "Бег / быстрое движение";
        if (containsAny(n, "look")) return "Осмотр по сторонам / движение головы";
        if (containsAny(n, "block.left")) return "Защитное движение или блок влево";
        if (containsAny(n, "block.right")) return "Защитное движение или блок вправо";
        if (containsAny(n, "cast")) return "Применение способности / заклинания";
        if (containsAny(n, "explode", "explosion")) return "Взрывная или предсмертная анимация";
        if (containsAny(n, "attack", "hit")) return "Атака / удар";
        if (containsAny(n, "hurt", "damage")) return "Получение урона / реакция на удар";
        if (containsAny(n, "death", "die")) return "Смерть персонажа";
        if (containsAny(n, "jump")) return "Прыжок";
        if (containsAny(n, "fall")) return "Падение";
        if (containsAny(n, "sit")) return "Положение сидя";
        if (containsAny(n, "sleep", "lay", "lie")) return "Лежит / спит";
        if (containsAny(n, "crawl")) return "Ползёт";
        if (containsAny(n, "swim")) return "Плавание";
        if (containsAny(n, "open")) return "Открытие / раскрытие";
        if (containsAny(n, "close")) return "Закрытие";
        if (containsAny(n, "spawn", "appear")) return "Появление персонажа";
        if (containsAny(n, "disappear", "despawn")) return "Исчезновение персонажа";
        if (containsAny(n, "talk", "speak")) return "Разговор / жестикуляция";
        if (containsAny(n, "wave")) return "Махание рукой / приветствие";
        if (containsAny(n, "dance")) return "Танцевальное движение";
        return "Пользовательская анимация; назначается сценарием или катсценой";
    }

    private static boolean containsAny(String value, String... parts) {
        for (String part : parts) if (value.contains(part)) return true;
        return false;
    }

    public record Entry(String name, Identifier file, String description) {}
}
