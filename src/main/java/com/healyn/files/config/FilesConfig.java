package com.healyn.files.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(HealynS3Properties.class)
public class FilesConfig {

    /** Client for server-side object operations (stat / read / delete). */
    @Bean
    @Primary
    public MinioClient minioClient(HealynS3Properties props) {
        return build(props.endpoint(), props);
    }

    /**
     * Client used only to mint presigned URLs. Its endpoint host is what ends up
     * in the signed URL, so it must be a host the client (mobile device / browser)
     * can actually reach — see {@link HealynS3Properties#presignEndpoint()}.
     */
    @Bean
    public MinioClient minioPresignClient(HealynS3Properties props) {
        return build(props.presignEndpoint(), props);
    }

    private static MinioClient build(String endpoint, HealynS3Properties props) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(props.accessKey(), props.secretKey());
        if (props.region() != null && !props.region().isBlank()) {
            builder.region(props.region());
        }
        return builder.build();
    }
}
