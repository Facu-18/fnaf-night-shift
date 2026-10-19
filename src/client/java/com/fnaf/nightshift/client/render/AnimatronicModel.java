package com.fnaf.nightshift.client.render;

import com.fnaf.nightshift.FnafNightShift;
import com.fnaf.nightshift.entity.AnimatronicEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Modelo GeckoLib de un animatrónico. Rutas:
 * geo/{id}.geo.json, textures/entity/{id}.png, animations/{id}.animation.json.
 * El hueso "neck" sigue la mirada de la entidad; "head" lo controlan las animaciones.
 */
public class AnimatronicModel<T extends AnimatronicEntity> extends GeoModel<T> {
	private final Identifier model;
	private final Identifier texture;
	private final Identifier animation;

	public AnimatronicModel(String id) {
		this.model = FnafNightShift.id("geo/" + id + ".geo.json");
		this.texture = FnafNightShift.id("textures/entity/" + id + ".png");
		this.animation = FnafNightShift.id("animations/" + id + ".animation.json");
	}

	@Override
	public Identifier getModelResource(T animatable) {
		return model;
	}

	@Override
	public Identifier getTextureResource(T animatable) {
		return texture;
	}

	@Override
	public Identifier getAnimationResource(T animatable) {
		return animation;
	}

	@Override
	public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
		super.setCustomAnimations(animatable, instanceId, animationState);
		CoreGeoBone neck = getAnimationProcessor().getBone("neck");
		if (neck == null) return;
		EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
		if (data == null) return;
		neck.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE * 0.8f);
		neck.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE * 0.8f);
	}
}
