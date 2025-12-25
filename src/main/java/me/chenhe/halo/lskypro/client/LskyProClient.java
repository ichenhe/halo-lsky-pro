package me.chenhe.halo.lskypro.client;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LskyProClient {
    protected WebClient client;

    public LskyProClient(@NotNull String server, @Nullable String token) {
        final String baseUrl = server + (server.endsWith("/") ? "" : "/") + "api/v1";

        var builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .filter(errorHandler());
        if (StringUtils.hasText(token)) {
            builder = builder.defaultHeader("Authorization", "Bearer " + token);
        }
        client = builder.build();
    }

    protected static ExchangeFilterFunction errorHandler() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            if (resp.statusCode().is5xxServerError()) {
                return resp.bodyToMono(String.class)
                    .flatMap(errorBody ->
                        Mono.error(new LskyProException(resp.statusCode(), errorBody)));
            } else if (!resp.statusCode().is2xxSuccessful()) {
                return resp.bodyToMono(LskyResponse.class).flatMap(body ->
                    Mono.error(new LskyProException(resp.statusCode(), body.message)));
            }
            // 2xx
            return Mono.just(resp);
        });
    }

    public Mono<UploadResponse> upload(@NotNull Flux<DataBuffer> content, @Nullable String filename,
        @Nullable MediaType contentType, Integer strategyId, Integer albumId) {

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
        if (albumId != null) {
            bodyBuilder.part("album_id", albumId);
        }

        return client.post()
            .uri("/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<LskyResponse<UploadResponse>>() {
            })
            .flatMap(this::checkResponse)
            .flatMap((data) -> {
                if (data == null || data.links() == null || !StringUtils.hasText(
                    data.links().url())) {
                    return Mono.error(
                        new LskyProException(HttpStatus.OK, "links or url is empty"));
                }
                return Mono.just(data);
            });
    }

    public Mono<Void> delete(@NotNull String key) {
        return client.delete()
            .uri("/images/" + key)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<LskyResponse<Void>>() {
            })
            .flatMap((this::checkResponse))
            .then(Mono.empty());
    }

    /**
     * Verify that the Lsky Pro API response status is {@code true}.
     */
    <T> Mono<T> checkResponse(LskyResponse<T> resp) {
        if (resp.status()) {
            return Mono.justOrEmpty(resp.data);
        }
        return Mono.error(
            new LskyProException(HttpStatus.OK, "status=false: " + resp.message));
    }

    public record LskyResponse<T>(boolean status, String message, T data) {
    }

}
