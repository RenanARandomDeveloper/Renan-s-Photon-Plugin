package ${package}.photon_plugin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModifyEntityEffectPacket {

    private List<Integer> entityIds = new ArrayList<>();
    private boolean hasName;
    private String effectName = "";
    private boolean hasOffset;
    private double offsetX, offsetY, offsetZ;
    private boolean hasRotation;
    private double rotationX, rotationY, rotationZ;
    private boolean hasScale;
    private double scaleX, scaleY, scaleZ;

    public ModifyEntityEffectPacket() {
    }

    public void setEntityIds(List<Integer> entityIds) {
        this.entityIds = entityIds;
    }

    public void setName(String name) {
        this.hasName = name != null && !name.isEmpty();
        this.effectName = this.hasName ? name : "";
    }

    public void setOffset(double x, double y, double z) {
        this.hasOffset = true;
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public void setRotation(double x, double y, double z) {
        this.hasRotation = true;
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }

    public void setScale(double x, double y, double z) {
        this.hasScale = true;
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
    }

    public static void encode(ModifyEntityEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityIds.size());
        for (int id : packet.entityIds) {
            buf.writeVarInt(id);
        }

        buf.writeBoolean(packet.hasName);
        if (packet.hasName) {
            buf.writeUtf(packet.effectName);
        }

        buf.writeBoolean(packet.hasOffset);
        if (packet.hasOffset) {
            buf.writeDouble(packet.offsetX);
            buf.writeDouble(packet.offsetY);
            buf.writeDouble(packet.offsetZ);
        }

        buf.writeBoolean(packet.hasRotation);
        if (packet.hasRotation) {
            buf.writeDouble(packet.rotationX);
            buf.writeDouble(packet.rotationY);
            buf.writeDouble(packet.rotationZ);
        }

        buf.writeBoolean(packet.hasScale);
        if (packet.hasScale) {
            buf.writeDouble(packet.scaleX);
            buf.writeDouble(packet.scaleY);
            buf.writeDouble(packet.scaleZ);
        }
    }

    public static ModifyEntityEffectPacket decode(FriendlyByteBuf buf) {
        var packet = new ModifyEntityEffectPacket();

        int idCount = buf.readVarInt();
        List<Integer> ids = new ArrayList<>(idCount);
        for (int i = 0; i < idCount; i++) {
            ids.add(buf.readVarInt());
        }
        packet.entityIds = ids;

        packet.hasName = buf.readBoolean();
        if (packet.hasName) {
            packet.effectName = buf.readUtf();
        }

        packet.hasOffset = buf.readBoolean();
        if (packet.hasOffset) {
            packet.offsetX = buf.readDouble();
            packet.offsetY = buf.readDouble();
            packet.offsetZ = buf.readDouble();
        }

        packet.hasRotation = buf.readBoolean();
        if (packet.hasRotation) {
            packet.rotationX = buf.readDouble();
            packet.rotationY = buf.readDouble();
            packet.rotationZ = buf.readDouble();
        }

        packet.hasScale = buf.readBoolean();
        if (packet.hasScale) {
            packet.scaleX = buf.readDouble();
            packet.scaleY = buf.readDouble();
            packet.scaleZ = buf.readDouble();
        }

        return packet;
    }

    public static void handle(ModifyEntityEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Client.execute(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        static void execute(ModifyEntityEffectPacket packet) {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            List<Entity> entities = new ArrayList<>();
            for (int id : packet.entityIds) {
                var entity = level.getEntity(id);
                if (entity != null) {
                    entities.add(entity);
                }
            }

            if (entities.isEmpty()) {
                return;
            }

            var builder = ModifyFXEntityClient.modify(entities);
            if (packet.hasName) {
                builder.name(packet.effectName);
            }
            if (packet.hasOffset) {
                builder.offset(packet.offsetX, packet.offsetY, packet.offsetZ);
            }
            if (packet.hasRotation) {
                builder.rotation(packet.rotationX, packet.rotationY, packet.rotationZ);
            }
            if (packet.hasScale) {
                builder.scale(packet.scaleX, packet.scaleY, packet.scaleZ);
            }
            builder.send();
        }
    }
}
