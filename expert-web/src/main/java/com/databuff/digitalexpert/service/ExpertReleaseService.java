package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;

public interface ExpertReleaseService {

    ExpertReleaseTaskResponse submitReleaseTask(Long expertId);

    ExpertReleaseTaskResponse getTask(Long expertId, String taskId);
}
