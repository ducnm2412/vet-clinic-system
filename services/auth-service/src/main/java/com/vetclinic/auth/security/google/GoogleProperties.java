package com.vetclinic.auth.security.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google")
@Getter
@Setter
public class GoogleProperties {

    private String clientId;
}
