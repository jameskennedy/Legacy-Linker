package controllers;

import git.ParseRepositoryJob;
import models.GITRepository;
import play.libs.F.Promise;
import play.mvc.Controller;
import services.RepositoryService;

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
            ParseRepositoryJob parseJob = new ParseRepositoryJob();
            promise = parseJob.now();
        }

        redirect("Repository.index");
    }

    /**
     * Completely erase all repository data.
     */
    public synchronized static void wipe() {
        RepositoryService.wipeRepositoryData();

        redirect("Repository.index");
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
