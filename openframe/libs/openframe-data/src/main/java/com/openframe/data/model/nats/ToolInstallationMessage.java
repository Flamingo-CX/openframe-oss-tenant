package com.openframe.data.model.nats;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ToolInstallationMessage {

    private String toolId;
    private String version;
    private List<String> installationCommandArgs;
    private List<String> runCommandArgs;
    private List<Asset> assets;

    @Getter
    @Setter
    public static class Asset {
        private String id;
        private String localFilename;
        private AssetSource source;
        private String path;
    }

    public enum AssetSource {
        ARTIFACTORY, TOOL_API
    }

}
