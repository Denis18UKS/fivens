package ru.fifth.horror.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import ru.fifth.horror.block.TelevisionBlock;
import ru.fifth.horror.block.TelevisionBlockEntity;

import java.util.Random;

/** Draws VHS content only on the physical black screen area of the television model. */
public final class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private final TextRenderer text;
    public TelevisionRenderer(BlockEntityRendererFactory.Context ctx){text=ctx.getTextRenderer();}

    @Override
    public void render(TelevisionBlockEntity be,float delta,MatrixStack ms,VertexConsumerProvider vertices,int light,int overlay){
        if(be.getRecording().isBlank()&&be.getStaticTicks()<=0)return;
        Direction facing=be.getCachedState().contains(TelevisionBlock.FACING)?be.getCachedState().get(TelevisionBlock.FACING):Direction.NORTH;
        float yaw=switch(facing){case SOUTH->180f;case EAST->-90f;case WEST->90f;default->0f;};

        ms.push();
        ms.translate(.5,.73,.5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        // Local north face. Tiny offset prevents z-fighting with the black screen polygon.
        ms.translate(0,0,-.505);
        ms.scale(.0062f,-.0062f,.0062f);

        var matrix=ms.peek().getPositionMatrix();
        int x=-54,y=-36;
        String black="██████████████████";
        for(int row=0;row<12;row++)text.draw(black,x,y+row*6,0xFF000000,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);

        VhsPlayback.Session session=VhsPlayback.session(be.getPos());
        boolean noise=be.getStaticTicks()>0||(session!=null&&session.staticPhase());
        if(noise){
            long seed=(be.getWorld()==null?0:be.getWorld().getTime())*31L+be.getPos().asLong();Random r=new Random(seed);
            String chars=" ░▒▓";
            for(int row=0;row<10;row++){
                StringBuilder line=new StringBuilder();for(int col=0;col<18;col++)line.append(chars.charAt(r.nextInt(chars.length())));
                int grey=0xFF555555+r.nextInt(0x888888);text.draw(line.toString(),x,y+3+row*6,grey,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);
            }
            text.draw("NO SIGNAL",-27,y+29,0xFFBFBFBF,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);
        }else if(session!=null){
            VhsPlayback.Sample s=session.sample();float p=session.progress();
            // Compact monochrome VHS frame derived from the recorded camera timeline. It stays inside the TV surface.
            for(int row=0;row<9;row++){
                StringBuilder line=new StringBuilder();
                for(int col=0;col<18;col++){
                    double wave=Math.sin((col*.72)+(row*.41)+(p*22)+(s.yaw()*.025)+s.x()*.03+s.z()*.02);
                    line.append(wave>.55?'▓':wave>0?'▒':wave>-.55?'░':' ');
                }
                int g=Math.max(55,Math.min(205,(int)(120+Math.sin(row+p*17)*55)));int color=0xFF000000|(g<<16)|(g<<8)|g;
                text.draw(line.toString(),x,y+3+row*6,color,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);
            }
            text.draw("REC",x+2,y+2,0xFFE6E6E6,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);
            String sub=session.subtitle();if(!sub.isBlank()){String clipped=sub.length()>25?sub.substring(0,25):sub;text.draw(Text.literal(clipped),x+2,y+60,0xFFE0E0E0,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);}
        }else{
            text.draw("REC  "+be.getRecording(),x+3,y+30,0xFFBFBFBF,false,matrix,vertices,TextRenderer.TextLayerType.POLYGON_OFFSET,0,light);
        }
        ms.pop();
    }
}
