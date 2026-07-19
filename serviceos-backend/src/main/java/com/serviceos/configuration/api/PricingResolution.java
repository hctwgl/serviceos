package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/** PRICING 试算输出：匹配计费行、币种与 minor 合计。 */
public record PricingResolution(
        String pricingKey,
        UUID assetVersionId,
        String contentDigest,
        String currency,
        long totalAmountMinor,
        List<MatchedLine> matchedLines,
        List<String> explanations
) {
    public PricingResolution {
        matchedLines = List.copyOf(matchedLines);
        explanations = List.copyOf(explanations);
        if (totalAmountMinor < 0) {
            throw new IllegalArgumentException("totalAmountMinor must not be negative");
        }
    }

    public record MatchedLine(
            String lineKey,
            String chargeCode,
            long amountMinor,
            String billableTo
    ) {
    }
}
