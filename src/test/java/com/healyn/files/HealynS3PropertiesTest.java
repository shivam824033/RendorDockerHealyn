package com.healyn.files;

import com.healyn.files.config.HealynS3Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealynS3PropertiesTest {

    private static HealynS3Properties props(String endpoint, String publicEndpoint) {
        return new HealynS3Properties(endpoint, publicEndpoint, "us-east-1",
                "minioadmin", "minioadmin", "healyn-files-dev", 300);
    }

    @Test
    void presignEndpoint_falls_back_to_endpoint_when_public_is_null_or_blank() {
        assertThat(props("http://localhost:9000", null).presignEndpoint())
                .isEqualTo("http://localhost:9000");
        assertThat(props("http://localhost:9000", "   ").presignEndpoint())
                .isEqualTo("http://localhost:9000");
    }

    @Test
    void presignEndpoint_uses_public_endpoint_when_set() {
        assertThat(props("http://minio:9000", "http://192.168.1.20:9000").presignEndpoint())
                .isEqualTo("http://192.168.1.20:9000");
    }
}
