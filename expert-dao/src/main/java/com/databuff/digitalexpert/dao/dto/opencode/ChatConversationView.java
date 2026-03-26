package com.databuff.digitalexpert.dao.dto.opencode;

import java.util.ArrayList;
import java.util.List;

public class ChatConversationView {

    private ChatConversationSummaryView conversation;

    private List<ChatMessageView> messages = new ArrayList<ChatMessageView>();

    public ChatConversationSummaryView getConversation() {
        return this.conversation;
    }

    public void setConversation(ChatConversationSummaryView conversation) {
        this.conversation = conversation;
    }

    public List<ChatMessageView> getMessages() {
        return this.messages;
    }

    public void setMessages(List<ChatMessageView> messages) {
        this.messages = messages;
    }
}
