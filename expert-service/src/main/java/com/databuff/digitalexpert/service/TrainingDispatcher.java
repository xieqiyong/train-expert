package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.bo.TrainingContext;

public interface TrainingDispatcher {

    void dispatch(TrainingContext context);
}
