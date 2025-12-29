package me.chenhe.halo.lskypro;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import me.chenhe.halo.lskypro.client.LskyProClient;
import me.chenhe.halo.lskypro.client.LskyProException;
import me.chenhe.halo.lskypro.client.UploadResponse;
import org.pf4j.Extension;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Constant;
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
    public static final String INSTANCE_ID = "lskypro.plugin.halo.chenhe.me/instance-id";


    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(ctx -> shouldHandle(ctx.policy(), ctx.file()))
            .flatMap(ctx -> {
                final var properties = getProperties(ctx.configMap());
                final var instanceId = getInstanceId(properties, ctx.policy());
                log.debug("Starting upload, instanceId: {}", instanceId);
                return upload(ctx, properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorMap(LskyProAttachmentHandler::handleError)
                    .map(resp -> {
                        log.debug("LskyPro response - key: {}, url: {}", resp.key(), resp.links().url());
                        return buildAttachment(resp, instanceId);
                    });
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(context -> this.shouldHandle(context.policy(), null))
            .flatMap(context -> {
                var imageKey = getImageKey(context.attachment());
                if (imageKey.isEmpty()) {
                    log.warn(
                        "Cannot obtain image key from attachment {}, skip deleting from LskyPro.",
                        context.attachment().getMetadata().getName());
                    return Mono.just(context.attachment());
                }
                
                final var properties = getProperties(context.configMap());
                final var instanceId = getInstanceId(properties, context.policy());
                final var imageInstanceId = getInstanceId(context.attachment());
                
                if (imageInstanceId.isEmpty() || !imageInstanceId.get().equals(instanceId)) {
                    log.warn(
                        "Attachment {} instance ID does not match (policy: {}, image: {}), skip deleting from LskyPro.",
                        context.attachment().getMetadata().getName(), instanceId, imageInstanceId.orElse("EMPTY"));
                    return Mono.just(context.attachment());
                }
                
                return delete(imageKey.get(), properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(v -> log.info("Delete image {} from LskyPro successfully",
                        imageKey.get()))
                    .onErrorMap(LskyProAttachmentHandler::handleError)
                    .thenReturn(context.attachment());
            });
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
        // 优先从 status 读取
        var status = attachment.getStatus();
        if (status != null && StringUtils.hasText(status.getPermalink())) {
            return Mono.just(URI.create(status.getPermalink()));
        }
        // 兼容旧数据，从 annotations 读取
        final var link = getImageLink(attachment);
        return link.map(s -> Mono.just(URI.create(s))).orElseGet(Mono::empty);
    }

    @Override
    public Mono<Map<ThumbnailSize, URI>> getThumbnailLinks(Attachment attachment, Policy policy,
        ConfigMap configMap) {
        if (!shouldHandle(policy, null)) {
            return Mono.empty();
        }
        // LskyPro does not support thumbnail parameters, return empty map
        return Mono.just(Map.of());
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
                    uploadContext.file().filename(), null, properties.getLskyStrategy(),
                    properties.getLskyAlbumId())
            ));
    }

    Optional<String> getImageLink(Attachment attachment) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(annotations.get(IMAGE_LINK));
    }

    Optional<String> getImageKey(Attachment attachment) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(annotations.get(IMAGE_KEY));
    }

    Optional<String> getInstanceId(Attachment attachment) {
        var annotations = attachment.getMetadata().getAnnotations();
        if (annotations == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(annotations.get(INSTANCE_ID));
    }

    Attachment buildAttachment(UploadResponse uploadResponse, @Nonnull String instanceId) {
        Assert.hasText(instanceId, "instanceId cannot be empty");
        Assert.notNull(uploadResponse.links(), "links cannot be null");
        Assert.hasText(uploadResponse.links().url(), "url cannot be empty");

        final var url = uploadResponse.links().url();

        final var metadata = new Metadata();
        metadata.setGenerateName(UUID.randomUUID().toString());
        final var annotations = Map.of(
            IMAGE_KEY, uploadResponse.key(),
            IMAGE_LINK, url,
            INSTANCE_ID, instanceId
        );
        metadata.setAnnotations(annotations);
        log.debug("Created attachment metadata with annotations: {}", annotations);

        // Warning: due to the limitation of Lsky Pro API,
        // the file size may be wrong if you configured server side image conversion.
        var spec = new Attachment.AttachmentSpec();

        // The lsky pro returns media type of uploaded file, which is not same with actual url if
        // image conversion or compression is enabled.
        // So, try to infer the real type from url. e.g. https://example.com/a.png -> image/png
        MediaType mediaType = null;
        try {
            mediaType = MediaType.parseMediaType(uploadResponse.mimetype());
        } catch (InvalidMediaTypeException ignored) {
        }
        final var filename =
            url.lastIndexOf('/') == -1 ? null : url.substring(url.lastIndexOf('/') + 1);
        var inferredMediaType = MediaTypeFactory.getMediaType(filename);
        if (inferredMediaType.isPresent() && !inferredMediaType.get().equals(mediaType)) {
            log.debug("Use inferred media type '{}', rather than '{}'", inferredMediaType.get(),
                mediaType);
            mediaType = inferredMediaType.get();
        }
        spec.setSize((long) (uploadResponse.size() * 1024L));
        spec.setDisplayName(uploadResponse.name());
        if (mediaType != null) {
            spec.setMediaType(mediaType.toString());
        } else {
            log.warn("No media type in API response nor can it be inferred from the url {}", url);
        }

        final var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        
        // Halo 2.22+ requires setting status with permalink
        var status = new Attachment.AttachmentStatus();
        status.setPermalink(url);
        attachment.setStatus(status);
        
        log.debug("Built attachment {} successfully", uploadResponse.key());
        return attachment;
    }

    static Throwable handleError(Throwable t) {
        if (t instanceof LskyProException e) {
            if (e.statusCode.value() == 401) {
                return new ServerWebInputException(
                    "Lsky Pro authentication failed, please check your API token.");
            } else if (e.statusCode.value() == 403) {
                return new ServerWebInputException(
                    "Lsky Pro API may have been disabled (HTTP 403).");
            } else if (e.statusCode.value() == 429) {
                return new ServerWebInputException(
                    "Lsky Pro API error: usage quota exceeded (HTTP 429).");
            }
            return new ServerWebInputException(
                "LskyPro API error (HTTP %d): %s".formatted(e.statusCode.value(), e.getMessage()));
        } else if (t instanceof WebClientRequestException e) {
            return new ServerWebInputException(
                "Failed to request LskyPro API: %s".formatted(e.getMessage()));
        }
        return t;
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

        // 不再限制文件类型，让 Lsky Pro 服务端自己验证
        // Lsky Pro 后台可以配置允许的文件格式
        return true;
    }

    /**
     * Each image must be associated with an instance ID. If the user does not specify it, an ID
     * associated with the current policy is automatically generated.
     * <p>
     * Images (attachments) associated with the same instance ID are considered to be managed
     * by current storage policy. This allows the relationship between attachments and LskyPro
     * server to be preserved even after the plug-in is reinstalled or the server address is
     * changed.
     *
     * @return Non-empty instance id of current policy.
     */
    @Nonnull
    String getInstanceId(LskyProProperties properties, Policy policy) {
        if (StringUtils.hasText(properties.getInstanceId())) {
            return properties.getInstanceId();
        }
        try {
            final var url = new URL(properties.getLskyUrl());
            final var idBuilder = new StringBuilder().append(url.getHost()).append(url.getPath());
            if (url.getPort() != -1) {
                idBuilder.append(':').append(url.getPort());
            }
            final var instanceId = idBuilder.toString();
            log.debug("Use default instance id '{}' for policy {}", instanceId,
                policy.getMetadata().getName());
            return instanceId;
        } catch (MalformedURLException e) {
            throw new ServerErrorException("Invalid LskyPro server: " + properties.getLskyUrl(), e);
        }
    }

    LskyProProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, LskyProProperties.class);
    }
}
