package ru.fifth.horror.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import ru.fifth.horror.cabinet.CabinetFeature;

/** Client renderer registration for the animated player cabinet. */
public final class CabinetClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererRegistry.register(CabinetFeature.CABINET_BE, CabinetRenderer::new);
    }
}
