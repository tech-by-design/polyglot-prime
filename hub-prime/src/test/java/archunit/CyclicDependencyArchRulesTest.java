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
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("org.techbd.service.http.hub.prime.ux");

        ArchRule rule = ArchRuleDefinition.noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..org.techbd.service.http.hub.prime.ux..");

        rule.check(importedClasses);
    }
}
