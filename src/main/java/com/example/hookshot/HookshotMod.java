package com.example.hookshot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTabs;

public class HookshotMod implements ModInitializer {
	public static final String MOD_ID = "hookshot";

	/** Player UUID -> hook currently out. Server thread only. */
	public static final Map<UUID, HookEntity> ACTIVE = new HashMap<>();

	/** Player UUID -> server tick at which the auto reload finishes. Server thread only. */
	private static final Map<UUID, Integer> RELOAD_AT = new HashMap<>();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void scheduleReload(ServerPlayer player, int ticks) {
		RELOAD_AT.put(player.getUUID(), player.level().getServer().getTickCount() + ticks);
	}

	@Override
	public void onInitialize() {
		ModEntities.init();
		ModItems.init();

		PayloadTypeRegistry.clientboundPlay().register(HookStatePayload.TYPE, HookStatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HookReleasePayload.TYPE, HookReleasePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(HookReleasePayload.TYPE, (payload, context) -> {
			HookEntity hook = ACTIVE.get(context.player().getUUID());
			if (hook != null && !hook.isRemoved()) {
				hook.manualRelease();
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(HookshotMod::tickReload);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(ModItems.HOOKSHOT));
	}

	/** Auto reload: when the cooldown ends, play the "loaded" click and a small spark effect. */
	private static void tickReload(MinecraftServer server) {
		if (RELOAD_AT.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Integer>> it = RELOAD_AT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			if (now < entry.getValue()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.9f, 1.1f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() - 0.3, player.getZ(),
					8, 0.25, 0.15, 0.25, 0.15);
		}
	}
}
