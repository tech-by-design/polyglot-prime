package archunit;

import org.junit.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(packages = "org.techbd")
public class CyclicDependencyArchRulesTest {

    @Test
    void checkForCyclicDependencies() {
        checkForCyclicDependenciesInPackages(
                "lib.aide",
                "org.techbd"
        );
    }

    void checkForCyclicDependenciesInPackages(String... packages) {
        for (String pkg : packages) {
            JavaClasses importedClasses = new ClassFileImporter()
                    .importPackages(pkg);

            ArchRule rule = ArchRuleDefinition.noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(".." + pkg + "..");

            rule.check(importedClasses);
        }
    }
}
