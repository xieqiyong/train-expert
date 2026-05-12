package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.bo.TrainingContext;

public interface TrainingPostProcessor {

    default boolean supports(TrainingContext context) {
        return true;
    }

    void postProcess(TrainingContext context);
}
