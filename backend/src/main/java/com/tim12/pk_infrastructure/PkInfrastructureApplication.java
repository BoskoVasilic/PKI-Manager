package com.tim12.pk_infrastructure;

import com.tim12.pk_infrastructure.service.KeyEncryptionService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PkInfrastructureApplication {

	public static void main(String[] args) {
		SpringApplication.run(PkInfrastructureApplication.class, args);
		System.out.println("PK Infrastructure Service is running...");
	}

}
