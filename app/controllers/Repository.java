package controllers;

import jobs.AssignAuthorsJob;
import jobs.MoveAuthorsToUsersJob;
import jobs.ParseRepositoryJob;
import models.GITRepository;
import models.PCAProgram;
import play.libs.F.Promise;
import play.mvc.Controller;
import services.LegacyLinkageService;
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
        GITRepository repository = GITRepository.getMainRepository();
        render(status, repository);
    }

    public static void changeRepository(final String repositoryPath, final String repositoryName) {
        GITRepository mainRepository = GITRepository.getMainRepository();

        mainRepository.location = repositoryPath;
        mainRepository.name = repositoryName;
        mainRepository.lastCommitParsed = null;
        mainRepository.save();

        index();
    }

    public static void syncWithRepository() {
        ParseRepositoryJob parseJob = new ParseRepositoryJob();
        promise = parseJob.now();
        index();
    }

    public static void recalculateProgramAuthors() {
        PCAProgram.em().createQuery("Update PCAProgram set needsAuthorUpdate = true").executeUpdate();
        PCAProgram.em().flush();
        AssignAuthorsJob job = new AssignAuthorsJob();
        promise = job.now();
        index();
    }

    /**
     * Completely erase all repository data and data depending on it
     */
    public static void wipeRepoData() {
        RepositoryService.wipeRepositoryData();
        index();
    }

    /**
     * Completely erase all PCA linkage data and data depending on it
     */
    public static void wipePCALinks() {
        LegacyLinkageService.wipeAllLinks();
        index();
    }

    public static void deriveAllUsers() {
        MoveAuthorsToUsersJob job = new MoveAuthorsToUsersJob();
        promise = job.now();
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
