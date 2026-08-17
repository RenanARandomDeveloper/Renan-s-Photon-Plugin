package ${package}.photon_plugin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class ModifyEntityEffectPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("${modid}", "modify_entity_effect");
    public static final Type<ModifyEntityEffectPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ModifyEntityEffectPacket> CODEC =
            StreamCodec.ofMember(ModifyEntityEffectPacket::encode, ModifyEntityEffectPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeVarInt(id);
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

    public static ModifyEntityEffectPacket decode(RegistryFriendlyByteBuf buf) {
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

    public static void execute(ModifyEntityEffectPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.execute(packet);
        }
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
