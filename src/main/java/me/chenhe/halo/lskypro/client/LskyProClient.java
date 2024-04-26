package me.chenhe.halo.lskypro.client;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LskyProClient {
    protected WebClient client;

    public LskyProClient(@NotNull String server, @Nullable String token) {
        final String baseUrl = server + (server.endsWith("/") ? "" : "/") + "api/v1";

        var builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json");
        if (StringUtils.hasText(token)) {
            builder = builder.defaultHeader("Authorization", token);
        }
        client = builder.build();
    }

    public Mono<UploadResponse> upload(@NotNull Flux<DataBuffer> content, @Nullable String filename,
        @Nullable MediaType contentType, Integer strategyId) {

        final var bodyBuilder = new MultipartBodyBuilder();
        final var filePartBuilder = bodyBuilder.asyncPart("file", content, DataBuffer.class);
        if (filename != null) {
            filePartBuilder.filename(filename);
        }
        if (contentType != null) {
            filePartBuilder.contentType(contentType);
        } else if (filename != null) {
            final var t = MediaTypeFactory.getMediaType(filename);
            filePartBuilder.contentType(t.orElse(MediaType.APPLICATION_OCTET_STREAM));
        }
        if (strategyId != null) {
            bodyBuilder.part("strategy_id", strategyId);
        }

        return client.post()
            .uri("/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<LskyResponse<UploadResponse>>() {
            })
            // TODO: error handling
            .map((resp) -> resp.data);
    }

    public Mono<Void> delete(@NotNull String key) {
        return client.delete()
            .uri("/images/" + key)
            .retrieve()
            .bodyToMono(LskyResponse.class)
            // TODO: error handling
            .flatMap((resp) -> Mono.empty());
    }

    public record LskyResponse<T>(boolean status, String message, T data) {
    }

}
