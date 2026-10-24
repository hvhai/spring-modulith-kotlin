plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com.codehunter"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	// web
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// UI
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect")

	// security
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-security")

	// modulith
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")

	// db
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("com.mysql:mysql-connector-j")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.springframework.boot:spring-boot-h2console")
	implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:2.3.0")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")

	// monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.modulith:spring-modulith-starter-insight")
	implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
	implementation("org.springframework.boot:spring-boot-opentelemetry")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("com.github.loki4j:loki-logback-appender:2.1.0")

	// swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

	// markdown
	implementation("org.commonmark:commonmark:0.24.0")

	// http client
	implementation("org.apache.httpcomponents.client5:httpclient5")


	// testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-resttestclient")
	testImplementation("org.springframework.boot:spring-boot-restclient")
	testImplementation("com.ninja-squad:springmockk:5.0.1")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	testImplementation("org.springframework.security:spring-security-test")

	testImplementation("org.wiremock:wiremock-standalone:3.10.0")

	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showExceptions = true
		showCauses = true
		showStackTraces = true
	}
	val failedTests = mutableListOf<String>()
	afterTest(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc: TestDescriptor, result: TestResult ->
		if (result.resultType == TestResult.ResultType.FAILURE) {
			val testClass = desc.className ?: "UnknownClass"
			val testMethod = desc.name ?: "UnknownMethod"
			failedTests.add("$testClass.$testMethod: ${desc.displayName}")
		}
	}))
	afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc: TestDescriptor, result: TestResult ->
		if (desc.parent == null) { // will match the outermost suite
			println("Test result: ${result.resultType}")
			println("Test summary: ${result.testCount} tests, ${result.successfulTestCount} succeeded, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
			if (failedTests.isNotEmpty()) {
				println("Failed tests:")
				failedTests.forEach { println(it) }
			}
		}
	}))

}
