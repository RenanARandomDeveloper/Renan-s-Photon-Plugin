package ${package}.photon_plugin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModifyBlockEffectPacket {

    private List<BlockPos> positions = new ArrayList<>();
    private boolean hasName;
    private String effectName = "";
    private boolean hasOffset;
    private double offsetX, offsetY, offsetZ;
    private boolean hasRotation;
    private double rotationX, rotationY, rotationZ;
    private boolean hasScale;
    private double scaleX, scaleY, scaleZ;

    public ModifyBlockEffectPacket() {
    }

    public void setPositions(List<BlockPos> positions) {
        this.positions = positions;
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

    public static void encode(ModifyBlockEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.positions.size());
        for (BlockPos pos : packet.positions) {
            buf.writeBlockPos(pos);
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

    public static ModifyBlockEffectPacket decode(FriendlyByteBuf buf) {
        var packet = new ModifyBlockEffectPacket();

        int posCount = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(posCount);
        for (int i = 0; i < posCount; i++) {
            positions.add(buf.readBlockPos());
        }
        packet.positions = positions;

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

    public static void handle(ModifyBlockEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Client.execute(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        static void execute(ModifyBlockEffectPacket packet) {
            if (packet.positions.isEmpty()) {
                return;
            }

            var builder = ModifyFXBlockClient.modify(packet.positions);
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
