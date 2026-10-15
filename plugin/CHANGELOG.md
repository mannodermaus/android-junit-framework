Change Log
==========

## Unreleased

## 2.0.2 (2026-10-14)
- Internal: Update RunnerBuilder reference to `AndroidJUnitFrameworkBuilder` and away from the deprecated value
- CI: Migrate from Firebase Test Lab to emulator.wtf

## 2.0.1 (2026-01-14)
- This release is identical to 2.0.0 in regard to the plugin, as it only resolves an issue with packaging of the associated instrumentation libraries

## 2.0.0 (2026-01-01)
- Update to Kotlin 2.3
- Internal: Replace deprecated `OutputDirectoryProvider` with its correct replacement
- Support instrumentation with JUnit 5 and 6 (the plugin will choose the correct runtime accordingly)
- Introduce `de.mannodermaus.android-junit` as the new plugin ID
- New: Control behavior of test execution on unsupported devices via `instrumentationTests.behaviorForUnsupportedDevices`
  - "Fail": Throw an exception and fail test execution (**this is the new default**)
  - "Skip": Skip tests and mark them as ignored (**this is the old behavior**)

## 1.14.0.0 (2025-10-10)
- JUnit 5.14.0

## 1.13.4.0 (2025-09-07)
- First considerations for Android Gradle Plugin 9.x
  - Remove all usages of the old plugin & variant DSL
  - **This means that Jacoco integration is effectively deprecated until further notice for users of AGP 9.0.0 and the new DSL**
- JUnit 5.13.4

## 1.13.3.0 (2025-09-07)
- JUnit 5.13.3

## 1.13.2.0 (2025-09-06)
- JUnit 5.13.2

## 1.13.1.0 (2025-06-29)
- JUnit 5.13.1

## 1.13.0.0 (2025-06-11)
- JUnit 5.13.0
- Update to Kotlin 2.x
- Raise minimum supported versions for AGP and Gradle to 8.2.x and 8.2, respectively

## 1.12.2.0 (2025-05-18)
- JUnit 5.12.2

## 1.12.1.0 (2025-05-18)
- JUnit 5.12.1

## 1.12.0.0 (2025-03-01)
- JUnit 5.12.0
- Add dependency on JUnit Platform Launcher to runtime classpath, accommodating an upstream change

## 1.11.3.0 (2024-12-23)
- JUnit 5.11.3

## 1.11.2.0 (2024-10-05)
- JUnit 5.11.2

## 1.11.1.0 (2024-10-05)
- JUnit 5.11.1

## 1.11.0.0 (2024-08-14)
- JUnit 5.11

## 1.10.3.0 (2024-08-14)
- JUnit 5.10.3
- Updates to the `jacocoOptions` DSL
  - Change the return type of each report type to match Jacoco expectations (html -> Directory; csv & xml -> File)
  - Turn off generation of csv & xml reports by default, matching Jacoco default configuration
