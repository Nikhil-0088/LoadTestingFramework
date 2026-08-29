package com.example.loadtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoadTestServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoadTestServiceApplication.class, args);
	}

}
