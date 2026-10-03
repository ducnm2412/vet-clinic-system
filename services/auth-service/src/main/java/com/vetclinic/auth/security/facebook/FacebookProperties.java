package com.vetclinic.auth.security.facebook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "facebook")
@Getter
@Setter
public class FacebookProperties {

    private String appId;
    private String appSecret;
    private String apiVersion;
}
