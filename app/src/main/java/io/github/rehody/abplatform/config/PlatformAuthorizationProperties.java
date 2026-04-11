package io.github.rehody.abplatform.config;

import io.github.rehody.abplatform.security.PlatformRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "ab.authorization")
public class PlatformAuthorizationProperties {

    @NotEmpty private Map<UUID, PlatformRole> actors = new LinkedHashMap<>();
}
