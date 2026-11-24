# Jakarta EE 10/11 alignment of the validation stack

- Date: 2026-11-25
- Base commit: `8c8015d21` (`main` = `upstream/main`)
- Scope: everything that pulls in `jakarta.validation`, Hibernate Validator (HV) or an EL implementation
- Companion report: `_THE_DEPENDENCIES_PROBLEM.md` (dependency *scopes* of `fixture-monkey-jakarta-validation`)

## TL;DR

- The project does **not** use `jakartaee-bom` or any other BOM. Each version is pinned in `gradle/libs.versions.toml`.
- The published **API** (`jakarta.validation-api` 3.0.2) is already the Jakarta EE 10 level, because EE 10 did not change Validation.
- The **implementations** (HV 7.0.5, GlassFish EL 4.0.2) are Jakarta EE 9 level. The starter publishes them as `api`.
- Full EE 10/11 alignment is **blocked by the Java 8 baseline**, which is the maintainers' choice. HV 8+/EL 5+ need Java 11, and HV 9+/EL 6+ need Java 17.
- Nothing in the code base references an implementation class, so the same Java 8 bytecode runs against the EE 10/11 stack. Two local branches prove it: `jakartaee-bom-10` and `jakartaee-bom-11`. See section 5.

## 1. What is declared today

`gradle/libs.versions.toml`:

| Alias | Coordinate | EE level |
|---|---|---|
| `jakarta-validation-api` | `jakarta.validation:jakarta.validation-api:3.0.2` | 9 / 10 |
| `hibernate-validator7` | `org.hibernate.validator:hibernate-validator:7.0.5.Final` | 9 |
| `jakarta-el4` | `org.glassfish:jakarta.el:4.0.2` | 9 |
| `javax-validation-api`, `hibernate-validator6`, `jakarta-el3` | javax stack | 8 (out of scope) |

Where they are used:

| Project | Dependency | Scope | Published |
|---|---|---|---|
| `fixture-monkey-jakarta-validation` | validation-api 3.0.2 | `api` | yes |
| `fixture-monkey-jakarta-validation` | HV 7 + EL 4 | `testImplementation` | no |
| `fixture-monkey-starter` | HV 7 + EL 4 | **`api`** | **yes**, and through `fixture-monkey-starter-kotlin` |
| `fixture-monkey-tests:kotlin-tests` | HV 7 + EL 4 | `testImplementation` | no |
| `fixture-monkey-tests:doc-examples:java-examples` | HV 7 + EL 4 | `testImplementation` | no |
| `fixture-monkey-javax-validation` | javax 2.0.1 + HV 6.2.1 + EL 3.0.4 | `api` | yes |

No source file in the repository imports `org.hibernate.*`, `jakarta.el.*` or `org.glassfish.*`. The plugin reaches a provider only through `Validation.buildDefaultValidatorFactory()` (`JakartaArbitraryValidator`).

## 2. The constraint: Java 8

`build.gradle.kts:33` sets `toolchain { languageVersion = JavaLanguageVersion.of(8) }` for every subproject. Only `fixture-monkey-jackson3`, `java-17-tests`, `kotlin-17-tests` and `java-concurrent-tests` override it to 17. Tests also **run** on the toolchain JVM.

Class-file major version of the first non-`module-info` class, read from the jars:

| Artifact | EE | Bytecode | Runs on Java 8 |
|---|---|---|---|
| `jakarta.validation-api` 3.0.2 | 9 / 10 | 52 (8) | yes |
| `jakarta.validation-api` 3.1.x | 11 | **52 (8)** | **yes** |
| `org.glassfish:jakarta.el` 4.0.2 | 9 | 52 (8) | yes |
| `jakarta.el-api` 5.0.x, `expressly` 5.0.0 | 10 | 55 (11) | no |
| `jakarta.el-api` 6.0.x, `expressly` 6.0.0 | 11 | 61 (17) | no |
| `hibernate-validator` 7.0.x | 9 | 52 (8) | yes |
| `hibernate-validator` 8.0.x | 10 | 55 (11) | no |
| `hibernate-validator` 9.x | 11 | 61 (17) | no |

The Validation 3.1 **API** (EE 11) is still Java 8 bytecode. Only the implementations need a newer JVM.

## 3. Upstream status of the pinned versions

From the hibernate.org Validator releases page (2026-11-25): 9.2 is the latest stable. 9.1, 8.0, **7.0** and 6.2 are all in *limited support*: "we will not always fix bugs or provide timely releases for this series".

