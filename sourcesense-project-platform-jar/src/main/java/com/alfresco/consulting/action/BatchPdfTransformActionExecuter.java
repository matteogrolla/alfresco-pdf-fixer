package com.alfresco.consulting.action;

import com.alfresco.consulting.batch.GeneratePdfsBatch;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Action to redo all PDF files in batch
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public class BatchPdfTransformActionExecuter extends ActionExecuterAbstractBase {

    // Logger
    private static Logger logger = Logger.getLogger(BatchPdfTransformActionExecuter.class);

    private GeneratePdfsBatch generatePdfsBatch;


    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        // No paramater right now
    }

    /* Executer l'action */
    @Override
    protected void executeImpl(Action action, NodeRef nodeRef) {

        logger.info("Start action PdfTransformBatchAction");

        Integer batchThreads = (Integer) action.getParameterValue("batchThreads");
        Integer batchSize = (Integer) action.getParameterValue("batchSize");
        Integer maxCountTest = (Integer) action.getParameterValue("maxCountTest");

        generatePdfsBatch.setConfiguration(batchThreads,batchSize,maxCountTest);
        generatePdfsBatch.startGeneratePdfsBatch();

        logger.info("End action PdfTransformBatchAction");
    }

    public void setGeneratePdfsBatch(GeneratePdfsBatch generatePdfsBatch) {
        this.generatePdfsBatch = generatePdfsBatch;
    }
}
