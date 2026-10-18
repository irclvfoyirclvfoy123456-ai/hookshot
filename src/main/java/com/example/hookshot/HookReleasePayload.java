package com.example.hookshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player pressed jump and wants to let go of the rope. */
public record HookReleasePayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HookReleasePayload> TYPE =
			new CustomPacketPayload.Type<>(HookshotMod.id("hook_release"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HookReleasePayload> CODEC =
			StreamCodec.unit(new HookReleasePayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
