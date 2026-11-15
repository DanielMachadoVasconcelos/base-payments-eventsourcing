package lab.payments.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentArchitectureTest {
    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("lab.payments");

    @Test
    @DisplayName("Should keep the domain independent When inspecting application dependencies")
    void shouldKeepTheDomainIndependentWhenInspectingApplicationDependencies() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..application..",
                        "..entry..",
                        "..infrastructure..",
                        "org.springframework..",
                        "software.amazon.awssdk..")
                .check(APPLICATION_CLASSES);
    }

    @Test
    @DisplayName("Should use ports When application code accesses external capabilities")
    void shouldUsePortsWhenApplicationCodeAccessesExternalCapabilities() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..entry..", "..infrastructure..", "software.amazon.awssdk..")
                .check(APPLICATION_CLASSES);
    }

    @Test
    @DisplayName("Should avoid persistence dependencies When entry code handles requests")
    void shouldAvoidPersistenceDependenciesWhenEntryCodeHandlesRequests() {
        noClasses()
                .that()
                .resideInAPackage("..entry..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "software.amazon.awssdk..")
                .check(APPLICATION_CLASSES);
    }

    @Test
    @DisplayName("Should avoid HTTP entry dependencies When persistence code stores data")
    void shouldAvoidHTTPEntryDependenciesWhenPersistenceCodeStoresData() {
        noClasses()
                .that()
                .resideInAPackage("..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..entry..")
                .check(APPLICATION_CLASSES);
    }
}
