package ru.fifth.horror.cutscene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import ru.fifth.horror.network.FifthNetworking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CutsceneManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,CutsceneDefinition> CACHE=new ConcurrentHashMap<>();
    private CutsceneManager(){}
    public static void tick(MinecraftServer server){}
    public static void save(MinecraftServer server,CutsceneDefinition scene){if(scene==null||scene.id==null||scene.id.isBlank())return;CACHE.put(scene.id,scene);try{Path p=dir(server).resolve(safe(scene.id)+".json");Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(scene));}catch(Exception e){throw new RuntimeException(e);}}
    public static CutsceneDefinition load(MinecraftServer server,String id){CutsceneDefinition c=CACHE.get(id);if(c!=null)return c;try{Path p=dir(server).resolve(safe(id)+".json");if(Files.exists(p)){c=GSON.fromJson(Files.readString(p),CutsceneDefinition.class);if(c!=null)CACHE.put(id,c);return c;}}catch(Exception ignored){}return null;}
    public static List<SceneInfo> list(MinecraftServer server){List<SceneInfo> out=new ArrayList<>();try{Files.createDirectories(dir(server));try(var stream=Files.list(dir(server))){stream.filter(p->p.getFileName().toString().endsWith(".json")).forEach(p->{try{CutsceneDefinition c=GSON.fromJson(Files.readString(p),CutsceneDefinition.class);if(c!=null){int ticks=0;for(var k:c.keyframes)ticks+=Math.max(1,k.durationTicks);out.add(new SceneInfo(c.id,c.keyframes.size(),ticks,c.teleportPlayerAtEnd));}}catch(Exception ignored){}});}}catch(Exception ignored){}out.sort(Comparator.comparing(SceneInfo::id,String.CASE_INSENSITIVE_ORDER));return out;}

    /** Legacy behavior: play for every connected player. */
    public static void play(MinecraftServer server,String id){play(server,id,PlayerLookup.all(server));}

    /** Targeted playback used by /fiven_catscene, /fiven_cs and trigger zones. */
    public static int play(MinecraftServer server,String id,Collection<ServerPlayerEntity> targets){
        CutsceneDefinition c=load(server,id);
        if(c==null||targets==null||targets.isEmpty())return 0;
        String json=GSON.toJson(c);
        int sent=0;
        Set<UUID> unique=new HashSet<>();
        for(ServerPlayerEntity p:targets){
            if(p==null||!unique.add(p.getUuid()))continue;
            PacketByteBuf buf=PacketByteBufs.create();
            buf.writeString(json,1_000_000);
            ServerPlayNetworking.send(p,FifthNetworking.CUTSCENE_PAYLOAD,buf);
            sent++;
        }
        return sent;
    }

    public static int play(MinecraftServer server,String id,ServerPlayerEntity target){return target==null?0:play(server,id,List.of(target));}
    public static String json(MinecraftServer server,String id){CutsceneDefinition c=load(server,id);return c==null?"":GSON.toJson(c);}
    private static Path dir(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("cutscenes");}
    private static String safe(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]","_");}
    public record SceneInfo(String id,int frames,int ticks,boolean teleportAtEnd){}
}
