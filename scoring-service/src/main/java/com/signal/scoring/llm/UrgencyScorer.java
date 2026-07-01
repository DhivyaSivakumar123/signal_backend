package com.signal.scoring.llm;

public interface UrgencyScorer {
    UrgencyResult score(ScoringContext context);
}
