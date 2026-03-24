//file:noinspection unused
package io.github.rehody.abplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.TestingExtension

class IntegrationTestConventionPlugin implements Plugin<Project> {

    private static final String INTEGRATION_TEST_SUITE_NAME = 'integrationTest'
    private static final String INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION = 'integrationTestImplementation'
    private static final String INTEGRATION_TEST_COMPILE_ONLY_CONFIGURATION = 'integrationTestCompileOnly'
    private static final String INTEGRATION_TEST_RUNTIME_ONLY_CONFIGURATION = 'integrationTestRuntimeOnly'
    private static final String INTEGRATION_TEST_ANNOTATION_PROCESSOR_CONFIGURATION = 'integrationTestAnnotationProcessor'

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('java') {
            def testing = project.extensions.getByType(TestingExtension)

            def integrationTestSuite = testing.suites.names.contains(INTEGRATION_TEST_SUITE_NAME)
                ? testing.suites.named(INTEGRATION_TEST_SUITE_NAME, JvmTestSuite)
                : testing.suites.register(INTEGRATION_TEST_SUITE_NAME, JvmTestSuite)

            integrationTestSuite.configure { JvmTestSuite suite ->
                suite.useJUnitJupiter()

                suite.sources {
                    java.srcDir('src/integrationTest/java')
                    resources.srcDir('src/integrationTest/resources')
                }

                suite.dependencies {
                    implementation(project)
                }
            }

            extendConfiguration(
                project,
                INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION,
                JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
            )

            extendConfiguration(
                project,
                INTEGRATION_TEST_COMPILE_ONLY_CONFIGURATION,
                JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME
            )

            extendConfiguration(project, INTEGRATION_TEST_RUNTIME_ONLY_CONFIGURATION, JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)

            extendConfiguration(
                project,
                INTEGRATION_TEST_ANNOTATION_PROCESSOR_CONFIGURATION,
                JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME
            )

            def integrationTestTask = project.tasks.named(INTEGRATION_TEST_SUITE_NAME, Test) { Test t ->
                t.description = 'Runs the integration tests.'
                t.group = LifecycleBasePlugin.VERIFICATION_GROUP
                t.shouldRunAfter(project.tasks.named(JavaPlugin.TEST_TASK_NAME))
            }

            project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
                dependsOn(integrationTestTask)
            }
        }
    }

    private static void extendConfiguration(Project project, String configurationName, String parentConfigurationName) {
        project.configurations.named(configurationName) { configuration ->
            configuration.extendsFrom(project.configurations.named(parentConfigurationName).get())
        }
    }
}
