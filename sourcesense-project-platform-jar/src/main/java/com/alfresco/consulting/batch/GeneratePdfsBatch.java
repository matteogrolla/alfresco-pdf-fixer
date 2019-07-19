package com.alfresco.consulting.batch;

import com.alfresco.consulting.model.PSModel;
import com.alfresco.consulting.service.pdf.PdfManagerService;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.domain.mimetype.MimetypeDAO;
import org.alfresco.repo.domain.node.NodeDAO;
import org.alfresco.repo.domain.patch.PatchDAO;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Batch using the batch processor to redo all PDF file
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public class GeneratePdfsBatch {

    // Default value batch
    private static final int DEFAULT_BATCH_THREADS = 1;
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int DEFAULT_MAX_COUNT_TEXT = -1; //Disabled

    // Logger
    private static Log logger = LogFactory.getLog(GeneratePdfsBatch.class);

    // DAO Alfresco
    private NodeDAO nodeDAO;
    private PatchDAO patchDAO;
    private MimetypeDAO mimetypeDAO;

    // Services
    private NodeService nodeService;
    private TransactionService transactionService;

    // Custom service
    private PdfManagerService pdfManagerService;

    // BatchProcessor
    private int batchThreads = DEFAULT_BATCH_THREADS;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int maxCountTest = DEFAULT_MAX_COUNT_TEXT;
    private int counterTest = 0;

    private int count;

    /**
     * Run the migration
     * @return
     * @throws Exception
     */
    public String startGeneratePdfsBatch()
    {
        //rebuild count
        count = batchSize * batchThreads;
        counterTest = 0;

        //Show configuration
        logger.info("**** Batch GeneratePdfsBatch ************");
        logger.info("Start Batch with configuration: ");
        logger.info("batchThreads: "+batchThreads);
        logger.info("batchSize: "+batchSize);
        logger.info("maxCountTest: "+maxCountTest);
        logger.info("****");

        /**
         * Get nodes and create packet
         */
        BatchProcessWorkProvider<Long> workProvider = new BatchProcessWorkProvider<Long>()
        {
            long maxNodeId = nodeDAO.getMaxNodeId();
            long minSearchNodeId = 0;
            long maxSearchNodeId = count;

            Pair<Long, String> qnameMimetype = mimetypeDAO.getMimetype(MimetypeMap.MIMETYPE_PDF);

            @Override
            public int getTotalEstimatedWorkSize() {

                // Get total node - deprecated
                // Method for getting the number of doc with mimetype doesn't exist
                long totalCount = patchDAO.getMaxAdmNodeID();

                return (int)totalCount;
            }

            @Override
            public Collection<Long> getNextWork()
            {
                List<Long> nodeIds = Collections.emptyList();

                while (nodeIds.isEmpty() && minSearchNodeId < maxNodeId ) {
                    //logger.info("maxNodeId: "+maxNodeId+"\n minSearchNodeId: "+ minSearchNodeId + "\n maxSearchNodeId: " + maxSearchNodeId );
                    if(maxSearchNodeId > maxNodeId){
                        maxSearchNodeId = maxNodeId+1;
                    }

                    // Get all nodes for this mimetype
                    nodeIds = patchDAO.getNodesByContentPropertyMimetypeId(qnameMimetype.getFirst(), minSearchNodeId, maxSearchNodeId);

                    minSearchNodeId = minSearchNodeId + count;
                    maxSearchNodeId = maxSearchNodeId + count;
                }

                //Check if the maxCountTest is enabled
                if(maxCountTest > 0) {
                    counterTest += nodeIds.size();
                    //If we reach the maxCountTest, we stop the job
                    if(counterTest >= maxCountTest){
                        minSearchNodeId = maxNodeId;
                    }
                }

                return nodeIds;
            }
        };


        /**
         * Create the BatchProcessor
         */
        BatchProcessor<Long> batchProcessor = new BatchProcessor<Long>(
                "Batch GeneratePdfsBatch",
                transactionService.getRetryingTransactionHelper(),
                workProvider,
                batchThreads,
                batchSize,
                null,
                logger,
                1000);


        // Worker BatchProcessor
        BatchProcessor.BatchProcessWorker<Long> worker = new BatchProcessor.BatchProcessWorker<Long>()
        {
            public void afterProcess() throws Throwable {
            }

            public void beforeProcess() throws Throwable {
                //Execute batch with admin user (or another?)
                AuthenticationUtil.setFullyAuthenticatedUser("admin");
            }

            public String getIdentifier(Long nodeId)
            {
                return Long.toString(nodeId);
            }

            public void process(Long nodeId) throws Throwable
            {

                //Get the nodeRef
                NodeRef.Status nodeIdStatus = nodeDAO.getNodeIdStatus(nodeId);
                NodeRef docNodeRef = nodeIdStatus.getNodeRef();

                // Migrate only noderef belonging to workspace://SpacesStore/
                if(!nodeIdStatus.isDeleted() && docNodeRef.toString().contains("workspace://SpacesStore/"))
                {

                    if(logger.isDebugEnabled()){
                        logger.debug("Process docNodeRef: "+ docNodeRef);
                    }

                    //(double) check if the aspect is set to the given nodeRef
                    if(nodeService.hasAspect(docNodeRef, PSModel.ASPECT_TRANSFORMEDPDFFILE)){
                        logger.debug("PDFs transformation already done: "+ docNodeRef);
                        return;
                    }

                    pdfManagerService.pdfToGenerateNodeRef(docNodeRef);

                    logger.info("PDFs transformation done for: "+ docNodeRef);


                }
            }

        };

        /**
         * Start the batchProcessor
         */
        batchProcessor.process(worker, true);

        return "Batch GeneratePdfsBatch: done";
    }



    // SETTER
    public void setNodeDAO(NodeDAO nodeDAO) {
        this.nodeDAO = nodeDAO;
    }
    public void setPatchDAO(PatchDAO patchDAO) {
        this.patchDAO = patchDAO;
    }
    public void setMimetypeDAO(MimetypeDAO mimetypeDAO) { this.mimetypeDAO = mimetypeDAO; }
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public void setTransactionService(TransactionService transactionService) { this.transactionService = transactionService; }
    public void setPdfManagerService(PdfManagerService pdfManagerService) { this.pdfManagerService = pdfManagerService; }
    public void setBatchThreads(int batchThreads) {
        this.batchThreads = batchThreads;
    }
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    public void setMaxCountTest(int maxCountTest) {
        this.maxCountTest = maxCountTest;
    }

    public void setConfiguration(Integer batchThreads, Integer batchSize, Integer maxCountTest) {

        if(batchThreads != null){
            this.batchThreads = batchThreads;
        }else{
            this.batchThreads = DEFAULT_BATCH_THREADS;
        }

        if(batchSize != null){
            this.batchSize = batchSize;
        }else{
            this.batchSize = DEFAULT_BATCH_SIZE;
        }

        if(maxCountTest != null){
            this.maxCountTest = maxCountTest;
        }else{
            this.maxCountTest = DEFAULT_MAX_COUNT_TEXT;
        }
    }

}



