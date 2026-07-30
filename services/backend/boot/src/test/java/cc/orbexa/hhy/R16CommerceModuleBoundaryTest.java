package cc.orbexa.hhy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class R16CommerceModuleBoundaryTest {
    private final JavaClasses classes =
            new ClassFileImporter().importPackages("cc.orbexa.hhy");

    @Test
    void commerceDoesNotDependOnAccessOrBoot() {
        noClasses().that().resideInAPackage("cc.orbexa.hhy.commerce..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "cc.orbexa.hhy.access..", "cc.orbexa.hhy.boot..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void r16CommerceControllersStayInBoot() {
        noClasses().that().resideInAPackage("cc.orbexa.hhy.commerce..")
                .should().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true)
                .check(classes);
    }
}
