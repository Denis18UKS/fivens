package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Full black lift transition with red vertically rolling floor numbers; no "loading" text. */
public final class LiftTravelOverlay {
    private static int from,to,total,tick;
    private static boolean active;
    private LiftTravelOverlay(){}
    public static void start(int a,int b,int ticks){from=a;to=b;total=Math.max(1,ticks);tick=0;active=true;}
    public static void finish(int floor){from=to=floor;tick=total;active=false;}
    public static boolean active(){return active;}
    public static void tick(){if(active&&++tick>=total)active=false;}
    public static void render(DrawContext c){
        if(!active)return; MinecraftClient mc=MinecraftClient.getInstance(); if(mc.textRenderer==null)return;
        int w=c.getScaledWindowWidth(),h=c.getScaledWindowHeight();c.fill(0,0,w,h,0xFF000000);
        float progress=MathHelper.clamp(tick/(float)total,0,1);int distance=Math.max(1,Math.abs(to-from));float exact=from+(to-from)*progress;
        int base=(int)Math.floor(exact);float frac=exact-base;int dir=Integer.compare(to,from);if(dir==0)dir=1;
        String a=Integer.toString(Math.max(1,Math.min(9,base)));String b=Integer.toString(Math.max(1,Math.min(9,base+dir)));
        int center=h/2;int travel=36;int yA=(int)(center-frac*travel);int yB=(int)(center+(1-frac)*travel);
        int col=0xFFFF2424;c.getMatrices().push();c.getMatrices().scale(3f,3f,1f);
        c.drawCenteredTextWithShadow(mc.textRenderer,a,w/6,yA/3,col);c.drawCenteredTextWithShadow(mc.textRenderer,b,w/6,yB/3,col);c.getMatrices().pop();
        c.fill(w/2-42,center+32,w/2+42,center+34,0xFF3B0909);int bar=(int)(84*progress);c.fill(w/2-42,center+32,w/2-42+bar,center+34,0xFFB21414);
    }
}
