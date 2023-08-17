package no.sikt.nva.nvi.test;

import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.dao.ApprovalStatus;
import no.sikt.nva.nvi.common.model.dao.PublicationDate;
import no.sikt.nva.nvi.common.model.dao.Status;
import no.sikt.nva.nvi.common.model.dao.VerifiedCreator;
import no.sikt.nva.nvi.common.model.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.dto.VerifiedCreatorDto;
import nva.commons.core.paths.UriWrapper;

public final class TestUtils {

    private static final String BUCKET_HOST = "example.org";
    private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
    private static final String PUBLICATION_API_PATH = "publication";
    private static final String API_HOST = "example.com";

    private TestUtils() {
    }

    public static ApprovalStatus createPendingApprovalStatus(URI institutionUri) {
        return new ApprovalStatus.Builder()
                   .withStatus(Status.PENDING)
                   .withInstitutionId(institutionUri)
                   .build();
    }

    public static PublicationDateDto randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new PublicationDateDto(String.valueOf(randomDate.getYear()),
                                      String.valueOf(randomDate.getMonthValue()),
                                      String.valueOf(randomDate.getDayOfMonth()));
    }

    public static PublicationDate toPublicationDate(PublicationDateDto publicationDate) {
        return new PublicationDate(publicationDate.year(),
                                   publicationDate.month(),
                                   publicationDate.day());
    }

    public static URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
    }

    public static URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION_API_PATH)
                   .addChild(identifier.toString())
                   .getUri();
    }

    public static List<VerifiedCreator> mapToVerifiedCreators(List<VerifiedCreatorDto> creatorDtos) {
        return creatorDtos.stream()
                   .map(creator -> new VerifiedCreator(creator.id(), creator.nviInstitutions()))
                   .toList();
    }

    public static Stream<URI> extractNviInstitutionIds(List<VerifiedCreatorDto> creators) {
        return creators.stream()
                   .flatMap(creatorDto -> creatorDto.nviInstitutions().stream())
                   .distinct();
    }

    private static LocalDate randomLocalDate() {
        var daysBetween = ChronoUnit.DAYS.between(START_DATE, LocalDate.now());
        var randomDays = new Random().nextInt((int) daysBetween);

        return START_DATE.plusDays(randomDays);
    }
}
