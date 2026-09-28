package com.archery.range;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ArcheryRangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArcheryRangeApplication.class, args);
    }
}
