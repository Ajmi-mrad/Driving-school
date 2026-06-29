package com.example.communicationservice;

import org.springframework.boot.SpringApplication;

public class TestCommunicationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(CommunicationServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}