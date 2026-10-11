package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

public final class TimelineExpression {
    private final ShapeExpression expression;

    private TimelineExpression(ShapeExpression expression) {
        this.expression = expression;
    }

    public static TimelineExpression parse(String text) {
        return new TimelineExpression(ShapeExpression.parse(text));
    }

    public double eval(double t) {
        return expression.eval(new double[]{t, Math.PI, 0, 0, 0, 0, 0});
    }
}
