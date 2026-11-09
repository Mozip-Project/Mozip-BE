package com.mozip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MozipApplication {

	public static void main(String[] args) {
		SpringApplication.run(MozipApplication.class, args);
	}

}
