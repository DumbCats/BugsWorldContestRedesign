package edu.osu.bugsworld.engine.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generates random BL programs (mostly valid) and random corruptions of them. */
final class RandomPrograms {

    private static final List<String> NAME_POOL = List.of(
            "a", "b", "go", "turn-around", "x1", "Move", "end", "a-", "Hunt-2", "step", "IFF", "skipper");
    private static final List<String> SEPARATORS = List.of(" ", " ", " ", "\n", "\t", "  ", "\r\n", "\n\n");
    private static final List<String> JUNK = List.of(
            "go_now", "#", "1x", "-a", "next-is-food", "Move", "end", "if", "True", "x.y", "//", "and");

    private final Random random;
    private final List<String> out = new ArrayList<>();

    RandomPrograms(long seed) {
        this.random = new Random(seed);
    }

    /** A random, usually valid, program. */
    String program() {
        out.clear();
        List<String> names = new ArrayList<>(NAME_POOL);
        Collections.shuffle(names, random);
        // Instruction k may call only instructions after it in callOrder, so there
        // is no recursion; definition order is shuffled to get forward references.
        List<String> callOrder = names.subList(0, random.nextInt(5));
        List<Integer> definitionOrder = new ArrayList<>();
        for (int i = 0; i < callOrder.size(); i++) {
            definitionOrder.add(i);
        }
        Collections.shuffle(definitionOrder, random);

        emit("PROGRAM", "P", "IS");
        for (int k : definitionOrder) {
            emit("INSTRUCTION", callOrder.get(k), "IS");
            block(0, callOrder.subList(k + 1, callOrder.size()));
            emit("END", callOrder.get(k));
        }
        emit("BEGIN");
        block(0, callOrder.subList(0, Math.min(2, callOrder.size())));
        emit("END", "P");
        return join(out);
    }

    /** {@code source} with one to three random token-level edits. */
    String mutate(String source) {
        List<String> tokens = new ArrayList<>(List.of(source.trim().split("\\s+")));
        List<String> vocabulary = new ArrayList<>(Lexer.KEYWORDS);
        vocabulary.addAll(Condition.TEXTS);
        vocabulary.addAll(Primitive.NAMES);
        vocabulary.addAll(NAME_POOL);
        vocabulary.addAll(JUNK);
        int edits = 1 + random.nextInt(3);
        for (int e = 0; e < edits && !tokens.isEmpty(); e++) {
            int i = random.nextInt(tokens.size());
            String word = vocabulary.get(random.nextInt(vocabulary.size()));
            switch (random.nextInt(5)) {
                case 0 -> tokens.remove(i);
                case 1 -> tokens.add(i, tokens.get(i));
                case 2 -> tokens.set(i, word);
                case 3 -> tokens.add(i, word);
                default -> {
                    if (i + 1 < tokens.size()) {
                        Collections.swap(tokens, i, i + 1);
                    }
                }
            }
        }
        return join(tokens);
    }

    private void block(int depth, List<String> callable) {
        int n = random.nextInt(4);
        for (int i = 0; i < n; i++) {
            int kind = random.nextInt(100);
            if (depth >= 3 || kind < 40) {
                emit(Primitive.NAMES.get(random.nextInt(Primitive.NAMES.size())));
            } else if (kind < 60 && !callable.isEmpty()) {
                emit(callable.get(random.nextInt(callable.size())));
            } else if (kind < 75) {
                emit("IF", condition(), "THEN");
                block(depth + 1, callable);
                emit("END", "IF");
            } else if (kind < 85) {
                emit("IF", condition(), "THEN");
                block(depth + 1, callable);
                emit("ELSE");
                block(depth + 1, callable);
                emit("END", "IF");
            } else {
                emit("WHILE", condition(), "DO");
                block(depth + 1, callable);
                emit("END", "WHILE");
            }
        }
    }

    private String condition() {
        return Condition.TEXTS.get(random.nextInt(Condition.TEXTS.size()));
    }

    private void emit(String... tokens) {
        out.addAll(List.of(tokens));
    }

    private String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            sb.append(t).append(SEPARATORS.get(random.nextInt(SEPARATORS.size())));
        }
        return sb.toString();
    }
}
