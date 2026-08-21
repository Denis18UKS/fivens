package ru.fifth.horror.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MonsterForLiftRenderer extends GeoEntityRenderer<MonsterForLiftEntity> {
    public MonsterForLiftRenderer(EntityRendererFactory.Context context) { super(context, new MonsterForLiftModel()); this.shadowRadius = 0.45f; }
}
