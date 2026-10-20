package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server-to-client: the ids of the diary entries the player has, in order (see com.lotusblight.story.Diary). */
public class DiarySyncPacket {
    private final List<String> ids;

    public DiarySyncPacket(List<String> ids) {
        this.ids = ids;
    }

    public static void encode(DiarySyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ids.size());
        for (String id : packet.ids) buf.writeUtf(id, 64);
    }

    public static DiarySyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUtf(64));
        return new DiarySyncPacket(ids);
    }

    public static void handle(DiarySyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        List<String> ids = packet.ids;
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.story.DiaryScreen.setEntries(ids)));
        ctx.setPacketHandled(true);
    }
}
