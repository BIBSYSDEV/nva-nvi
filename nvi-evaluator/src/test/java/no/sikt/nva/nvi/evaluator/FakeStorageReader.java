package no.sikt.nva.nvi.evaluator;

import java.net.URI;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.evaluator.aws.S3StorageReader;
import no.unit.nva.events.models.EventReference;
import software.amazon.awssdk.services.s3.S3Client;

class FakeStorageReader implements StorageReader<EventReference> {

    private final StorageReader<EventReference> reader;

    public FakeStorageReader(S3Client s3Client) {
        this.reader = new S3StorageReader(s3Client);
    }

    @Override
    public String readMessage(EventReference blob) {
        return reader.readMessage(blob);
    }

    @Override
    public String readUri(URI uri) {
        return null;
    }
}
