package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.model.Organization.Builder;
import org.junit.jupiter.api.Test;

class OrganizationTest {

    @Test
    void shouldReturnDeepestPartOfAsTopLevelOrg() {
        var topLevelOrg = randomOrganization().build();
        var organization = randomOrganizationWithPartOf(topLevelOrg);
        var actualTopLevelOrg = organization.getTopLevelOrg();
        assertEquals(topLevelOrg, actualTopLevelOrg);
    }

    @Test
    void shouldReturnSelfAsTopLevelOrgWhenNoPartOf() {
        var organization = randomOrganization().build();
        var actualTopLevelOrg = organization.getTopLevelOrg();
        assertEquals(organization, actualTopLevelOrg);
    }

    @Test
    void shouldSerializeAndDeserializeWithoutLossOfData() throws Exception {
        var organization = randomOrganizationWithPartOf(randomOrganization().build());
        var json = organization.asJsonString();
        var actualOrganization = Organization.from(json);
        assertEquals(organization, actualOrganization);
    }

    private static Builder randomOrganization() {
        return Organization.builder()
                   .withId(randomUri())
                   .withContext(randomString())
                   .withLabels(Map.of(randomString(), randomString()))
                   .withType(randomString());
    }

    private static Organization randomOrganizationWithPartOf(Organization topLevelOrg) {
        return randomOrganization().withPartOf(List.of(topLevelOrg)).build();
    }
}