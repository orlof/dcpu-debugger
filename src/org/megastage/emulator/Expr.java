package org.megastage.emulator;

import java.util.HashMap;

public class Expr {
    private DCPU dcpu;
    private HashMap<String, String> constants;
    private Node root;

    public Expr(String text, DCPU dcpu) {
        this.dcpu = dcpu;
        this.constants = dcpu.debugData.constants;

        Lexer lexer = new Lexer(text);
        if (lexer.token == 'e') {
            root = new Node();
        } else {
            root = expr(lexer);
        }
    }

    private Node expr(Lexer lexer) {
        Node node = term(lexer);
        do {
            if(lexer.token == '+') {
                lexer.next();
                node = new MathNode(node, term(lexer)) {
                    public int eval() {
                        return left.eval() + right.eval();
                    }
                };
            }
            if(lexer.token == '-') {
                lexer.next();
                node = new MathNode(node, term(lexer)) {
                    public int eval() {
                        return left.eval() - right.eval();
                    }
                };
            }
        } while("+-".indexOf(lexer.token) != -1);

        return node;
    }

    private Node term(Lexer lexer) {
        Node node = factor(lexer);
        do {
            if(lexer.token == '*') {
                lexer.next();
                node = new MathNode(node, term(lexer)) {
                    public int eval() {
                        return left.eval() * right.eval();
                    }
                };
            }
            if(lexer.token == '/') {
                lexer.next();
                node = new MathNode(node, term(lexer)) {
                    public int eval() {
                        return left.eval() / right.eval();
                    }
                };
            }
        } while("*/".indexOf(lexer.token) != -1);

        return node;
    }

    private Node factor(Lexer lexer) {
        if(lexer.token == '(') {
            lexer.next();
            Node node = expr(lexer);
            if (lexer.token != ')') {
                throw new IllegalArgumentException("Missing )");
            }
            lexer.next();
            return node;
        } else if(lexer.token == '[') {
            lexer.next();
            Node node = new MemNode(expr(lexer));
            if(lexer.token != ']') {
                throw new IllegalArgumentException("Missing ]");
            }
            lexer.next();
            return node;
        } else if("dlx".indexOf(lexer.token) != -1) {
            int num = lexer.getNumber();
            lexer.next();
            return new IntNode(num);
        } else if(lexer.token == '-') {
            lexer.next();
            return new NegateNode(factor(lexer));
        } else if(lexer.token == 's') {
            String symbol = lexer.getSymbol();
            lexer.next();
            if("a b c x y z i j ex pc sp".contains(symbol.toLowerCase())) {
                return new RegisterNode(symbol.toLowerCase());
            }
            String val = constants.get(symbol);
            if(val == null) {
                throw new IllegalArgumentException("Unknown #define or label: " + symbol);
            }
            return expr(new Lexer(constants.get(symbol)));
        }
        throw new IllegalArgumentException("Cannot parse");
    }

    public int eval() {
        return root.eval();
    }

    private class Node {
        public int eval() {
            return 0;
        }
    }

    private class MemNode extends Node {
        private final Node addr;

        public MemNode(Node addr) {
            this.addr = addr;
        }

        public int eval() {
            return dcpu.ram[addr.eval()];
        }
    }

    private class RegisterNode extends Node {
        private final String reg;

        public RegisterNode(String reg) {
            this.reg = reg;
        }

        public int eval() {
            if(reg.length() == 1) {
                int i = "abcxyzij".indexOf(reg);
                return dcpu.registers[i];
            }
            if(reg.equals("sp")) {
                return dcpu.sp;
            }
            if(reg.equals("pc")) {
                return dcpu.pc;
            }
            if(reg.equals("ex")) {
                return dcpu.ex;
            }
            throw new IllegalArgumentException();
        }
    }

    private class IntNode extends Node {
        int value;
        IntNode(int value) {
            this.value = value;
        }
        public int eval() {
            return value;
        }
    }

    private class NegateNode extends Node {
        Node node;
        NegateNode(Node node) {
            this.node = node;
        }
        public int eval() {
            return -node.eval();
        }
    }

    private class MathNode extends Node {
        Node left, right;
        MathNode(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        public int eval() {
            return 0;
        }
    }

    private class Lexer {
        private final String text;
        private int pos = 0;

        public char token;
        private int tokenStart;

        Lexer(String text) {
            this.text = text;
            next();
        }

        private void skipWhite() {
            while(pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                advance();
            }
        }

        String getSymbol() {
            return text.substring(tokenStart, pos);
        }

        int getNumber() {
            if(token == 'd') {
                return Integer.parseInt(getSymbol());
            } else if(token == 'x') {
                return Integer.parseInt(text.substring(tokenStart + 2, pos), 16);
            } else if(token == 'l') {
                return (int) getSymbol().charAt(1) & 0xffff;
            }
            return 0;
        }

        void next() {
            skipWhite();

            if(pos == text.length()) {
                token = 'e';
                return;
            }

            tokenStart = pos;

            char c = text.charAt(pos);
            if(Character.isDigit(c)) {
                eat_number();
            } else if(c == '"' || c == '\'') {
                eat_literal();
            } else if(Character.isLetter(c) || c == '_') {
                eat_symbol();
            } else if("+-*/()[]".indexOf(c) != -1) {
                advance();
                token = c;
            }
        }

        private void eat_number() {
            if(text.charAt(pos) == '0') {
                advance();
                if(pos < text.length() && text.charAt(pos) == 'x') {
                    advance();
                    while (pos < text.length() && isHex(text.charAt(pos))) {
                        advance();
                    }
                    token = 'x';
                    return;
                }
            }

            while(pos < text.length() && Character.isDigit(text.charAt(pos))) {
                advance();
            }
            token = 'd';
        }

        private boolean isHex(char c) {
            return "0123456789abcdefABCDEF".indexOf(c) != -1;
        }

        private void eat_literal() {
            char startChar = text.charAt(pos);
            while(pos < text.length() && text.charAt(pos) != startChar) {
                advance();
            }
            token = pos == text.length() ? 'r': 'l';
        }

        private void eat_symbol() {
            while(pos < text.length() && isSymbol(text.charAt(pos))) {
                advance();
            }
            token = 's';
        }

        private boolean isSymbol(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        private void advance() {
            pos++;
        }
    }
}
