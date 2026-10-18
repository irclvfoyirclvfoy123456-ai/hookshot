package com.example.hookshot;

import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The flying / anchored hook.
 *
 * <p>It implements {@link Leashable}, so the vanilla lead renderer draws the rope from this entity to the
 * player (no particle rope). All movement of the *player* is done client side (see HookClient) for perfectly
 * smooth motion; the server only owns this entity, the anchor point and the effects.
 */
public class HookEntity extends Entity implements Leashable, ItemSupplier {
	private static final double FLIGHT_SPEED = 3.0;       // blocks per tick
	private static final double MAX_RANGE = 48.0;
	private static final int MAX_FLIGHT_TICKS = 24;
	private static final int MAX_ATTACH_TICKS = 20 * 12;
	private static final double ARRIVE_DISTANCE = 1.9;
	public static final int RELOAD_TICKS = 28;

	private enum State { FLYING, ATTACHED, RETRACTING }

	private UUID ownerId;
	private UUID targetId;
	private State state = State.FLYING;
	private Vec3 anchor = Vec3.ZERO;
	private Vec3 flightVelocity = Vec3.ZERO;
	private boolean reel;
	private int attachedTicks;
	private Leashable.LeashData leashData;

	public HookEntity(EntityType<? extends HookEntity> type, Level level) {
		super(type, level);
	}

	public void setup(ServerPlayer owner, Vec3 pos, Vec3 dir, boolean reelMode) {
		this.ownerId = owner.getUUID();
		this.reel = reelMode;
		this.setPos(pos.x, pos.y, pos.z);
		this.flightVelocity = dir.normalize().scale(FLIGHT_SPEED);
		this.setDeltaMovement(this.flightVelocity);
	}

	// ------------------------------------------------------------------ Leashable (rope rendering)

	@Override
	public Leashable.LeashData getLeashData() {
		// Server: report "no leash" so vanilla's lead logic (elastic pull, snapping at ~12 blocks, dropping a lead
		// item) never touches the hook. Client: real data, so the rope is drawn.
		return this.level().isClientSide() ? this.leashData : null;
	}

	@Override
	public void setLeashData(Leashable.LeashData data) {
		this.leashData = data;
	}

	// ------------------------------------------------------------------ ItemSupplier (visual of the hook)

	@Override
	public ItemStack getItem() {
		return new ItemStack(ModItems.HOOK_HEAD);
	}

	// ------------------------------------------------------------------ boilerplate

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	// ------------------------------------------------------------------ logic

	public void manualRelease() {
		if (this.level() instanceof ServerLevel level) {
			ServerPlayer owner = findOwner(level);
			if (owner != null) {
				release(level, owner, false);
			} else {
				this.discard();
			}
		}
	}

	private ServerPlayer findOwner(ServerLevel level) {
		if (ownerId == null) {
			return null;
		}
		if (level.getPlayerByUUID(ownerId) instanceof ServerPlayer sp && sp.isAlive() && sp.level() == level) {
			return sp;
		}
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return; // client: position is interpolated from server packets, rope drawn by the lead renderer
		}

		ServerPlayer owner = findOwner(level);
		if (owner == null) {
			HookshotMod.ACTIVE.values().remove(this);
			this.discard();
			return;
		}
		if (this.position().distanceTo(owner.position()) > MAX_RANGE + 20.0) {
			release(level, owner, false);
			return;
		}

