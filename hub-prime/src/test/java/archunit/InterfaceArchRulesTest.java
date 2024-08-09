package archunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.techbd")
public class InterfaceArchRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("org.techbd");

    @Test
    @DisplayName("ArchUnit test to ensure that the interfaces should not have names ending with the word - Interface")
    public void interfacesShouldNotHaveNamesEndingWithTheWordInterface() {
        noClasses().that().areInterfaces().should().haveNameMatching(".*Interface").check(classes);
    }

    @Test
    @DisplayName("ArchUnit test to ensure that the interfaces should not have simple class names containing the word - Interface")
    public void interfacesShouldNotHaveSimpleClassNamesContainingTheWordInterface() {
        noClasses().that().areInterfaces().should().haveSimpleNameContaining("Interface").check(classes);
    }

    // TODO: Test fails. Check whether this test is required, and fix.
    // @Test
    // public void interfacesShouldHaveSimpleClassNamesEndingwith() {
    //     classes().that().areInterfaces().should().haveSimpleNameEndingWith("able").check(classes);
    // }
}
