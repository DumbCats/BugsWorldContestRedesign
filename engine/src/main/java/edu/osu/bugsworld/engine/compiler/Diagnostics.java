package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.compiler.Diagnostic.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collects diagnostics, keeping at most one error per source position. */
final class Diagnostics {

    /** Stop collecting after this many, so pathological input stays fast. */
    static final int MAX = 100;

    private final List<Diagnostic> list = new ArrayList<>();
    private final Set<Integer> errorOffsets = new HashSet<>();

    void error(String code, Span span, String message, String hint) {
        // During error recovery several rules may complain about the same
        // token; the first complaint is the most specific one.
        if (errorOffsets.add(span.offset())) {
            add(new Diagnostic(Severity.ERROR, code, message, span, hint));
        }
    }

    void error(String code, Span span, String message) {
        error(code, span, message, null);
    }

    void warning(String code, Span span, String message, String hint) {
        add(new Diagnostic(Severity.WARNING, code, message, span, hint));
    }

    void add(Diagnostic d) {
        if (list.size() < MAX) {
            list.add(d);
        }
    }

    boolean hasErrors() {
        return list.stream().anyMatch(Diagnostic::isError);
    }

    /** All diagnostics in source order. */
    List<Diagnostic> sorted() {
        List<Diagnostic> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt((Diagnostic d) -> d.span().offset())
                .thenComparing(d -> d.severity()));
        return List.copyOf(sorted);
    }
}
