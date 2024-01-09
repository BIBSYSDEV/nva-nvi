package no.sikt.nva.nvi.common;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

public interface StorageWriter<T> {

    URI write(T blob) throws IOException;

    void delete(UUID identifier) throws IOException;
}
