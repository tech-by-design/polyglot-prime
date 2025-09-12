package org.techbd.replay;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ccda")
public class CcdaReplayController {

    private final CcdaReplayService replayService;
    private final CcdaMirthRawReplayService mirthRawReplayService;

    public CcdaReplayController(CcdaReplayService replayService, CcdaMirthRawReplayService mirthRawReplayService) {
        this.replayService = replayService;
        this.mirthRawReplayService = mirthRawReplayService;
    }

    /**
     * Replay CCDA Bundles asynchronously for the given list of Bundle IDs.
     *
     * @param bundleIds list of bundle IDs to replay
     * @return interim response with acknowledgement
     */
    @PostMapping("/replay")
    public Object replayBundles(
            @RequestBody List<String> bundleIds,
            @RequestParam(name = "trialRun", required = false, defaultValue = "true") boolean trialRun,
            @RequestParam(name = "sendToNyec", required = false, defaultValue = "false") boolean sendToNyec,
            @RequestParam(name = "immediate", required = false, defaultValue = "false") boolean immediate,
            @RequestParam(name = "useMirthRaw", required = false, defaultValue = "false") boolean useMirthRaw) {
        {
            final var replayMasterInteractionId = UUID.randomUUID().toString();
            if (useMirthRaw) {
                return mirthRawReplayService.replayBundlesAsync(bundleIds, replayMasterInteractionId, trialRun, immediate);
            }
            return replayService.replayBundlesAsync(bundleIds, replayMasterInteractionId, trialRun,
                    immediate);
        }

    }
}
