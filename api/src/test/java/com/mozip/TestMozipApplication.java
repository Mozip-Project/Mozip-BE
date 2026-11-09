package com.mozip;

import org.springframework.boot.SpringApplication;

public class TestMozipApplication {

	public static void main(String[] args) {
		SpringApplication.from(MozipApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
