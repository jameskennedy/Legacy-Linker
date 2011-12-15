package jobs;

import play.Logger;
import play.jobs.Job;
import services.PCALinkageService;

public class PCALinkageJob extends Job {

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
            int filesProcessed = PCALinkageService.updateFileLinkage();
            Logger.info("STOP: Updated PCA links for %d files.", filesProcessed);

            new AssignAuthorsJob().now();
        } finally {
            inProgress = Boolean.FALSE;
        }

    }

}
