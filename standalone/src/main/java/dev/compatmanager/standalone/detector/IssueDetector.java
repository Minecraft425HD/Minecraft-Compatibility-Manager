package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.List;

public interface IssueDetector {
    List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx);
}
