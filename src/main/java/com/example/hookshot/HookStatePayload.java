package com.example.hookshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: tells the owner where the hook is anchored so the client can glide toward it. */
public record HookStatePayload(boolean active, double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HookStatePayload> TYPE =
			new CustomPacketPayload.Type<>(HookshotMod.id("hook_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HookStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, HookStatePayload::active,
			ByteBufCodecs.DOUBLE, HookStatePayload::x,
			ByteBufCodecs.DOUBLE, HookStatePayload::y,
			ByteBufCodecs.DOUBLE, HookStatePayload::z,
			HookStatePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
