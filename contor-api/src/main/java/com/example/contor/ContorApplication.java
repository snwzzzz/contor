package com.example.contor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ContorApplication {

    public static void main(String[] args) {
        // All timestamps are timestamptz/Instant; run the JVM (and every JDBC session) in UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ContorApplication.class, args);
    }
}
