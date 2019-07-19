package com.alfresco.consulting.service.pdf;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Interface for the PDF manager service
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public interface PdfManagerService {

    /**
     * Transform PDF file
     * @param pdfNodeRef
     * @return 0 if fail to update, 1 if update is ok
     */
    int pdfToGenerateNodeRef(NodeRef pdfNodeRef);

}
