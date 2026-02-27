package no.sikt.nva.nvi.index.report.model;

import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import java.math.BigDecimal;

public record CandidateTotal(int candidateCount, BigDecimal totalPoints) {

  public static final CandidateTotal ZERO = new CandidateTotal(0, BigDecimal.ZERO);

  public CandidateTotal {
    validateCandidateCount(candidateCount);
    totalPoints = validateAndAdjustPoints(totalPoints);
  }

  public CandidateTotal add(CandidateTotal other) {
    return new CandidateTotal(
        candidateCount + other.candidateCount, totalPoints.add(other.totalPoints));
  }

  private static BigDecimal validateAndAdjustPoints(BigDecimal points) {
    if (points.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("totalPoints cannot be negative");
    }
    return adjustScaleAndRoundingMode(points);
  }

  private static void validateCandidateCount(int candidateCount) {
    if (candidateCount < 0) {
      throw new IllegalArgumentException("candidateCount cannot be negative");
    }
  }
}
