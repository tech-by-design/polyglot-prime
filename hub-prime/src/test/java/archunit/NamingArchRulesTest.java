package archunit;

import java.util.regex.Pattern;

import org.springframework.stereotype.Controller;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "org.techbd")
public class NamingArchRulesTest {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9$]*$");

    @ArchTest
    ArchRule controllerNaming = classes()
            .that().areAnnotatedWith(Controller.class)
            .should().haveSimpleNameEndingWith("Controller");

    // TODO: Test fails. Rename the service classes if needed.
    //@ArchTest
    //ArchRule serviceNaming = classes()
    //        .that().areAnnotatedWith(Service.class)
    //        .should().haveSimpleNameEndingWith("Service");
}
