package controllers;

import jobs.ImportPCAProgramsJob;
import jobs.ParseRepositoryJob;
import models.GITRepository;
import play.libs.F.Action;
import play.libs.F.Promise;
import play.mvc.Controller;
import services.RepositoryService;

/**
 * Controller for the page that manages the GIT repository.
 * 
 * @author james.kennedy
 * 
 */
public class Repository extends Controller {

    private static Promise promise;

    public static void index() {
        String status = determineStatus();
        GITRepository currentRepository = GITRepository.getMainRepository();
        render(status, currentRepository);
    }

    public static void changeRepository(final String repositoryPath, final String repositoryName) {
        GITRepository mainRepository = GITRepository.getMainRepository();

        mainRepository.location = repositoryPath;
        mainRepository.name = repositoryName;
        mainRepository.lastCommitParsed = null;
        mainRepository.save();

        redirect("Repository.index");
    }

    public synchronized static void update() {
        String status = determineStatus();

        if (status.equals("Idle")) {
            ImportPCAProgramsJob importProgramJob = new ImportPCAProgramsJob();
            importProgramJob.now().onRedeem(new Action() {

                @Override
                public void invoke(final Object result) {
                    ParseRepositoryJob parseJob = new ParseRepositoryJob();
                    promise = parseJob.now();
                }

            });
        }

        redirect("Repository.index");
    }

    /**
     * Completely erase all repository data.
     */
    public synchronized static void wipe() {
        RepositoryService.wipeRepositoryData();

        index();
    }

    private static String determineStatus() {
        String status = "Idle";

        if (null != promise) {
            synchronized (promise) {
                if (promise.isCancelled()) {
                    status = "Cancelled";
                    promise = null;
                } else if (promise.isDone()) {
                    status = "Done";
                    promise = null;
                } else {
                    status = "Working...";
                }
            }
        }
        return status;
    }

}
