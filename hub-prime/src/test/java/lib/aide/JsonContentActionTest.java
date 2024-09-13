package lib.aide;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonContentActionTest {

    @Test
    public void testRejectContentTransformation() {
        // Sample JSON object
        final Map<String, Object> json = Map.of(
            "severity", "error",
            "location", List.of(
                "Bundle.entry[0].resource/Patient/12345/.meta.lastUpdated",
                "Line[1] Col[490]"),
            "rejectionsList", List.of(), // this is where we're store the rejection, it must exist (empty)
            "rejectionsMap", Map.of()); // this is where we're store the rejection, it must exist (empty)
        
        // Create JsonContentAction with the combined reject rule
        final var jsonContentAction = new JsonContentAction.Builder()
            .withReject(
                ".location[?(@[0] =~ /Bundle\\.entry\\[\\d+\\]\\.resource\\/Patient\\/.*\\.meta\\.lastUpdated$/)]",
                List.of(new JsonContentAction.ApplyKeyValuePairs(
                    JsonPath.compile("$.rejectionsMap"), Map.of(
                        "'injectedKey' + '1'", "'Injected value for ' + rule.description() + ' ' + rule.elaboration.e1"))),
                "test 1", Map.of("e1", "e1Value"))
            .withReject(
                ".location[?(@[0] =~ /Bundle\\.entry\\[\\d+\\]\\.resource\\/Patient\\/.*\\.meta\\.lastUpdated$/)]",
                List.of(new JsonContentAction.AppendObject(
                    JsonPath.compile("$.rejectionsList"), Map.of(
                        "'injectedKey' + '1'", "'Injected value for ' + rule.description() + ' ' + rule.elaboration.e1"))),
                "test 2", Map.of("e1", "e1Value"))
            .build();

        // Execute the action on the sample JSON
        final var result = jsonContentAction.execute(json);

        // Assert that a rejection occurred and the transformation was applied
        assertThat(result.rejections()).isNotEmpty();
        assertThat(result.rejections().get(0).originalJson()).isEqualTo(json);
        assertThat(result.rejections().get(1).originalJson()).isEqualTo(json);

        // Assertions for the first rejection (rejectionsMap modification)
        final var appliedJson = result.rejections().get(0).transformed(new HashMap<>(json), Map.of());
        assertThat(appliedJson).isEqualTo(Map.of(
            "severity", "error",
            "location", List.of(
                "Bundle.entry[0].resource/Patient/12345/.meta.lastUpdated",
                "Line[1] Col[490]"
            ),
            "rejectionsList", List.of(), // unchanged
            "rejectionsMap", Map.of(
                "injectedKey1", "Injected value for test 1 e1Value" // injected value added
            )
        ));

        // Assertions for the second rejection (rejectionsList append)
        final var appendedJson = result.rejections().get(1).transformed(new HashMap<>(json), Map.of());
        assertThat(appendedJson).isEqualTo(Map.of(
            "severity", "error",
            "location", List.of(
                "Bundle.entry[0].resource/Patient/12345/.meta.lastUpdated",
                "Line[1] Col[490]"
            ),
            "rejectionsList", List.of(
                Map.of("injectedKey1", "Injected value for test 2 e1Value") // object appended
            ),
            "rejectionsMap", Map.of() // unchanged
        ));

        // Assert that severity is still "error"
        assertThat(appliedJson.get("severity")).isEqualTo("error");
        assertThat(appendedJson.get("severity")).isEqualTo("error");
    }
}
