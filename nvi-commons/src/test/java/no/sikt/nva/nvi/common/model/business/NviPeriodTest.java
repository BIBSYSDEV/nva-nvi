package no.sikt.nva.nvi.common.model.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.Test;

class NviPeriodTest {

    @Test
    public void shouldConvertBackAndForthBetweeenDbModelWithoutLoosingData() {
        var period = TestUtils.randomNviPeriod();
        var db = period.toDynamoDb();
        var periodFromDb = NviPeriod.fromDynamoDb(db);
        assertEquals(period, periodFromDb);
    }

}