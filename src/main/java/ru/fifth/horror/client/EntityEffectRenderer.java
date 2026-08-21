package ru.fifth.horror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Entity-space horror renderer. Effects are anchored to the entity, never to the viewer/camera. */
public final class EntityEffectRenderer {
    private static final Map<UUID, EntityEffectManager.Config> CONFIGS=new ConcurrentHashMap<>();
    private static ShaderProgram shader;
    private EntityEffectRenderer(){}

    public static void init(){
        CoreShaderRegistrationCallback.EVENT.register(context->context.register(FifthMod.id("entity_effect"),VertexFormats.POSITION_COLOR,program->shader=program));
        WorldRenderEvents.AFTER_ENTITIES.register(context->render(context.matrixStack(),context.camera().getPos(),context.tickDelta()));
    }
    public static void update(EntityEffectManager.Config c){try{UUID u=UUID.fromString(c.uuid);if("off".equals(c.type))CONFIGS.remove(u);else CONFIGS.put(u,c);}catch(Exception ignored){}}

    private static void render(MatrixStack matrices,Vec3d camera,float tickDelta){
        if(shader==null||matrices==null||CONFIGS.isEmpty())return;MinecraftClient client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return;
        Box search=client.player.getBoundingBox().expand(128);List<Entity> loaded=new ArrayList<>();if(CONFIGS.containsKey(client.player.getUuid()))loaded.add(client.player);loaded.addAll(client.world.getOtherEntities(client.player,search,e->CONFIGS.containsKey(e.getUuid())));
        for(Entity e:loaded){EntityEffectManager.Config cfg=CONFIGS.get(e.getUuid());if(cfg!=null)drawOne(matrices,camera,e,cfg,tickDelta);}
    }

    private static void drawOne(MatrixStack matrices,Vec3d camera,Entity e,EntityEffectManager.Config cfg,float tickDelta){
        Vec3d p=e.getLerpedPos(tickDelta);matrices.push();matrices.translate(p.x-camera.x+cfg.offsetX,p.y-camera.y+cfg.offsetY,p.z-camera.z+cfg.offsetZ);
        // Base transform follows the entity body, never Minecraft's viewer camera.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.getYaw()));
        if("eyes".equals(cfg.type)&&e instanceof MonsterForLiftEntity mfl)applyMflHeadTransform(matrices,mfl,tickDelta);

        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.setShader(()->shader);
        float time=(System.currentTimeMillis()%100000L)/1000f;if(shader.getUniform("FivenTime")!=null)shader.getUniform("FivenTime").set(time);if(shader.getUniform("EffectKind")!=null)shader.getUniform("EffectKind").set("eyes".equals(cfg.type)?1f:0f);
        Matrix4f m=matrices.peek().getPositionMatrix();int a=(cfg.color>>>24)&255,r=(cfg.color>>>16)&255,g=(cfg.color>>>8)&255,b=cfg.color&255;
        if("eyes".equals(cfg.type))drawEyes(m,e.getWidth(),e.getHeight(),r,g,b,a,cfg.intensity,time);else drawDarkParticles(m,e,cfg.intensity,time);
        RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();matrices.pop();
    }

    /** Mirrors the head rotations from the supplied MFL animation file so eye rings live in head-space. */
    private static void applyMflHeadTransform(MatrixStack matrices,MonsterForLiftEntity m,float tickDelta){
        float t=Math.max(0,(m.age-m.getAnimationStartAge()+tickDelta)/20f);String a=m.getCurrentAnimation();float yaw=0,pitch=0,down=0,forward=0;
        if("looking_left".equals(a)){yaw=t<=2?-25f*(t/2f):-25f*Math.max(0,1-(t-2f)/2f);}
        else if("looking_right".equals(a)){yaw=t<=2?25f*(t/2f):25f*Math.max(0,1-(t-2f)/2f);}
        else if("looking_backward".equals(a)){yaw=180f*Math.min(1,t/2f);}
        else if("mfl_screamer".equals(a)){float q=Math.min(1,t/.125f);pitch=17.5f*q;down=-3f/16f*q;forward=-3f/16f*q;}
        float headY=m.getHeight()*.82f;
        matrices.translate(0,headY+down,forward);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.translate(0,-headY,0);
    }

