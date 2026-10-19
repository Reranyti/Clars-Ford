package com.lotusblight.map;

import com.lotusblight.story.StoryState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server: a dialogue set a story flag (a choice, an introduction). Only plain flag names are accepted. */
public class StoryFlagPacket {
    private final String flag;

    public StoryFlagPacket(String flag) {
        this.flag = flag;
    }

    public static void encode(StoryFlagPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.flag, 48);
    }

    public static StoryFlagPacket decode(FriendlyByteBuf buf) {
        return new StoryFlagPacket(buf.readUtf(48));
    }

    public static void handle(StoryFlagPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null || !packet.flag.matches("[a-z0-9_]{1,48}")) return;
            StoryState.get(player.getServer()).set(packet.flag);
        });
        ctx.setPacketHandled(true);
    }
}
