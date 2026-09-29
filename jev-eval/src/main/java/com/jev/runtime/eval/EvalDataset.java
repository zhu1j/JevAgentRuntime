package com.jev.runtime.eval;

import java.util.List;
import java.util.function.Function;

/**
 * A named golden dataset with a runner that produces candidate answers.
 */
public interface EvalDataset {

    String name();

    List<EvalCase> cases();

    /** The agent-under-test: turn a goal into an answer. */
    Function<String, String> agent();
}
