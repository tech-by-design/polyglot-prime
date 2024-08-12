package archunit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;

@AnalyzeClasses(packages = "org.techbd")
public class GeneralArchRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.techbd.service.http.hub.prime");

    @Test
    @DisplayName("ArchUnit test to ensure loggers should be private static final")
    public void loggersShouldBePrivateStaticFinal() {
        fields().that().haveRawType(Logger.class)
                .should().bePrivate()
                .andShould().beStatic()
                .andShould().beFinal()
                .because("logging frameworks and libraries follow and recommend this convention")
                .check(classes);
    }

    @Test
    @DisplayName("ArchUnit test to ensure that the classes should not use JavaUtil logging")
    public void classesShouldNotUseJavaUtilLogging() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(classes);
    }

    // TODO: Test fails. Check whether this test is required, and fix.
    // @Test
    // public void classesShouldNotAccessStandardStreamsDefinedByHand() {
    //     noClasses().should(ACCESS_STANDARD_STREAMS).check(classes);
    // }
    @Test
    @DisplayName("ArchUnit test to ensure that the classes should not use Joda time")
    public void classesShouldNotUseJodaTime() {
        NO_CLASSES_SHOULD_USE_JODATIME.check(classes);
    }

    @Test
    @DisplayName("ArchUnit test to ensure that the classes should not use field injection except tests")
    public void classesShouldNotUseFieldInjectionExceptTests() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
    }

    // TODO: test fails at 33 places. Check whether this is useful, and fix accordingly.
    //@Test
    //public void no_accesses_to_upper_package() {
    //    NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES.check(classes);
    //}
}
