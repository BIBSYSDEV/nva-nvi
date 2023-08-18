package no.sikt.nva.nvi.index;

import java.net.URI;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidateMessageBody;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

class FakeStorageReader implements StorageReader<NviCandidateMessageBody> {

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");

    private final S3Driver s3Driver;

    public FakeStorageReader(S3Client s3Client) {
        this.s3Driver = new S3Driver(s3Client, EXPANDED_RESOURCES_BUCKET);
    }

    @Override
    public String readMessage(NviCandidateMessageBody blob) {
        var resourceRelativePath = UriWrapper.fromUri(blob.publicationBucketUri()).toS3bucketPath();
        return s3Driver.getFile(resourceRelativePath);
    }

    @Override
    public String readUri(URI uri) {
        return null;
    }
}
