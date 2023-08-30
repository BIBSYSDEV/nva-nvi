package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.NviCandidateRepository.defaultNviCandidateRepository;
import static no.sikt.nva.nvi.common.model.events.CandidateStatus.CANDIDATE;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;

import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NviService {

    public static final Logger LOGGER = LoggerFactory.getLogger(NviService.class);
    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    private final NviCandidateRepository nviCandidateRepository;

    public NviService(NviCandidateRepository nviCandidateRepository) {
        this.nviCandidateRepository = nviCandidateRepository;
    }

    //TODO: Remove JacocoGenerated when other if/else cases are implemented
    @JacocoGenerated
    public Optional<CandidateWithIdentifier> upsertCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        if (isNotExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            return Optional.of(createCandidate(evaluatedCandidate));
        }
        return Optional.empty();
    }

    //TODO: Implement persistence
    public NviPeriod createPeriod(NviPeriod period) throws BadRequestException {
        validatePeriod(period);
        return period;
    }

    //TODO: Implement persistence, remember validation rules for reportingDate
    public NviPeriod updatePeriod(NviPeriod period) throws NotFoundException, ConflictException, BadRequestException {
        validatePeriod(period);
        return period;
    }

    //TODO: Implement persistence
    public NviPeriod getPeriod(String publishingYear) {
        return new NviPeriod.Builder().withPublishingYear(publishingYear).build();
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasInvalidLength(NviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(
                       verifiedCreatorDto -> new Creator(verifiedCreatorDto.id(), verifiedCreatorDto.nviInstitutions()))
                   .toList();
    }

    private static List<URI> extractInstitutionIds(CandidateDetails candidateDetails) {
        return candidateDetails.verifiedCreators()
                   .stream()
                   .flatMap(creator -> creator.nviInstitutions().stream())
                   .distinct()
                   .toList();
    }

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CANDIDATE.equals(evaluatedCandidate.status());
    }

    private static PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<ApprovalStatus> generatePendingApprovalStatuses(List<URI> institutionUri) {
        return institutionUri.stream()
                   .map(uri -> new ApprovalStatus.Builder().withStatus(Status.PENDING).withInstitutionId(uri).build())
                   .toList();
    }

    @JacocoGenerated
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean exists(UUID uuid) {
        return nviCandidateRepository.findById(uuid).isPresent();
    }

    private boolean existsByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private CandidateWithIdentifier createCandidate(CandidateEvaluatedMessage candidateEvaluatedMessage) {
        var pendingCandidate = toPendingCandidate(candidateEvaluatedMessage);
        return nviCandidateRepository.save(pendingCandidate);
    }

    @JacocoGenerated //TODO: Remove when its actually used
    public Optional<CandidateWithIdentifier> findById(UUID uuid) {
        return nviCandidateRepository.findById(uuid);
    }

    @JacocoGenerated //TODO: Remove when its actually used
    public Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId);
    }

    private static Candidate toPendingCandidate(CandidateEvaluatedMessage candidateDetails) {
        return new Candidate.Builder()
                   .withPublicationId(candidateDetails.candidateDetails().publicationId())
                   .withIsApplicable(true)
                   .withCreators(mapToVerifiedCreators(candidateDetails.candidateDetails().verifiedCreators()))
                   .withLevel(Level.parse(candidateDetails.candidateDetails().level()))
                   .withInstanceType(candidateDetails.candidateDetails().instanceType())
                   .withPublicationDate(mapToPublicationDate(candidateDetails.candidateDetails().publicationDate()))
                   .withApprovalStatuses(generatePendingApprovalStatuses(extractInstitutionIds(candidateDetails.candidateDetails())))
                   .build();
    }

    private void validatePeriod(NviPeriod period) throws BadRequestException {
        if (hasInvalidLength(period)) {
            throw new BadRequestException(INVALID_LENGTH_MESSAGE);
        }
        if (!isInteger(period.publishingYear())) {
            throw new BadRequestException(PERIOD_NOT_NUMERIC_MESSAGE);
        }
    }

    //TODO: Remove JacocoGenerated when case for existing candidate is implemented
    @JacocoGenerated
    private boolean isNotExistingCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return !existsByPublicationId(evaluatedCandidate.candidateDetails().publicationId());
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        LOGGER.info("Initiating something NviService");
        return new NviService(defaultNviCandidateRepository());
    }
}
