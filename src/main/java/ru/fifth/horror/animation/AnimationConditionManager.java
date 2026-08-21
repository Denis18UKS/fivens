package ru.fifth.horror.animation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.network.FifthNetworking;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Conditions can play animations or a screamer and can test the animation currently playing on an entity. */
public final class AnimationConditionManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE=new TypeToken<List<Rule>>(){}.getType();
    private static final List<Rule> RULES=new ArrayList<>();
    private static final Map<String,Boolean> LAST_STATE=new HashMap<>();
    private static MinecraftServer loaded; private static int ticker;
    private AnimationConditionManager(){}

    public static void load(MinecraftServer s){if(loaded==s)return;loaded=s;RULES.clear();LAST_STATE.clear();try{Path p=file(s);if(Files.exists(p)){List<Rule> l=GSON.fromJson(Files.readString(p),TYPE);if(l!=null)RULES.addAll(l);}}catch(Exception ignored){}}
    public static void saveRule(MinecraftServer s,Rule r){load(s);RULES.removeIf(x->x.id.equalsIgnoreCase(r.id));RULES.add(r);LAST_STATE.remove(r.id);save(s);}
    public static List<Rule> list(MinecraftServer s){load(s);return List.copyOf(RULES);}

    public static void tick(MinecraftServer s){
        load(s); if(++ticker%5!=0)return; long time=s.getOverworld().getTime();
        for(Rule r:RULES){
            if(!r.enabled)continue; Entity e=find(s,r.targetUuid); if(e==null)continue;
            boolean active=switch(r.type){
                case "player_near"->nearPlayer(e,Math.max(1,r.value));
                case "timer"->(time%(long)Math.max(5,r.value))<5;
                case "animation_is"->currentAnimation(e).equalsIgnoreCase(r.conditionAnimation==null?"":r.conditionAnimation.trim());
                default->false;
            };
            boolean was=LAST_STATE.getOrDefault(r.id,false);
            if(active&&!was)fire(s,e,r);
            LAST_STATE.put(r.id,active);
        }
    }

    public static boolean trigger(MinecraftServer s,String id){load(s);for(Rule r:RULES)if(r.id.equalsIgnoreCase(id)){Entity e=find(s,r.targetUuid);if(e!=null){fire(s,e,r);return true;}}return false;}

    private static void fire(MinecraftServer server,Entity e,Rule r){
        if(r.screamer){
            if(e.getWorld() instanceof ServerWorld sw){for(ServerPlayerEntity p:sw.getPlayers(p->!p.isSpectator()&&p.squaredDistanceTo(e)<=24*24))FifthNetworking.sendScreamer(p,30,1.0f);}
            return;
        }
        play(e,r.animation);
    }

    public static Entity find(MinecraftServer s,String uuid){try{UUID u=UUID.fromString(uuid);for(ServerWorld w:s.getWorlds()){Entity e=w.getEntity(u);if(e!=null)return e;}}catch(Exception ignored){}return null;}
    public static Entity findNamed(MinecraftServer s,String target){for(ServerWorld w:s.getWorlds()){for(DirectorNpcEntity n:w.getEntitiesByClass(DirectorNpcEntity.class,new net.minecraft.util.math.Box(-3e7,-2048,-3e7,3e7,4096,3e7),x->x.getNpcId().equalsIgnoreCase(target)))return n;for(MonsterForLiftEntity m:w.getEntitiesByClass(MonsterForLiftEntity.class,new net.minecraft.util.math.Box(-3e7,-2048,-3e7,3e7,4096,3e7),x->x.getUuidAsString().startsWith(target)))return m;}return null;}
    public static void play(Entity e,String a){if(e instanceof DirectorNpcEntity n)n.setCurrentAnimation(a);else if(e instanceof MonsterForLiftEntity m)m.preview(a);}
    public static void stop(Entity e){if(e instanceof DirectorNpcEntity n)n.setCurrentAnimation("");else if(e instanceof MonsterForLiftEntity m)m.preview("idle");}
    public static String currentAnimation(Entity e){if(e instanceof DirectorNpcEntity n)return Objects.toString(n.getCurrentAnimation(),"");if(e instanceof MonsterForLiftEntity m)return Objects.toString(m.getCurrentAnimation(),"");return "";}
    private static boolean nearPlayer(Entity e,double d){if(!(e.getWorld() instanceof ServerWorld sw))return false;return !sw.getEntitiesByClass(ServerPlayerEntity.class,e.getBoundingBox().expand(d),p->!p.isSpectator()).isEmpty();}
    private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("animation_conditions.json");}
    private static void save(MinecraftServer s){try{Path p=file(s);Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(RULES,TYPE));}catch(Exception ignored){}}

    public static final class Rule{
        public String id="rule",targetUuid="",animation="",type="player_near",conditionAnimation="";public double value=6;public boolean screamer=false,enabled=true;
        public Rule(){}
        public Rule(String id,String targetUuid,String animation,String type,double value){this(id,targetUuid,animation,type,value,"",false);}
        public Rule(String id,String targetUuid,String animation,String type,double value,String conditionAnimation,boolean screamer){this.id=id;this.targetUuid=targetUuid;this.animation=animation;this.type=type;this.value=value;this.conditionAnimation=conditionAnimation==null?"":conditionAnimation;this.screamer=screamer;}
    }
}
