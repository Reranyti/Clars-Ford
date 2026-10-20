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
    private final List<String> states;

    public QuestSyncPacket(List<String> ids, List<Boolean> done, List<String> states) {
        this.ids = ids;
        this.done = done;
        this.states = states;
    }

    public static void encode(QuestSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ids.size());
        for (int i = 0; i < packet.ids.size(); i++) {
            buf.writeUtf(packet.ids.get(i), 64);
            buf.writeBoolean(packet.done.get(i));
            buf.writeUtf(packet.states.get(i), 64);
        }
    }

    public static QuestSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        List<String> ids = new ArrayList<>(n);
        List<Boolean> done = new ArrayList<>(n);
        List<String> states = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(64));
            done.add(buf.readBoolean());
            states.add(buf.readUtf(64));
        }
        return new QuestSyncPacket(ids, done, states);
    }

    public static void handle(QuestSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        List<String> ids = packet.ids;
        List<Boolean> done = packet.done;
        List<String> states = packet.states;
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.story.QuestScreen.setQuests(ids, done, states)));
        ctx.setPacketHandled(true);
    }
}
