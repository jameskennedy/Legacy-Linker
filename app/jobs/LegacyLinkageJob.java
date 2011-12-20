package jobs;

import java.util.List;

import models.RepoFile;
import play.Logger;
import play.jobs.Job;
import services.LegacyLinkageService;

public class LegacyLinkageJob extends Job {

    private static Boolean inProgress = Boolean.FALSE;

    @Override
    public void doJob() {
        synchronized (inProgress) {
            if (inProgress) {
                Logger.info("SKIP: Skipped updating PCA links since job already in progress.");
                return;
            }
            inProgress = Boolean.TRUE;
        }

        try {
            Logger.info("START: Updating PCA links...");
            int filesProcessed = LegacyLinkageService.updateFileLinkage();
            Logger.info("STOP: Updated PCA links for %d files.", filesProcessed);
        } finally {
            inProgress = Boolean.FALSE;
        }

        List<RepoFile> filesToLink = RepoFile.find("byLinkUpdateNeeded", Boolean.TRUE).fetch();
        if (!filesToLink.isEmpty()) {
            // Reschedule self while there are updates left to be done
            this.now();
        } else {
            Logger.info("STOP: All programs have been linked.");
            new AssignAuthorsJob().now();
        }

    }

}
