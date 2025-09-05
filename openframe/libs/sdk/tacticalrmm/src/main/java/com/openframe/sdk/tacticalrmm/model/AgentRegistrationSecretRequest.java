package com.openframe.sdk.tacticalrmm.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AgentRegistrationSecretRequest {

    @JsonProperty("installMethod")
    private String installMethod;

    @JsonProperty("client")
    private Integer client;

    @JsonProperty("site")
    private Integer site;

    @JsonProperty("expires")
    private Integer expires;

    @JsonProperty("agenttype")
    private String agentType;

    @JsonProperty("power")
    private Integer power;

    @JsonProperty("rdp")
    private Integer rdp;

    @JsonProperty("ping")
    private Integer ping;

    @JsonProperty("goarch")
    private String goarch;

    @JsonProperty("api")
    private String api;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("plat")
    private String platform;

    public String getInstallMethod() {
        return this.installMethod;
    }

    public Integer getClient() {
        return this.client;
    }

    public Integer getSite() {
        return this.site;
    }

    public Integer getExpires() {
        return this.expires;
    }

    public String getAgentType() {
        return this.agentType;
    }

    public Integer getPower() {
        return this.power;
    }

    public Integer getRdp() {
        return this.rdp;
    }

    public Integer getPing() {
        return this.ping;
    }

    public String getGoarch() {
        return this.goarch;
    }

    public String getApi() {
        return this.api;
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getPlatform() {
        return this.platform;
    }

    @JsonProperty("installMethod")
    public void setInstallMethod(String installMethod) {
        this.installMethod = installMethod;
    }

    @JsonProperty("client")
    public void setClient(Integer client) {
        this.client = client;
    }

    @JsonProperty("site")
    public void setSite(Integer site) {
        this.site = site;
    }

    @JsonProperty("expires")
    public void setExpires(Integer expires) {
        this.expires = expires;
    }

    @JsonProperty("agenttype")
    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    @JsonProperty("power")
    public void setPower(Integer power) {
        this.power = power;
    }

    @JsonProperty("rdp")
    public void setRdp(Integer rdp) {
        this.rdp = rdp;
    }

    @JsonProperty("ping")
    public void setPing(Integer ping) {
        this.ping = ping;
    }

    @JsonProperty("goarch")
    public void setGoarch(String goarch) {
        this.goarch = goarch;
    }

    @JsonProperty("api")
    public void setApi(String api) {
        this.api = api;
    }

    @JsonProperty("fileName")
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @JsonProperty("plat")
    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String toString() {
        return "AgentRegistrationSecretRequest(installMethod=" + this.getInstallMethod() + ", client=" + this.getClient() + ", site=" + this.getSite() + ", expires=" + this.getExpires() + ", agentType=" + this.getAgentType() + ", power=" + this.getPower() + ", rdp=" + this.getRdp() + ", ping=" + this.getPing() + ", goarch=" + this.getGoarch() + ", api=" + this.getApi() + ", fileName=" + this.getFileName() + ", platform=" + this.getPlatform() + ")";
    }
}
