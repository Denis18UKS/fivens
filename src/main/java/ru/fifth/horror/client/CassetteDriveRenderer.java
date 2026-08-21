package ru.fifth.horror.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import ru.fifth.horror.block.CassetteDriveBlock;
import ru.fifth.horror.block.CassetteDriveBlockEntity;

/** Renders the cassette following the latest 1.5 second insert/eject animation path. */
public final class CassetteDriveRenderer implements BlockEntityRenderer<CassetteDriveBlockEntity>{
    private final BlockEntityRendererFactory.Context ctx;public CassetteDriveRenderer(BlockEntityRendererFactory.Context c){ctx=c;}
    @Override public void render(CassetteDriveBlockEntity be,float delta,MatrixStack m,VertexConsumerProvider v,int light,int overlay){
        ItemStack st=be.getCassette();if(st.isEmpty())return;
        float progress;
        if(be.getPhase()==1) progress=1f-(be.getTimer()-delta)/(float)CassetteDriveBlockEntity.INSERT_TICKS;
        else if(be.getPhase()==3) progress=(be.getTimer()-delta)/(float)CassetteDriveBlockEntity.EJECT_TICKS;
        else progress=1f;
        progress=Math.max(0,Math.min(1,progress));

        Direction facing=be.getCachedState().contains(CassetteDriveBlock.FACING)?be.getCachedState().get(CassetteDriveBlock.FACING):Direction.NORTH;
        float yaw=switch(facing){case SOUTH->180f;case EAST->-90f;case WEST->90f;default->0f;};
        // Supplied animation moves caseta from local Z=-15 to Z=0; 16 model units = one block.
        float zModel=-15f*(1f-progress);
        m.push();
        m.translate(.5,.34,.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        m.translate(0,0,zModel/16f-.48f);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        m.scale(.78f,.78f,.78f);
        ctx.getItemRenderer().renderItem(st,ModelTransformationMode.FIXED,light,overlay,m,v,be.getWorld(),0);
        m.pop();
    }
}
