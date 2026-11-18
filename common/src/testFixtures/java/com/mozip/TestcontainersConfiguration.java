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

	// 리포의 db/ddl/*.sql로 테이블을 만들고 테스트는 ddl-auto: validate로 엔티티와 대조 (SQL을 빠뜨리면 컨텍스트 로딩에서 실패)
	// postgres 이미지는 첫 기동 때 /docker-entrypoint-initdb.d/ 안의 .sql을 파일명 순으로 실행
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
			.withCopyFileToContainer(MountableFile.forHostPath(ddlDirectory()), "/docker-entrypoint-initdb.d/");
	}

	// 테스트 DB는 db/ddl/*.sql로 만들고 엔티티와 어긋나면 컨텍스트 로딩에서 실패하게 함 (프로필 yml의 update보다 우선 적용됨)
	@Bean
	DynamicPropertyRegistrar ddlValidate() {
		return registry -> registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
	}

	// Gradle은 모듈 폴더(common/)에서, IDE는 리포 루트에서 실행할 수 있어 상위 폴더로 올라가며 db/ddl을 찾음
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
