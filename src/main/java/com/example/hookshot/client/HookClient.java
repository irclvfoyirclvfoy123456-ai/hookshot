package com.example.hookshot.client;

import com.example.hookshot.HookReleasePayload;
import com.example.hookshot.HookStatePayload;
import com.example.hookshot.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the hookshot.
 *
 * <p>Smoothness comes from here: instead of the server teleporting the player every tick, the client keeps its
 * own velocity and eases it toward the anchor every tick (ease-in at the start, brake near the end), so the
 * vanilla renderer interpolates it between ticks like any normal movement.
 */
public class HookClient implements ClientModInitializer {
	private static final double MAX_SPEED = 1.55;     // blocks per tick (~31 blocks/second)
	private static final int RAMP_TICKS = 12;         // ease-in length
	private static final double STEER = 0.30;         // how fast velocity follows the desired velocity

	private static boolean active;
	private static Vec3 anchor = Vec3.ZERO;
	private static int ticks;
	private static long lastPacketTime;

	@Override
	public void onInitializeClient() {
		// The flying hook is drawn as a billboarded item; the lead renderer draws the rope for us.
		EntityRendererRegistry.register(ModEntities.HOOK, context -> new ThrownItemRenderer<>(context, 1.4f, true));

		ClientPlayNetworking.registerGlobalReceiver(HookStatePayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (!payload.active()) {
				active = false;
				ticks = 0;
				return;
			}
			if (!active) {
				active = true;
				ticks = 0;
			}
			anchor = new Vec3(payload.x(), payload.y(), payload.z());
			lastPacketTime = mc.level == null ? 0L : mc.level.getGameTime();
		});

		ClientTickEvents.END_CLIENT_TICK.register(HookClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			active = false;
			return;
		}
		if (!active) {
			return;
		}
		// Safety: the server sends the anchor every tick; if it goes quiet, stop.
		if (mc.level.getGameTime() - lastPacketTime > 6) {
			active = false;
			ticks = 0;
			return;
		}

		ticks++;

		// Jump = let go, with a little hop for style.
		if (ticks > 4 && mc.options.keyJump.isDown()) {
			ClientPlayNetworking.send(new HookReleasePayload());
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.max(v.y, 0.0) + 0.42, v.z);
			active = false;
			ticks = 0;
			return;
		}

		Vec3 body = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 toAnchor = anchor.subtract(body);
		double dist = toAnchor.length();
		if (dist < 0.05) {
			return;
		}

		// smoothstep ease-in, then brake when close so arrival is gentle
		double t = Math.min(1.0, (double) ticks / RAMP_TICKS);
		double ease = t * t * (3.0 - 2.0 * t);
		double speed = MAX_SPEED * (0.25 + 0.75 * ease);
		speed = Math.min(speed, 0.35 + dist * 0.16);

		Vec3 desired = toAnchor.scale(speed / dist);
		Vec3 velocity = player.getDeltaMovement().lerp(desired, STEER);
		// cancel the gravity that vanilla will add next tick so the path stays a straight line
		velocity = velocity.add(0.0, 0.07, 0.0);
		if (player.onGround()) {
			velocity = velocity.add(0.0, 0.1, 0.0); // peel off the ground instead of scraping along it
		}
		player.setDeltaMovement(velocity);
		player.resetFallDistance();

		// client-only speed lines + wind
		var random = mc.level.getRandom();
		Vec3 back = velocity.scale(-0.25);
		for (int i = 0; i < 2; i++) {
			mc.level.addParticle(ParticleTypes.CLOUD,
					player.getX() + (random.nextDouble() - 0.5) * 0.8,
					player.getY() + random.nextDouble() * 1.8,
					player.getZ() + (random.nextDouble() - 0.5) * 0.8,
					back.x, back.y, back.z);
		}
		if (ticks % 7 == 1) {
			mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35f, 1.0f + random.nextFloat() * 0.2f, false);
		}
	}
}
