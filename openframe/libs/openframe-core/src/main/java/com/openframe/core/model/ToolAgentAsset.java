package com.openframe.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToolAgentAsset {
    
    private String id;
    private String localFilename;
    private ToolAgentAssetSource source;
    private String path;
    
}