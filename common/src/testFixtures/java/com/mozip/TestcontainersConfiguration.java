package com.mozip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final Path DDL_DIRECTORY = Paths.get("db", "ddl");

	// 리포의 db/ddl/*.sql을 파일명 순으로 실행해 테스트 DB를 만듦
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
			.withCopyFileToContainer(MountableFile.forHostPath(ddlDirectory()), "/docker-entrypoint-initdb.d/");
	}

	// 엔티티와 DDL이 어긋나면 컨텍스트 로딩에서 실패 (프로필 yml의 ddl-auto보다 우선)
	@Bean
	DynamicPropertyRegistrar ddlValidate() {
		return registry -> registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
	}

	// 현재 폴더부터 상위로 올라가며 db/ddl을 찾음
	private static Path ddlDirectory() {
		Path current = Paths.get("").toAbsolutePath();
		while (current != null) {
			Path candidate = current.resolve(DDL_DIRECTORY);
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("db/ddl 폴더를 찾을 수 없습니다: " + Paths.get("").toAbsolutePath());
	}

}
