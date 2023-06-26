package no.sikt.nva.nvi.index;

import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

class FakeStorageReader implements StorageReader<NviCandidate> {

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");

    private final S3Driver s3Driver;

    public FakeStorageReader(S3Client s3Client) {
        this.s3Driver = new S3Driver(s3Client, EXPANDED_RESOURCES_BUCKET);
    }

    @Override
    public String read(NviCandidate blob) {
        var uri = blob.publicationId();
        var resourceRelativePath = UriWrapper.fromUri(uri).toS3bucketPath();
        return s3Driver.getFile(resourceRelativePath);
    }
}
