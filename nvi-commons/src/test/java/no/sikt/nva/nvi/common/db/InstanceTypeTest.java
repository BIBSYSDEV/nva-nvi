package no.sikt.nva.nvi.common.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class InstanceTypeTest {

    @ParameterizedTest
    @EnumSource(InstanceType.class)
    void shouldConvertEnumValueToEnum(InstanceType instanceType) {
        var value = instanceType.getValue();
        var enumType = InstanceType.parse(value);
        assertThat(enumType, is(equalTo(instanceType)));
    }
}
