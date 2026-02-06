package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.API_HOST;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestUtils.generateUniqueIdAsString;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import nva.commons.core.paths.UriWrapper;

public final class CristinTestUtils {
  public static final LocalDate DATE_CONTROLLED = LocalDate.now();
  public static final String STATUS_CONTROLLED = "J";

  private CristinTestUtils() {}

  public static List<Organization> getTopLevelOrganizations(CristinNviReport cristinNviReport) {
    var actualTopLevelOrganizations =
        cristinNviReport.cristinLocales().stream()
            .map(CristinIdWrapper::from)
            .map(CristinIdWrapper::getInstitutionId)
            .toList();
    return cristinNviReport.scientificResources().stream()
        .map(ScientificResource::getCreators)
        .flatMap(List::stream)
        .map(CristinIdWrapper::from)
        .map(CristinIdWrapper::getTopLevelOrganization)
        .filter(organization -> actualTopLevelOrganizations.contains(organization.id()))
        .toList();
  }

  public static CristinLocale randomCristinLocale(String institutionIdentifier) {
    return CristinLocale.builder()
        .withDateControlled(DATE_CONTROLLED)
        .withControlStatus(STATUS_CONTROLLED)
        .withInstitutionIdentifier(institutionIdentifier)
        .withDepartmentIdentifier("0")
        .withSubDepartmentIdentifier("0")
        .withOwnerCode(randomString())
        .withGroupIdentifier("0")
        .withControlledByUser(
            CristinUser.builder().withIdentifier(generateUniqueIdAsString()).build())
        .build();
  }

  public static URI expectedCreatorId(ScientificPerson person) {
    return UriWrapper.fromHost(API_HOST.getValue())
        .addChild("cristin")
        .addChild("person")
        .addChild(person.getCristinPersonIdentifier())
        .getUri();
  }

  public static ContributorDto toContributorDto(ScientificPerson person) {
    var creatorId = expectedCreatorId(person);
    var cristinId = CristinIdWrapper.from(person);
    var affiliation = cristinId.createLeafNodeOrganization();

    return new ContributorDto(creatorId, null, STATUS_VERIFIED, ROLE_CREATOR, List.of(affiliation));
  }

  public static SampleExpandedPublicationFactory createPublicationFactory(
      CristinNviReport cristinNviReport, TestScenario testScenario) {
    var topLevelOrganizations = getTopLevelOrganizations(cristinNviReport);
    var contributors =
        cristinNviReport.scientificResources().stream()
            .map(ScientificResource::getCreators)
            .flatMap(List::stream)
            .map(CristinTestUtils::toContributorDto)
            .toList();
    var publicationDate =
        nonNull(cristinNviReport.publicationDate())
            ? cristinNviReport.publicationDate()
            : randomPublicationDate();
    return new SampleExpandedPublicationFactory()
        .withContributors(contributors)
        .withTopLevelOrganizations(topLevelOrganizations)
        .withPublicationDate(publicationDate);
  }

  public static VerifiedNviCreatorDto expectedCreator(ScientificPerson person) {
    var creatorId = expectedCreatorId(person);
    var affiliation = organizationIdFromIdentifier(person.getOrganization());
    return new VerifiedNviCreatorDto(creatorId, null, List.of(affiliation));
  }
}
