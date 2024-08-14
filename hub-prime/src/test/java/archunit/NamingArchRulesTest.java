package archunit;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
    
    @Test
    @DisplayName("ArchUnit test to ensure that the methods should follow camelCase convention")
    public void methodsShouldFollowCamelCaseConvention() {
        ArchRule rule = classes()
                .should(new ArchCondition<JavaClass>("have methods following camelCase convention") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (javaClass.isEnum()) {
                            return; // Skip enum classes
                        }
                        for (JavaMethod method : javaClass.getMethods()) {
                            String methodName = method.getName();
                            if (!CAMEL_CASE_PATTERN.matcher(methodName).matches()) {
                                String message = String.format("Method %s in class %s does not follow camelCase convention",
                                        methodName, javaClass.getName());
                                events.add(SimpleConditionEvent.violated(method, message));
                            }
                        }
                    }
                });

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("org.techbd"));
    }

    @Test
    @DisplayName("ArchUnit test to ensure that the fields should follow camelCase convention")
    public void fieldsShouldFollowCamelCaseConvention() {
        ArchRule rule = classes()
                .should(new ArchCondition<JavaClass>("have fields following camelCase convention") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (javaClass.isEnum()) {
                            return; // Skip enum classes
                        }
                        for (JavaField field : javaClass.getFields()) {
                            if (field.getModifiers().contains(JavaModifier.STATIC)
                                    && field.getModifiers().contains(JavaModifier.FINAL)) {
                                continue; // Skip static final fields
                            }
                            String fieldName = field.getName();
                            if (!CAMEL_CASE_PATTERN.matcher(fieldName).matches()) {
                                String message = String.format("Field %s in class %s does not follow camelCase convention",
                                        fieldName, javaClass.getName());
                                events.add(SimpleConditionEvent.violated(field, message));
                            }
                        }
                    }
                });

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("org.techbd"));
    }

}
