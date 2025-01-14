package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

@JsonSerialize
public record PublicationDetails(URI publicationId, URI publicationBucketUri, String type,
                                 PublicationDate publicationDate, List<NviCreatorDto> creators,
                                 PublicationChannel publicationChannel) {

    public static PublicationDetails from(CandidateDao candidateDao) {
        var dbCandidate = candidateDao.candidate();
        return new PublicationDetails(dbCandidate.publicationId(),
                                      dbCandidate.publicationBucketUri(),
                                      dbCandidate.instanceType(),
                                      PublicationDate.from(dbCandidate.publicationDate()),
                                      dbCandidate.creators()
                                                 .stream()
                                                 .map(DbCreatorType::toNviCreator)
                                                 .toList(),
                                      new PublicationChannel(dbCandidate.channelType(),
                                                             dbCandidate.channelId(),
                                                             dbCandidate.level()
                                                                        .getValue()));
    }

    public List<URI> getNviCreatorAffiliations() {
        return creators.stream()
                       .map(NviCreatorDto::affiliations)
                       .flatMap(List::stream)
                       .toList();
    }

    public List<VerifiedNviCreatorDto> getVerifiedCreators() {
        return creators.stream()
                       .filter(VerifiedNviCreatorDto.class::isInstance)
                       .map(VerifiedNviCreatorDto.class::cast)
                       .toList();
    }

    public List<UnverifiedNviCreatorDto> getUnverifiedCreators() {
        return creators.stream()
                       .filter(UnverifiedNviCreatorDto.class::isInstance)
                       .map(UnverifiedNviCreatorDto.class::cast)
                       .toList();
    }

    public Set<URI> getVerifiedNviCreatorIds() {
        return creators.stream()
                       .filter(VerifiedNviCreatorDto.class::isInstance)
                       .map(VerifiedNviCreatorDto.class::cast)
                       .map(VerifiedNviCreatorDto::id)
                       .collect(Collectors.toSet());
    }

    public Set<String> getUnverifiedNviCreatorNames() {
        return creators.stream()
                       .filter(UnverifiedNviCreatorDto.class::isInstance)
                       .map(UnverifiedNviCreatorDto.class::cast)
                       .map(UnverifiedNviCreatorDto::name)
                       .collect(Collectors.toSet());
    }

    public record PublicationDate(String year, String month, String day) {

        public static PublicationDate from(DbPublicationDate dbPublicationDate) {
            return new PublicationDate(dbPublicationDate.year(), dbPublicationDate.month(), dbPublicationDate.day());
        }
    }
}
