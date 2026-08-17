package ${package}.photon_plugin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class ModifyBlockEffectPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("${modid}", "modify_block_effect");
    public static final Type<ModifyBlockEffectPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ModifyBlockEffectPacket> CODEC =
            StreamCodec.ofMember(ModifyBlockEffectPacket::encode, ModifyBlockEffectPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }

        buf.writeBoolean(hasName);
        if (hasName) {
            buf.writeUtf(effectName);
        }

        buf.writeBoolean(hasOffset);
        if (hasOffset) {
            buf.writeDouble(offsetX);
            buf.writeDouble(offsetY);
            buf.writeDouble(offsetZ);
        }

        buf.writeBoolean(hasRotation);
        if (hasRotation) {
            buf.writeDouble(rotationX);
            buf.writeDouble(rotationY);
            buf.writeDouble(rotationZ);
        }

        buf.writeBoolean(hasScale);
        if (hasScale) {
            buf.writeDouble(scaleX);
            buf.writeDouble(scaleY);
            buf.writeDouble(scaleZ);
        }
    }

    public static ModifyBlockEffectPacket decode(RegistryFriendlyByteBuf buf) {
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

    public static void execute(ModifyBlockEffectPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.execute(packet);
        }
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
