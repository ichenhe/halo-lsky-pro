package me.chenhe.halo.lskypro;

import jakarta.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import me.chenhe.halo.lskypro.client.LskyProClient;
import me.chenhe.halo.lskypro.client.UploadResponse;
import org.pf4j.Extension;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;

@Slf4j
@Extension
public class LskyProAttachmentHandler implements AttachmentHandler {

    public static final String IMAGE_KEY = "lskypro.plugin.halo.chenhe.me/image-key";
    public static final String IMAGE_LINK = "lskypro.plugin.halo.chenhe.me/image-link";


    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(ctx -> shouldHandle(ctx.policy(), ctx.file()))
            .flatMap(ctx -> {
                final var properties = getProperties(ctx.configMap());
                return upload(ctx, properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(this::buildAttachment);
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter((ctx) -> shouldHandle(ctx.policy(), null))
            .flatMap((ctx) -> {
                final var key = getImageKey(ctx.attachment());
                if (key.isEmpty()) {
                    log.warn(
                        "Cannot obtain image key from attachment {}, skip deleting from LskyPro.",
                        ctx.attachment().getMetadata().getName());
                    return Mono.just(ctx);
                }
                return delete(key.get(), getProperties(ctx.configMap()))
                    .then(Mono.just(ctx));
            })
            .map(DeleteContext::attachment);
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!shouldHandle(policy, null)) {
            return Mono.empty();
        }
        final var link = getImageLink(attachment);
        return link.map(s -> Mono.just(URI.create(s))).orElseGet(Mono::empty);
    }

    Mono<Void> delete(String key, LskyProProperties properties) {
        return Mono.defer(() ->
                Mono.just(new LskyProClient(properties.getLskyUrl(), properties.getLskyToken()))
            )
            .flatMap((lskyProClient -> lskyProClient.delete(key)));
    }

    Mono<UploadResponse> upload(UploadContext uploadContext,
        LskyProProperties properties) {
        return Mono.defer(() ->
                Mono.just(new LskyProClient(properties.getLskyUrl(), properties.getLskyToken()))
            )
            .flatMap((lskyProClient ->
                lskyProClient.upload(uploadContext.file().content(),
                    uploadContext.file().filename(), null, properties.getLskyStrategy())
            ));
    }

    Optional<String> getImageLink(Attachment attachment) {
        return Optional.ofNullable(attachment.getMetadata().getAnnotations().get(IMAGE_LINK));
    }

    Optional<String> getImageKey(Attachment attachment) {
        return Optional.ofNullable(attachment.getMetadata().getAnnotations().get(IMAGE_KEY));
    }

    Attachment buildAttachment(UploadResponse uploadResponse) {
        final var metadata = new Metadata();
        metadata.setGenerateName(UUID.randomUUID().toString());
        final var annotations = Map.of(
            IMAGE_KEY, uploadResponse.key(),
            IMAGE_LINK, uploadResponse.links().url()
        );
        metadata.setAnnotations(annotations);

        // Warning: due to the limitation of Lsky Pro API,
        // the file size and media type may be wrong if you configured server side image conversion.
        var spec = new Attachment.AttachmentSpec();
        spec.setSize((long) (uploadResponse.size() * 1024L));
        spec.setDisplayName(uploadResponse.name());
        spec.setMediaType(uploadResponse.mimetype());

        final var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        log.info("Built attachment {} successfully", uploadResponse.key());
        return attachment;
    }

    /**
     * Whether the current request should be handled by this plugin.
     */
    boolean shouldHandle(Policy policy, @Nullable FilePart filePart) {
        // check policy
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        if (!"chenhe-lsky-pro".equals(templateName)) {
            return false;
        }

        // check media type
        if (filePart == null) {
            // not an upload request, no need to check the media type
            return true;
        }
        final var mediaType = MediaTypeFactory.getMediaType(filePart.filename());
        if (mediaType.isEmpty()) {
            log.warn("Ignore attachment request {} due to empty media type", filePart.filename());
            return false;
        }
        if (!"image".equals(mediaType.get().getType())) {
            log.warn("Ignore attachment request {} due to non-image media type: {}",
                filePart.filename(), mediaType.get());
            return false;
        }
        return true;
    }

    LskyProProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, LskyProProperties.class);
    }
}
