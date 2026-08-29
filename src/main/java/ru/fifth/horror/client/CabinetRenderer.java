package ru.fifth.horror.client;

import ru.fifth.horror.block.CabinetBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class CabinetRenderer extends GeoBlockRenderer<CabinetBlockEntity> {
    public CabinetRenderer(net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context context) {
        super(new CabinetModel());
    }
}
