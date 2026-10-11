package net.renan.photonplugin.menus.resource.preview.forge;

import net.mcreator.ui.init.L10N;

import java.util.ArrayList;
import java.util.List;

final class FExpr {
    private final Node root;

    private FExpr(Node root) {
        this.root = root;
    }

    static FExpr parse(String input) {
        if (input == null) {
            return null;
        }
        try {
            Parser parser = new Parser(input);
            Node node = parser.expression(0);
            if (parser.peek().kind != Kind.EOF) {
                return null;
            }
            return new FExpr(node);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isLetter(c)) {
                int start = i;
                while (i < n && (Character.isLetter(s.charAt(i)) || Character.isDigit(s.charAt(i)))) {
                    i++;
                }
                out.add(new Token(Kind.WORD, s.substring(start, i), 0));
            } else if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    i++;
                }
                if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int save = i;
                    i++;
                    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                        i++;
                    }
                    if (i < n && Character.isDigit(s.charAt(i))) {
                        while (i < n && Character.isDigit(s.charAt(i))) {
                            i++;
                        }
                    } else {
                        i = save;
                    }
                }
                try {
                    out.add(new Token(Kind.NUMBER, s.substring(start, i), Double.parseDouble(s.substring(start, i))));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_bad_number"));
                }
            } else if ("*/+-^<>=,()".indexOf(c) >= 0) {
                if (c == '<' && i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(Kind.OP, "<=", 0));
                    i += 2;
                } else if (c == '>' && i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(Kind.OP, ">=", 0));
                    i += 2;
                } else if (c == '<' && i + 1 < n && s.charAt(i + 1) == '>') {
                    out.add(new Token(Kind.OP, "<>", 0));
                    i += 2;
                } else {
                    out.add(new Token(Kind.OP, String.valueOf(c), 0));
                    i++;
                }
            } else {
                throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_bad_char"));
            }
        }
        out.add(new Token(Kind.EOF, "", 0));
        return out;
    }

    double value(Env env) {
        return root.eval(env);
    }

    private enum Kind {NUMBER, WORD, OP, EOF}

    private interface Node {
        double eval(Env env);
    }

    static final class Env {
        final double[] random = new double[5];
        double t;
    }

    private record Token(Kind kind, String text, double number) {
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(String input) {
            tokens = tokenize(input);
        }

        private static Node binary(String op, Node a, Node b) {
            return switch (op) {
                case "+" -> e -> a.eval(e) + b.eval(e);
                case "-" -> e -> a.eval(e) - b.eval(e);
                case "*" -> e -> a.eval(e) * b.eval(e);
                case "/" -> e -> a.eval(e) / b.eval(e);
                case "^" -> e -> Math.pow(a.eval(e), b.eval(e));
                case "<" -> e -> a.eval(e) < b.eval(e) ? 1 : 0;
                case "<=" -> e -> a.eval(e) <= b.eval(e) ? 1 : 0;
                case "=" -> e -> a.eval(e) == b.eval(e) ? 1 : 0;
                case "<>" -> e -> a.eval(e) != b.eval(e) ? 1 : 0;
                case ">=" -> e -> a.eval(e) >= b.eval(e) ? 1 : 0;
                case ">" -> e -> a.eval(e) > b.eval(e) ? 1 : 0;
                case "and" -> e -> a.eval(e) != 0 && b.eval(e) != 0 ? 1 : 0;
                case "or" -> e -> a.eval(e) != 0 || b.eval(e) != 0 ? 1 : 0;
                default ->
                        throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_bad_operator"));
            };
        }

        private static Node unary(String name, Node a) {
            return switch (name) {
                case "abs" -> e -> Math.abs(a.eval(e));
                case "acos" -> e -> Math.acos(a.eval(e));
                case "asin" -> e -> Math.asin(a.eval(e));
                case "atan" -> e -> Math.atan(a.eval(e));
                case "ceil" -> e -> Math.ceil(a.eval(e));
                case "cos" -> e -> Math.cos(a.eval(e));
                case "exp" -> e -> Math.exp(a.eval(e));
                case "floor" -> e -> Math.floor(a.eval(e));
                case "log" -> e -> Math.log(a.eval(e));
                case "round" -> e -> Math.rint(a.eval(e));
                case "sin" -> e -> Math.sin(a.eval(e));
                case "sqrt" -> e -> Math.sqrt(a.eval(e));
                default -> e -> Math.tan(a.eval(e));
            };
        }

        Token peek() {
            return tokens.get(pos);
        }

        private Token next() {
            return tokens.get(pos++);
        }

        private void expect(String op) {
            Token t = next();
            if (t.kind != Kind.OP || !t.text.equals(op)) {
                throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_expected", op));
            }
        }

        private int[] power(Token t) {
            if (t.kind == Kind.WORD) {
                return switch (t.text) {
                    case "and" -> new int[]{5, 6};
                    case "or" -> new int[]{10, 11};
                    default -> null;
                };
            }
            if (t.kind != Kind.OP) {
                return null;
            }
            return switch (t.text) {
                case "<", "<=", "=", "<>", ">=", ">" -> new int[]{20, 21};
                case "+", "-" -> new int[]{30, 31};
                case "*", "/" -> new int[]{40, 41};
                case "^" -> new int[]{50, 50};
                default -> null;
            };
        }

        Node expression(int minPrecedence) {
            Node left = factor();
            while (true) {
                Token op = peek();
                int[] p = power(op);
                if (p == null || p[0] < minPrecedence) {
                    return left;
                }
                next();
                Node right = expression(p[1]);
                left = binary(op.text, left, right);
            }
        }

        private Node factor() {
            Token t = next();
            switch (t.kind) {
                case NUMBER -> {
                    double v = t.number;
                    return e -> v;
                }
                case WORD -> {
                    return word(t.text);
                }
                case OP -> {
                    if (t.text.equals("(")) {
                        Node inner = expression(0);
                        expect(")");
                        return inner;
                    }
                    if (t.text.equals("-")) {
                        Node operand = expression(35);
                        return e -> -operand.eval(e);
                    }
                    throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_unexpected", t.text));
                }
                default ->
                        throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_unexpected_end"));
            }
        }

        private Node word(String name) {
            switch (name) {
                case "abs", "acos", "asin", "atan", "ceil", "cos", "exp", "floor", "log", "round", "sin", "sqrt",
                     "tan" -> {
                    expect("(");
                    Node arg = expression(0);
                    expect(")");
                    return unary(name, arg);
                }
                case "atan2", "max", "min" -> {
                    expect("(");
                    Node a = expression(0);
                    expect(",");
                    Node b = expression(0);
                    expect(")");
                    return switch (name) {
                        case "atan2" -> e -> Math.atan2(a.eval(e), b.eval(e));
                        case "max" -> e -> Math.max(a.eval(e), b.eval(e));
                        default -> e -> Math.min(a.eval(e), b.eval(e));
                    };
                }
                case "if" -> {
                    expect("(");
                    Node test = expression(0);
                    expect(",");
                    Node yes = expression(0);
                    expect(",");
                    Node no = expression(0);
                    expect(")");
                    return e -> test.eval(e) != 0 ? yes.eval(e) : no.eval(e);
                }
                case "t" -> {
                    return e -> e.t;
                }
                case "PI", "pi" -> {
                    return e -> Math.PI;
                }
                case "randomA" -> {
                    return e -> e.random[0];
                }
                case "randomB" -> {
                    return e -> e.random[1];
                }
                case "randomC" -> {
                    return e -> e.random[2];
                }
                case "randomD" -> {
                    return e -> e.random[3];
                }
                case "randomE" -> {
                    return e -> e.random[4];
                }
                default -> {
                    return e -> 0;
                }
            }
        }
    }
}
