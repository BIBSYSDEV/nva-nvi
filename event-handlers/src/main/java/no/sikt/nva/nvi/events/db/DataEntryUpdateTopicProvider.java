package no.sikt.nva.nvi.events.db;

import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import nva.commons.core.Environment;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateTopicProvider {

    public static final String CANDIDATE_REMOVE_TOPIC = "CANDIDATE_REMOVE_TOPIC";
    public static final String APPROVAL_REMOVE_TOPIC = "APPROVAL_REMOVE_TOPIC";
    public static final String CANDIDATE_INSERT_TOPIC = "CANDIDATE_INSERT_TOPIC";
    public static final String APPROVAL_INSERT_TOPIC = "APPROVAL_INSERT_TOPIC";
    public static final String APPROVAL_UPDATE_TOPIC = "APPROVAL_UPDATE_TOPIC";
    public static final String CANDIDATE_APPLICABLE_UPDATE_TOPIC = "CANDIDATE_APPLICABLE_UPDATE_TOPIC";
    public static final String CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC = "CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC";
    public static final String ILLEGAL_DAO_TYPE_MESSAGE = "Illegal dao type: ";
    private final Environment environment;

    public DataEntryUpdateTopicProvider(Environment environment) {
        this.environment = environment;
    }

    public String getTopic(OperationType operationType, Dao dao) {
        return switch (operationType) {
            case INSERT -> readEnvironmentVariable(dao, CANDIDATE_INSERT_TOPIC, APPROVAL_INSERT_TOPIC);
            case MODIFY -> getUpdateTopic(dao);
            case REMOVE -> readEnvironmentVariable(dao, CANDIDATE_REMOVE_TOPIC, APPROVAL_REMOVE_TOPIC);
            default -> throw new IllegalArgumentException(ILLEGAL_DAO_TYPE_MESSAGE + operationType);
        };
    }

    private String readEnvironmentVariable(Dao dao, String candidateTopic, String approvalTopic) {
        switch (dao.type()) {
            case CandidateDao.TYPE -> {
                return environment.readEnv(candidateTopic);
            }
            case ApprovalStatusDao.TYPE -> {
                return environment.readEnv(approvalTopic);
            }
            default -> throw new IllegalArgumentException(ILLEGAL_DAO_TYPE_MESSAGE + dao.type());
        }
    }

    private String getUpdateTopic(Dao dao) {
        switch (dao.type()) {
            case CandidateDao.TYPE -> {
                return getCandidateUpdateTopic(dao);
            }
            case ApprovalStatusDao.TYPE -> {
                return environment.readEnv(APPROVAL_UPDATE_TOPIC);
            }
            default -> throw new IllegalArgumentException("Illegal dao type: " + dao.type());
        }
    }

    private String getCandidateUpdateTopic(Dao dao) {
        var candidateDao = (CandidateDao) dao;
        return candidateDao.candidate().applicable()
                   ? environment.readEnv(CANDIDATE_APPLICABLE_UPDATE_TOPIC)
                   : environment.readEnv(CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC);
    }
}
