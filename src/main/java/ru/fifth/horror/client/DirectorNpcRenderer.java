package ru.fifth.horror.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import ru.fifth.horror.entity.DirectorNpcEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DirectorNpcRenderer extends GeoEntityRenderer<DirectorNpcEntity> {
    public DirectorNpcRenderer(EntityRendererFactory.Context context) { super(context, new DirectorNpcModel()); this.shadowRadius = 0.35f; }
}
