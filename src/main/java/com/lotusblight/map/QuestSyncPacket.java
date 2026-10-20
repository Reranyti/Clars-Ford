package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server-to-client: the player's quests (ids and whether each is done), in the order they were given (see com.lotusblight.story.Quests). */
public class QuestSyncPacket {
    private final List<String> ids;
    private final List<Boolean> done;

    public QuestSyncPacket(List<String> ids, List<Boolean> done) {
        this.ids = ids;
        this.done = done;
    }

    public static void encode(QuestSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ids.size());
        for (int i = 0; i < packet.ids.size(); i++) {
            buf.writeUtf(packet.ids.get(i), 64);
            buf.writeBoolean(packet.done.get(i));
        }
    }

    public static QuestSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        List<String> ids = new ArrayList<>(n);
        List<Boolean> done = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(64));
            done.add(buf.readBoolean());
        }
        return new QuestSyncPacket(ids, done);
    }

    public static void handle(QuestSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        List<String> ids = packet.ids;
        List<Boolean> done = packet.done;
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.story.QuestScreen.setQuests(ids, done)));
        ctx.setPacketHandled(true);
    }
}
