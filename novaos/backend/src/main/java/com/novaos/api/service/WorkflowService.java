package com.novaos.api.service;

import com.novaos.api.entity.Workflow;
import com.novaos.api.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepository;

    public WorkflowService(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAll();
    }

    public List<Workflow> getActiveWorkflows() {
        return workflowRepository.findByActive(true);
    }

    public Workflow saveWorkflow(Workflow workflow) {
        return workflowRepository.save(workflow);
    }

    /**
     * Triggers active workflows registered for specific events (e.g. On Candidate Submit)
     */
    public void triggerWorkflowEvent(String triggerEvent, Object payload) {
        logger.info("Evaluating trigger event: {} with payload: {}", triggerEvent, payload);
        
        List<Workflow> matchingWorkflows = workflowRepository.findByActive(true);
        for (Workflow workflow : matchingWorkflows) {
            if (workflow.getTriggerEvent().equalsIgnoreCase(triggerEvent)) {
                logger.info("Executing active automated workflow: {}", workflow.getName());
                // Execution steps logic
            }
        }
    }
}
