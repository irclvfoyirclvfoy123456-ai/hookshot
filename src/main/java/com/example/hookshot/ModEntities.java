package com.example.hookshot;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<HookEntity> HOOK = registerHook();

	private static EntityType<HookEntity> registerHook() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HookshotMod.id("hook"));
		return Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.<HookEntity>of(HookEntity::new, MobCategory.MISC)
						.sized(0.3f, 0.3f)
						.clientTrackingRange(8)
						.updateInterval(1)
						.noSave()
						.build(key));
	}

	public static void init() {
		// Touching the class runs the static initializer above.
	}

	private ModEntities() {
	}
}
