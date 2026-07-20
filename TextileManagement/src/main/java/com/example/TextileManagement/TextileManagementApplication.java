package com.example.TextileManagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TextileManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(TextileManagementApplication.class, args);
	}
}
