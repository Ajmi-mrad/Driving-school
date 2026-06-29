package com.example.vehicleservice;

import org.springframework.boot.SpringApplication;

public class TestVehicleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(VehicleServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
