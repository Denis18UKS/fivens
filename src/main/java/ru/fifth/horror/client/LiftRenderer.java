package ru.fifth.horror.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import ru.fifth.horror.block.LiftBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class LiftRenderer extends GeoBlockRenderer<LiftBlockEntity> {
    public LiftRenderer(BlockEntityRendererFactory.Context context) {
        super(new LiftModel());
    }
}
