package no.sikt.nva.nvi.common.db;

import static java.util.Objects.isNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.mapToDbCreators;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.mapToDbPublicationDate;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInCurrentYear;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbOrganization;
import no.sikt.nva.nvi.common.db.model.DbPageCount;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.PageCount;

public class DbPublicationDetailsFixtures {
  public static DbPublicationDetails.Builder randomPublicationBuilder(URI organizationId) {
    var creatorId = randomUri();
    var publicationIdentifier = randomUUID();
    var publicationId = generatePublicationId(publicationIdentifier);
    var topLevelNviOrganization = DbOrganization.builder().id(organizationId).build();
    return DbPublicationDetails.builder()
        .id(publicationId)
        .identifier(publicationIdentifier.toString())
        .publicationBucketUri(randomUri())
        .publicationDate(randomPublicationDateInCurrentYear().toDbPublicationDate())
        .modifiedDate(Instant.now())
        .topLevelNviOrganizations(List.of(topLevelNviOrganization))
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(organizationId))
                    .build()));
  }

  public static DbPublicationDetails.Builder getExpectedPublicationDetailsBuilder(
      UpsertNviCandidateRequest request) {
    var dtoPublicationDetails = request.publicationDetails();
    var dbCreators = mapToDbCreators(request.verifiedCreators(), request.unverifiedCreators());
    var dbOrganizations =
        request.topLevelNviOrganizations().stream().map(Organization::toDbOrganization).toList();
    return DbPublicationDetails.builder()
        .id(request.publicationId())
        .identifier(dtoPublicationDetails.identifier())
        .publicationBucketUri(request.publicationBucketUri())
        .title(dtoPublicationDetails.title())
        .status(dtoPublicationDetails.status())
        .publicationDate(mapToDbPublicationDate(dtoPublicationDetails.publicationDate()))
        .modifiedDate(dtoPublicationDetails.modifiedDate())
        .creators(dbCreators)
        .contributorCount(dtoPublicationDetails.creatorCount())
        .abstractText(dtoPublicationDetails.abstractText())
        .pages(getDbPageCountFromRequest(request))
        .topLevelNviOrganizations(dbOrganizations);
  }

  public static DbPublicationDetails getExpectedPublicationDetails(
      UpsertNviCandidateRequest request) {
    return getExpectedPublicationDetailsBuilder(request).build();
  }

  private static DbPageCount getDbPageCountFromRequest(UpsertNviCandidateRequest request) {
    var publicationDetails = request.publicationDetails();
    if (isNull(publicationDetails) || isNull(publicationDetails.pageCount())) {
      return null;
    }
    return PageCount.from(publicationDetails.pageCount()).toDbPageCount();
  }
}
