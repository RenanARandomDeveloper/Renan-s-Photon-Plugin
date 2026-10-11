package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.mcreator.ui.init.L10N;

final class ShapeExpression {
    private static final String[] VARIABLES = {"t", "PI", "randomA", "randomB", "randomC", "randomD", "randomE"};
    private final Node root;

    private ShapeExpression(Node root) {
        this.root = root;
    }

    static ShapeExpression parse(String text) {
        try {
            Parser p = new Parser(text == null ? "" : text);
            Node n = p.parseExpr(0);
            if (p.pos < p.s.length()) {
                return new ShapeExpression(null);
            }
            return new ShapeExpression(n);
        } catch (RuntimeException e) {
            return new ShapeExpression(null);
        }
    }

    double eval(double[] vars) {
        return root == null ? 0.0 : root.eval(vars);
    }

    private interface Node {
        double eval(double[] vars);
    }

    private static final class Parser {
        private static final String[][] OPS = {
                {"<=", "<=", "20", "21"}, {">=", ">=", "20", "21"}, {"!=", "!=", "20", "21"}, {"<>", "!=", "20", "21"},
                {"==", "==", "20", "21"}, {"<", "<", "20", "21"}, {">", ">", "20", "21"}, {"=", "==", "20", "21"},
                {"+", "+", "30", "31"}, {"-", "-", "30", "31"}, {"*", "*", "40", "41"}, {"/", "/", "40", "41"},
                {"^", "^", "50", "50"}};
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        private static Node binary(String op, Node a, Node b) {
            return switch (op) {
                case "+" -> v -> a.eval(v) + b.eval(v);
                case "-" -> v -> a.eval(v) - b.eval(v);
                case "*" -> v -> a.eval(v) * b.eval(v);
                case "/" -> v -> a.eval(v) / b.eval(v);
                case "^" -> v -> Math.pow(a.eval(v), b.eval(v));
                case "<" -> v -> a.eval(v) < b.eval(v) ? 1 : 0;
                case "<=" -> v -> a.eval(v) <= b.eval(v) ? 1 : 0;
                case ">" -> v -> a.eval(v) > b.eval(v) ? 1 : 0;
                case ">=" -> v -> a.eval(v) >= b.eval(v) ? 1 : 0;
                case "==" -> v -> a.eval(v) == b.eval(v) ? 1 : 0;
                case "!=" -> v -> a.eval(v) != b.eval(v) ? 1 : 0;
                case "and" -> v -> a.eval(v) != 0 && b.eval(v) != 0 ? 1 : 0;
                default -> v -> a.eval(v) != 0 || b.eval(v) != 0 ? 1 : 0;
            };
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        boolean peek(String tok) {
            skipWs();
            return s.startsWith(tok, pos);
        }

        boolean eat(String tok) {
            if (peek(tok)) {
                pos += tok.length();
                return true;
            }
            return false;
        }

        void expect(String tok) {
            if (!eat(tok)) {
                throw new IllegalStateException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_expected", tok));
            }
        }

        boolean peekWord(String w) {
            skipWs();
            if (!s.startsWith(w, pos)) {
                return false;
            }
            int end = pos + w.length();
            return end >= s.length() || !Character.isLetterOrDigit(s.charAt(end)) && s.charAt(end) != '_';
        }

        Node parseExpr(int precedence) {
            Node left = parseFactor();
            while (true) {
                skipWs();
                String token = null;
                String op = null;
                int l = 0;
                int r = 0;
                for (String[] entry : OPS) {
                    if (s.startsWith(entry[0], pos)) {
                        token = entry[0];
                        op = entry[1];
                        l = Integer.parseInt(entry[2]);
                        r = Integer.parseInt(entry[3]);
                        break;
                    }
                }
                if (token == null) {
                    if (peekWord("and")) {
                        token = "and";
                        op = "and";
                        l = 5;
                        r = 6;
                    } else if (peekWord("or")) {
                        token = "or";
                        op = "or";
                        l = 10;
                        r = 11;
                    } else {
                        break;
                    }
                }
                if (l < precedence) {
                    break;
                }
                pos += token.length();
                final Node a = left;
                final Node b = parseExpr(r);
                left = binary(op, a, b);
            }
            return left;
        }

        Node parseFactor() {
            skipWs();
            if (pos >= s.length()) {
                throw new IllegalStateException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_factor"));
            }
            char ch = s.charAt(pos);
            if (Character.isDigit(ch) || ch == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                    pos++;
                }
                if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                    int save = pos;
                    pos++;
                    if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                        pos++;
                    }
                    if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                        while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                            pos++;
                        }
                    } else {
                        pos = save;
                    }
                }
                double value = Double.parseDouble(s.substring(start, pos));
                return v -> value;
            }
            if (ch == '(') {
                pos++;
                Node inner = parseExpr(0);
                expect(")");
                return inner;
            }
            if (ch == '-') {
                pos++;
                Node operand = parseExpr(35);
                return v -> -operand.eval(v);
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                    pos++;
                }
                String word = s.substring(start, pos);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == '(') {
                    return parseCall(word);
                }
                if (word.equals("pi")) {
                    return v -> Math.PI;
                }
                for (int i = 0; i < VARIABLES.length; i++) {
                    if (VARIABLES[i].equals(word)) {
                        final int idx = i;
                        return v -> v[idx];
                    }
                }
                return v -> 0.0;
            }
            throw new IllegalStateException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_char", ch));
        }

        private Node parseCall(String name) {
            expect("(");
            Node a = parseExpr(0);
            if (name.equals("if")) {
                expect(",");
                Node b = parseExpr(0);
                expect(",");
                Node c = parseExpr(0);
                expect(")");
                return v -> a.eval(v) != 0 ? b.eval(v) : c.eval(v);
            }
            if (name.equals("atan2") || name.equals("max") || name.equals("min")) {
                expect(",");
                Node b = parseExpr(0);
                expect(")");
                return switch (name) {
                    case "atan2" -> v -> Math.atan2(a.eval(v), b.eval(v));
                    case "max" -> v -> Math.max(a.eval(v), b.eval(v));
                    default -> v -> Math.min(a.eval(v), b.eval(v));
                };
            }
            expect(")");
            return switch (name) {
                case "abs" -> v -> Math.abs(a.eval(v));
                case "acos" -> v -> Math.acos(a.eval(v));
                case "asin" -> v -> Math.asin(a.eval(v));
                case "atan" -> v -> Math.atan(a.eval(v));
                case "ceil" -> v -> Math.ceil(a.eval(v));
                case "cos" -> v -> Math.cos(a.eval(v));
                case "exp" -> v -> Math.exp(a.eval(v));
                case "floor" -> v -> Math.floor(a.eval(v));
                case "log" -> v -> Math.log(a.eval(v));
                case "round" -> v -> Math.rint(a.eval(v));
                case "sin" -> v -> Math.sin(a.eval(v));
                case "sqrt" -> v -> Math.sqrt(a.eval(v));
                case "tan" -> v -> Math.tan(a.eval(v));
                default ->
                        throw new IllegalStateException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.expr_function", name));
            };
        }
    }
}
