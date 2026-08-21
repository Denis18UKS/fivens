package ru.fifth.horror.animation;

import com.google.gson.Gson;import com.google.gson.GsonBuilder;import com.google.gson.reflect.TypeToken;import net.minecraft.entity.Entity;import net.minecraft.server.MinecraftServer;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.server.world.ServerWorld;import net.minecraft.util.WorldSavePath;import ru.fifth.horror.entity.DirectorNpcEntity;import ru.fifth.horror.entity.MonsterForLiftEntity;
import java.lang.reflect.Type;import java.nio.file.Files;import java.nio.file.Path;import java.util.*;
public final class AnimationConditionManager{
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();private static final Type TYPE=new TypeToken<List<Rule>>(){}.getType();private static final List<Rule> RULES=new ArrayList<>();private static MinecraftServer loaded;private static int ticker;
 private AnimationConditionManager(){}
 public static void load(MinecraftServer s){if(loaded==s)return;loaded=s;RULES.clear();try{Path p=file(s);if(Files.exists(p)){List<Rule> l=GSON.fromJson(Files.readString(p),TYPE);if(l!=null)RULES.addAll(l);}}catch(Exception ignored){}}
 public static void saveRule(MinecraftServer s,Rule r){load(s);RULES.removeIf(x->x.id.equals(r.id));RULES.add(r);save(s);}
 public static List<Rule> list(MinecraftServer s){load(s);return List.copyOf(RULES);}
 public static void tick(MinecraftServer s){load(s);if(++ticker%5!=0)return;long time=s.getOverworld().getTime();for(Rule r:RULES){if(!r.enabled)continue;Entity e=find(s,r.targetUuid);if(e==null)continue;boolean fire=switch(r.type){case "player_near"->nearPlayer(e,Math.max(1,r.value));case "timer"->((time%(long)Math.max(5,r.value))<5);default->false;};if(fire)play(e,r.animation);}}
 public static boolean trigger(MinecraftServer s,String id){load(s);for(Rule r:RULES)if(r.id.equalsIgnoreCase(id)){Entity e=find(s,r.targetUuid);if(e!=null){play(e,r.animation);return true;}}return false;}
 public static Entity find(MinecraftServer s,String uuid){try{UUID u=UUID.fromString(uuid);for(ServerWorld w:s.getWorlds()){Entity e=w.getEntity(u);if(e!=null)return e;}}catch(Exception ignored){}return null;}
 public static Entity findNamed(MinecraftServer s,String target){for(ServerWorld w:s.getWorlds()){for(DirectorNpcEntity n:w.getEntitiesByClass(DirectorNpcEntity.class,new net.minecraft.util.math.Box(-3e7,-2048,-3e7,3e7,4096,3e7),x->x.getNpcId().equalsIgnoreCase(target)))return n;for(MonsterForLiftEntity m:w.getEntitiesByClass(MonsterForLiftEntity.class,new net.minecraft.util.math.Box(-3e7,-2048,-3e7,3e7,4096,3e7),x->x.getUuidAsString().startsWith(target)))return m;}return null;}
 public static void play(Entity e,String a){if(e instanceof DirectorNpcEntity n)n.setCurrentAnimation(a);else if(e instanceof MonsterForLiftEntity m)m.preview(a);}
 public static void stop(Entity e){if(e instanceof DirectorNpcEntity n)n.setCurrentAnimation("");}
 private static boolean nearPlayer(Entity e,double d){if(!(e.getWorld() instanceof ServerWorld sw))return false;return !sw.getEntitiesByClass(ServerPlayerEntity.class,e.getBoundingBox().expand(d),p->!p.isSpectator()).isEmpty();}
 private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("animation_conditions.json");}private static void save(MinecraftServer s){try{Path p=file(s);Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(RULES,TYPE));}catch(Exception ignored){}}
 public static final class Rule{public String id="rule";public String targetUuid="";public String animation="";public String type="player_near";public double value=6;public boolean enabled=true;public Rule(){}public Rule(String id,String targetUuid,String animation,String type,double value){this.id=id;this.targetUuid=targetUuid;this.animation=animation;this.type=type;this.value=value;}}
}
