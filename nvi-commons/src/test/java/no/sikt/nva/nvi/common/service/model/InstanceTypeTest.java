package no.sikt.nva.nvi.common.service.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstanceTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"AcademicCommentary", "AcademicMonograph", "AcademicChapter", "AcademicArticle",
        "AcademicLiteratureReview"})
    void shouldParseValidStrings(String value) {
        InstanceType instanceType = InstanceType.parse(value);
        assertEquals(value, instanceType.getValue());
    }

    @Test
    void shouldThrowExceptionForInvalidString() {
        assertThrows(NoSuchElementException.class, () -> InstanceType.parse("InvalidString"));
    }
}