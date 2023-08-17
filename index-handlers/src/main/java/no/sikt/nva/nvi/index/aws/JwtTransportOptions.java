package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import no.unit.nva.auth.CachedJwtProvider;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

class JwtTransportOptions implements AwsSdk2TransportOptions {

    private final CachedJwtProvider cachedJwtProvider;
    private final AwsCredentialsProvider credentials;
    private final Integer requestCompressionSize;
    private final Boolean responseCompression;
    private final JsonpMapper mapper;

    JwtTransportOptions(CachedJwtProvider cachedJwtProvider, AwsCredentialsProvider awsCredentialsProvider,
                        Integer requestCompressionSize, Boolean responseCompression, JsonpMapper mapper) {
        this.cachedJwtProvider = cachedJwtProvider;
        this.credentials = awsCredentialsProvider;
        this.requestCompressionSize = requestCompressionSize;
        this.responseCompression = responseCompression;
        this.mapper = mapper;
    }

    @Override
    public AwsCredentialsProvider credentials() {
        return credentials;
    }

    @Override
    public Integer requestCompressionSize() {
        return requestCompressionSize;
    }

    @Override
    public Boolean responseCompression() {
        return responseCompression;
    }

    @Override
    public JsonpMapper mapper() {
        return mapper;
    }

    @Override
    public AwsSdk2TransportOptions.Builder toBuilder() {
        return new AwsSdk2TransportOptions.BuilderImpl(this);
    }

    @Override
    public List<Entry<String, String>> headers() {
        var token = cachedJwtProvider.getValue().getToken();

        List<Map.Entry<String, String>> headers = new ArrayList<>(Collections.emptyList());
        headers.add(Map.entry(AUTHORIZATION, token));
        return headers;
    }

    @Override
    public Map<String, String> queryParameters() {
        return null;
    }

    @Override
    public Function<List<String>, Boolean> onWarnings() {
        return null;
    }
}

