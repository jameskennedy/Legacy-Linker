package jobs;

import java.io.File;

import play.jobs.Job;
import services.PCALinkageService;

public class ImportPCAProgramsJob extends Job {

    @Override
    public void doJob() {
        PCALinkageService.loadProgramsFromDisk(new File("/Users/james.kennedy/Documents/workspace/MartensSource"));
    }

}
