package me.chenhe.halo.lskypro;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * The properties of storage policy that uses this plugin as backend.
 * <p>
 * This data class is bound to {@code policy-template-lskypro.yaml}.
 */
@Data
public class LskyProProperties {

    /**
     * Including protocol, without trailing {@code /} or api path.
     */
    private String lskyUrl;

    /**
     * Without leading {@code Bearer}.
     */
    private String lskyToken;

    private Integer lskyStrategy;

    /**
     * User-specified instance ID.
     */
    private @Nullable String instanceId;

    @SuppressWarnings("unused")
    public void setLskyUrl(String lskyUrl) {
        final var fileSeparator = "/";
        final var apiSuffix = "/api/v1";
        if (!StringUtils.hasText(lskyUrl)) {
            this.lskyUrl = null;
            return;
        }
        if (lskyUrl.endsWith(fileSeparator)) {
            lskyUrl = lskyUrl.substring(0, lskyUrl.length() - 1);
        }
        if (lskyUrl.endsWith(apiSuffix)) {
            lskyUrl = lskyUrl.substring(0, lskyUrl.length() - apiSuffix.length());
        }
        this.lskyUrl = lskyUrl;
    }

    @SuppressWarnings("unused")
    public void setLskyToken(@Nullable String lskyToken) {
        if (!StringUtils.hasText(lskyToken)) {
            this.lskyToken = null;
            return;
        }
        lskyToken = lskyToken.trim();
        final String prefix = "Bearer";
        if (lskyToken.startsWith(prefix)) {
            lskyToken = lskyToken.substring(prefix.length());
        }
        this.lskyToken = StringUtils.hasText(lskyToken) ? lskyToken : null;
    }

    @SuppressWarnings("unused")
    public void setInstanceId(String instanceId) {
        if (StringUtils.hasText(instanceId)) {
            this.instanceId = instanceId;
        } else {
            this.instanceId = null;
        }
    }
}
