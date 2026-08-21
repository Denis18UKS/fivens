package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Asset-free configurable screamer overlay used by animation conditions. */
public final class ScreamerOverlay {
    private static int ticks,total; private static float intensity=1f;
    private ScreamerOverlay(){}
    public static void start(int duration,float power){total=ticks=Math.max(1,duration);intensity=Math.max(.1f,Math.min(3f,power));}
    public static void tick(){if(ticks>0)ticks--;}
    public static boolean active(){return ticks>0;}
    public static void render(DrawContext c){
        if(ticks<=0)return;MinecraftClient mc=MinecraftClient.getInstance();int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();float p=ticks/(float)Math.max(1,total);float pulse=(float)(.55+.45*Math.sin((total-ticks)*1.7));int alpha=Math.min(245,(int)(180*intensity*(.55+.45*pulse)));
        c.fill(0,0,w,h,(alpha<<24)|0x090000);
        int cx=w/2,cy=h/2;int eyeW=Math.max(18,(int)(w*.12*intensity)),eyeH=Math.max(4,(int)(h*.025*intensity)),gap=Math.max(12,(int)(w*.055));int eyeAlpha=Math.min(255,(int)(230*pulse));int red=(eyeAlpha<<24)|0xCC1010;
        c.fill(cx-gap-eyeW,cy-eyeH,cx-gap,cy+eyeH,red);c.fill(cx+gap,cy-eyeH,cx+gap+eyeW,cy+eyeH,red);
        long t=System.nanoTime()/10_000_000L;for(int i=0;i<9;i++){int y=(int)Math.floorMod(t*17+i*97,Math.max(1,h));int hh=1+(i%3);int a=Math.min(180,(int)(90*intensity));c.fill(0,y,w,y+hh,(a<<24)|(i%2==0?0xFFFFFF:0x7A0000));}
    }
}
