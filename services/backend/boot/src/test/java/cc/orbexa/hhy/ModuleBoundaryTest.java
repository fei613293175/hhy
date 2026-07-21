package cc.orbexa.hhy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import cc.orbexa.hhy.platform.release.PublishedAppReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryTest {
    private static final String PROJECT_REPOSITORY_PATTERN =
            "cc\\.orbexa\\.hhy\\.(?!shared(?:\\.|$)).*Repository$";

    private final JavaClasses classes=new ClassFileImporter().importPackages("cc.orbexa.hhy");

    @Test void domainModulesDoNotReachAcrossRepositories() {
        ArchRule rule=noClasses().that().resideInAnyPackage("..access..","..content..","..commerce..","..incentive..")
                .should().dependOnClassesThat().haveNameMatching(PROJECT_REPOSITORY_PATTERN);
        rule.allowEmptyShould(true).check(classes);
    }

    @Test void repositoryTargetPatternExcludesFrameworkAnnotationsButKeepsProjectRepositories() {
        assertFalse(Repository.class.getName().matches(PROJECT_REPOSITORY_PATTERN));
        assertTrue(PublishedAppReleaseRepository.class.getName().matches(PROJECT_REPOSITORY_PATTERN));
    }

    @Test void controllersOnlyLiveInBootOrPlatform() {
        noClasses().that().resideInAnyPackage("..access..","..content..","..commerce..","..incentive..")
                .should().haveSimpleNameEndingWith("Controller").allowEmptyShould(true).check(classes);
    }
}