		switch (state) {
			case FLYING -> flyTick(level, owner);
			case ATTACHED -> attachedTick(level, owner);
			case RETRACTING -> retractTick(level, owner);
		}
	}

	private void flyTick(ServerLevel level, ServerPlayer owner) {
		Vec3 from = this.position();
		Vec3 to = from.add(this.flightVelocity);

		BlockHitResult blockHit = level.clip(new ClipContext(from, to,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
		Vec3 end = hitBlock ? blockHit.getLocation() : to;

		// nearest mob on the path
		Entity hitEntity = null;
		double best = from.distanceToSqr(end);
		AABB sweep = new AABB(from, end).inflate(0.5);
		for (Entity e : level.getEntities(this, sweep,
				ent -> ent instanceof LivingEntity && ent != owner && ent.isAlive() && !ent.isSpectator())) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(from, end);
			if (hit.isPresent()) {
				double d = from.distanceToSqr(hit.get());
				if (d < best) {
					best = d;
					hitEntity = e;
				}
			}
		}

		spawnTrail(level, from, end);

		if (hitEntity != null) {
			this.targetId = hitEntity.getUUID();
			Vec3 center = hitEntity.position().add(0.0, hitEntity.getBbHeight() * 0.5, 0.0);
			this.setPos(center.x, center.y, center.z);
			this.setDeltaMovement(Vec3.ZERO);
			this.state = State.ATTACHED;
			this.anchor = center;
			hitEffects(level, center, true);
		} else if (hitBlock) {
			Vec3 normal = new Vec3(
					blockHit.getDirection().getStepX(),
					blockHit.getDirection().getStepY(),
					blockHit.getDirection().getStepZ());
			Vec3 point = blockHit.getLocation();
			this.setPos(point.x, point.y, point.z);
			this.setDeltaMovement(Vec3.ZERO);
			this.targetId = null;
			// aim slightly off the surface so the player ends up beside / on top of the block
			this.anchor = point.add(normal.scale(0.55));
			this.state = State.ATTACHED;
			hitEffects(level, point, false);
		} else {
			this.setPos(to.x, to.y, to.z);
			if (this.tickCount > MAX_FLIGHT_TICKS || to.distanceTo(owner.getEyePosition()) > MAX_RANGE) {
				startRetract(level);
			}
		}
	}

	private void attachedTick(ServerLevel level, ServerPlayer owner) {
		attachedTicks++;
		Vec3 anchorNow = this.anchor;
		Entity target = null;

		if (targetId != null) {
			target = level.getEntity(targetId);
			if (target == null || !target.isAlive()) {
				startRetract(level);
				return;
			}
			anchorNow = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			this.setPos(anchorNow.x, anchorNow.y, anchorNow.z);
		}

		Vec3 body = owner.position().add(0.0, owner.getBbHeight() * 0.5, 0.0);
		double dist = body.distanceTo(anchorNow);

		if (attachedTicks > MAX_ATTACH_TICKS) {
			release(level, owner, false);
			return;
		}

		if (reel && target != null) {
			// Reel the mob toward the player.
			Vec3 toOwner = body.subtract(anchorNow);
			if (dist < 2.6) {
				release(level, owner, true);
				return;
			}
			Vec3 pull = toOwner.normalize().scale(Math.min(1.1, 0.25 + attachedTicks * 0.06));
			target.setDeltaMovement(target.getDeltaMovement().scale(0.55));
			target.push(pull.x * 0.55, pull.y * 0.55 + 0.04, pull.z * 0.55);
			target.fallDistance = 0.0;
			if (attachedTicks % 5 == 1) {
				level.playSound(null, anchorNow.x, anchorNow.y, anchorNow.z,
						SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.5f, 1.4f);
			}
			level.sendParticles(ParticleTypes.CRIT, anchorNow.x, anchorNow.y, anchorNow.z, 2, 0.2, 0.2, 0.2, 0.05);
			return;
		}

		// Pull the owner: the client glides, we just keep it informed and handle effects.
		ServerPlayNetworking.send(owner, new HookStatePayload(true, anchorNow.x, anchorNow.y, anchorNow.z));
		owner.resetFallDistance();

		if (dist < ARRIVE_DISTANCE && attachedTicks > 3) {
			release(level, owner, true);
			return;
		}

		if (attachedTicks == 1) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.8f, 1.5f);
		}
		if (attachedTicks % 6 == 0) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.0f + Math.min(0.5f, attachedTicks * 0.01f));
		}
		level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.2, owner.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 2, 0.3, 0.4, 0.3, 0.1);
	}

	private void startRetract(ServerLevel level) {
		this.state = State.RETRACTING;
		this.targetId = null;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.7f, 1.2f);
	}

	private void retractTick(ServerLevel level, ServerPlayer owner) {
		Vec3 target = owner.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 here = this.position();
		Vec3 delta = target.subtract(here);
		double dist = delta.length();
		if (dist < 2.5) {
			release(level, owner, false);
			return;
		}
		Vec3 next = here.add(delta.scale(Math.min(3.5, dist) / dist));
		spawnTrail(level, here, next);
		this.setPos(next.x, next.y, next.z);
	}

	/** Ends the hook, tells the client to stop gliding, starts the automatic reload. */
	private void release(ServerLevel level, ServerPlayer owner, boolean arrived) {
		ServerPlayNetworking.send(owner, new HookStatePayload(false, 0.0, 0.0, 0.0));
		HookshotMod.ACTIVE.remove(owner.getUUID(), this);
		owner.resetFallDistance();
		owner.getCooldowns().addCooldown(new ItemStack(ModItems.HOOKSHOT), RELOAD_TICKS);
		HookshotMod.scheduleReload(owner, RELOAD_TICKS);

		Vec3 p = this.position();
		if (arrived) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.5f, 1.6f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 14, 0.4, 0.5, 0.4, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.03);
		this.discard();
	}

	private void spawnTrail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(d.length() / 0.75));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.add(d.scale((double) i / steps));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	private void hitEffects(ServerLevel level, Vec3 p, boolean mob) {
		level.playSound(null, p.x, p.y, p.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0f, mob ? 1.5f : 1.1f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35f, 1.9f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 24, 0.25, 0.25, 0.25, 0.3);
		level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 8, 0.1, 0.1, 0.1, 0.12);
		level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 12, 0.3, 0.3, 0.3, 0.4);
	}
}
