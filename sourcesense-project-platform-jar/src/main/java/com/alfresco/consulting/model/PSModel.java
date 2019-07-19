package com.alfresco.consulting.model;

import org.alfresco.service.namespace.QName;

/**
 * Alfresco PS model constants class.
 *
 * @author Sefer AKBULUT (sefer.akbulut@alfresco.com)
 */
public class PSModel {

    // Namespaces
    public static final String PS_MODEL_URI = "http://www.alfresco-ps.com/model/content/1.0";
    public static final String PS_MODEL_PREFIX = "ps";

    public static final QName ASPECT_TRANSFORMEDPDFFILE = QName.createQName(PS_MODEL_URI, "transformedPdfFile");

}
