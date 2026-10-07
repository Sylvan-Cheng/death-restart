package local.sylvan.deathreset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent only at the end of the countdown, immediately before closing the LAN session. */
public record ResetNoticePayload() implements CustomPacketPayload {
    public static final ResetNoticePayload INSTANCE = new ResetNoticePayload();
    public static final Type<ResetNoticePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deathreset", "reset_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResetNoticePayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
