package no.sikt.nva.nvi.common.db;

import static java.util.Objects.isNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.mapToDbCreators;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.mapToDbPublicationDate;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.PageCount;

public class DbPublicationDetailsFixtures {
  public static DbPublicationDetails.Builder randomPublicationBuilder(URI organizationId) {
    var creatorId = randomUri();
    var publicationIdentifier = randomUUID();
    var publicationId = generatePublicationId(publicationIdentifier);
    return DbPublicationDetails.builder()
        .id(publicationId)
        .identifier(publicationIdentifier.toString())
        .publicationBucketUri(randomUri())
        .publicationDate(publicationDate(String.valueOf(CURRENT_YEAR)))
        .modifiedDate(Instant.now())
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(organizationId))
                    .build()));
  }

  public static DbPublicationDetails getExpectedPublicationDetails(
      UpsertNviCandidateRequest request) {
    var dtoPublicationDetails = request.publicationDetails();
    var dbCreators = mapToDbCreators(request.verifiedCreators(), request.unverifiedCreators());
    var dbOrganizations =
        dtoPublicationDetails.topLevelOrganizations().stream()
            .map(Organization::toDbOrganization)
            .toList();
    return DbPublicationDetails.builder()
        .id(request.publicationId())
        .identifier(dtoPublicationDetails.identifier())
        .publicationBucketUri(request.publicationBucketUri())
        .title(dtoPublicationDetails.title())
        .status(dtoPublicationDetails.status())
        .publicationDate(mapToDbPublicationDate(dtoPublicationDetails.publicationDate()))
        .modifiedDate(dtoPublicationDetails.modifiedDate())
        .creators(dbCreators)
        .contributorCount(dtoPublicationDetails.contributors().size())
        .abstractText(dtoPublicationDetails.abstractText())
        .pages(getDbPagesFromRequest(request))
        .topLevelOrganizations(dbOrganizations)
        .build();
  }

  private static DbPublicationDate publicationDate(String year) {
    return new DbPublicationDate(year, null, null);
  }

  private static DbPages getDbPagesFromRequest(UpsertNviCandidateRequest request) {
    var publicationDetails = request.publicationDetails();
    if (isNull(publicationDetails) || isNull(publicationDetails.pageCount())) {
      return null;
    }
    return PageCount.from(publicationDetails.pageCount()).toDbPages();
  }
}