| Version | Released (Maven Central) |
|---|---|
| 7.0.5.Final (pinned) | 2022-08-03 |
| 7.0.6.Final | 2026-10-19 |
| 7.0.7.Final | 2026-10-25 |

Changelog of 7.0.6 / 7.0.7: bug fixes (HV-2256, HV-1978, HV-1939, HV-1932), message improvements, and a CDI API bump (HV-2278). There is no CVE fix.

Latest per EE level, checked on Maven Central:

| | EE 10 | EE 11 |
|---|---|---|
| `jakarta.platform:jakarta.jakartaee-bom` | 10.0.0 | 11.0.0 |
| `jakarta.validation-api` (BOM-managed) | 3.0.2 | 3.1.1 |
| `jakarta.el-api` (BOM-managed) | 5.0.1 | 6.0.1 |
| `hibernate-validator` | 8.0.6.Final | 9.2.0.Final |
| `org.glassfish.expressly:expressly` | 5.0.0 | 6.0.0 |

The BOM coordinate is `jakarta.platform:jakarta.jakartaee-bom`, **not** `jakarta.platform:jakartaee-bom`. The BOM manages only the spec APIs. HV and Expressly are not in it.

## 4. What is possible, and what is not

| Goal | Possible? | Notes |
|---|---|---|
| Users on EE 10/11 use the plugin | yes (shown by section 5) | Only the standard API is used, and 3.0 is binary-compatible with 3.1 |
| Bump the published API to 3.1 | technically yes (Java 8 bytecode) | No benefit: the module uses no 3.1 feature. A library should declare the lowest API it needs |
| Test against EE 10 / EE 11 providers | yes, with a Java 11 / 17 **test** JVM | Compilation stays on Java 8 |
| Starter ships an EE 10/11 provider | **no**, while the baseline is Java 8 | The starter would then need Java 11/17 at runtime |
| Whole project on EE 10/11 | **no** | Needs the baseline raised to 11/17 (maintainer decision) |
| Adopt `jakartaee-bom` in the published build | not recommended | It manages one artifact here (the API), and `api(platform(...))` would push constraints into users' builds |

## 5. Proof: local branches `jakartaee-bom-10` / `jakartaee-bom-11`

Each branch is **one commit on top of `main`**, designed to rebase cleanly on upstream changes:

- a new file, `gradle/jakartaee.gradle.kts` (all the logic)
- one line appended to the root `build.gradle.kts`: `apply(from = "gradle/jakartaee.gradle.kts")`
- the toml and the module build files are **not** touched

What the script does:

1. **Imports the BOM** (`platform(jakarta.jakartaee-bom)`) into `implementation` of every Java subproject, so the spec APIs follow the EE level.
2. **Substitutes the implementations** with `resolutionStrategy.eachDependency`:
   - any `hibernate-validator` that is not 6.x → the EE-level HV
   - `org.glassfish:jakarta.el` that is not 3.x → `org.glassfish.expressly:expressly`

   The javax stack (HV 6.x, EL 3.x) is left alone. The rules match on "not 6.x / not 3.x" instead of an exact version, so an upstream bump of the pinned versions is still caught after a rebase.
3. **Raises the test JVM** only for test tasks whose `testRuntimeClasspath` actually contains a Jakarta provider (Java 11 for EE 10, Java 17 for EE 11). The check runs at resolution time, so a project that upstream adds later is picked up automatically.
4. **Relaxes the JVM-version attribute** of the provider modules (`org.hibernate.validator`, `org.hibernate.accessor`, `org.glassfish.expressly`, `jakarta.el`) with a component metadata rule. HV 9.x depends on `org.hibernate.accessor:hibernate-accessor`, whose Gradle module metadata declares Java 17. Without the rule, Gradle refuses it on the classpath of a Java 8 project (`No matching variant ... compatible with Java 17 and the consumer needed ... Java 8`), even though those tests run on 17. HV 8 publishes no such metadata, so EE 10 does not need the rule. Both branches carry it anyway, so the two scripts differ **only in the version map** at the top.
5. Adds `./gradlew verifyJakartaEeAlignment`, which fails on any of these:
   - an unresolved dependency on a test runtime classpath
   - the old provider still present
   - the wrong API version
   - a test JVM that is too old

   The first check matters: `resolutionResult.allComponents` silently skips unresolved dependencies, so without it the task passed while the EE 11 build was broken.

