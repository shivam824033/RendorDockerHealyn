package com.healyn.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClockUtc() {
        return Clock.systemUTC();
    }
}
