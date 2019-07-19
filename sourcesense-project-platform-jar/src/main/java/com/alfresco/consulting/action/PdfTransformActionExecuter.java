package com.alfresco.consulting.action;

import com.alfresco.consulting.service.pdf.PdfManagerService;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Action to redo only PDF file
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public class PdfTransformActionExecuter extends ActionExecuterAbstractBase {

    // Logger
    private static Logger logger = Logger.getLogger(PdfTransformActionExecuter.class);

    // Alfresco services
    private NodeService nodeService;

    // Custom service
    private PdfManagerService pdfManagerService;

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        // No paramater right now
    }

    @Override
    protected void executeImpl(Action action, NodeRef actionedUponNodeRef) {
        if (nodeService.exists(actionedUponNodeRef) == true) {

            try {
                // Do transformation
                pdfManagerService.pdfToGenerateNodeRef(actionedUponNodeRef);

            } catch (Exception e) {
                logger.error("Transformation PDF failed", e);
                throw new AlfrescoRuntimeException("Could not generate the pdf : " + e.getMessage());
            }
        }
    }


    // Setter
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public void setPdfManagerService(PdfManagerService pdfManagerService) {
        this.pdfManagerService = pdfManagerService;
    }
}
