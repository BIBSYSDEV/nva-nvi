package no.sikt.nva.nvi.common.service.model;

import static nva.commons.core.StringUtils.isBlank;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;

@JsonSerialize
public record PublicationDetails(URI publicationId,
                                 URI publicationBucketUri,
                                 String type,
                                 PublicationDate publicationDate, List<NviCreatorType> creators,
                                 PublicationChannel publicationChannel) {

    public static PublicationDetails from(CandidateDao candidateDao) {
        var dbCandidate = candidateDao.candidate();
        return new PublicationDetails(dbCandidate.publicationId(),
                                      dbCandidate.publicationBucketUri(),
                                      dbCandidate.instanceType(),
                                      PublicationDate.from(dbCandidate.publicationDate()),
                                      dbCandidate.creators()
                                          .stream()
                                                 .map(PublicationDetails::creatorFromDao)
                                          .toList(),
                                      new PublicationChannel(dbCandidate.channelType(),
                                                             dbCandidate.channelId(),
                                                             dbCandidate.level().getValue()));
    }

    public static NviCreatorType creatorFromDao(DbCreatorType dbCreator) {
        if (dbCreator instanceof DbCreator(URI creatorId, List<URI> affiliations)) {
            return new Creator(creatorId, affiliations);
        } else {
            var creator = (DbUnverifiedCreator) dbCreator;
            return new UnverifiedNviCreator(creator.creatorName(), creator.affiliations());
        }
    }

    public static DbCreatorType creatorToDao(NviCreatorType creator) {
        if (creator instanceof Creator creatorType) {
            return creatorType.toDbCreator();
        } else {
            return ((UnverifiedNviCreator) creator).toDbUnverifiedCreator();
        }
    }

    public List<URI> getNviCreatorAffiliations() {
        return creators.stream()
                       .map(NviCreatorType::affiliations)
                   .flatMap(List::stream)
                   .toList();
    }

    public List<Creator> getVerifiedCreators() {
        return creators.stream()
                       .filter(Creator.class::isInstance)
                       .map(Creator.class::cast)
                   .toList();
    }


    public record PublicationDate(String year, String month, String day) {

        public static PublicationDate from(DbPublicationDate dbPublicationDate) {
            return new PublicationDate(dbPublicationDate.year(),
                                       dbPublicationDate.month(),
                                       dbPublicationDate.day());
        }
    }

    public Set<URI> getNviCreatorIds() {
        return creators.stream()
                       .filter(Creator.class::isInstance)
                       .map(Creator.class::cast)
                   .map(Creator::id)
                   .collect(Collectors.toSet());
    }

    public sealed interface NviCreatorType permits Creator, UnverifiedNviCreator {

        List<URI> affiliations();
    }

    public record Creator(URI id, List<URI> affiliations) implements NviCreatorType {

        public static Builder builder() {
            return new Builder();
        }

        public DbCreator toDbCreator() {
            return new DbCreator(id, affiliations);
        }

        public static final class Builder {

            private URI id;
            private List<URI> affiliations = Collections.emptyList();

            private Builder() {
            }

            public Builder withId(URI id) {
                this.id = id;
                return this;
            }

            public Builder withAffiliations(List<URI> affiliations) {
                this.affiliations = affiliations;
                return this;
            }

            public Creator build() {
                if (isBlank(id.toString())) {
                    throw new IllegalStateException("ID cannot be null or blank");
                }
                return new Creator(id, affiliations);
            }
        }
    }
}
