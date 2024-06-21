package no.sikt.nva.nvi.common.validator;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.UserRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;
import no.sikt.nva.nvi.common.client.model.User;
import no.sikt.nva.nvi.common.client.model.User.ViewingScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViewingScopeValidatorTest {

    private static final String SOME_USERNAME = "username";
    private OrganizationRetriever organizationRetriever;
    private UserRetriever userRetriever;
    private ViewingScopeValidator viewingScopeValidator;

    @BeforeEach
    void setUp() {
        organizationRetriever = mock(OrganizationRetriever.class);
        userRetriever = mock(UserRetriever.class);
        viewingScopeValidator = new ViewingScopeValidator(userRetriever, organizationRetriever);
    }

    @Test
    void shouldReturnFalseIfUserIsNotAllowedToAccessRequestedOrgs() {
        var allowedOrg = randomUri();
        when(userRetriever.fetchUser(SOME_USERNAME)).thenReturn(userWithViewingScope(allowedOrg));
        when(organizationRetriever.fetchOrganization(allowedOrg)).thenReturn(createOrg(allowedOrg));
        var someOtherOrg = randomUri();
        assertFalse(viewingScopeValidator.userIsAllowedToAccess(SOME_USERNAME, List.of(someOtherOrg)));
    }

    @Test
    void shouldReturnTrueIfUserIsAllowedToAccessRequestedOrgs() {
        var allowedOrg = randomUri();
        when(userRetriever.fetchUser(SOME_USERNAME)).thenReturn(userWithViewingScope(allowedOrg));
        when(organizationRetriever.fetchOrganization(allowedOrg)).thenReturn(createOrg(allowedOrg));
        assertTrue(viewingScopeValidator.userIsAllowedToAccess(SOME_USERNAME, List.of(allowedOrg)));
    }

    @Test
    void shouldReturnTrueIfUserIsAllowedToAccessRequestedOrgsSubOrg() {
        var org = URI.create("https://www.example.com/org");
        var subOrg = URI.create("https://www.example.com/subOrg");
        when(userRetriever.fetchUser(SOME_USERNAME)).thenReturn(userWithViewingScope(org));
        when(organizationRetriever.fetchOrganization(org)).thenReturn(createOrgWithSubOrg(org, subOrg));
        assertTrue(viewingScopeValidator.userIsAllowedToAccess(SOME_USERNAME, List.of(subOrg)));
    }

    private static Organization createOrg(URI orgId) {
        return defaultBuilder(orgId).build();
    }

    private static Organization createOrgWithSubOrg(URI orgId, URI subOrgId) {
        return defaultBuilder(orgId).withHasPart(List.of(createOrg(subOrgId))).build();
    }

    private static Builder defaultBuilder(URI orgId) {
        return Organization.builder()
                   .withId(orgId)
                   .withContext("https://bibsysdev.github.io/src/organization-context.json");
    }

    private static User userWithViewingScope(URI allowedOrg) {
        return new User("userName", new ViewingScope(List.of(allowedOrg.toString())));
    }
}