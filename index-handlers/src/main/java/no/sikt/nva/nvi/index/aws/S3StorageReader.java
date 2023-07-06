package no.sikt.nva.nvi.index.aws;

import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidateMessageBody;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

@JacocoGenerated
//TODO: Handle test coverage
public class S3StorageReader implements StorageReader<NviCandidateMessageBody> {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3StorageReader.class);
    private final S3Driver s3Driver;

    public S3StorageReader(String bucket) {
        this(S3Driver.defaultS3Client().build(), bucket);
    }

    public S3StorageReader(S3Client client, String bucket) {
        this.s3Driver = new S3Driver(client, bucket);
    }

    @Override
    public String read(NviCandidateMessageBody candidate) {
        var resourceRelativePath = UriWrapper.fromUri(candidate.publicationBucketUri()).toS3bucketPath();
        LOGGER.info("Getting s3 path for file {}", resourceRelativePath.toString());
        return s3Driver.getFile(resourceRelativePath);
    }
}
