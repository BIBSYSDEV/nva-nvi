package no.sikt.nva.nvi.events.evaluator.calculator;

import static no.sikt.nva.nvi.common.model.ChannelType.JOURNAL;
import static no.sikt.nva.nvi.common.model.ChannelType.PUBLISHER;
import static no.sikt.nva.nvi.common.model.ChannelType.SERIES;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_COMMENTARY;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.common.model.ScientificValue.LEVEL_ONE;
import static no.sikt.nva.nvi.common.model.ScientificValue.LEVEL_TWO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.ScientificValue;

public final class PointCalculationConstants {

  public static final int SCALE = 10;
  public static final int RESULT_SCALE = 4;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  public static final MathContext MATH_CONTEXT = new MathContext(SCALE, ROUNDING_MODE);
  public static final BigDecimal INTERNATIONAL_COLLABORATION_FACTOR =
      new BigDecimal("1.3").setScale(1, ROUNDING_MODE);
  public static final BigDecimal NOT_INTERNATIONAL_COLLABORATION_FACTOR =
      BigDecimal.ONE.setScale(1, ROUNDING_MODE);
  public static final Map<InstanceType, Map<ChannelType, Map<ScientificValue, BigDecimal>>>
      INSTANCE_TYPE_AND_LEVEL_POINT_MAP =
          Map.of(
              ACADEMIC_MONOGRAPH,
                  Map.of(
                      PUBLISHER,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(5),
                              LEVEL_TWO, BigDecimal.valueOf(8)),
                      SERIES,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(5),
                              LEVEL_TWO, BigDecimal.valueOf(8))),
              ACADEMIC_COMMENTARY,
                  Map.of(
                      PUBLISHER,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(5),
                              LEVEL_TWO, BigDecimal.valueOf(8)),
                      SERIES,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(5),
                              LEVEL_TWO, BigDecimal.valueOf(8))),
              ACADEMIC_CHAPTER,
                  Map.of(
                      PUBLISHER,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(0.7),
                              LEVEL_TWO, BigDecimal.valueOf(1)),
                      SERIES,
                          Map.of(
                              LEVEL_ONE, BigDecimal.valueOf(1),
                              LEVEL_TWO, BigDecimal.valueOf(3))),
              ACADEMIC_ARTICLE,
                  Map.of(
                      JOURNAL,
                      Map.of(
                          LEVEL_ONE, BigDecimal.valueOf(1),
                          LEVEL_TWO, BigDecimal.valueOf(3))),
              ACADEMIC_LITERATURE_REVIEW,
                  Map.of(
                      JOURNAL,
                      Map.of(
                          LEVEL_ONE, BigDecimal.valueOf(1),
                          LEVEL_TWO, BigDecimal.valueOf(3))));

  private PointCalculationConstants() {}
}
