package com.databuff.digitalexpert.dao.dto.opencode;

import com.xie.opencode.core.model.OpenCodeServerStatus;
import com.xie.opencode.core.model.ProviderCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatBootstrapView {

    private OpenCodeServerStatus server;

    private ProviderCatalog providers;

    private List<ChatConversationSummaryView> conversations = new ArrayList<ChatConversationSummaryView>();

    private String selectedProviderId;

    private String selectedModelId;

    private String defaultAgent;

    private String username;

    private Map<String, String> providerApis = new LinkedHashMap<String, String>();

    public OpenCodeServerStatus getServer() {
        return this.server;
    }

    public void setServer(OpenCodeServerStatus server) {
        this.server = server;
    }

    public ProviderCatalog getProviders() {
        return this.providers;
    }

    public void setProviders(ProviderCatalog providers) {
        this.providers = providers;
    }

    public List<ChatConversationSummaryView> getConversations() {
        return this.conversations;
    }

    public void setConversations(List<ChatConversationSummaryView> conversations) {
        this.conversations = conversations;
    }

    public String getSelectedProviderId() {
        return this.selectedProviderId;
    }

    public void setSelectedProviderId(String selectedProviderId) {
        this.selectedProviderId = selectedProviderId;
    }

    public String getSelectedModelId() {
        return this.selectedModelId;
    }

    public void setSelectedModelId(String selectedModelId) {
        this.selectedModelId = selectedModelId;
    }

    public String getDefaultAgent() {
        return this.defaultAgent;
    }

    public void setDefaultAgent(String defaultAgent) {
        this.defaultAgent = defaultAgent;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Map<String, String> getProviderApis() {
        return this.providerApis;
    }

    public void setProviderApis(Map<String, String> providerApis) {
        this.providerApis = providerApis;
    }
}
