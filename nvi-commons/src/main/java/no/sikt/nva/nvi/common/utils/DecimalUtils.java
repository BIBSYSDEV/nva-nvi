package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.nonNull;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalUtils {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private DecimalUtils() {
    }

    public static BigDecimal setScaleAndRoundingMode(BigDecimal bigDecimal) {
        return nonNull(bigDecimal) ? bigDecimal.setScale(SCALE, ROUNDING_MODE) : null;
    }
}
