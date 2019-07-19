package com.alfresco.consulting.service.pdf;

import com.alfresco.consulting.model.PSModel;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ActionServiceImpl;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.*;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.TempFileProvider;
import org.apache.log4j.Logger;
import org.ghost4j.Ghostscript;
import org.ghost4j.GhostscriptException;

/**
 * Service to manage PDF
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public class PdfManagerServiceImpl implements PdfManagerService {

    // Logger
    private static Logger logger = Logger.getLogger(PdfManagerServiceImpl.class);

    // Alfresco services
    private ContentService contentService;
    private NodeService nodeService;
    private BehaviourFilter behaviourFilter;
    private TransactionService transactionService;
    private ActionService actionService;

    // Get GhostScript instance
    private Ghostscript gs = Ghostscript.getInstance();

    /**
     * Transform PDF file
     * @param pdfNodeRef
     * @return 0 if fail to update, 1 if update is ok
     */
    public int pdfToGenerateNodeRef(NodeRef pdfNodeRef){

        int updateSucces = 0;

        if(logger.isDebugEnabled())
        {
            logger.debug("Start gs pdf transformation for: " + pdfNodeRef.toString() );
        }

        // Get a reader and write document to a temporary file
        String readFilePath = readPdfFile(contentService, pdfNodeRef);

        // Transform the PDF file
        String transformFilePath = transformPdfFile(readFilePath);

        // Update document content with transform file
        if(transformFilePath != null)
        {
          updateSucces = updatePdfContent(pdfNodeRef, transformFilePath);
        }

        if(updateSucces == 1){
            // Run Metadata extractor
            doExtractMetadata(pdfNodeRef);
        }

        return updateSucces;
    }


    /**
     * Read the content of the document and copy it in a temporary file
     * @param pdfNodeRef
     * @return true if extraction done
     */
    private boolean doExtractMetadata(NodeRef pdfNodeRef) {

        if(logger.isDebugEnabled())
        {
            logger.debug("Do extraction metadata for: " + pdfNodeRef);
        }

        Map<String, Serializable> props = new HashMap<String, Serializable>(1);
        Action extractAction = actionService.createAction("extract-metadata", props);
        if (extractAction != null) {
            actionService.executeAction(extractAction, pdfNodeRef);
        } else {
            throw new AlfrescoRuntimeException("Could not create extract-metadata action");
        }

        if(logger.isDebugEnabled())
        {
            logger.debug("Extraction metadata done");
        }

        return true;
    }


    /**
     * Read the content of the document and copy it in a temporary file
     * @param contentService
     * @param nodeRef
     * @return A string containing absolute path of the temporary file
     */
    private String readPdfFile(ContentService contentService, NodeRef nodeRef)
    {

        if(logger.isDebugEnabled())
        {
            logger.debug("Read PDF file: " + nodeRef);
        }

        // Get content reader
        ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        // Copy pdf content into a temporary file
        File pdfTempFile = TempFileProvider.createTempFile("untransform_", ".pdf");
        contentReader.getContent(pdfTempFile);

        // Return the absolute path of the temporary file
        return pdfTempFile.getAbsolutePath();
    }


    /**
     *  the PDF file with GhostScript
     * @param readFilePath
     * @return A string containing the absolute path of the temporary file
     */
    private String transformPdfFile(String readFilePath)
    {
        boolean hasError = false;

        if(logger.isDebugEnabled())
        {
            logger.debug("Start Ghostscript transformation");
        }

        // Get a temporary file in which transform file will be stored
        File pdfTempFile = TempFileProvider.createTempFile("transform_", ".pdf");
        String transformFilePath = pdfTempFile.getAbsolutePath();

        // Prepare GhostScript interpreter parameters
        String[] gsArgs = new String[10];
        gsArgs[0] = "-dSAFER";
        gsArgs[1] = "-sDEVICE=pdfwrite";
        gsArgs[2] = "-dCompatibilityLevel=1.4";
        gsArgs[3] = "-dPDFSETTINGS=/ebook";
        gsArgs[4] = "-dNOPAUSE";
        gsArgs[5] = "-dQUIET";
        gsArgs[6] = "-dBATCH";
        gsArgs[7] = "-sOutputFile=" + transformFilePath;
        gsArgs[8] = "-f";
        gsArgs[9] = readFilePath;

        // Execute and exit interpreter
        try {
            synchronized(gs) {
                // Call interpreter operations
                gs.initialize(gsArgs);
                gs.exit();
            }
        }
        catch (GhostscriptException e)
        {
            // An exception occurs during PDF transformation
            hasError = true;
            logger.error("Fail to transform PDF file with Ghostscript due to an exception : ", e);
        }
        finally {
            // Delete interpreter instance (safer)
            try {
                Ghostscript.deleteInstance();
            }
            catch (GhostscriptException e)
            {
                // Do nothing
                logger.error("Fail to delete the Ghostscript instance due to an exception : ", e);
            }
        }

        if(!hasError)
        {
            // No error during transformation operation => return the absolute path
            return transformFilePath;
        }
        else
        {
            // Return null when error occurs during transformation
            return null;
        }
    }

    /**
     * Update document content with transform PDF file
     * @param nodeRef
     * @param transformFilePath
     * @return 0 if fail to update, 1 if update is ok
     */
    private int updatePdfContent(final NodeRef nodeRef, final  String transformFilePath)
    {

        if(logger.isDebugEnabled())
        {
            logger.debug("Update doc content");
        }

        int updateResult = 1;

        // Function to update PDF document in a transaction
        RetryingTransactionHelper.RetryingTransactionCallback<NodeRef> txnWork = new RetryingTransactionHelper.RetryingTransactionCallback<NodeRef>()
        {
            public NodeRef execute() throws Exception
            {
                // Disable behaviours
                behaviourFilter.disableBehaviour(nodeRef);

                // Update pdf document's content with transform PDF file
                File transformPdfFile = new File(transformFilePath);
                ContentWriter contentWriter = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
                contentWriter.setEncoding("UTF8");
                contentWriter.setMimetype(MimetypeMap.MIMETYPE_PDF);
                contentWriter.putContent(transformPdfFile);

                // Enable behaviours
                behaviourFilter.enableBehaviour(nodeRef);

                // Add aspect ps:transformedPdfFile
                nodeService.addAspect(nodeRef, PSModel.ASPECT_TRANSFORMEDPDFFILE, null);

                return nodeRef;
            }
        };

        try {
            // Update PDF content
           // NodeRef finalNodeRef = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false, true );

            behaviourFilter.disableBehaviour(nodeRef);

            // Update pdf document's content with transform PDF file
            File transformPdfFile = new File(transformFilePath);
            ContentWriter contentWriter = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
            contentWriter.setEncoding("UTF8");
            contentWriter.setMimetype(MimetypeMap.MIMETYPE_PDF);
            contentWriter.putContent(transformPdfFile);

            // Enable behaviours
            behaviourFilter.enableBehaviour(nodeRef);

            // Add aspect ps:transformedPdfFile
            nodeService.addAspect(nodeRef, PSModel.ASPECT_TRANSFORMEDPDFFILE, null);

        }
        catch(Exception e)
        {
            // An exception occurs during content update
            logger.error("Fail to update PDF document content due to an exception : ", e);
        }

        return updateResult;
    }




    // SETTER
    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setBehaviourFilter(BehaviourFilter behaviourFilter) {
        this.behaviourFilter = behaviourFilter;
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public void setActionService(ActionService actionService) {
        this.actionService = actionService;
    }
}
