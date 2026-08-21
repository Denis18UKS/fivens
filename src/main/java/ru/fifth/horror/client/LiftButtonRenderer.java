package ru.fifth.horror.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import ru.fifth.horror.entity.LiftButtonEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class LiftButtonRenderer extends GeoEntityRenderer<LiftButtonEntity> {
    public LiftButtonRenderer(EntityRendererFactory.Context context) { super(context, new LiftButtonModel()); this.shadowRadius = 0.0f; }
}
