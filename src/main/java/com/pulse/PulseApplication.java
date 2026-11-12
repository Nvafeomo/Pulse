package com.pulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Pulse notification API. Accepts notification
 * jobs over REST, persists them, and publishes events to SNS for per-channel
 * delivery.
 */
@SpringBootApplication
public class PulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(PulseApplication.class, args);
	}

}
