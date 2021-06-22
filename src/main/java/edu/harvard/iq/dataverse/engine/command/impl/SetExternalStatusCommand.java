package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetLock;
import edu.harvard.iq.dataverse.DatasetVersionUser;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.batch.util.LoggingUtil;
import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.util.BundleUtil;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrServerException;

@RequiredPermissions(Permission.EditDataset)
public class SetExternalStatusCommand extends AbstractDatasetCommand<Dataset> {

    private static final Logger logger = Logger.getLogger(SetExternalStatusCommand.class.getName());
    
    String label;
    
    public SetExternalStatusCommand(DataverseRequest aRequest, Dataset dataset, String label) {
        super(aRequest, dataset);
        this.label=label;
    }

    @Override
    public Dataset execute(CommandContext ctxt) throws CommandException {

        if (getDataset().getLatestVersion().isReleased()) {
            throw new IllegalCommandException(BundleUtil.getStringFromBundle("dataset.submit.failure.isReleased"), this);
        }
        Pattern pattern = Pattern.compile("(/^[\\w ]+$/");
        Matcher matcher = pattern.matcher(label);
        if(!matcher.matches()) {
            logger.info("Label rejected: " + label);
            throw new IllegalCommandException(BundleUtil.getStringFromBundle("dataset.submit.failure"), this);
        }
        getDataset().getLatestVersion().setExternalStatusLabel(label);
        Dataset updatedDataset = save(ctxt);
        
        return updatedDataset;
    }

    public Dataset save(CommandContext ctxt) throws CommandException {

        getDataset().getEditVersion().setLastUpdateTime(getTimestamp());
        getDataset().setModificationTime(getTimestamp());

        Dataset savedDataset = ctxt.em().merge(getDataset());
        ctxt.em().flush();

        updateDatasetUser(ctxt);

        AuthenticatedUser requestor = getUser().isAuthenticated() ? (AuthenticatedUser) getUser() : null;
        
        List<AuthenticatedUser> authUsers = ctxt.permissions().getUsersWithPermissionOn(Permission.PublishDataset, savedDataset);
        for (AuthenticatedUser au : authUsers) {
            ctxt.notifications().sendNotification(au, new Timestamp(new Date().getTime()), UserNotification.Type.SUBMITTEDDS, savedDataset.getLatestVersion().getId(), "", requestor, false);
        }
        
        //  TODO: What should we do with the indexing result? Print it to the log?
        return savedDataset;
    }
    
    @Override
    public boolean onSuccess(CommandContext ctxt, Object r) {
        boolean retVal = true;
        Dataset dataset = (Dataset) r;

        try {
            Future<String> indexString = ctxt.index().indexDataset(dataset, true);
        } catch (IOException | SolrServerException e) {
            String failureLogText = "Post submit for review indexing failed. You can kickoff a re-index of this dataset with: \r\n curl http://localhost:8080/api/admin/index/datasets/" + dataset.getId().toString();
            failureLogText += "\r\n" + e.getLocalizedMessage();
            LoggingUtil.writeOnSuccessFailureLog(this, failureLogText, dataset);
            retVal = false;
        }
        return retVal;
    }

}
