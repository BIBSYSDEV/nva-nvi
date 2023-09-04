package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.model.events.CandidateStatus.CANDIDATE;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    private final NviCandidateRepository nviCandidateRepository;
    private final NviPeriodRepository nviPeriodRepository;

    public NviService(DynamoDbClient dynamoDbClient) {
        this.nviCandidateRepository = new NviCandidateRepository(dynamoDbClient);
        this.nviPeriodRepository = new NviPeriodRepository(dynamoDbClient);
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        return new NviService(defaultDynamoClient());
    }

    //TODO: Remove JacocoGenerated when other if/else cases are implemented
    @JacocoGenerated
    public Optional<CandidateWithIdentifier> upsertCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        if (!isExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            return Optional.of(createCandidate(evaluatedCandidate));
        } else if (isExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            var existing = findByPublicationId(evaluatedCandidate.candidateDetails().publicationId()).orElseThrow();
            //TODO: Reset NVI Candidates here. See https://unit.atlassian.net/browse/NP-45113;
            return Optional.of(updateCandidate(existing.identifier(), evaluatedCandidate));
        }
        return Optional.empty();
    }

    public NviPeriod createPeriod(NviPeriod period) throws BadRequestException {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public NviPeriod updatePeriod(NviPeriod period) throws NotFoundException, ConflictException, BadRequestException {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public NviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return nviPeriodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public CandidateWithIdentifier upsertApproval(ApprovalStatus approvalStatus) {

        return null;
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

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CANDIDATE.equals(evaluatedCandidate.status());
    }

    private static PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<ApprovalStatus> generatePendingApprovalStatuses(Set<URI> institutionUris) {
        return institutionUris.stream()
                   .map(uri -> ApprovalStatus.builder()
                                          .withStatus(Status.PENDING)
                                          .withInstitutionId(uri)
                                          .build())
                   .toList();
    }

    @JacocoGenerated
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean exists(UUID uuid) {
        return nviCandidateRepository.findById(uuid).isPresent();
    }

    public Optional<CandidateWithIdentifier> findById(UUID uuid) {
        return nviCandidateRepository.findById(uuid);
    }

    public Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId);
    }

    private static Candidate toPendingCandidate(CandidateEvaluatedMessage candidateEvaluatedMessage,
                                                Map<URI, BigDecimal> institutionPoints) {
        return Candidate.builder()
                   .withPublicationBucketUri(candidateEvaluatedMessage.publicationBucketUri())
                   .withPublicationId(candidateEvaluatedMessage.candidateDetails().publicationId())
                   .withIsApplicable(true)
                   .withCreators(mapToVerifiedCreators(candidateEvaluatedMessage.candidateDetails().verifiedCreators()))
                   .withLevel(Level.parse(candidateEvaluatedMessage.candidateDetails().level()))
                   .withInstanceType(candidateEvaluatedMessage.candidateDetails().instanceType())
                   .withPublicationDate(mapToPublicationDate(candidateEvaluatedMessage.candidateDetails()
                                                                 .publicationDate()))
                   .withPoints(institutionPoints)
                   .withApprovalStatuses(generatePendingApprovalStatuses(institutionPoints.keySet()))
                   .build();
    }

    private boolean existsByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private CandidateWithIdentifier createCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        var pendingCandidate = toPendingCandidate(evaluatedCandidate,
                                                  evaluatedCandidate.institutionPoints());
        return nviCandidateRepository.create(pendingCandidate);
    }

    private CandidateWithIdentifier updateCandidate(UUID identifier, CandidateEvaluatedMessage evaluatedCandidate) {
        var pendingCandidate = toPendingCandidate(evaluatedCandidate,
                                                  evaluatedCandidate.institutionPoints());
        return nviCandidateRepository.update(identifier, pendingCandidate);
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
    private boolean isExistingCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return existsByPublicationId(evaluatedCandidate.candidateDetails().publicationId());
    }
}
