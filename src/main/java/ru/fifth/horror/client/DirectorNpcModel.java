package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.entity.DirectorNpcEntity;
import software.bernie.geckolib.model.GeoModel;

public class DirectorNpcModel extends GeoModel<DirectorNpcEntity> {
    @Override public Identifier getModelResource(DirectorNpcEntity npc) { return npc.getModelResource(); }
    @Override public Identifier getTextureResource(DirectorNpcEntity npc) { return RuntimeSkinManager.textureFor(npc.getSkinBase64(), npc.getTextureResource()); }
    @Override public Identifier getAnimationResource(DirectorNpcEntity npc) { return npc.getAnimationResource(); }
}
