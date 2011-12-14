package jobs;

import play.Logger;
import play.jobs.Job;
import services.PCALinkageService;

public class PCALinkageJob extends Job {

    @Override
    public void doJob() {
        Logger.info("START: Updating PCA links...");
        int filesProcessed = PCALinkageService.updateFileLinkage();
        Logger.info("STOP: Updated PCA links for %d files.", filesProcessed);
    }

}
