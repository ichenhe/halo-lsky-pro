package me.chenhe.halo.lskypro;

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

    /** Including Bearer */
    private String lskyToken;

    private Integer lskyStrategy;

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
}
