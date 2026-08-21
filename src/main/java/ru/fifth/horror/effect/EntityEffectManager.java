package ru.fifth.horror.effect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import ru.fifth.horror.network.FifthNetworking;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Persistent shader-effect bindings keyed by entity UUID. No vanilla particle system is used. */
public final class EntityEffectManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Config>>(){}.getType();
    private static final Map<String, Config> CONFIGS = new LinkedHashMap<>();
    private static MinecraftServer loaded;
    private EntityEffectManager() {}

    public static void load(MinecraftServer server){
        if(loaded==server)return; loaded=server; CONFIGS.clear();
        try{Path p=file(server);if(Files.exists(p)){Map<String,Config> m=GSON.fromJson(Files.readString(p),TYPE);if(m!=null)CONFIGS.putAll(m);}}catch(Exception ignored){}
    }
    public static void save(MinecraftServer server, Config config){load(server);if("off".equals(config.type))CONFIGS.remove(config.uuid);else CONFIGS.put(config.uuid,config);persist(server);broadcast(server,config);}
    public static void syncAll(ServerPlayerEntity player){load(player.getServer());for(Config c:CONFIGS.values())send(player,c);}
    private static void broadcast(MinecraftServer server,Config c){for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())send(p,c);}
    private static void send(ServerPlayerEntity p,Config c){PacketByteBuf b=PacketByteBufs.create();write(b,c);ServerPlayNetworking.send(p,FifthNetworking.ENTITY_EFFECT_SYNC,b);}
    public static void write(PacketByteBuf b,Config c){b.writeString(c.uuid,64);b.writeString(c.type,16);b.writeInt(c.color);b.writeDouble(c.offsetX);b.writeDouble(c.offsetY);b.writeDouble(c.offsetZ);b.writeFloat(c.intensity);}
    public static Config read(PacketByteBuf b){return new Config(b.readString(64),b.readString(16),b.readInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat());}
    private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("entity_shader_effects.json");}
    private static void persist(MinecraftServer s){try{Path p=file(s);Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(CONFIGS,TYPE));}catch(Exception ignored){}}
    public static final class Config{
        public String uuid="",type="dark";public int color=0xFFFF2020;public double offsetX,offsetY,offsetZ;public float intensity=1f;
        public Config(){}
        public Config(String uuid,String type,int color,double x,double y,double z,float intensity){this.uuid=uuid;this.type=type;this.color=color;offsetX=x;offsetY=y;offsetZ=z;this.intensity=intensity;}
    }
}
