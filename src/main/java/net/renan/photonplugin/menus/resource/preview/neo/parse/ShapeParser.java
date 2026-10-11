package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.model.shape.*;

public final class ShapeParser {
    private ShapeParser() {
    }

    public static ShapeSetting parseShapeSetting(NbtTag.CompoundTag shapeSettingTag) {
        EmitterShape shape = parseShape(shapeSettingTag.getCompound("shape"));
        NumberFunction3 rotation = NumberFunctionParser.parse3(shapeSettingTag.get("rotation"), 0, 0, 0);
        NumberFunction3 scale = NumberFunctionParser.parse3(shapeSettingTag.get("scale"), 1, 1, 1);
        NumberFunction3 position = NumberFunctionParser.parse3(shapeSettingTag.get("position"), 0, 0, 0);
        return new ShapeSetting(shape, rotation, scale, position);
    }

    private static EmitterShape parseShape(NbtTag.CompoundTag wrapper) {
        String type = wrapper.getString("type", "cone");
        NbtTag.CompoundTag data = wrapper.getCompound("data");
        return switch (type) {
            case "circle" -> new CircleShape(
                    data.getFloat("radius", 0.5f),
                    data.getFloat("radiusThickness", 1),
                    data.getFloat("arc", 360),
                    parseShapeArc(data.getCompound("shapeArc")));
            case "sphere" -> new SphereShape(
                    data.getFloat("radius", 0.5f),
                    data.getFloat("radiusThickness", 1),
                    data.getFloat("arc", 360),
                    parseShapeArc(data.getCompound("shapeArc")));
            case "box" -> new BoxShape(parseBoxEmitFrom(data.getString("emitFrom", "Volume")));
            case "cylinder" -> new CylinderShape(
                    data.getFloat("radius", 0.5f),
                    data.getFloat("radiusThickness", 1),
                    data.getFloat("arc", 360),
                    parseShapeArc(data.getCompound("shapeArc")));
            case "dot" -> new DotShape();
            case "function" ->
                    new FunctionShape(data.getString("x", "0"), data.getString("y", "0"), data.getString("z", "0"),
                            data.getString("speedX", "0"), data.getString("speedY", "0"), data.getString("speedZ", "0"));
            case "cone" -> new ConeShape(
                    data.getFloat("angle", 25),
                    data.getFloat("radius", 0.5f),
                    data.getFloat("radiusThickness", 1),
                    data.getFloat("arc", 360),
                    parseShapeArc(data.getCompound("shapeArc")));
            case "mesh" -> new MeshShape(parseMeshShapeType(data.getString("type", "Triangle")),
                    FxNbtParser.parseModelSource(data.getCompound("meshData")));
            default -> new DotShape();
        };
    }

    private static MeshShape.Type parseMeshShapeType(String s) {
        return switch (s) {
            case "Vertex" -> MeshShape.Type.VERTEX;
            case "Edge" -> MeshShape.Type.EDGE;
            default -> MeshShape.Type.TRIANGLE;
        };
    }

    private static BoxShape.EmitFrom parseBoxEmitFrom(String s) {
        return switch (s) {
            case "Shell" -> BoxShape.EmitFrom.SHELL;
            case "Edge" -> BoxShape.EmitFrom.EDGE;
            default -> BoxShape.EmitFrom.VOLUME;
        };
    }

    private static ShapeArcSetting parseShapeArc(NbtTag.CompoundTag shapeArcTag) {
        String mode = shapeArcTag.getString("arcMode", "Random");
        float spread = shapeArcTag.getFloat("arcSpread", 0f);
        NumberFunction speed = NumberFunctionParser.parse(shapeArcTag.get("arcSpeed"), 1);
        return new ShapeArcSetting(mode, spread, speed);
    }
}
