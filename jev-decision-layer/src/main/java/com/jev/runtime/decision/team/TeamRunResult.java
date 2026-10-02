package com.jev.runtime.decision.team;

import java.util.List;

/**
 * Outcome of one team run: the per-member step trace, the synthesized answer
 * and the final Jev grading.
 *
 * @param synthesizedAnswer the leader's combined answer
 * @param steps             ordered per-member execution trace
 * @param gradingScore      Jev-as-judge score of the synthesized answer in [0,1]
 */
public record TeamRunResult(String synthesizedAnswer,
                            List<Step> steps,
                            double gradingScore) {

    public record Step(String memberId,
                       String subGoal,
                       String answer,
                       String jevPrimitive,
                       Double jevValue) {
    }
}
