package com.databuff.digitalexpert.service.prompt;

public interface TrainingPromptStrategy {

    boolean supports(TrainingPromptContext context);

    String buildPrompt(TrainingPromptContext context);
}
