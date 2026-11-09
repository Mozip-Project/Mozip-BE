package com.mozip.global.health;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HealthGraphqlController {

	@QueryMapping
	public HealthResponse health() {
		return HealthResponse.up();
	}

}
