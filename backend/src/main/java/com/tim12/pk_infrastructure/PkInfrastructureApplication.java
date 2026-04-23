package com.tim12.pk_infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PkInfrastructureApplication {

	public static void main(String[] args) {
		SpringApplication.run(PkInfrastructureApplication.class, args);
		System.out.println("PK Infrastructure Service is running...");
	}

}
