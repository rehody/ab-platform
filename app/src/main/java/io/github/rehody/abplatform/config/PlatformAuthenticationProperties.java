package io.github.rehody.abplatform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "ab.authentication.internal-token")
public class PlatformAuthenticationProperties {

    @NotBlank private String issuer;

    @NotBlank @Size(min = 32) private String sharedSecret;
}
