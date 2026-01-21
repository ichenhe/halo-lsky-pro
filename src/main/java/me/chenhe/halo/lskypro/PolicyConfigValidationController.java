package me.chenhe.halo.lskypro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chenhe.halo.lskypro.client.LskyProClient;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.infra.utils.PathUtils;
import run.halo.app.plugin.ApiVersion;

@ApiVersion("lsky-pro.chenhe.me/v1")
@RestController
@RequiredArgsConstructor
@Slf4j
public class PolicyConfigValidationController {
    /**
     * The image file in resource dir.
     */
    private static final String FILE_NAME = "validation.png";

    @PostMapping("/policies/validation")
    public Mono<Void> validatePolicyConfig(@RequestBody LskyProProperties props) {
        final var content = readImage();
        final var client = new LskyProClient(props.getLskyUrl(), props.getLskyToken());
        return client.upload(content, FILE_NAME, null, props.getLskyStrategy(),
                props.getLskyAlbumId())
            .doOnNext(r -> log.info("Validate LskyPro policy config: upload successful: {}", r))
            .flatMap((uploadResp) -> client.delete(uploadResp.key()))
            .onErrorMap(LskyProAttachmentHandler::handleError)
            .then(Mono.empty());
    }

    private Flux<DataBuffer> readImage() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader(this.getClass()
            .getClassLoader());
        String path = PathUtils.combinePath(FILE_NAME);
        String simplifyPath = StringUtils.cleanPath(path);
        Resource resource = resourceLoader.getResource(simplifyPath);
        return DataBufferUtils.read(resource, new DefaultDataBufferFactory(), 1024);
    }
}
