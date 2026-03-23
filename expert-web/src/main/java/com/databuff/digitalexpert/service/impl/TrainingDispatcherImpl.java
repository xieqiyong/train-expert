package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.dao.bo.TrainingContext;
import com.databuff.digitalexpert.service.TrainingDispatcher;
import com.databuff.digitalexpert.service.TrainingPostProcessor;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrainingDispatcherImpl implements TrainingDispatcher {

    @Autowired(required = false)
    private List<TrainingPostProcessor> processors = List.of();

    @Override
    public void dispatch(TrainingContext context) {
        if (context == null || processors == null || processors.isEmpty()) {
            return;
        }
        for (TrainingPostProcessor processor : processors) {
            if (processor == null) {
                continue;
            }
            try {
                if (!processor.supports(context)) {
                    continue;
                }
                processor.postProcess(context);
            } catch (Exception ex) {
                log.error("执行训练后置处理失败, processor={}, taskId={}, status={}",
                        processor.getClass().getSimpleName(),
                        context.trainingTask() == null ? null : context.trainingTask().getTaskId(),
                        context.status(),
                        ex);
            }
        }
    }
}
