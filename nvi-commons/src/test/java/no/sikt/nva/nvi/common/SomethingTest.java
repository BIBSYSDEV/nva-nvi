package no.sikt.nva.nvi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;

public class SomethingTest {

    @Test
    void testSomething() {
        var test = new Something();
        assertThat(test, notNullValue());
    }
}
