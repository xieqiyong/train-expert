package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import java.util.List;

public interface ExpertTrainingService {

    ExpertTrainingTaskResponse submitTrainingTask(Long expertId, CreateExpertTrainingTaskRequest request);

    ExpertTrainingTaskResponse getTask(Long expertId, String taskId);

    List<ExpertTrainingTaskResponse> listTasks(Long expertId);

    void pollTrainingTasks();
}
