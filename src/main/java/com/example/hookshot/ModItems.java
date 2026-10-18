package com.example.hookshot;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	/** The crossbow-style launcher the player holds. */
	public static final Item HOOKSHOT = register("hookshot",
			props -> new HookshotItem(props.stacksTo(1).rarity(Rarity.RARE)));

	/** Only used as the visual of the flying hook (rendered by the entity). */
	public static final Item HOOK_HEAD = register("hook_head", Item::new);

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HookshotMod.id(name));
		T item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		// Touching the class runs the static initializers above.
	}

	private ModItems() {
	}
}
