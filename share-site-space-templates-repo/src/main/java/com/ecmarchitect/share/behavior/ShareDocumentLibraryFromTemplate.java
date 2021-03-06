package com.ecmarchitect.share.behavior;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;

public class ShareDocumentLibraryFromTemplate implements NodeServicePolicies.OnCreateNodePolicy {

	// are these not already defined by Alfresco somewhere?
	private static final String SITE_MODEL_URI = "http://www.alfresco.org/model/site/1.0";
	private static final QName TYPE_SITE = QName.createQName(SITE_MODEL_URI, "site");
	private static final QName PROP_SITE_PRESET = QName.createQName(SITE_MODEL_URI, "sitePreset");
	private static final QName ASPECT_SITE_CONTAINER = QName.createQName(SITE_MODEL_URI, "siteContainer");
	private static final QName PROP_SITE_COMPONENT_ID = QName.createQName(SITE_MODEL_URI, "componentId");
	
	// Dependencies
    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private FileFolderService fileFolderService;
    private SearchService searchService;

    // Behaviors
    private Behaviour onCreateNode;
    
    private Logger logger = Logger.getLogger(ShareDocumentLibraryFromTemplate.class);
    
    public void init() {
    	if (logger.isDebugEnabled()) logger.debug("Initializing rateable behaviors");
    	
        // Create behaviors
        this.onCreateNode = new JavaBehaviour(this, "onCreateNode", NotificationFrequency.TRANSACTION_COMMIT);

        // Bind behaviors to node policies
        this.policyComponent.bindClassBehaviour(QName.createQName(NamespaceService.ALFRESCO_URI, "onCreateNode"), TYPE_SITE, this.onCreateNode);
    }
    
	public void onCreateNode(ChildAssociationRef childAssocRef) {
		if (logger.isDebugEnabled()) logger.debug("Inside onCreateNode for ShareDocumentLibraryFromTemplate");
		
		NodeRef siteFolder = childAssocRef.getChildRef();
		
		if (!nodeService.exists(siteFolder)) {
			logger.debug("Site folder doesn't exist yet");
			return;
		}
		
		//grab the site preset value
		String sitePreset = (String) nodeService.getProperty(siteFolder, PROP_SITE_PRESET);
		
		//see if there is a folder in the Space Templates folder of the same name
		String query = "+PATH:\"/app:company_home/app:dictionary/app:space_templates/*\" +@cm\\:name:\"" + sitePreset + "\"";
		ResultSet rs = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, query);
		
		//if not, bail, there is nothing more to do
		if (rs.length() <= 0) {
			logger.debug("Found no space templates for: " + sitePreset);
			return;
		}

		NodeRef spaceTemplate = rs.getNodeRef(0);
		if (!nodeService.exists(spaceTemplate)) {
			logger.debug("Space template doesn't exist");
			return;
		}

		//otherwise, create the documentLibrary folder as a child of this site folder
		//using the space template found above
		NodeRef documentLibrary;
		try {
			documentLibrary = fileFolderService.copy(spaceTemplate, siteFolder, "documentLibrary").getNodeRef();

			logger.debug("Successfully created the document library node from a template");

			//add the site container aspect, set the descriptions, set the component ID
			Map<QName, Serializable> props = new HashMap<QName, Serializable>();
			props.put(ContentModel.PROP_DESCRIPTION, "Document Library");
			props.put(PROP_SITE_COMPONENT_ID, "documentLibrary");
			nodeService.addAspect(documentLibrary, ASPECT_SITE_CONTAINER, props);

		} catch (FileExistsException e) {
			logger.debug("The document library node already exists. Each child needs to be copied.");
			// TODO implement this piece
			//iterate over the children of the source space template and copy them into the target
			
		} catch (FileNotFoundException e) {
			//can't find the space template, just bail
			logger.warn("Share site tried to use a space template, but the source space template could not be found.");
		}
		
	}

	public NodeService getNodeService() {
		return nodeService;
	}


	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}


	public PolicyComponent getPolicyComponent() {
		return policyComponent;
	}


	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}

	public FileFolderService getFileFolderService() {
		return fileFolderService;
	}

	public void setFileFolderService(FileFolderService fileFolderService) {
		this.fileFolderService = fileFolderService;
	}

	public SearchService getSearchService() {
		return searchService;
	}

	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

}

