package ru.fifth.horror.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import ru.fifth.horror.block.CassetteDriveBlockEntity;

/** Uses the timing/keyframe shape supplied with videocaseta animation, including reverse ejection. */
public final class CassetteDriveRenderer implements BlockEntityRenderer<CassetteDriveBlockEntity>{
    private final BlockEntityRendererFactory.Context ctx;public CassetteDriveRenderer(BlockEntityRendererFactory.Context c){ctx=c;}
    @Override public void render(CassetteDriveBlockEntity be,float delta,MatrixStack m,VertexConsumerProvider v,int light,int overlay){
        ItemStack st=be.getCassette();if(st.isEmpty())return;float p;
        if(be.getPhase()==1)p=1f-be.getTimer()/(float)CassetteDriveBlockEntity.INSERT_TICKS;
        else if(be.getPhase()==2||be.getPhase()==0)p=1f;
        else if(be.getPhase()==3)p=be.getTimer()/(float)CassetteDriveBlockEntity.EJECT_TICKS;
        else p=0f;
        p=Math.max(0,Math.min(1,p));
        // Piecewise approximation of supplied 0/0.5/1/3.4167/4.9167s keyframes.
        float t=p*4.9167f,y,z,rot;
        if(t<.5f){float q=t/.5f;y=-2+2*q;z=0;rot=0;}
        else if(t<1f){float q=(t-.5f)/.5f;y=q;z=q;rot=30*q;}
        else if(t<3.4167f){float q=(t-1f)/2.4167f;y=1-2*q;z=1+4*q;rot=30*(1-q);}
        else{float q=(t-3.4167f)/1.5f;y=-1;z=5+6.59f*q;rot=0;}
        m.push();m.translate(.5,.54+y*.018,.20+z*.025);m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(75+rot));m.scale(.75f,.75f,.75f);ctx.getItemRenderer().renderItem(st,ModelTransformationMode.FIXED,light,overlay,m,v,be.getWorld(),0);m.pop();
    }
}
