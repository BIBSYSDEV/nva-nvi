package no.sikt.nva.nvi.evaluator.aws;

import java.net.URI;
import no.sikt.nva.nvi.evaluator.StorageReader;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class S3StorageReader implements StorageReader<EventReference> {

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");

    private static final Logger LOGGER = LoggerFactory.getLogger(S3StorageReader.class);
    private final S3Driver s3Driver;

    public S3StorageReader() {
        this(S3Driver.defaultS3Client().build());
    }

    public S3StorageReader(S3Client client) {
        this.s3Driver = new S3Driver(client, EXPANDED_RESOURCES_BUCKET);
    }

    @JacocoGenerated

    @Override
    public String read(EventReference blob) {
        URI uri = blob.getUri();
        LOGGER.info("Event URI {}", uri.toString());
        var resourceRelativePath = UriWrapper.fromUri(uri).toS3bucketPath();
        LOGGER.info("Getting s3 path for file {}", resourceRelativePath.toString());
        return s3Driver.getFile(resourceRelativePath);
    }
}