Resolved stack (`verifyJakartaEeAlignment`):

| Project | Before (main) | `jakartaee-bom-10` | `jakartaee-bom-11` |
|---|---|---|---|
| `fixture-monkey-jakarta-validation` | API 3.0.2, HV 7.0.5, EL 4.0.2, Java 8 | API 3.0.2, HV 8.0.6, Expressly 5.0.0, Java 11 | API 3.1.1, HV 9.2.0, Expressly 6.0.0, Java 17 |
| `fixture-monkey-starter`, `-starter-kotlin` | same as above | same as above | same as above |
| `kotlin-tests`, `doc-examples:java-examples` | same + javax EL 3.0.4 | same + javax EL 3.0.4 | same + javax EL 3.0.4 |
| javax-only projects | HV 6.2.1, EL 3.0.4, Java 8 | unchanged | unchanged |

Regression results. Each run deleted every `build/test-results` first and used `--no-build-cache --rerun-tasks --info`. The JVM column is the forked test executor taken from the `--info` log:

| Project | Tests (F/E/S) | `jakartaee-bom-10` JVM | `jakartaee-bom-11` JVM |
|---|---|---|---|
| `object-farm-api` | 266 (0/0/0) | 8 | 8 |
| `fixture-monkey` | 1899 (0/0/0) | 8 | 8 |
| `fixture-monkey-jakarta-validation` | 39 (0/0/0) | **11** | **17** |
| `fixture-monkey-javax-validation` | 39 (0/0/0) | 8 | 8 |
| `fixture-monkey-starter` | 1 (0/0/0) | **11** | **17** |
| `fixture-monkey-starter-kotlin` | no test sources | – | – |
| `fixture-monkey-tests:java-tests` | 1273 (0/0/0) | 8 | 8 |
| `fixture-monkey-tests:java-17-tests` | 6774 (0/0/0) | 17 (own toolchain) | 17 (own toolchain) |
| `fixture-monkey-tests:kotlin-tests` | 4673 (0/0/0) | **11** | **17** |
| `fixture-monkey-tests:doc-examples:java-examples` | 96 (0/0/0) | **11** | **17** |
| `fixture-monkey-tests:doc-examples:kotlin-examples` | 62 (0/0/0) | 8 | 8 |

Both branches: BUILD SUCCESSFUL, 73 actionable tasks, all executed. The test counts are identical on both branches and match `main`'s project set.

`kotlin-tests` now runs on JDK 11 (EE 10) or 17 (EE 11) instead of 8, because it holds a Jakarta provider. That change of JVM caused no failures.

Keeping the branches up to date:

```bash
git fetch upstream
git rebase upstream/main jakartaee-bom-10
git rebase upstream/main jakartaee-bom-11
./gradlew verifyJakartaEeAlignment   # on each branch
```

Side effect to know about: HV 6.2.x depends on `jakarta.validation:jakarta.validation-api:2.0.2`, which holds the *javax* packages under the jakarta coordinates. In javax-only projects the BOM lifts that artifact to 3.x. The `javax.validation` classes then come only from `javax.validation:validation-api:2.0.1.Final`, which `fixture-monkey-javax-validation` declares anyway. `main` already does the same in `kotlin-tests`, where both stacks are present.

## 6. PR candidates

| # | Change | Risk | Notes |
|---|---|---|---|
| 1 | Bump `hibernate-validator7` 7.0.5 → 7.0.7 | very low | Same line, Java 8. Bug fixes only. Starter users get it directly |
| 2 | Add an EE 11 (and optionally EE 10) compatibility test project on a Java 17 toolchain, following the `java-17-tests` pattern | low | Test-only, nothing published changes. Proves EE 10/11 support |
| 3 | Issue: the starter publishes HV 7 + EL 4 as `api` | needs maintainer decision | Conflicts with users' own provider. Changing it breaks the documented "pre-configured" promise. See `_THE_DEPENDENCIES_PROBLEM.md` |
| 4 | Issue (informational): full EE 10/11 alignment depends on the Java baseline | none | Gives maintainers the data for a future baseline decision |

Not recommended: adding `jakartaee-bom` to the published build (section 4).

## 7. Not verified

- Publishing the branches' artifacts and consuming them from an external Spring Boot 3/4 project.
- Running the javax stack's own tests on a Java 11/17 JVM. Those tests stay on Java 8 because they have no Jakarta provider.
