package edu.osu.bugsworld.server;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Diagnostic;
import java.util.List;

/** Request and response bodies of the HTTP API (serialized by Jackson). */
final class Api {

    private Api() {
    }

    record CompileRequest(String source) {
    }

    record DiagnosticJson(String severity, String code, String message, String hint, int line,
            int column, int offset, int length) {

        static DiagnosticJson of(Diagnostic d) {
            return new DiagnosticJson(d.severity().name().toLowerCase(), d.code(), d.message(),
                    d.hint(), d.span().line(), d.span().column(), d.span().offset(),
                    d.span().length());
        }
    }

    /**
     * @param ok          true if there are no errors
     * @param programName the name after PROGRAM, if it parsed
     * @param code        object code, or null on errors
     * @param formatted   the program in standard format, or null on errors
     */
    record CompileResponse(boolean ok, String programName, List<DiagnosticJson> diagnostics,
            int[] code, String formatted) {

        static CompileResponse of(CompileResult r) {
            return new CompileResponse(r.succeeded(),
                    r.program() == null ? null : r.program().name(),
                    r.diagnostics().stream().map(DiagnosticJson::of).toList(),
                    r.succeeded() ? r.code().toArray() : null, r.formatted());
        }
    }

    record SubmissionRequest(String student, String source) {
    }

    /** {@code opponent}: an example bug's name, or "none"; default "hunter". */
    record PracticeRequest(String source, String opponent, Long seed) {
    }

    record SubmissionResponse(long id, String student, String programName,
            List<DiagnosticJson> warnings) {
    }

    /** All fields optional; missing ones keep the jar's defaults. */
    record GameSettings(Integer rows, Integer columns, Integer bugsPerSpecies, Long seed,
            Long maxTicks, Boolean endWhenOneSpeciesLeft, Boolean infectedBugsActSameTick,
            Integer jumpBudget, Integer ticksPerSecond) {
    }

    record AddSpeciesRequest(Long submissionId, String name) {
    }

    record SpeedRequest(Integer ticksPerSecond) {
    }

    record ErrorResponse(String error) {
    }
}
