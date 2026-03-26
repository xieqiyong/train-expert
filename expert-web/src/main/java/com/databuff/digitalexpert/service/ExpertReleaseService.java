package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import java.util.List;

public interface ExpertReleaseService {

    ExpertReleaseTaskResponse submitReleaseTask(Long expertId);

    ExpertReleaseTaskResponse getTask(Long expertId, String taskId);

    List<ExpertReleaseTaskResponse> listTasks(Long expertId);
}
