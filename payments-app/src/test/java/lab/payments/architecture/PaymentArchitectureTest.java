package lab.payments.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "lab.payments", importOptions = ImportOption.DoNotIncludeTests.class)
class PaymentArchitectureTest {
    @ArchTest
    static final ArchRule domainOwnsItsDecisions =
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
                            "software.amazon.awssdk..");

    @ArchTest
    static final ArchRule applicationUsesPorts =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..entry..", "..infrastructure..", "software.amazon.awssdk..");

    @ArchTest
    static final ArchRule entryDoesNotAccessPersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..entry..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..", "software.amazon.awssdk..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnHttpEntry =
            noClasses()
                    .that()
                    .resideInAPackage("..infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..entry..");
}
