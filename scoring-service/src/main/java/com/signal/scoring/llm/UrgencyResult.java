package com.signal.scoring.llm;

import com.signal.common.dto.Urgency;

public record UrgencyResult(Urgency urgency, String reason) {}
