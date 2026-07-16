package cc.orbexa.hhy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {
    private final JavaClasses classes=new ClassFileImporter().importPackages("cc.orbexa.hhy");
    @Test void domainModulesDoNotReachAcrossRepositories() {
        ArchRule rule=noClasses().that().resideInAnyPackage("..access..","..content..","..commerce..","..incentive..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository").andShould().resideOutsideOfPackage("..shared..");
        rule.allowEmptyShould(true).check(classes);
    }
    @Test void controllersOnlyLiveInBootOrPlatform() {
        noClasses().that().resideInAnyPackage("..access..","..content..","..commerce..","..incentive..")
                .should().haveSimpleNameEndingWith("Controller").allowEmptyShould(true).check(classes);
    }
}
