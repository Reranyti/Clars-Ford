package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-to-client: opens the dialogue screen for a dialogue file (see com.lotusblight.client.story.DialogueScreen). */
public class OpenDialoguePacket {
    private final String id;

    public OpenDialoguePacket(String id) {
        this.id = id;
    }

    public static void encode(OpenDialoguePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.id, 64);
    }

    public static OpenDialoguePacket decode(FriendlyByteBuf buf) {
        return new OpenDialoguePacket(buf.readUtf(64));
    }

    public static void handle(OpenDialoguePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        String id = packet.id;
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.story.DialogueScreen.open(id)));
        ctx.setPacketHandled(true);
    }
}
