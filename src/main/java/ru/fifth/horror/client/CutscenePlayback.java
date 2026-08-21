package ru.fifth.horror.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

public final class CutscenePlayback {
    private static CutsceneDefinition scene;
    private static int tick;
    private static int lastSubtitleIndex=-1;
    private CutscenePlayback() {}
    public static void start(CutsceneDefinition s){scene=s;tick=0;lastSubtitleIndex=-1;MinecraftClient c=MinecraftClient.getInstance();if(c.options!=null&&s!=null&&s.hideHud)c.options.hudHidden=true;}
    public static void stop(){MinecraftClient c=MinecraftClient.getInstance();if(c.options!=null)c.options.hudHidden=false;scene=null;tick=0;lastSubtitleIndex=-1;}
    private static void finish(){
        CutsceneDefinition finished=scene;
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.options!=null)c.options.hudHidden=false;
        scene=null;tick=0;lastSubtitleIndex=-1;
        if(finished!=null&&finished.teleportPlayerAtEnd&&finished.id!=null&&!finished.id.isBlank()&&c.getNetworkHandler()!=null){
            PacketByteBuf out=PacketByteBufs.create();out.writeString(finished.id,128);ClientPlayNetworking.send(FifthNetworking.CUTSCENE_END_TELEPORT,out);
        }
    }
    public static boolean active(){return scene!=null&&!scene.keyframes.isEmpty();}
    public static boolean hideHud(){return active()&&scene.hideHud;}
    public static boolean lockInput(){return active()&&scene.lockInput;}
    public static void tick(){
        if(!active())return;tick++;Sample s=sample(0);
        if(s!=null&&s.index!=lastSubtitleIndex){lastSubtitleIndex=s.index;String text=scene.keyframes.get(s.index).subtitle;if(text!=null&&!text.isBlank()&&MinecraftClient.getInstance().inGameHud!=null)MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(text),false);}
        if(tick>=totalTicks())finish();
    }
    public static Sample sample(float delta){
        if(!active())return null;int cursor=0;
        if(scene.keyframes.size()==1){var k=scene.keyframes.get(0);return new Sample(k.x,k.y,k.z,k.yaw,k.pitch,k.fov,0);}
        for(int i=0;i<scene.keyframes.size()-1;i++){
            CutsceneDefinition.Keyframe a=scene.keyframes.get(i),b=scene.keyframes.get(i+1);int d=Math.max(1,a.durationTicks);
            if(tick<cursor+d){float t=MathHelper.clamp((tick+delta-cursor)/(float)d,0,1);t=t*t*(3-2*t);return new Sample(MathHelper.lerp(t,a.x,b.x),MathHelper.lerp(t,a.y,b.y),MathHelper.lerp(t,a.z,b.z),lerpAngle(t,a.yaw,b.yaw),MathHelper.lerp(t,a.pitch,b.pitch),MathHelper.lerp(t,a.fov,b.fov),i);}
            cursor+=d;
        }
        var k=scene.keyframes.get(scene.keyframes.size()-1);return new Sample(k.x,k.y,k.z,k.yaw,k.pitch,k.fov,scene.keyframes.size()-1);
    }
    private static float lerpAngle(float t,float a,float b){return a+MathHelper.wrapDegrees(b-a)*t;}
    private static int totalTicks(){int n=0;for(int i=0;i<scene.keyframes.size()-1;i++)n+=Math.max(1,scene.keyframes.get(i).durationTicks);return Math.max(1,n+1);}
    public record Sample(double x,double y,double z,float yaw,float pitch,double fov,int index){}
}
