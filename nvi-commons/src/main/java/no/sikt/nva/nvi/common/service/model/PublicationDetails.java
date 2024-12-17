package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;

@JsonSerialize
public record PublicationDetails(URI publicationId, URI publicationBucketUri, String type,
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
                                                 .map(DbCreatorType::toNviCreatorType)
                                                 .toList(),
                                      new PublicationChannel(dbCandidate.channelType(),
                                                             dbCandidate.channelId(),
                                                             dbCandidate.level()
                                                                        .getValue()));
    }

    public List<URI> getNviCreatorAffiliations() {
        return creators.stream()
                       .map(NviCreatorType::affiliations)
                       .flatMap(List::stream)
                       .toList();
    }

    public List<VerifiedNviCreator> getVerifiedCreators() {
        return creators.stream()
                       .filter(VerifiedNviCreator.class::isInstance)
                       .map(VerifiedNviCreator.class::cast)
                       .toList();
    }

    public Set<URI> getNviCreatorIds() {
        return creators.stream()
                       .filter(VerifiedNviCreator.class::isInstance)
                       .map(VerifiedNviCreator.class::cast)
                       .map(VerifiedNviCreator::id)
                       .collect(Collectors.toSet());
    }


    // FIXME: This is a duplicate, extract and remove others
    public record PublicationDate(String year, String month, String day) {

        public static PublicationDate from(DbPublicationDate dbPublicationDate) {
            return new PublicationDate(dbPublicationDate.year(), dbPublicationDate.month(), dbPublicationDate.day());
        }
    }
}
