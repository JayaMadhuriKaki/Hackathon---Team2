package com.hackathon.backend.policy;

import java.util.List;

/** Everything the engine needs for one submission. Lines are in creation order. */
public record PolicyInput(String claimId, String claimNumber, String claimantId, List<LineInput> lines,
		List<EarlierLine> earlierLines, PolicyConfigSnapshot config) {
}
