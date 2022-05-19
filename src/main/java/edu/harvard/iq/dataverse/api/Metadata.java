/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import javax.ws.rs.core.Response;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.harvest.server.OAISet;
import org.apache.solr.client.solrj.SolrServerException;

/**
 *
 * @author Leonid Andreev
 * 
 */

@Path("admin/metadata")
public class Metadata extends AbstractApiBean {
    private static final Logger logger = Logger.getLogger(Metadata.class.getName());

    @EJB
    OAISetServiceBean oaiSetService;

    @EJB
    DatasetServiceBean datasetService;

    // The following 2 commands start export all jobs in the background, 
    // asynchronously. 
    // (These API calls should probably not be here;
    // May be under "/admin" somewhere?)
    // exportAll will attempt to go through all the published, local 
    // datasets *that haven't been exported yet* - which is determined by
    // checking the lastexporttime value of the dataset; if it's null, or < the last 
    // publication date = "unexported" - and export them. 
    @GET
    @Path("/exportAll")
    @Produces("application/json")
    public Response exportAll() {
        datasetService.exportAllAsync();
        return this.accepted();
    }
    
    // reExportAll will FORCE A FULL REEXPORT on every published, local 
    // dataset, regardless of the lastexporttime value.
    @GET
    @Path("/reExportAll")
    @Produces("application/json")
    public Response reExportAll() {
        datasetService.reExportAllAsync();
        return this.accepted();
    }

    @GET
    @Path("reExportDataset")
    public Response indexDatasetByPersistentId(@QueryParam("persistentId") String persistentId) {
        if (persistentId == null) {
            return error(Response.Status.BAD_REQUEST, "No persistent id given.");
        }
        Dataset dataset = null;
        try {
            dataset = datasetService.findByGlobalId(persistentId);
        } catch (Exception ex) {
            return error(Response.Status.BAD_REQUEST, "Problem looking up dataset with persistent id \"" + persistentId + "\". Error: " + ex.getMessage());
        }
        if (dataset != null) {
            datasetService.reExportDatasetAsync(dataset);
            return ok("export started");
        } else {
            return error(Response.Status.BAD_REQUEST, "Could not find dataset with persistent id " + persistentId);
        }
    }

    /**
     * initial attempt at triggering indexing/creation/population of a OAI set without going throught
     * the UI.
     */
    @PUT
    @Path("/exportOAI/{specname}")
    public Response exportOaiSet( @PathParam("specname") String spec )
    {
	    // assuming this belongs here (because it's a metadata export), but open to moving it elsewhere
	    OAISet set = null;
	    try
	    {
		    set = oaiSetService.findBySpec(spec);
	    }
	    catch(Exception ex)
	    {
		    return error(Response.Status.BAD_REQUEST,"bad request / invalid OAI set");
	    }
	    if ( null == set )
	    {
		    return error(Response.Status.NOT_FOUND, "unable to find specified OAI set");
	    }
	    try
	    {
		    oaiSetService.setUpdateInProgress( set.getId() );
		    oaiSetService.exportOaiSetAsync(set);
		    return ok("export started");
	    }
	    catch( Exception ex )
	    {
		    return error(Response.Status.BAD_REQUEST, "problem exporting OAI set");
	    }
    }
}
