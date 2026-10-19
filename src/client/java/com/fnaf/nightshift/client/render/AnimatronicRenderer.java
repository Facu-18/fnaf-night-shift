package com.fnaf.nightshift.client.render;

import com.fnaf.nightshift.entity.AnimatronicEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderizador común. La capa AutoGlowingGeoLayer usa {id}_glowmask.png para
 * que los ojos brillen en la oscuridad.
 */
public class AnimatronicRenderer<T extends AnimatronicEntity> extends GeoEntityRenderer<T> {
	private final float scale;

	public AnimatronicRenderer(EntityRendererFactory.Context ctx, String id, float scale) {
		super(ctx, new AnimatronicModel<>(id));
		this.scale = scale;
		this.shadowRadius = 0.5f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public void render(T entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
		poseStack.push();
		poseStack.scale(scale, scale, scale);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		poseStack.pop();
	}

	@Override
	protected float getDeathMaxRotation(T animatable) {
		return 0f;
	}
}
