package no.sikt.nva.nvi.common;

import java.net.URI;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

@JacocoGenerated
//TODO: Handle test coverage
public class S3StorageReader implements StorageReader<URI> {

    private final S3Driver s3Driver;

    public S3StorageReader(String bucket) {
        this(S3Driver.defaultS3Client().build(), bucket);
    }

    public S3StorageReader(S3Client client, String bucket) {
        this.s3Driver = new S3Driver(client, bucket);
    }

    @Override
    public String read(URI uri) {
        var resourceRelativePath = UriWrapper.fromUri(uri).toS3bucketPath();
        return s3Driver.getFile(resourceRelativePath);
    }
}
