package com.example.hookshot;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Right click: fire the hook. Right click again while hooked: let go.
 * Sneak + right click at a mob: reel the mob in instead of flying to it.
 * Reloading is automatic (item cooldown + click sound when ready).
 */
public class HookshotItem extends Item {
	public HookshotItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS; // client: just swing the arm
		}

		// A hook is already out: second click lets go.
		HookEntity existing = HookshotMod.ACTIVE.get(serverPlayer.getUUID());
		if (existing != null && !existing.isRemoved()) {
			existing.manualRelease();
			return InteractionResult.SUCCESS;
		}

		Vec3 look = player.getLookAngle();
		Vec3 muzzle = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.18, 0.0);

		HookEntity hook = new HookEntity(ModEntities.HOOK, serverLevel);
		hook.setup(serverPlayer, muzzle, look, player.isShiftKeyDown());
		serverLevel.addFreshEntity(hook);
		// The rope: a vanilla lead drawn from the hook to the player.
		hook.setLeashedTo(player, true);
		HookshotMod.ACTIVE.put(serverPlayer.getUUID(), hook);

		// Fire effects + sound
		serverLevel.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.75f);
		serverLevel.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6f, 1.7f);
		serverLevel.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 8, 0.08, 0.08, 0.08, 0.06);
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.25);

		return InteractionResult.SUCCESS;
	}
}