- Fix: Use the correct version of the instrumentation libraries with the plugin (#345)

## 1.10.2.0 (2024-07-25)
- JUnit 5.10.2
- Raise minimum supported versions for AGP and Gradle to 8.0.x and 8.0, respectively
- Allow overriding the version of the instrumentation libraries applied with the plugin
- Update Jacoco & instrumentation test DSLs of the plugin to use Gradle Providers for their input parameters (e.g. `instrumentationTests.enabled.set(true)` instead of `instrumentationTests.enabled = true`)
- Removed deprecated `integrityCheckEnabled` flag from the plugin DSL's instrumentation test options
- Allow opt-in usage of extension library via the plugin's DSL
- Allow autoconfiguration of instrumentation libraries if Compose or JUnit 5 are found among the test/androidTest dependency lists 
- Decouple discovery of instrumentation tests from Jupiter, allowing non-Jupiter test engines to be discovered as well
- Update lifecycle of instrumentation runner params to only be set once, instead of once per test
- Properly reported disabled dynamic tests to Android instrumentation
- Use new Variant API to register generated resource folder for instrumentation filters file

## 1.10.0.0 (2023-11-05)
- JUnit 5.10.0
- Fix binary-incompatible API change between Gradle 7&8 for output location of Jacoco reports (#302)

## 1.9.3.0 (2023-04-29)
- JUnit 5.9.3

## 1.9.2.0 (2023-04-29)
- JUnit 5.9.2

## 1.9.1.0 (2023-04-29)
- JUnit 5.9.1

## 1.9.0.0 (2023-04-29)
- JUnit 5.9.0
- Add support for Android Gradle Plugin 8.x.y
- Raise minimum supported versions for AGP and Gradle to 7.0.x and 7.0, respectively 
- Refactor implementation to mostly use new Variant API from Android Gradle Plugin
- Remove integrity check and deprecate `integrityCheckEnabled` flag for instrumentation tests (they are auto-configured if junit-jupiter-api is found on the `androidTest` classpath)
- Use the correct Kotlin source directory set on AGP 7 (@Goooler, #279)
- Recommend new plugin DSL for configuration over legacy DSL
- Work around breaking binary change for PackagingOptions in AGP 8.x

## 1.8.2.1 (2022-07-02)
- Support Gradle configuration cache (#265)
- Replace usage of deprecated `destinationDir` compiler task property to support Kotlin 1.7.0 and beyond (#274) 

## 1.8.2.0 (2021-12-19)
- JUnit 5.8.2

## 1.8.1.0 (2021-12-19)
- JUnit 5.8.1
- Replaced deprecated method `Report.setEnabled` with `Report.required.set` to remove deprecation warnings in Gradle 7+ (CC @gmarques33, #260)

## 1.8.0.0 (2021-09-17)

#### Added
- JUnit 5.8.0
- New Plugin Marker artifact facilitating usage of the plugin through the `plugins {}` DSL

#### Changed
- The plugin no longer requires users to apply an Android plugin first - ordering can be arbitrary

#### Removed
- Support for Android Gradle Plugins 3.x
- Support for the deprecated com.android.feature plugin, which was removed in Android Gradle Plugin 4.x
- The shorthand version for applying the plugin through the `android-junit5` ID has been removed. Going forward, please apply the Android JUnit 5 plugin through the long form: `de.mannodermaus.android-junit5` 

## 1.7.1.1 (2021-02-11)

This release is identical to 1.7.1.0, but fixes an incorrect entry in the POM file, making the previous version unusable.

## 1.7.1.0 (2021-02-10)

#### Added
- JUnit 5.7.1
#### Changed
- Move location of "junitPlatform" extension to Project
- Migrate Jacoco task integration to new lazy Gradle API
- Migrate instrumentation test integration task to new lazy TaskProvider API
#### Fixed
#### Removed
- Remove workaround for KotlinJvmOptions (not needed anymore)

## 1.7.0.0 (2020-12-18)

#### Changed
- Use task avoidance APIs to prevent eager instantiation of Gradle tasks

#### Added
- JUnit 5.7.0

#### Changed
- Automatically exclude JUnit 5 metadata files from causing conflicts during packaging (#233)

#### Fixed
- Become more lenient when test tasks are absent (#226)
- Provide configuration parameters to instrumentation tests, allowing e.g. Extension Auto-Detection to work (#229)

## 1.6.2.0 (2020-05-03)

#### Added
- JUnit 5.6.2

## 1.6.1.0 (2020-05-03)

#### Added
- Compatibility with Android Gradle Plugin 4.1.x
- JUnit 5.6.1
#### Changed
- Restricted visibility of internal APIs to prevent them from leaking into consumer code

## 1.6.0.0 (2020-02-27)

#### Added
- JUnit 5.6.0
- Compatibility with Android Gradle Plugin 4.x versions
- Compatibility with Gradle 6
#### Changed
- Raised minimum supported version to Gradle 6.1.1

## 1.5.2.0 (2019-10-09)

- JUnit 5.5.2

## 1.5.1.0 (2019-08-07)

- JUnit 5.5.1
- Fix compatibility with latest iterations of AGP 3.6.x (#180)

## 1.5.0.0 (2019-07-14)

- JUnit 5.5.0

#### Added
- Integrity checks for users including instrumentation tests into their project. An error will be thrown on incomplete setup of the instrumentation test environment. To disable the check, use the junitPlatform.instrumentationTests.integrityCheckEnabled API
- Added Gradle tasks to generate resource files for filter configurations in instrumentation tests (support @Tag filters in instrumentation tests)
#### Changed
- Use Dokka for platform-agnostic generation of documentation files
#### Removed
- Marked junitPlatform.instrumentationTests.enabled and junitPlatform.instrumentationTests.version as deprecated, as they don't do anything anymore - they are scheduled for removal in 1.6.0.0

## 1.4.2.1 (2019-06-12)

- Fix compatibility with the latest versions of the Android Gradle Plugin

## 1.4.2.0 (2019-04-08)

- JUnit 5.4.2

## 1.4.1.0 (2019-04-07)

- JUnit 5.4.1

## 1.4.0.0 (2019-03-03)

- JUnit 5.4.0

## 1.3.2.0 (2018-12-31)

- JUnit 5.3.2

#### Added
- Support for projects running Gradle 5.x (#131, #133)
#### Changed
- Converted all helper classes for unit tests to Kotlin & inverted the dependency between Kotlin and Groovy. Now, the only Groovy thing left in that particular module are some unit tests (#136)
- Moved to a type-safe way to declare libraries & versions in Kotlin DSL, instead of relying on the extra API in Gradle (#137)
#### Fixed
- Sample project correctly sets up its source folders (#135; thanks, @pardom!)
#### Removed
- The plugin no longer tries to configure modules that use the com.android.test plugin. These modules only use instrumentation tests, and do not expose any unit test tasks. If you want JUnit 5 in these modules, use the instrumentation test libraries instead (#134)

## 1.3.1.1 (2018-11-04)

- Improve usability of the plugin for users of the Kotlin DSL for Gradle

## 1.3.1.0 (2018-10-28)

- JUnit 5.3.1
- Add support for `com.android.dynamic-feature` plugin (#115)
- Fix DSL issue in projects with multi-dimensional product flavors (#110)
- Add support for AGP 3.3.0 alphas

## 1.3.0.0 (2018-10-28)

- JUnit 5.3.0

## 1.2.0.0 (2018-10-11)

- JUnit 5.2.0
- Update versioning scheme: `JUnit 5.x.y` == `Plugin 1.x.y.0`
- Introduce new configuration DSL
- Remove dependency handlers
- Remove the need to create custom tasks - instead hook into the existing test tasks
- Remove most of the customized config DSLs and move towards a more streamlined filters API

## 1.0.33 (2018-10-12)

- Last release of the pre-3.2.0 line for backwards compatibility

## 1.0.32 (2018-05-03)

- Enhance compatibility with Android Gradle Plugin 3.2.0 alpha versions
- Remove dependency on Java-based JUnit 5 Gradle plugin
- Add warning on incorrect plugin application order with Kotlin
- JaCoCo now configures the source directories
- Add DSL for JUnit 5 unit tests related to default values & Android resources

## 1.0.31 (2018-03-03)

- Raise minimum required Gradle version to 4.3
- Add several DSL functions for configuration
- Remove deprecated `jacoco` container

## 1.0.30 (2018-01-22)

- JUnit 5.0.3
- Rename `jacoco` DSL to `jacocoOptions`

## 1.0.22 (2017-12-04)

#### Added

- Add initial support for instrumentation tests
- Add DSL & dependency handler for instrumentation tests

#### Fixed

- Unable to find method during gradle sync (#34)
- Running tests on multiple flavors (#36)
- Move junitPlatform configuration into android namespace enhancement (#37)
- Delete duplicated Jacoco config, obey the default (#38)

## 1.0.21 (2017-11-22)

- No notes

## 1.0.20 (2017-11-20)

- JUnit 5.0.2
- Replace the Copy task for Kotlin-based unit tests
- Rewrite plugin to almost-100% Kotlin

## 1.0.12 (2017-11-02)

#### Fixed

- Product Flavor tasks don't find their (Kotlin) tests properly (#25)

## 1.0.11 (2017-10-21)

- No notes

## 1.0.10 (2017-10-10)

- JUnit 5.0.1
- New versioning scheme: `JUnit 5.x.y` == `Plugin 1.x.y0`
- Add JaCoCo support for plugin structure
- Add dependency handler for embedded runtime artifact

## 1.0.0 (2017-09-11)

- JUnit 5.0.0
- Removal of deprecated dependency handlers

## 1.0.0-RC3-rev1 (2017-09-09)

- Rename dependency handlers provided by the plugin: `junitJupiter()` to `junit5()` & `junitParams()` to `junit5Params()`
- Introduce `android-junit5-embedded-runtime` artifact to bridge incompatibilities with IDE runtimes

## 1.0.0-RC3 (2017-08-29)

- Support `configurationParameters` in DSL

## 1.0.0-RC2 (2017-08-28)

- Dependency update

## 1.0.0-M6 (2017-07-19)

- Dependency update

## 1.0.0-M5 (2017-07-08)

- Improve backwards compatibility between Android Gradle Plugin & IDE runtimes
- Introduce snapshot builds

## 1.0.0-M4-rev3 (2017-06-11)

- Improve classpath compatibility & inconsistent execution behavior between IDE and command-line builds

## 1.0.0-M4-rev2 (2017-06-04)

- Improve compatibility with Android Gradle Plugin 3.0.0
- Add junitParams() dependency handler for parameterized tests
- Hopefully mitigate classpath-related issues when running unit tests from the IDE

## 1.0.0-M4-rev1 (2017-05-19)

- Added compatibility with Android Gradle Plugin 3.0.0 versions
- Included JUnit 4 by default, which allows AS builds w/o “class not found” errors
- Deprecated junitVintage() dependency handler, issuing a warning if you're still using it
- Both Jupiter & Vintage TestEngines are included on the runtime classpath by default

## 1.0.0-M4 (2017-05-03)

- Dependency update

## 1.0.0-M2 (2016-09-30)

- Initial release
