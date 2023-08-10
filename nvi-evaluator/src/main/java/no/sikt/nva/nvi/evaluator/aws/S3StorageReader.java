package no.sikt.nva.nvi.evaluator.aws;

import no.sikt.nva.nvi.common.StorageReader;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

public class S3StorageReader implements StorageReader<EventReference> {

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");

    private final S3Driver s3Driver;

    @JacocoGenerated
    public S3StorageReader() {
        this(S3Driver.defaultS3Client().build());
    }

    public S3StorageReader(S3Client client) {
        this.s3Driver = new S3Driver(client, EXPANDED_RESOURCES_BUCKET);
    }

    @JacocoGenerated

    @Override
    public String read(EventReference blob) {
        return s3Driver.getFile(UriWrapper.fromUri(blob.getUri()).toS3bucketPath());
    }
}
