package ru.fifth.horror.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import ru.fifth.horror.entity.LiftEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class LiftRenderer extends GeoEntityRenderer<LiftEntity> {
    public LiftRenderer(EntityRendererFactory.Context context) { super(context, new LiftModel()); this.shadowRadius = 0.0f; }
}
