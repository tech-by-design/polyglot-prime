package archunit;

import org.junit.jupiter.api.DisplayName;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

@AnalyzeClasses(packages = "org.techbd")
public class CyclicDependencyArchRulesTest {

    @ArchTest
    @DisplayName("ArchUnit test to ensure no cyclic dependencies between packages")
    public static void noCyclicDependenciesBetweenPackages(JavaClasses importedClasses) {
        SlicesRuleDefinition.slices()
                .matching("org.techbd.(*)..")
                .should().beFreeOfCycles()
                .check(importedClasses);
    }
}