    /** Round-ish black shader geometry drifts from the body surface upward and outward. */
    private static void drawDarkParticles(Matrix4f m,Entity e,float intensity,float time){
        BufferBuilder bb=Tessellator.getInstance().getBuffer();bb.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);
        float width=Math.max(.45f,e.getWidth()),height=Math.max(1.0f,e.getHeight());long seed=e.getUuid().getLeastSignificantBits();
        for(int i=0;i<26;i++){
            float phase=(time*(.14f+.018f*(i%5))+hash(seed,i)*.91f)%1f;
            float angle=hash(seed+17,i)*6.283185f+time*.08f*(i%2==0?1:-1);
            float radius=width*(.28f+.36f*phase);
            float x=(float)Math.cos(angle)*radius,z=(float)Math.sin(angle)*radius;
            float y=.08f+hash(seed+41,i)*height*.72f+phase*height*.52f;
            float s=(.025f+.045f*hash(seed+71,i))*(.75f+Math.min(2f,intensity)*.35f);
            int alpha=(int)(210f*(1f-phase)*Math.min(1.35f,intensity));
            diskXY(bb,m,x,y,z,s,Math.max(0,alpha));diskXZ(bb,m,x,y,z,s*.82f,Math.max(0,alpha));
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static float hash(long seed,int i){long x=seed+i*0x9E3779B97F4A7C15L;x^=x>>>33;x*=0xff51afd7ed558ccdL;x^=x>>>33;return (float)((x&0xFFFFFF)/(double)0x1000000);}
    private static void diskXY(BufferBuilder bb,Matrix4f m,float cx,float cy,float cz,float r,int a){disk(bb,m,cx,cy,cz,r,a,false);}
    private static void diskXZ(BufferBuilder bb,Matrix4f m,float cx,float cy,float cz,float r,int a){disk(bb,m,cx,cy,cz,r,a,true);}
    private static void disk(BufferBuilder bb,Matrix4f m,float cx,float cy,float cz,float r,int a,boolean horizontal){final int n=8;for(int i=0;i<n;i++){double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;vertex(bb,m,cx,cy,cz,1,1,2,a);if(horizontal){vertex(bb,m,cx+(float)Math.cos(a0)*r,cy,cz+(float)Math.sin(a0)*r,1,1,2,a);vertex(bb,m,cx+(float)Math.cos(a1)*r,cy,cz+(float)Math.sin(a1)*r,1,1,2,a);}else{vertex(bb,m,cx+(float)Math.cos(a0)*r,cy+(float)Math.sin(a0)*r,cz,1,1,2,a);vertex(bb,m,cx+(float)Math.cos(a1)*r,cy+(float)Math.sin(a1)*r,cz,1,1,2,a);}}}

    private static void drawEyes(Matrix4f m,float width,float height,int r,int g,int b,int a,float intensity,float time){
        BufferBuilder bb=Tessellator.getInstance().getBuffer();bb.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);float y=height*.82f,sep=Math.max(.08f,width*.16f),w=.07f,h=.035f,z=-width*.51f,pulse=.75f+.25f*(float)Math.sin(time*6f),mul=Math.min(2.5f,intensity)*pulse;int rr=Math.min(255,(int)(r*mul)),gg=Math.min(255,(int)(g*mul)),bbv=Math.min(255,(int)(b*mul)),aa=Math.min(255,(int)(a*Math.min(1f,.75f*intensity)));eyeRing(bb,m,-sep,y,z,w,h,rr,gg,bbv,aa);eyeRing(bb,m,sep,y,z,w,h,rr,gg,bbv,aa);BufferRenderer.drawWithGlobalProgram(bb.end());
    }
    private static void eyeRing(BufferBuilder bb,Matrix4f m,float cx,float cy,float z,float w,float h,int r,int g,int b,int a){float t=.012f;quad(bb,m,cx-w,cy-h,cx+w,cy-h+t,z,r,g,b,a);quad(bb,m,cx-w,cy+h-t,cx+w,cy+h,z,r,g,b,a);quad(bb,m,cx-w,cy-h,cx-w+t,cy+h,z,r,g,b,a);quad(bb,m,cx+w-t,cy-h,cx+w,cy+h,z,r,g,b,a);}
    private static void quad(BufferBuilder bb,Matrix4f m,float x1,float y1,float x2,float y2,float z,int r,int g,int b,int a){vertex(bb,m,x1,y1,z,r,g,b,a);vertex(bb,m,x2,y1,z,r,g,b,a);vertex(bb,m,x2,y2,z,r,g,b,a);vertex(bb,m,x1,y2,z,r,g,b,a);}
    private static void vertex(BufferBuilder bb,Matrix4f m,float x,float y,float z,int r,int g,int b,int a){bb.vertex(m,x,y,z).color(r,g,b,a).next();}
}
