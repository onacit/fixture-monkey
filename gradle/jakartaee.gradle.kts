import org.gradle.api.artifacts.component.ModuleComponentIdentifier

val jakartaEe = mapOf(
	"bom" to "jakarta.platform:jakarta.jakartaee-bom:10.0.0",
	"validationApi" to "3.0.2",
	"hibernateValidator" to "8.0.6.Final",
	"expressly" to "5.0.0",
	"testJava" to "11",
)

fun usesJakartaValidationProvider(cp: Configuration) = cp.incoming.resolutionResult.allComponents
	.mapNotNull { it.id as? ModuleComponentIdentifier }
	.any {
		(it.group == "org.hibernate.validator" && it.module == "hibernate-validator" && !it.version.startsWith("6.")) ||
			it.group == "org.glassfish.expressly"
	}

val jakartaProviderGroups = setOf(
	"org.hibernate.validator",
	"org.hibernate.accessor",
	"org.glassfish.expressly",
	"jakarta.el",
)

subprojects {
	dependencies.components.all {
		if (id.group in jakartaProviderGroups) {
			allVariants {
				attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
			}
		}
	}

	configurations.configureEach {
		resolutionStrategy.eachDependency {
			val version = requested.version ?: return@eachDependency
			if (requested.group == "org.hibernate.validator" && requested.name == "hibernate-validator" &&
				!version.startsWith("6.")
			) {
				useVersion(jakartaEe.getValue("hibernateValidator"))
				because("Jakarta EE alignment")
			}
			if (requested.group == "org.glassfish" && requested.name == "jakarta.el" && !version.startsWith("3.")) {
				useTarget("org.glassfish.expressly:expressly:${jakartaEe.getValue("expressly")}")
				because("Jakarta EE alignment")
			}
		}
	}

	plugins.withId("java") {
		dependencies.add("implementation", dependencies.platform(jakartaEe.getValue("bom")))

		val toolchains = extensions.getByType<JavaToolchainService>()
		val defaultToolchain = extensions.getByType<JavaPluginExtension>().toolchain
		tasks.withType<Test>().configureEach {
			val cp = project.configurations.getByName("testRuntimeClasspath")
			javaLauncher.set(
				project.provider { usesJakartaValidationProvider(cp) }.flatMap { aligned ->
					if (aligned) {
						toolchains.launcherFor {
							languageVersion.set(JavaLanguageVersion.of(jakartaEe.getValue("testJava")))
						}
					} else {
						toolchains.launcherFor(defaultToolchain)
					}
				}
			)
		}
	}
}

tasks.register("verifyJakartaEeAlignment") {
	group = "verification"
	description = "Verifies every test runtime classpath uses the aligned Jakarta EE validation stack."
	doLast {
		val problems = mutableListOf<String>()
		subprojects.forEach { p ->
			val cp = p.configurations.findByName("testRuntimeClasspath") ?: return@forEach
			cp.incoming.resolutionResult.allDependencies
				.filterIsInstance<UnresolvedDependencyResult>()
				.forEach { problems += "${p.path}: unresolved ${it.attempted.displayName}" }
			val modules = cp.incoming.resolutionResult.allComponents
				.mapNotNull { it.id as? ModuleComponentIdentifier }
			fun version(group: String, name: String) =
				modules.firstOrNull { it.group == group && it.module == name }?.version

			val api = version("jakarta.validation", "jakarta.validation-api")
			val hv = version("org.hibernate.validator", "hibernate-validator")
			val el = version("org.glassfish", "jakarta.el")
			val expressly = version("org.glassfish.expressly", "expressly")
			val usesJakartaHv = hv != null && !hv.startsWith("6.")

			if (api != null && api.startsWith("3.") && api != jakartaEe["validationApi"]) {
				problems += "${p.path}: jakarta.validation-api $api"
			}
			if (usesJakartaHv && hv != jakartaEe["hibernateValidator"]) {
				problems += "${p.path}: hibernate-validator $hv"
			}
			if (el != null && !el.startsWith("3.")) {
				problems += "${p.path}: org.glassfish:jakarta.el $el"
			}
			if (usesJakartaHv && expressly != jakartaEe["expressly"]) {
				problems += "${p.path}: expressly $expressly"
			}
			if (usesJakartaHv) {
				p.tasks.withType<Test>().forEach { test ->
					val actual = test.javaLauncher.get().metadata.languageVersion.asInt()
					if (actual < jakartaEe.getValue("testJava").toInt()) {
						problems += "${p.path}:${test.name} runs on Java $actual"
					}
				}
			}
			println("${p.path}: validation-api=$api hibernate-validator=$hv el=$el expressly=$expressly")
		}
		if (problems.isNotEmpty()) {
			throw GradleException("Jakarta EE alignment problems:\n" + problems.joinToString("\n"))
		}
	}
}
