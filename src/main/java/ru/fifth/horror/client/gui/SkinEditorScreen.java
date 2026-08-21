package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.fifth.horror.FifthMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

public class SkinEditorScreen extends HorrorScreen {
    private final NpcCreatorScreen parent;private BufferedImage image;private boolean overlay;private int color=0xFF2A2A2A;private int ox,oy,cell;
    private static final int[] PALETTE={0xFF000000,0xFF2B2B2B,0xFF5A4030,0xFFEFC29A,0xFFFFFFFF,0xFFB51D2A,0xFF2964B8,0xFF228A45,0x00000000};
    public SkinEditorScreen(NpcCreatorScreen parent,String base64){super(Text.literal("ПЯТЫЙ / РЕДАКТОР СКИНА 64×64"));this.parent=parent;load(base64);}
    private void load(String b64){try{if(b64!=null&&!b64.isBlank())image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));else try(var in=net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(FifthMod.id("textures/entity/npc_default.png")).orElseThrow().getInputStream()){image=ImageIO.read(in);}}catch(Exception ignored){}if(image==null||image.getWidth()!=64||image.getHeight()!=64)image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);}
    @Override protected void init(){
        beginHorrorInit();
        int side=Math.max(92,Math.min(132,width/3));
        int maxByW=Math.max(1,(width-side-28)/64),maxByH=Math.max(1,(height-safeTop()-safeBottom()-10)/64);
        cell=Math.max(1,Math.min(5,Math.min(maxByW,maxByH)));
        ox=Math.max(side+12,(width-64*cell+side)/2);oy=safeTop();
        int bx=10,bw=side-18,bh=20;
        addDrawableChild(HorrorButton.builder(Text.literal(overlay?"Слой: ВТОРОЙ":"Слой: ОСНОВНОЙ"),b->{overlay=!overlay;b.setMessage(Text.literal(overlay?"Слой: ВТОРОЙ":"Слой: ОСНОВНОЙ"));}).dimensions(bx,safeTop(),bw,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить"),b->save()).dimensions(bx,safeTop()+26,bw,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(bx,safeTop()+52,bw,bh).build());
    }
    private void save(){try{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);parent.setSkinBase64(Base64.getEncoder().encodeToString(out.toByteArray()));client.setScreen(parent);}catch(Exception ignored){}}
    private boolean isOverlay(int x,int y){return (y<16&&x>=32)||(y>=32&&y<48&&((x<16)||(x>=16&&x<40)||(x>=40&&x<56)))||(y>=48&&((x<16)||(x>=48)));}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){int side=Math.max(92,Math.min(132,width/3)),sw=Math.max(20,(side-28)/3),ph=17,py=safeTop()+88;for(int i=0;i<PALETTE.length;i++){int x=10+i%3*(sw+3),y=py+i/3*(ph+4);if(mx>=x&&mx<x+sw&&my>=y&&my<y+ph){color=PALETTE[i];return true;}}int px=(int)((mx-ox)/cell),piy=(int)((my-oy)/cell);if(px>=0&&px<64&&piy>=0&&piy<64&&isOverlay(px,piy)==overlay){image.setRGB(px,piy,color);return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==0){int px=(int)((mx-ox)/cell),py=(int)((my-oy)/cell);if(px>=0&&px<64&&py>=0&&py<64&&isOverlay(px,py)==overlay){image.setRGB(px,py,color);return true;}}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int rgb=image.getRGB(x,y),a=(rgb>>>24)&255;int col=a==0?(((x+y)&1)==0?0xFF35383B:0xFF202326):rgb;if(isOverlay(x,y)!=overlay)col=(col&0x00FFFFFF)|0x4D000000;c.fill(ox+x*cell,oy+y*cell,ox+(x+1)*cell,oy+(y+1)*cell,col);}c.drawBorder(ox-1,oy-1,64*cell+2,64*cell+2,0xFF9B4148);
        int side=Math.max(92,Math.min(132,width/3)),sw=Math.max(20,(side-28)/3),ph=17,py=safeTop()+88;
        c.drawTextWithShadow(textRenderer,"Палитра",10,py-12,0xFFB09F97);
        for(int i=0;i<PALETTE.length;i++){int x=10+i%3*(sw+3),y=py+i/3*(ph+4);c.fill(x,y,x+sw,y+ph,PALETTE[i]);c.drawBorder(x,y,sw,ph,PALETTE[i]==color?0xFFF0D8D5:0xFF5A3A3E);}
        if(height>245)c.drawTextWrapped(textRenderer,Text.literal("Прозрачная ячейка = ластик. Редактируется только выбранный UV-слой."),10,py+70,side-18,0xFF8F8580);
        super.render(c,mx,my,d);
    }
}
